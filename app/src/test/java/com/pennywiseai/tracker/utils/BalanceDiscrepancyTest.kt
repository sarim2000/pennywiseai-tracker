package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** #734/#135: the bank's reported balance vs. previous snapshot + everything since. */
class BalanceDiscrepancyTest {
    private fun bd(s: String) = BigDecimal(s)

    @Test
    fun `consistent ledger reports nothing`() {
        val d = BalanceDiscrepancy.compute(
            previousBalance = bd("3000"),
            between = listOf(TransactionType.EXPENSE to bd("100")),
            reported = bd("2900"),
            currency = "INR"
        )
        assertNull(d)
    }

    @Test
    fun `missing spend shows as a negative delta`() {
        // Paid 100 but the bank says 2400: 500 left the account untracked.
        val d = BalanceDiscrepancy.compute(bd("3000"), listOf(TransactionType.EXPENSE to bd("100")), bd("2400"), "INR")!!
        assertEquals(bd("2900"), d.expected)
        assertEquals(bd("-500"), d.delta)
    }

    @Test
    fun `manual entries between snapshots close the gap`() {
        val between = listOf(TransactionType.EXPENSE to bd("100"), TransactionType.EXPENSE to bd("500"))
        assertNull(BalanceDiscrepancy.compute(bd("3000"), between, bd("2400"), "INR"))
    }

    @Test
    fun `sub-unit rounding is tolerated`() {
        assertNull(BalanceDiscrepancy.compute(bd("3000"), listOf(TransactionType.INCOME to bd("99.50")), bd("3100"), "INR"))
    }
}
