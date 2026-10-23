package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.BalanceDiscrepancy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class BalanceAdjustmentTest {

    private val at = LocalDateTime.of(2026, 10, 22, 12, 0)
    private val tx = TransactionEntity(
        id = 7,
        amount = BigDecimal("50"),
        merchantName = "Shop",
        category = "Shopping",
        transactionType = TransactionType.EXPENSE,
        dateTime = at,
        transactionHash = "h",
        bankName = "Kotak Bank",
        accountNumber = "4321",
        balanceAfter = BigDecimal("9750")
    )
    // Expected 9850 after the ₹50, bank says 9750: ₹100 left untracked.
    private val shortfall = BalanceDiscrepancy(BigDecimal("9850"), BigDecimal("9750"), "INR")

    @Test
    fun `merchant and category come from the sheet`() {
        val adj = balanceAdjustment(tx, shortfall, "  Tea shop ", "Food & Dining")
        assertEquals("Tea shop", adj.merchantName)
        assertEquals("Food & Dining", adj.category)
        assertEquals(TransactionType.EXPENSE, adj.transactionType)
        assertEquals(BigDecimal("100"), adj.amount)
        assertEquals("INR", adj.currency)
    }

    @Test
    fun `an empty merchant falls back to Balance adjustment`() {
        assertEquals("Balance adjustment", balanceAdjustment(tx, shortfall, "", "Others").merchantName)
        assertEquals("Balance adjustment", balanceAdjustment(tx, shortfall, "   ", "Others").merchantName)
    }

    @Test
    fun `insertion stays as before - hash, timestamp, no balance`() {
        val adj = balanceAdjustment(tx, shortfall, "Tea shop", "Food & Dining")
        assertEquals("manual_balance_adjustment_7", adj.transactionHash)
        assertEquals(at.minusSeconds(1), adj.dateTime)
        assertNull(adj.balanceAfter)
        assertEquals("4321", adj.accountNumber)
        // Never at/before the detector's snapshot.
        assertEquals(at, balanceAdjustment(tx, shortfall.copy(since = at.minusSeconds(1)), "", "Others").dateTime)
    }

    @Test
    fun `default category follows the adjustment type`() {
        val cats = listOf(
            CategoryEntity(id = 1, name = "Others", color = "#000000", isSystem = true),
            CategoryEntity(id = 2, name = "Income", color = "#000000", isIncome = true, isSystem = true)
        )
        assertEquals("Others", cats.balanceAdjustmentDefaultCategory(TransactionType.EXPENSE))
        assertEquals("Income", cats.balanceAdjustmentDefaultCategory(TransactionType.INCOME))
        val surplus = BalanceDiscrepancy(BigDecimal("9750"), BigDecimal("9850"), "INR")
        assertEquals(TransactionType.INCOME, balanceAdjustmentType(surplus))
    }
}
