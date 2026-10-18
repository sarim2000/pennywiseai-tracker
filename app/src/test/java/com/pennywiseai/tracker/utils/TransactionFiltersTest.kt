package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #800/#831: the one shared "how much of this counts toward a total" rule every screen defers to. */
class TransactionFiltersTest {
    private fun tx(
        amount: String = "100",
        loanId: Long? = null,
        loanContribution: String? = null,
        excludedFromAnalytics: Boolean = false
    ) = TransactionEntity(
        id = 7, amount = BigDecimal(amount), merchantName = "Test", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 9, 15, 12, 0),
        transactionHash = "h", loanId = loanId, loanContribution = loanContribution?.let(::BigDecimal),
        excludedFromAnalytics = excludedFromAnalytics
    )

    @Test
    fun `plain transaction counts in full and is returned as is`() {
        val t = tx()
        assertEquals(BigDecimal("100"), t.countedAmount())
        assertSame(t, t.forTotals())
    }

    @Test
    fun `fully loan-linked transaction counts nothing`() {
        assertEquals(BigDecimal.ZERO, tx(loanId = 1L).countedAmount())
        assertNull(tx(loanId = 1L).forTotals())
    }

    @Test
    fun `excluded-from-analytics transaction counts nothing, loan or not`() {
        assertNull(tx(excludedFromAnalytics = true).forTotals())
        assertNull(tx(loanId = 1L, loanContribution = "40", excludedFromAnalytics = true).forTotals())
    }

    @Test
    fun `split bill counts your own share`() {
        // ₹1,000 dinner, ₹400 of it lent: ₹600 is your spend.
        val counted = tx(amount = "1000", loanId = 1L, loanContribution = "400").forTotals()!!
        assertEquals(BigDecimal("600"), counted.amount)
        assertNull(counted.loanId)
        assertEquals(7L, counted.id)
    }

    @Test
    fun `overpaid repayment counts the extra`() {
        // ₹500 back on a ₹130 loan, repayment capped at ₹130: ₹370 is income.
        assertEquals(BigDecimal("370"), tx(amount = "500", loanId = 2L, loanContribution = "130").countedAmount())
    }

    @Test
    fun `forTotals is idempotent`() {
        val once = tx(amount = "1000", loanId = 1L, loanContribution = "400").forTotals()!!
        assertSame(once, once.forTotals())
        assertEquals(BigDecimal("600"), listOf(once).forTotals().single().amount)
    }

    @Test
    fun `a loan part larger than the amount never goes negative`() {
        assertNull(tx(amount = "100", loanId = 1L, loanContribution = "150").forTotals())
    }

    @Test
    fun `splits shrink in proportion and still sum to the counted amount`() {
        val row = TransactionWithSplits(
            tx(amount = "100", loanId = 1L, loanContribution = "40"),
            listOf(
                TransactionSplitEntity(transactionId = 7, category = "Food", amount = BigDecimal("33.33")),
                TransactionSplitEntity(transactionId = 7, category = "Drinks", amount = BigDecimal("33.33")),
                TransactionSplitEntity(transactionId = 7, category = "Tip", amount = BigDecimal("33.34"))
            )
        )
        val counted = row.forTotals()!!
        assertEquals(BigDecimal("60"), counted.transaction.amount)
        assertEquals(listOf("20.00", "20.00", "20.00"), counted.splits.map { it.amount.setScale(2).toPlainString() })
        assertEquals(0, counted.getSplitsTotal().compareTo(BigDecimal("60")))
    }

    @Test
    fun `tiny counted part never makes a split negative`() {
        // ₹1,000 with ₹999.97 lent: ₹0.03 spread over 167/167/167/167/332.
        val row = TransactionWithSplits(
            tx(amount = "1000", loanId = 1L, loanContribution = "999.97"),
            listOf("167", "167", "167", "167", "332").map {
                TransactionSplitEntity(transactionId = 7, category = "C$it", amount = BigDecimal(it))
            }
        )
        val splits = row.forTotals()!!.splits
        splits.forEach { assertTrue("negative split ${it.amount}", it.amount.signum() >= 0) }
        assertEquals(0, splits.fold(BigDecimal.ZERO) { a, s -> a + s.amount }.compareTo(BigDecimal("0.03")))
    }

    @Test
    fun `bill split with several people counts only your part`() {
        // #346: ₹1,000 between you and two others, ₹333.33 each lent: ₹333.34 is yours.
        val split = tx(amount = "1000").copy(lentSharesTotal = BigDecimal("666.66"))
        assertEquals(BigDecimal("333.34"), split.countedAmount())
        val counted = split.forTotals()!!
        assertEquals(BigDecimal("333.34"), counted.amount)
        assertNull(counted.lentSharesTotal)
        // Idempotent: a row already cut isn't cut again.
        assertEquals(BigDecimal("333.34"), counted.forTotals()!!.amount)
    }

    @Test
    fun `bill paid entirely for others counts nothing`() {
        assertNull(tx(amount = "900").copy(lentSharesTotal = BigDecimal("900")).forTotals())
    }

    @Test
    fun `excluded split bill counts nothing`() {
        assertNull(tx(amount = "1000", excludedFromAnalytics = true).copy(lentSharesTotal = BigDecimal("500")).forTotals())
    }

    @Test
    fun `fully loan-linked split row drops out`() {
        val row = TransactionWithSplits(
            tx(loanId = 1L),
            listOf(TransactionSplitEntity(transactionId = 7, category = "Food", amount = BigDecimal("100")))
        )
        assertNull(row.forTotals())
    }
}
