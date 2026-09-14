package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #734/#135: the bank's reported balance vs. previous snapshot + everything since. */
class BalanceDiscrepancyTest {
    private fun bd(s: String) = BigDecimal(s)
    private fun tx(type: TransactionType, amount: String, from: String? = null, to: String? = null) = TransactionEntity(
        amount = bd(amount), merchantName = "m", category = "Others", transactionType = type,
        dateTime = LocalDateTime.of(2026, 9, 14, 12, 0), transactionHash = "h-$type-$amount-$from-$to",
        fromAccount = from, toAccount = to
    )
    private fun effects(vararg t: TransactionEntity) = t.map { BalanceDiscrepancy.effectOn(it, "5678") }

    @Test
    fun `consistent ledger reports nothing`() {
        assertNull(BalanceDiscrepancy.compute(bd("3000"), effects(tx(TransactionType.EXPENSE, "100")), bd("2900"), "INR"))
    }

    @Test
    fun `missing spend shows as a negative delta`() {
        // Paid 100 but the bank says 2400: 500 left the account untracked.
        val d = BalanceDiscrepancy.compute(bd("3000"), effects(tx(TransactionType.EXPENSE, "100")), bd("2400"), "INR")!!
        assertEquals(bd("2900"), d.expected)
        assertEquals(bd("-500"), d.delta)
    }

    @Test
    fun `manual entries between snapshots close the gap`() {
        val e = effects(tx(TransactionType.EXPENSE, "100"), tx(TransactionType.EXPENSE, "500"))
        assertNull(BalanceDiscrepancy.compute(bd("3000"), e, bd("2400"), "INR"))
    }

    @Test
    fun `transfers count by leg`() {
        // 1000 moved out of this account to 9999, 250 moved in from 1111.
        val e = effects(
            tx(TransactionType.TRANSFER, "1000", from = "5678", to = "9999"),
            tx(TransactionType.TRANSFER, "250", from = "1111", to = "5678")
        )
        assertNull(BalanceDiscrepancy.compute(bd("3000"), e, bd("2250"), "INR"))
    }

    @Test
    fun `sub-unit rounding is tolerated`() {
        assertNull(BalanceDiscrepancy.compute(bd("3000"), effects(tx(TransactionType.INCOME, "99.50")), bd("3100"), "INR"))
    }
}
