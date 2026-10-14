package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #898: the Spending view = expense + card spend, with the same exclusions as Expense. */
class AnalyticsRowsForTest {
    private fun tx(
        id: Long,
        type: TransactionType,
        amount: String = "100",
        loanId: Long? = null,
        loanContribution: String? = null,
        excluded: Boolean = false,
        impact: BudgetImpactType? = null
    ) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Test", category = "Others",
            transactionType = type, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
            transactionHash = "h$id", loanId = loanId, loanContribution = loanContribution?.let(::BigDecimal),
            excludedFromAnalytics = excluded, budgetImpactType = impact
        ),
        emptyList()
    )

    private val rows = listOf(
        tx(1, TransactionType.EXPENSE, "500"),
        tx(2, TransactionType.CREDIT, "200"),
        tx(3, TransactionType.INCOME, "1000"),
        tx(4, TransactionType.INCOME, "50", impact = BudgetImpactType.DEDUCT_SPENT),
        tx(5, TransactionType.TRANSFER, "300"),
        tx(6, TransactionType.CREDIT, "80", excluded = true),
        tx(7, TransactionType.CREDIT, "1000", loanId = 9, loanContribution = "400"),
        tx(8, TransactionType.EXPENSE, "70", loanId = 9)
    )

    private fun amounts(filter: TransactionTypeFilter) =
        analyticsRowsFor(rows, filter).associate { it.transaction.id to it.transaction.amount }

    @Test
    fun `spending keeps expense and card spend, minus excluded and loan parts`() {
        assertEquals(
            mapOf(1L to BigDecimal("500"), 2L to BigDecimal("200"), 7L to BigDecimal("600")),
            amounts(TransactionTypeFilter.SPENDING)
        )
    }

    @Test
    fun `spending is exactly expense plus credit`() {
        assertEquals(
            amounts(TransactionTypeFilter.EXPENSE) + amounts(TransactionTypeFilter.CREDIT),
            amounts(TransactionTypeFilter.SPENDING)
        )
    }

    @Test
    fun `income drops refunds, which the spend views net instead`() {
        assertEquals(setOf(3L), amounts(TransactionTypeFilter.INCOME).keys)
        assertTrue(TransactionTypeFilter.SPENDING.netsRefunds)
        assertTrue(TransactionTypeFilter.EXPENSE.netsRefunds)
        assertFalse(TransactionTypeFilter.CREDIT.netsRefunds)
        assertFalse(TransactionTypeFilter.INCOME.netsRefunds)
    }

    @Test
    fun `spending stays out of the Transactions type menu`() {
        assertFalse(TransactionTypeFilter.SPENDING.inTransactionsList)
        assertTrue(
            TransactionTypeFilter.entries
                .filter { it != TransactionTypeFilter.SPENDING }
                .all { it.inTransactionsList }
        )
    }
}
