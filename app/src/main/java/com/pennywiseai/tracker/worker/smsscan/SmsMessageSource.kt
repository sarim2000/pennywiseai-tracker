package com.pennywiseai.tracker.worker.smsscan

import android.content.Context
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
    /** Sends every message since [scanStartMillis] into [feed]; the pipeline closes [feed] afterwards. */
    suspend fun streamTo(feed: SendChannel<ScannedSms>, scanStartMillis: Long)
}

/** The device inbox: SMS first, then RCS (stored as MMS with a `proto:` transaction id). */
internal class DeviceSmsMessageSource(private val context: Context) : SmsMessageSource {

    /** Fast COUNT queries — avoids loading all messages before the pipeline. */
    suspend fun count(scanStartMillis: Long): Int =
        countSmsMessages(scanStartMillis) + countRcsMessages(scanStartMillis / 1000)

    override suspend fun streamTo(feed: SendChannel<ScannedSms>, scanStartMillis: Long) {
        streamSmsToChannel(feed, scanStartMillis)
        streamRcsToChannel(feed, scanStartMillis / 1000)
    }

    private companion object {
        val SMS_PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE
        )
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

    /** Streams SMS cursor rows directly into the feed channel — no intermediate list. */
    private suspend fun streamSmsToChannel(
        feed: SendChannel<ScannedSms>,
        scanStartTime: Long
    ) {
        var count = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                SMS_PROJECTION,
                "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString(), scanStartTime.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                val idIdx      = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val dateIdx    = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val bodyIdx    = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val typeIdx    = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext()) {
                    feed.send(ScannedSms(
                        id        = c.getLong(idIdx),
                        sender    = c.getString(addressIdx) ?: "",
                        timestamp = c.getLong(dateIdx),
                        body      = c.getString(bodyIdx) ?: "",
                        type      = c.getInt(typeIdx)
                    ))
                    count++
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error streaming SMS", e)
        }
        Log.i(TAG, "Streamed $count SMS messages to pipeline")
    }

    /** Streams RCS messages into the feed channel. */
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
