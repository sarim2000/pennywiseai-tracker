package com.pennywiseai.tracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecentTransactionsWidgetUpdateWorkerTest {

    @Test
    fun `uses display currency in unified mode`() {
        val target = RecentTransactionsWidgetUpdateWorker.resolveTargetCurrency(
            isUnified = true,
            displayCurrency = "PKR",
            baseCurrency = "INR"
        )

        assertEquals("PKR", target)
    }

    @Test
    fun `uses base currency when unified mode is off`() {
        val target = RecentTransactionsWidgetUpdateWorker.resolveTargetCurrency(
            isUnified = false,
            displayCurrency = "PKR",
            baseCurrency = "INR"
        )

        assertEquals("INR", target)
    }

    @Test
    fun `row time reads as time today, yesterday, then a date`() {
        val today = LocalDate.of(2026, 10, 22)
        val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val date = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        fun label(at: LocalDateTime) =
            RecentTransactionsWidgetUpdateWorker.whenLabel(at, today, "Yesterday", time, date)

        assertEquals("6:27 PM", label(today.atTime(18, 27)))
        assertEquals("Yesterday", label(today.minusDays(1).atTime(23, 59)))
        assertEquals("Oct 20", label(today.minusDays(2).atTime(0, 1)))
    }
}
