package com.pennywiseai.tracker.worker.smsscan

import android.database.Cursor
import android.database.sqlite.SQLiteException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SmsRowsTest {

    @Test
    fun `a row that can't be read is skipped and the rows after it still come through`() = runBlocking {
        var row = -1
        val cursor = mockk<Cursor>(relaxed = true) {
            every { getColumnIndexOrThrow(any()) } returns 0
            every { moveToNext() } answers { ++row < 3 }
            every { isAfterLast } answers { row >= 3 }
            every { getLong(any()) } answers { row.toLong() }
            every { getString(any()) } answers {
                // What a row too big for the cursor window throws, every time it's read.
                if (row == 1) throw IllegalStateException("Couldn't read row 1 from CursorWindow") else "text"
            }
        }
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)
        var unreadable = 0

        val sent = streamSmsRows(cursor, feed, onUnreadable = { unreadable++ })
        feed.close()

        assertEquals(2, sent)
        assertEquals(1, unreadable)
        assertEquals(listOf(0L, 2L), feed.toList().map { it.id })
    }

    @Test
    fun `a row the cursor can't load is stepped over and the read goes on after it`() = runBlocking {
        val windowError = IllegalStateException("Row too big to fit into CursorWindow")
        val afterKeys = mutableListOf<SmsRowKey?>()
        val sent = mutableListOf<Long>()
        var unreadable = 0
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)

        // The inbox is rows 0..4; row 2 is too big to load, so moveToNext() throws on it.
        val count = streamSmsSkippingBadRows(
            query = { projection, after, only ->
                when {
                    only != null -> rowCursor(only.id.toInt(), badRow = 2, windowError)
                    projection.size == SMS_KEY_PROJECTION.size -> keyCursor(after.next())
                    else -> {
                        afterKeys += after
                        inboxCursor(after.next(), badRow = 2, windowError)
                    }
                }
            },
            feed = feed,
            onUnreadable = { unreadable++ },
        )
        feed.close()
        for (sms in feed) sent += sms.id

        assertEquals(listOf(0L, 1L, 3L, 4L), sent)
        assertEquals(4, count)
        assertEquals(1, unreadable)
        assertEquals("resumed after the bad row", 2L, afterKeys.last()?.id)
    }

    @Test
    fun `a provider that can't load even a row's key fails the read`() = runBlocking {
        val providerError = IllegalStateException("provider died")
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)

        val thrown = runCatching {
            streamSmsSkippingBadRows({ _, _, _ -> throw providerError }, feed) {}
        }.exceptionOrNull()

        assertSame(providerError, thrown)
    }

    @Test
    fun `a read that breaks off once skips nothing and reads on`() = runBlocking {
        var failures = 1
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)
        var unreadable = 0

        val count = streamSmsSkippingBadRows(
            query = { projection, after, only ->
                when {
                    only != null -> rowCursor(only.id.toInt(), badRow = -1, IllegalStateException())
                    projection.size == SMS_KEY_PROJECTION.size -> keyCursor(after.next())
                    failures-- > 0 -> throw IllegalStateException("provider busy")
                    else -> inboxCursor(after.next(), badRow = -1, IllegalStateException())
                }
            },
            feed = feed,
            onUnreadable = { unreadable++ },
        )

        assertEquals(5, count)
        assertEquals(0, unreadable)
    }

    @Test
    fun `an inbox with more bad rows than the resume limit still reads through`() = runBlocking {
        val rows = (0L until 30L).map { SmsRowKey(date = 1_000L + it, id = it) }
        val inbox = FakeInbox(rows, tooBig = (1L..25L).toSet())
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)
        var unreadable = 0

        val count = streamSmsSkippingBadRows(inbox::query, feed, onUnreadable = { unreadable++ })
        feed.close()

        assertEquals(listOf(0L) + (26L until 30L), feed.toList().map { it.id })
        assertEquals(5, count)
        assertEquals(25, unreadable)
    }

    @Test
    fun `a read that keeps breaking off in one place still gives up`() = runBlocking {
        val rows = (0L until 5L).map { SmsRowKey(date = 1_000L + it, id = it) }
        // Row 2 breaks the read every time, yet reads fine on its own: nothing to skip.
        val inbox = FakeInbox(rows, breaksRead = setOf(2L))
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)

        val thrown = runCatching { streamSmsSkippingBadRows(inbox::query, feed) {} }.exceptionOrNull()

        assertSame(inbox.windowError, thrown)
    }

    @Test
    fun `rows that share a date resume by id, with the key read from the right columns`() = runBlocking {
        // Ids out of step with dates: the feed's order is date, then _id.
        val rows = listOf(
            SmsRowKey(date = 1_000L, id = 40L),
            SmsRowKey(date = 2_000L, id = 31L),
            SmsRowKey(date = 2_000L, id = 30L),
            SmsRowKey(date = 2_000L, id = 32L),
            SmsRowKey(date = 3_000L, id = 10L),
        )
        val inbox = FakeInbox(rows, tooBig = setOf(31L))
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)
        var unreadable = 0

        streamSmsSkippingBadRows(inbox::query, feed, onUnreadable = { unreadable++ })
        feed.close()

        assertEquals(listOf(40L, 30L, 32L, 10L), feed.toList().map { it.id })
        assertEquals(1, unreadable)
        assertEquals(SmsRowKey(date = 2_000L, id = 31L), inbox.resumedAfter.last())
    }

    @Test
    fun `a row too big for even its own query is skipped and the rows after it still come through`() = runBlocking {
        val rows = (0L until 5L).map { SmsRowKey(date = 1_000L + it, id = it) }
        val inbox = FakeInbox(rows, tooBig = setOf(2L), ownQueryError = RowTooBig())
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)
        var unreadable = 0

        streamSmsSkippingBadRows(inbox::query, feed, onUnreadable = { unreadable++ })
        feed.close()

        assertEquals(listOf(0L, 1L, 3L, 4L), feed.toList().map { it.id })
        assertEquals(1, unreadable)
    }

    @Test
    fun `any other failure of a row's own query fails the read`() = runBlocking {
        val rows = (0L until 5L).map { SmsRowKey(date = 1_000L + it, id = it) }
        val inbox = FakeInbox(rows, tooBig = setOf(2L), ownQueryError = IllegalStateException("provider died"))
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)

        val thrown = runCatching { streamSmsSkippingBadRows(inbox::query, feed) {} }.exceptionOrNull()

        assertSame("the read's own error", inbox.windowError, thrown)
    }

    @Test
    fun `a read that stops short of the end without throwing reads on from where it stopped`() = runBlocking {
        val rows = (0L until 5L).map { SmsRowKey(date = 1_000L + it, id = it) }
        val inbox = FakeInbox(rows, dropsAt = setOf(3L))
        val feed = Channel<ScannedSms>(Channel.UNLIMITED)
        var unreadable = 0

        val count = streamSmsSkippingBadRows(inbox::query, feed, onUnreadable = { unreadable++ })
        feed.close()

        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), feed.toList().map { it.id })
        assertEquals(5, count)
        assertEquals(0, unreadable)
        assertEquals(SmsRowKey(date = 1_002L, id = 2L), inbox.resumedAfter.last())
    }

    private fun SmsRowKey?.next() = this?.let { it.id.toInt() + 1 } ?: 0

    /** Row [id] read on its own; reading [badRow] throws [error]. */
    private fun rowCursor(id: Int, badRow: Int, error: Exception): Cursor = mockk(relaxed = true) {
        every { moveToFirst() } returns true
        every { getString(any()) } answers { if (id == badRow) throw error else "text" }
    }

    /** Rows [from]..4 with every column; moving onto [badRow] throws [error]. */
    private fun inboxCursor(from: Int, badRow: Int, error: Exception): Cursor {
        var row = from - 1
        return mockk(relaxed = true) {
            every { getColumnIndexOrThrow(any()) } answers { SMS_PROJECTION.indexOf(firstArg()) }
            every { moveToNext() } answers { if (++row == badRow) throw error else row < 5 }
            every { isAfterLast } answers { row >= 5 }
            every { getLong(any()) } answers { row.toLong() }
            every { getString(any()) } returns "text"
        }
    }

    /** Row [from]'s key: _id then date. */
    private fun keyCursor(from: Int): Cursor = mockk(relaxed = true) {
        every { moveToFirst() } returns (from < 5)
        every { getLong(any()) } returns from.toLong()
    }
}

