package com.pennywiseai.tracker.worker.smsscan

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.provider.Telephony
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel

private const val TAG = "OptimizedSmsReaderWorker"

/** One inbox message as the scan pipeline sees it, whichever provider it came from. */
internal data class ScannedSms(
    val id: Long,
    val sender: String,
    val timestamp: Long,
    val body: String,
    val type: Int
)

/** Where a scan's messages come from. A separate seam so the pipeline can be tested without a device inbox. */
internal interface SmsMessageSource {
    /**
     * Sends every message since [scanStartMillis] into [feed]; the pipeline closes [feed]
     * afterwards. A row that can't be read is skipped and reported to [onUnreadable].
     */
    suspend fun streamTo(feed: SendChannel<ScannedSms>, scanStartMillis: Long, onUnreadable: () -> Unit)
}

/** The device inbox: SMS first, then RCS (stored as MMS with a `proto:` transaction id). */
internal class DeviceSmsMessageSource(private val context: Context) : SmsMessageSource {

    /** Fast COUNT queries — avoids loading all messages before the pipeline. */
    suspend fun count(scanStartMillis: Long): Int =
        countSmsMessages(scanStartMillis) + countRcsMessages(scanStartMillis / 1000)

    override suspend fun streamTo(feed: SendChannel<ScannedSms>, scanStartMillis: Long, onUnreadable: () -> Unit) {
        streamSmsToChannel(feed, scanStartMillis, onUnreadable)
        streamRcsToChannel(feed, scanStartMillis / 1000)
    }

