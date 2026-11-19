package com.pennywiseai.tracker.worker.smsscan

import android.database.Cursor
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
