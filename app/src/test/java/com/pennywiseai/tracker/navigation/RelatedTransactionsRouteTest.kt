package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.presentation.common.TimePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Transaction detail's "More from" / "More in" (#1391) open the list on that merchant or category. */
class RelatedTransactionsRouteTest {

    @Test
    fun `More from lists the raw merchant across all time, in the row's currency`() {
        assertEquals(
            TransactionsWithFilter(merchant = "SAMPLE MART 01", period = "ALL", currency = "AED"),
            relatedTransactions(merchant = "SAMPLE MART 01", category = null, currency = "AED")
        )
    }

    @Test
    fun `More in lists the category across all time, with no search`() {
        val list = relatedTransactions(merchant = null, category = "Food & Dining", currency = "INR")
        assertEquals("Food & Dining", list.category)
        assertNull(list.merchant)
        // Earlier rows too, not just the list's default This Month.
        assertEquals(TimePeriod.ALL.name, list.period)
        assertEquals("INR" to false, list.currency to list.currencyOnly)
    }

    @Test
    fun `the same link twice is the same list, so it isn't stacked again`() {
        assertEquals(
            relatedTransactions("SAMPLE MART 01", null, "INR"),
            relatedTransactions("SAMPLE MART 01", null, "INR")
        )
    }
}
