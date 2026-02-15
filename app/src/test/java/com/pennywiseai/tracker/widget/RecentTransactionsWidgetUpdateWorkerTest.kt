package com.pennywiseai.tracker.widget

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