    /** Fast COUNT-only query — reads zero message bodies. */
    private suspend fun countSmsMessages(scanStartTime: Long): Int {
        var count = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("COUNT(*)"),
                "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) count = c.getInt(0)
            }
        } catch (e: Exception) {
            // Lost access isn't an empty inbox: let the scan fail instead of "finding" nothing.
            if (e is CancellationException || e is SecurityException) throw e
            Log.e(TAG, "Error counting SMS", e)
        }
        return count
    }

    /** Robust query for RCS messages. */
    private suspend fun countRcsMessages(scanStartSeconds: Long): Int {
        try {
            context.contentResolver.query(
                "content://mms".toUri(),
                arrayOf("COUNT(*)"),
                "date >= ? AND tr_id LIKE 'proto:%'",
                arrayOf(scanStartSeconds.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    return c.getInt(0)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Fast RCS count failed, falling back to cursor iteration: ${e.message}")
        }

        var count = 0
        try {
            context.contentResolver.query(
                "content://mms".toUri(),
                arrayOf("tr_id"),
                "date >= ?",
                arrayOf(scanStartSeconds.toString()),
                null
            )?.use { c ->
                val trIdIdx = c.getColumnIndex("tr_id")
                if (trIdIdx >= 0) {
                    while (c.moveToNext()) {
                        val trId = c.getString(trIdIdx)
                        if (trId != null && trId.startsWith("proto:")) {
                            count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error counting RCS via fallback scan", e)
        }
        return count
    }

    /**
     * Streams SMS cursor rows directly into the feed channel — no intermediate list.
     * A read that breaks off throws: ending the feed quietly would pass for the whole
     * inbox, record a success and move the incremental cursor past messages never read.
     */
    private suspend fun streamSmsToChannel(
        feed: SendChannel<ScannedSms>,
        scanStartTime: Long,
        onUnreadable: () -> Unit,
    ) {
        var count = 0
        try {
            count = streamSmsSkippingBadRows({ projection, after, only -> querySms(projection, scanStartTime, after, only) }, feed, onUnreadable)
        } catch (e: Exception) {
            if (e !is CancellationException) Log.e(TAG, "SMS read broke off", e)
            throw e
        }
        Log.i(TAG, "Streamed $count SMS messages to pipeline")
    }

    /** Inbox SMS since [scanStartTime], as [SmsQuery] asks; oldest first, a stable order to resume in. */
    private fun querySms(projection: Array<String>, scanStartTime: Long, after: SmsRowKey?, only: SmsRowKey?): Cursor? {
        var selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?"
        var args = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString())
        if (only != null) {
            selection += " AND ${Telephony.Sms._ID} = ?"
            args += only.id.toString()
        } else if (after != null) {
            selection += " AND (${Telephony.Sms.DATE} > ? OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} > ?))"
            args += arrayOf(after.date.toString(), after.date.toString(), after.id.toString())
        }
        return context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            args,
            "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"
        )
    }

    /**
     * Streams RCS messages into the feed channel. Best effort, unlike SMS: RCS sits in the
     * undocumented MMS provider, which differs by phone, and must not fail every SMS scan.
     */
    private suspend fun streamRcsToChannel(
        feed: SendChannel<ScannedSms>,
        scanStartSeconds: Long
    ) {
        var count = 0
        try {
            context.contentResolver.query(
                "content://mms".toUri(),
                arrayOf("_id", "thread_id", "date", "tr_id", "m_id"),
                "date >= ?",
                arrayOf(scanStartSeconds.toString()),
                // Oldest first like the SMS feed, so a balance row seldom lands
                // before rows already saved and has to move them (#982).
                "date ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val messageId = c.getLong(c.getColumnIndexOrThrow("_id"))
                    val date      = c.getLong(c.getColumnIndexOrThrow("date"))
                    val trId      = c.getColumnIndex("tr_id").takeIf { it >= 0 }
                        ?.let { c.getString(it) } ?: continue
                    if (!trId.startsWith("proto:")) continue

                    val sender = extractRcsSender(trId) ?: continue
                    var text = getRcsMessageText(messageId) ?: continue
                    if (text.trim().startsWith("{")) text = extractTextFromRcsJson(text) ?: continue

                    feed.send(ScannedSms(messageId, sender, date * 1000, text, Telephony.Sms.MESSAGE_TYPE_INBOX))
                    count++
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error streaming RCS", e)
        }
        Log.i(TAG, "Streamed $count RCS messages to pipeline")
    }

    // ─── RCS helpers ──────────────────────────────────────────────────────────

    private fun extractRcsSender(trId: String): String? = try {
        val decoded = String(android.util.Base64.decode(trId.removePrefix("proto:"), android.util.Base64.DEFAULT))
        Regex("""([a-z_]+)_[a-z0-9]+_agent@rbm\.goog""").find(decoded)?.let { m ->
            return m.groupValues[1].split("_")
                .joinToString(" ") { if (it.isNotEmpty()) it.substring(0, 1).uppercase() + it.substring(1) else it }
        }
        Regex("""[\x12\x1a][\x00-\x20]([A-Za-z][A-Za-z\s]+)""").find(decoded)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.length in 4..49) return name
        }
        null
    } catch (_: Exception) { null }

    private fun getRcsMessageText(messageId: Long): String? = try {
        context.contentResolver.query(
            "content://mms/part".toUri(), null, "mid = ?", arrayOf(messageId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val ct = c.getColumnIndex("ct").takeIf { it >= 0 }?.let { c.getString(it) } ?: continue
                if (!ct.startsWith("text/") && ct != "application/smil") continue
                c.getColumnIndex("text").takeIf { it >= 0 }?.let { idx ->
                    c.getString(idx)?.takeIf { it.isNotEmpty() }?.let { return it }
                }
                val partId = c.getLong(c.getColumnIndexOrThrow("_id"))
                try {
                    context.contentResolver
                        .openInputStream("content://mms/part/$partId".toUri())
                        ?.bufferedReader()?.use { it.readText() }
                        ?.takeIf { it.isNotEmpty() }?.let { return it }
                } catch (_: Exception) {}
            }
            null
        }
    } catch (_: Exception) { null }

    private fun extractTextFromRcsJson(json: String): String? = try {
        val obj = org.json.JSONObject(json)
        obj.optString("text").takeIf { it.isNotEmpty() }
            ?: obj.optJSONObject("message")?.optString("text")?.takeIf { it.isNotEmpty() }
            ?: run {
                val texts    = mutableListOf<String>()
                val skipKeys = setOf("media", "suggestions", "postback", "urlAction")
                val textKeys = listOf("text", "message", "body", "title", "description", "content", "caption")
                fun extract(any: Any?, depth: Int = 0) {
                    if (depth > 10) return
                    when (any) {
                        is org.json.JSONObject -> {
                            textKeys.forEach { k ->
                                any.optString(k).takeIf { it.isNotEmpty() && !it.startsWith("{") }
                                    ?.let { texts.add(it) }
                            }
                            any.keys().forEach { k ->
                                if (k !in skipKeys) try { extract(any.get(k), depth + 1) } catch (_: Exception) {}
                            }
                        }
                        is org.json.JSONArray -> for (i in 0 until any.length()) extract(any.get(i), depth + 1)
                    }
                }
                extract(obj)
                texts.distinct().joinToString(" | ").takeIf { it.isNotEmpty() }
            }
    } catch (_: Exception) { json }
}

internal val SMS_PROJECTION = arrayOf(
    Telephony.Sms._ID,
    Telephony.Sms.ADDRESS,
    Telephony.Sms.DATE,
    Telephony.Sms.BODY,
    Telephony.Sms.TYPE
)

/** Just a row's place in the feed's order: small enough to load where the full row can't. */
internal val SMS_KEY_PROJECTION = arrayOf(Telephony.Sms._ID, Telephony.Sms.DATE)

/** Times the SMS read may break off at one place and resume there before it calls the inbox broken. */
private const val MAX_RESUMES = 20

/** Where a read is in the feed's order (date, then _id). */
internal data class SmsRowKey(val date: Long, val id: Long)

/**
 * Queries inbox SMS in [projection]: just the row at [only] when it's given, else the rows
 * after [after] (all when null), oldest first by date, then _id.
 */
internal typealias SmsQuery = (projection: Array<String>, after: SmsRowKey?, only: SmsRowKey?) -> Cursor?

private class NoSmsCursor : IllegalStateException("SMS query returned no cursor")

/**
 * Streams the inbox from [query] into [feed] and returns how many messages it sent. When
 * the read breaks off (a row too big for the cursor window throws from moveToNext(), or
 * from the query when it comes first), a key-only query finds the next row and that row
 * is read on its own. Only if it fails there too is it reported to [onUnreadable] and
 * stepped over, so one bad row can't fail every later scan; otherwise the read resumes
 * where it was. A provider that can't list keys, no cursor, and lost access all throw,
 * and the scan keeps its cursor.
 */
internal suspend fun streamSmsSkippingBadRows(
    query: SmsQuery,
    feed: SendChannel<ScannedSms>,
    onUnreadable: () -> Unit,
): Int {
    var after: SmsRowKey? = null
    var count = 0
    // Counted per place: a read that moved on (a skipped row too) made progress, so an
    // inbox with many bad rows still reads through, and only a read stuck in one place fails.
    var resumes = 0
    var resumedAt: SmsRowKey? = null
    while (true) {
        val error = try {
            // No cursor means the provider failed, not an empty inbox (that is a cursor
            // with no rows); reading it as empty would advance the scan cursor.
            val cursor = query(SMS_PROJECTION, after, null) ?: throw NoSmsCursor()
            // Counted per row: a read that breaks off throws away streamSmsRows' own count.
            cursor.use { c ->
                streamSmsRows(c, feed, onUnreadable) { key, sent ->
                    after = key
                    if (sent) count++
                }
            }
            return count
        } catch (e: Exception) {
            if (e is CancellationException || e is SecurityException || e is NoSmsCursor) throw e
            e
        }
        if (after != resumedAt) {
            resumes = 0
            resumedAt = after
        }
        if (++resumes > MAX_RESUMES) throw error
        val next = probe(error) {
            query(SMS_KEY_PROJECTION, after, null)?.use { c ->
                if (c.moveToFirst()) SmsRowKey(date = c.getLong(1), id = c.getLong(0)) else null
            } ?: throw error
        }
        if (next != null && !rowLoads(query, next, error)) {
            Log.w(TAG, "Skipped an SMS row the cursor couldn't load: ${error.javaClass.simpleName}")
            onUnreadable()
            after = next
        }
        // Otherwise the break wasn't that row's (a passing failure, or the row is gone): read on from where it stopped.
    }
}

/** Runs [block]; a failure there is the provider's, so the read's own [error] is what throws. */
private inline fun <T> probe(error: Exception, block: () -> T): T = try {
    block()
} catch (e: Exception) {
    if (e is SecurityException) throw e
    throw error
}

/**
 * Whether [key]'s row reads on its own; true if it's gone. The provider fills the first
 * cursor window inside the query, so a row too big for it throws from the query itself.
 * Any other query failure, or no cursor, is the provider's, and throws [error].
 */
private fun rowLoads(query: SmsQuery, key: SmsRowKey, error: Exception): Boolean {
    val cursor = try {
        query(SMS_PROJECTION, null, key)
    } catch (e: Exception) {
        if (e.isRowTooBig()) return false
        if (e is SecurityException) throw e
        throw error
    } ?: throw error
    return try {
        cursor.use { c ->
            if (c.moveToFirst()) SMS_PROJECTION.forEach { c.getString(c.getColumnIndexOrThrow(it)) }
        }
        true
    } catch (e: Exception) {
        if (e is SecurityException) throw e
        false
    }
}

/**
 * SQLite's "Row too big to fit into CursorWindow" (SQLiteBlobTooBigException in the
 * provider, a plain SQLiteException once it crosses to this process). Matched by its
 * message, as the type doesn't survive the trip; anything else isn't taken for it.
 */
private fun Exception.isRowTooBig(): Boolean =
    this is SQLiteException && message?.contains("too big", ignoreCase = true) == true

/**
 * Sends each SMS row of [c] into [feed] and returns how many it sent. A row that can't be
 * read (one too big for the cursor window) is skipped and reported to [onUnreadable]:
 * failing the scan there would make every later scan fail at the same row, and new
 * messages would never be imported. A row without even a readable key, or a cursor that
 * breaks off as a whole (it throws, or stops short of the end), throws.
 */
internal suspend fun streamSmsRows(
    c: Cursor,
    feed: SendChannel<ScannedSms>,
    onUnreadable: () -> Unit,
    onRead: (key: SmsRowKey, sent: Boolean) -> Unit = { _, _ -> },
): Int {
    val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
    val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
    val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
    val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
    val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
    var count = 0
    while (c.moveToNext()) {
        val sms = try {
            ScannedSms(
                id = c.getLong(idIdx),
                sender = c.getString(addressIdx) ?: "",
                timestamp = c.getLong(dateIdx),
                body = c.getString(bodyIdx) ?: "",
                type = c.getInt(typeIdx),
            )
        } catch (e: Exception) {
            if (e is SecurityException) throw e
            // Without its key the cursor may not be on a row at all (Android 8 can move on with
            // a window that lacks it): let the caller find the row instead of guessing.
            val key = runCatching { SmsRowKey(c.getLong(dateIdx), c.getLong(idIdx)) }.getOrNull() ?: throw e
            Log.w(TAG, "Skipped an SMS row that couldn't be read: ${e.javaClass.simpleName}")
            onUnreadable()
            onRead(key, false)
            continue
        }
        onRead(SmsRowKey(sms.timestamp, sms.id), true)
        feed.send(sms)
        count++
    }
    // moveToNext() is also false when the provider dies mid-read (the binder error is
    // swallowed); only the end may finish the read, or the scan cursor skips unread rows.
    check(c.isAfterLast) { "SMS read stopped before the end" }
    return count
}
