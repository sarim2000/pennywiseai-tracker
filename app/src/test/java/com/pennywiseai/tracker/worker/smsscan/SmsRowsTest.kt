package com.pennywiseai.tracker.worker.smsscan

import android.database.Cursor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        val sent = streamSmsRows(cursor, feed) { unreadable++ }
        feed.close()

        assertEquals(2, sent)
        assertEquals(1, unreadable)
        assertEquals(listOf(0L, 2L), feed.toList().map { it.id })
    }
}

private suspend fun <T> Channel<T>.toList(): List<T> = buildList { for (item in this@toList) add(item) }
