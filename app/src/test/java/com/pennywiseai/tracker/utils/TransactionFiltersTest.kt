package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #800: the one shared "does this count toward a total" rule every screen defers to. */
class TransactionFiltersTest {
    private fun tx(loanId: Long? = null, excludedFromAnalytics: Boolean = false) = TransactionEntity(
        amount = BigDecimal("100"), merchantName = "Test", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 9, 15, 12, 0),
        transactionHash = "h", loanId = loanId, excludedFromAnalytics = excludedFromAnalytics
    )

    @Test
    fun `plain transaction counts`() {
        assertTrue(tx().countsInTotals())
    }

    @Test
    fun `loan-linked transaction does not count`() {
        assertFalse(tx(loanId = 1L).countsInTotals())
    }

    @Test
    fun `excluded-from-analytics transaction does not count`() {
        assertFalse(tx(excludedFromAnalytics = true).countsInTotals())
    }

    @Test
    fun `both loan-linked and excluded still does not count`() {
        assertFalse(tx(loanId = 1L, excludedFromAnalytics = true).countsInTotals())
    }
}
