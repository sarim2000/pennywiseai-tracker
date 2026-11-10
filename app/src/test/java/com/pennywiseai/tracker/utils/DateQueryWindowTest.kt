package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.converter.Converters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * date_time is compared as text in SQL but as a parsed value in the list filter. The
 * window handed to SQL must never lose a row the filter would keep, whichever stored
 * form that row is in.
 */
class DateQueryWindowTest {
    private val converters = Converters()

    private val start = LocalDate.of(2026, 11, 1).atStartOfDay()
    private val end = LocalDate.of(2026, 11, 30).atTime(23, 59, 59)

    private fun sqlBetween(stored: String, low: LocalDateTime, high: LocalDateTime) =
        stored >= converters.fromLocalDateTime(low)!! && stored <= converters.fromLocalDateTime(high)!!

    private fun kept(stored: String) = converters.toLocalDateTime(stored)!! in start..end

    private val edgeRows = listOf(
        "2026-10-31 24:00:00", "2026-11-01T00:00:00", "2026-11-01T00:00", "2026-11-01 00:00:00", "2026-11-01 08:30:15",
        "2026-11-01T00:00:00.001", "2026-11-15T12:00:00.5", "2026-11-30T23:59", "2026-11-30T23:59:59",
        "2026-11-30 23:59:59", "2026-11-30T23:59:59.5",
        "2026-10-31T23:59:59", "2026-10-31T23:59:59.5", "2026-10-31 23:59:59",
        "2026-12-01T00:00:00", "2026-12-01T00:00", "2026-12-01 00:00:00"
    )

    @Test
    fun `the window returns every row the exact filter keeps`() {
        val (low, high) = dateQueryWindow(start, end)
        edgeRows.filter(::kept).forEach { stored ->
            assertTrue("window lost $stored", sqlBetween(stored, low, high))
        }
    }

    @Test
    fun `the exact boundaries alone would lose rows, which is why the window is padded`() {
        listOf("2026-10-31 24:00:00", "2026-11-01T00:00", "2026-11-01 00:00:00", "2026-11-01 08:30:15").forEach { stored ->
            assertTrue(kept(stored))
            assertFalse("$stored now fits the exact bounds; padding may be removable", sqlBetween(stored, start, end))
        }
    }

    @Test
    fun `the exact filter drops what the padding lets through`() {
        val (low, high) = dateQueryWindow(start, end)
        listOf("2026-10-31T23:59:59.5", "2026-11-30T23:59:59.5", "2026-12-01T00:00:00").forEach { stored ->
            assertTrue(sqlBetween(stored, low, high))
            assertFalse("$stored should be outside the period", kept(stored))
        }
    }
}
