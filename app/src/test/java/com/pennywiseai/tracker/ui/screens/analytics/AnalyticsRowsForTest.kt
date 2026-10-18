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
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking

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

    // Refund netting (#898 review): an expense, a card purchase and a card refund.
    private fun row(
        id: Long, type: TransactionType, amount: String, merchant: String, account: String, day: Int,
        impact: BudgetImpactType? = null, budgetCategory: String? = null
    ) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = merchant, category = "Shopping",
            transactionType = type, dateTime = LocalDateTime.of(2026, 10, day, 12, 0),
            transactionHash = "r$id", bankName = "Bank", accountNumber = account,
            budgetImpactType = impact, budgetCategory = budgetCategory
        ),
        emptyList()
    )

    private val spend = listOf(
        row(1, TransactionType.EXPENSE, "500", "Grocer", "1111", day = 1),
        row(2, TransactionType.CREDIT, "300", "Shop", "2222", day = 2),
        row(
            3, TransactionType.INCOME, "200", "Shop", "2222", day = 2,
            impact = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Shopping"
        )
    )

    /** A view's surfaces as AnalyticsViewModel builds them: gross per bucket, then [netOf]. */
    private class View(
        val total: BigDecimal,
        val category: Map<String, BigDecimal>,
        val merchant: Map<String, BigDecimal>,
        val account: Map<String, BigDecimal>,
        val refundByDay: Map<LocalDate, BigDecimal>
    )

    private fun view(filter: TransactionTypeFilter): View = runBlocking {
        val rows = analyticsRowsFor(spend, filter).map { it.transaction }
        val refunds = refundNetting(spend, filter, null, emptyMap()) { it.amount }
        fun gross(key: (TransactionEntity) -> String) =
            rows.groupBy(key).mapValues { (_, t) -> t.fold(BigDecimal.ZERO) { a, x -> a + x.amount } }
        val category = gross { it.category }.toMutableMap()
        refunds.byCategory.forEach { (c, r) -> category[c] = netOf(category[c] ?: BigDecimal.ZERO, r) }
        val total = rows.fold(BigDecimal.ZERO) { a, x -> a + x.amount }
        View(
            total = if (filter.netsRefunds) netOf(total, refunds.total) else total,
            category = category,
            merchant = gross { it.merchantName }.mapValues { (m, g) -> netOf(g, refunds.byMerchant[m]) },
            account = gross { "${it.bankName}_${it.accountNumber}" }
                .mapValues { (k, g) -> netOf(g, refunds.byAccount[k]) },
            refundByDay = refunds.byDay
        )
    }

    private val oct2 = LocalDate.of(2026, 10, 2)

    @Test
    fun `spending nets a card refund off total, category, merchant, account and trend`() {
        val v = view(TransactionTypeFilter.SPENDING)
        assertEquals(BigDecimal("600"), v.total)
        assertEquals(mapOf("Shopping" to BigDecimal("600")), v.category)
        assertEquals(mapOf("Grocer" to BigDecimal("500"), "Shop" to BigDecimal("100")), v.merchant)
        assertEquals(mapOf("Bank_1111" to BigDecimal("500"), "Bank_2222" to BigDecimal("100")), v.account)
        assertEquals(mapOf(oct2 to BigDecimal("200")), v.refundByDay)
    }

    @Test
    fun `expense nets the same refund the same way`() {
        val v = view(TransactionTypeFilter.EXPENSE)
        assertEquals(BigDecimal("300"), v.total)
        assertEquals(mapOf("Shopping" to BigDecimal("300")), v.category)
        assertEquals(mapOf("Grocer" to BigDecimal("500")), v.merchant)
        assertEquals(mapOf("Bank_1111" to BigDecimal("500")), v.account)
        assertEquals(mapOf(oct2 to BigDecimal("200")), v.refundByDay)
    }

    @Test
    fun `credit view nets nothing, and an unconvertible refund is skipped`() = runBlocking {
        assertEquals(BigDecimal("300"), view(TransactionTypeFilter.CREDIT).total)
        val unconvertible = refundNetting(spend, TransactionTypeFilter.SPENDING, null, emptyMap()) { null }
        assertEquals(BigDecimal.ZERO, unconvertible.total)
    }

    @Test
    fun `refund outside the category filter is not netted, buckets floor at zero`() = runBlocking {
        val r = refundNetting(spend, TransactionTypeFilter.SPENDING, setOf("Food"), emptyMap()) { it.amount }
        assertEquals(BigDecimal.ZERO, r.total)
        assertTrue(r.byCategory.isEmpty())
        assertEquals(BigDecimal.ZERO, netOf(BigDecimal("50"), BigDecimal("200")))
    }

    @Test
    fun `Analytics opens on Spending only when card spend counts as expense`() {
        assertEquals(TransactionTypeFilter.SPENDING, defaultTypeFilter(countCreditCardAsExpense = true))
        assertEquals(TransactionTypeFilter.EXPENSE, defaultTypeFilter(countCreditCardAsExpense = false))
    }

    @Test
    fun `type filter follows the setting until picked, and again after Clear`() {
        val f = TypeFilterSelection()
        assertEquals(TransactionTypeFilter.EXPENSE, f.value.value)

        f.onDefaultChanged(TransactionTypeFilter.SPENDING) // setting turned on
        assertEquals(TransactionTypeFilter.SPENDING, f.value.value)

        f.pick(TransactionTypeFilter.EXPENSE) // user picks a type
        f.onDefaultChanged(TransactionTypeFilter.EXPENSE)
        f.onDefaultChanged(TransactionTypeFilter.SPENDING)
        assertEquals("a manual pick survives setting changes", TransactionTypeFilter.EXPENSE, f.value.value)

        f.reset() // Clear
        assertEquals(TransactionTypeFilter.SPENDING, f.value.value)
        f.onDefaultChanged(TransactionTypeFilter.EXPENSE) // setting turned off after Clear
        assertEquals("follows the setting again after Clear", TransactionTypeFilter.EXPENSE, f.value.value)
    }
}
