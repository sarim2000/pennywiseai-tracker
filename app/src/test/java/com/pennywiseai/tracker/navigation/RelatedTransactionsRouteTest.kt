package com.pennywiseai.tracker.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.presentation.common.TimePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Transaction detail's "More from" / "More in" (#1391) open the list on that merchant or category. */
class RelatedTransactionsRouteTest {

    @Test
    fun `More from lists the raw merchant across all time, pinned to the row's currency`() {
        assertEquals(
            TransactionsWithFilter(merchant = "SAMPLE MART 01", period = "ALL", currency = "AED", currencyOnly = true, rowProfileId = 1),
            relatedTransactions(merchant = "SAMPLE MART 01", category = null, currency = "AED", profileId = 1)
        )
    }

    @Test
    fun `More in lists the category across all time, with no search`() {
        val list = relatedTransactions(merchant = null, category = "Food & Dining", currency = "INR", profileId = 2)
        assertEquals("Food & Dining", list.category)
        assertNull(list.merchant)
        // Earlier rows too, not just the list's default This Month.
        assertEquals(TimePeriod.ALL.name, list.period)
        assertEquals("INR" to true, list.currency to list.currencyOnly)
        // The row's profile, so a list kept to one profile still shows the row.
        assertEquals(2L, list.rowProfileId)
    }

    @Test
    fun `a link to the list right under the detail goes back to it`() {
        val list = relatedTransactions("SAMPLE MART 01", null, "INR", 1)
        val s = NavBackStack(Home, list, TransactionDetail(1))
        s.openFrom(TransactionDetail(1), list)
        assertEquals(listOf<NavKey>(Home, list), s.toList())
    }

    @Test
    fun `a link from a row of another profile goes back to the list it came from`() {
        // Opened from a Business row; the list (all profiles) also shows a Personal row of that merchant.
        val list = relatedTransactions("SAMPLE MART 01", null, "INR", 2)
        val s = NavBackStack(Home, TransactionDetail(1), list, TransactionDetail(2))
        s.openFrom(TransactionDetail(2), relatedTransactions("SAMPLE MART 01", null, "INR", 1))
        assertEquals(listOf<NavKey>(Home, TransactionDetail(1), list), s.toList())
    }

    @Test
    fun `a link to a list deeper in the stack opens it again and drops nothing`() {
        val amazon = relatedTransactions("SAMPLE MART 01", null, "INR", 1)
        val food = relatedTransactions(null, "Food & Dining", "INR", 1)
        val s = NavBackStack(Home, amazon, TransactionDetail(1), food, TransactionDetail(2))
        s.openFrom(TransactionDetail(2), amazon)
        assertEquals(
            listOf(Home, amazon, TransactionDetail(1), food, TransactionDetail(2), amazon),
            s.toList()
        )

        val fromHome = NavBackStack(Home, TransactionDetail(3))
        fromHome.openFrom(TransactionDetail(3), food)
        assertEquals(listOf(Home, TransactionDetail(3), food), fromHome.toList())
    }
}