private suspend fun <T> Channel<T>.toList(): List<T> = buildList { for (item in this@toList) add(item) }

/**
 * An inbox that answers [SmsQuery] the way the SMS provider does: rows in date, then _id
 * order, each column where its projection puts it. Moving onto a [tooBig] row throws, and
 * so does reading it on its own, or querying it on its own when [ownQueryError] is given.
 * A [breaksRead] row breaks the read but loads on its own.
 */
private class FakeInbox(
    rows: List<SmsRowKey>,
    private val tooBig: Set<Long> = emptySet(),
    private val breaksRead: Set<Long> = emptySet(),
    private val ownQueryError: Exception? = null,
    dropsAt: Set<Long> = emptySet(),
) {
    private val dropsAt = dropsAt.toMutableSet()
    val windowError = IllegalStateException("Row too big to fit into CursorWindow")
    val resumedAfter = mutableListOf<SmsRowKey?>()
    private val rows = rows.sortedWith(compareBy({ it.date }, { it.id }))

    fun query(projection: Array<String>, after: SmsRowKey?, only: SmsRowKey?): Cursor {
        val keysOnly = projection.contentEquals(SMS_KEY_PROJECTION)
        if (only != null && only.id in tooBig && ownQueryError != null) throw ownQueryError
        if (!keysOnly && only == null) resumedAfter += after
        val selected = when {
            only != null -> rows.filter { it.id == only.id }
            after != null -> rows.filter { it.date > after.date || (it.date == after.date && it.id > after.id) }
            else -> rows
        }
        var pos = -1
        fun moveTo(next: Int): Boolean {
            pos = next
            val row = selected.getOrNull(pos) ?: return false
            if (!keysOnly && only == null && dropsAt.remove(row.id)) {
                // A provider that dies mid-read: Android logs the binder error, moves to -1, returns false.
                pos = -1
                return false
            }
            if (!keysOnly && (row.id in tooBig || (only == null && row.id in breaksRead))) throw windowError
            return true
        }
        return mockk(relaxed = true) {
            every { getColumnIndexOrThrow(any()) } answers { projection.indexOf(firstArg()) }
            every { moveToNext() } answers { moveTo(pos + 1) }
            every { moveToFirst() } answers { moveTo(0) }
            every { isAfterLast } answers { pos >= selected.size }
            every { getLong(any()) } answers {
                val row = selected[pos]
                when (projection[firstArg()]) {
                    SMS_KEY_PROJECTION[0] -> row.id
                    SMS_KEY_PROJECTION[1] -> row.date
                    else -> 0L
                }
            }
            every { getString(any()) } returns "text"
        }
    }
}

/** What the provider's "Row too big to fit into CursorWindow" arrives as in this process. */
private class RowTooBig : SQLiteException() {
    override val message = "Row too big to fit into CursorWindow requiredPos=0, totalRows=1"
}
