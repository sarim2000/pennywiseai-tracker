package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CardDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.BudgetPreferences
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.navigation.transactionsFor
import com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupEditUiState
import com.pennywiseai.tracker.presentation.budgetgroups.CategoryBudgetItem
import com.pennywiseai.tracker.presentation.budgetgroups.categoryRows
import com.pennywiseai.tracker.widget.unifiedGroupSpending
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * A sub-category's spend counts toward its parent's line unless the sub-category has its own
 * line in the same budget (#1425): on every surface, in both currency modes, rollover or not.
 */
class BudgetSubCategoryLinesTest {

    private val today = LocalDate.now()
    private val month = YearMonth.from(today)
    private val window = BudgetWindow(month.atDay(1), month.atEndOfMonth(), month.lengthOfMonth())

    private fun budget(rollover: Boolean, vararg categories: String) = BudgetWithCategories(
        BudgetEntity(
            id = 1, name = "Monthly", limitAmount = BigDecimal("10000"), periodType = BudgetPeriodType.MONTHLY,
            startDate = window.start, endDate = window.end, monthStartDay = 1,
            currency = "INR", groupType = BudgetGroupType.LIMIT,
            rolloverEnabled = rollover, rolloverSince = today.minusMonths(2)
        ),
        categories.map { BudgetCategoryEntity(budgetId = 1, categoryName = it, budgetAmount = BigDecimal("1000")) }
    )

    private fun tx(id: Long, amount: String, category: String) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Shop", category = category,
            transactionType = TransactionType.EXPENSE, dateTime = today.atTime(9, 0), currency = "INR", transactionHash = "h$id"
        ),
        emptyList()
    )

    // ₹450 on Food & Dining, ₹100 on Snacks, a sub-category of it.
    private val txs = listOf(tx(1, "450", "Food & Dining"), tx(2, "100", "Snacks"))

    // A refund filed against [category]; the row's own category is Income.
    private fun refund(id: Long, amount: String, category: String) = tx(id, amount, "Income").let {
        it.copy(
            transaction = it.transaction.copy(
                transactionType = TransactionType.INCOME, budgetCategory = category, budgetImpactType = BudgetImpactType.DEDUCT_SPENT
            )
        )
    }

    private fun repository(group: BudgetWithCategories, txs: List<TransactionWithSplits> = this.txs) = BudgetGroupRepository(
        mockk<BudgetDao> { every { getActiveBudgetsWithCategories() } returns flowOf(listOf(group)) },
        mockk<TransactionSplitDao> {
            every { getTransactionsWithSplitsAllCurrencies(any(), any()) } returns flowOf(txs)
            every { getTransactionsWithSplitsFiltered(any(), any(), any()) } returns flowOf(txs)
        },
        mockk<BudgetPreferences> {
            every { budgetCycleStartDay } returns flowOf(1)
            coEvery { getBudgetCycleStartDay() } returns 1
        },
        mockk<CategoryDao> {
            coEvery { getAllCategoriesList() } returns listOf(
                CategoryEntity(id = 1, name = "Food & Dining", color = "#000000"),
                CategoryEntity(id = 2, name = "Snacks", color = "#000000", parentId = 1)
            )
        },
        mockk<IgnoredAccountsStore> { every { keysFlow } returns flowOf(emptySet()) },
        mockk<CardDao> { every { getAllCards() } returns flowOf(emptyList()) },
        mockk<AccountBalanceDao> { every { getAllLatestBalances() } returns flowOf(emptyList()) }
    )

    private val conversion = mockk<CurrencyConversionService> {
        coEvery { convertAmountOrNull(any(), any(), any(), any()) } answers { firstArg() }
        every { getAllRatesFlow() } returns flowOf(emptyList())
    }

    private val asIs: suspend (String, BigDecimal) -> BigDecimal? = { _, amount -> amount }

    private fun budgets(repo: BudgetGroupRepository, unified: Boolean): BudgetGroupSpending = runBlocking {
        val prefs = mockk<CurrencyPreferences> {
            every { baseCurrency } returns flowOf("INR")
            every { unifiedCurrencyMode } returns flowOf(unified)
            every { displayCurrency } returns flowOf("INR")
        }
        BudgetSummaryProvider(repo, prefs, conversion).summary(month, flagUncounted = true).first().groups.single()
    }

    private fun Iterable<BudgetCategorySpending>.lines() =
        associate { it.categoryName to it.actualAmount.stripTrailingZeros().toPlainString() }

    private fun BigDecimal.plain() = stripTrailingZeros().toPlainString()

    /** Each surface's lines and total: the Budgets card in both modes, Home and the widget in unified mode, the history breakdown in both. */
    private fun everywhere(group: BudgetWithCategories): Map<String, Pair<Map<String, String>, String>> = runBlocking {
        val repo = repository(group)
        val raw = repo.getGroupSpendingAllCurrencies(month.year, month.monthValue).first()
        val home = homeBudgetSummary(raw, displayCurrency = "INR", baseCurrency = "INR") { amount, _, _ -> amount }.groups.single()
        val carries = raw.budgetsWithCategories.associate { it.budget.id to raw.carryFor(it, "INR", "INR", asIs) }
        val widget = unifiedGroupSpending(raw, carries, asIs, { it }, ownWindows = true).single()
        val breakdown = repo.getBudgetWindowBreakdown(group.budget, window, "INR")
        val unifiedBreakdown = repo.getBudgetWindowBreakdown(group.budget, window, "INR", asIs)
        mapOf(
            "native card" to budgets(repo, unified = false).let { it.categorySpending.lines() to it.totalActual.plain() },
            "unified card" to budgets(repo, unified = true).let { it.categorySpending.lines() to it.totalActual.plain() },
            "unified Home" to (home.categorySpending.lines() to home.totalActual.plain()),
            "unified widget" to (widget.categorySpending.lines() to widget.totalActual.plain()),
            "native breakdown" to (breakdown.categorySpending.lines() to breakdown.totalActual.plain()),
            "unified breakdown" to (unifiedBreakdown.categorySpending.lines() to unifiedBreakdown.totalActual.plain()),
        )
    }

    private fun assertEverywhere(expected: Pair<Map<String, String>, String>, group: BudgetWithCategories) {
        val all = everywhere(group)
        assertEquals(all.keys.associateWith { expected }, all)
    }

    @Test
    fun `a parent's line counts its sub-category's spend, in both modes, rollover or not`() {
        for (rollover in listOf(false, true)) {
            assertEverywhere(mapOf("Food & Dining" to "550") to "550", budget(rollover, "Food & Dining"))
        }
    }

    @Test
    fun `a sub-category with its own line keeps its spend off the parent's line, counted once`() {
        for (rollover in listOf(false, true)) {
            assertEverywhere(
                mapOf("Food & Dining" to "450", "Snacks" to "100") to "550",
                budget(rollover, "Food & Dining", "Snacks")
            )
        }
    }

    @Test
    fun `unified mode's pace line counts a parent's sub-category, as its total does`() {
        val card = budgets(repository(budget(rollover = false, "Food & Dining")), unified = true)
        assertEquals(550.0, card.dailyCumulativeSpending.last(), 0.01)
    }

    @Test
    fun `budget history counts a parent's sub-category, in both modes`() = runBlocking {
        val group = budget(rollover = false, "Food & Dining")
        for (convert in listOf(null, asIs)) {
            val history = repository(group).getBudgetHistoryForMonth(group.budget, month.year, month.monthValue, "INR", convert)
            assertEquals("550", history.single().spent.plain())
        }
    }

    @Test
    fun `the parent line's list leaves out a sub-category with its own line`() {
        val both = budget(rollover = false, "Food & Dining", "Snacks")
        val card = budgets(repository(both), unified = true)
        assertEquals(listOf("Snacks"), transactionsFor(card.categoryRows("Food & Dining", "INR", unified = true)).withoutCategories)
        // Alone, the parent's list keeps its sub-categories.
        val alone = budgets(repository(budget(rollover = false, "Food & Dining")), unified = true)
        assertEquals(emptyList<String>(), transactionsFor(alone.categoryRows("Food & Dining", "INR", unified = true)).withoutCategories)
    }

    @Test
    fun `the budget form's Spent hint follows the same rule`() {
        fun hints(vararg rows: String) = BudgetGroupEditUiState(
            categories = rows.map { CategoryBudgetItem(it, BigDecimal.ZERO) },
            categorySpending = mapOf("Food & Dining" to BigDecimal("450"), "Snacks" to BigDecimal("100")),
            parentOf = mapOf("Snacks" to "Food & Dining")
        ).withSpentHints().categories.associate { it.categoryName to it.currentSpending.plain() }
        assertEquals(mapOf("Food & Dining" to "550"), hints("Food & Dining"))
        assertEquals(mapOf("Food & Dining" to "450", "Snacks" to "100"), hints("Food & Dining", "Snacks"))
    }

    @Test
    fun `the pace line floors each line's refunds as the total does, in both modes`() {
        // ₹200 back on Snacks, its own line, comes off Snacks' ₹100 only: Food & Dining keeps its ₹450.
        val both = repository(budget(rollover = false, "Food & Dining", "Snacks"), txs + refund(3, "200", "Snacks"))
        // ₹700 back on a budget's only line, or on all spending, takes it to zero, not below.
        val overRefunded = txs + refund(3, "700", "Food & Dining")
        val cases = listOf(
            "450" to both,
            "0" to repository(budget(rollover = false, "Food & Dining"), overRefunded),
            "0" to repository(budget(rollover = false), overRefunded)
        )
        for ((spent, repo) in cases) {
            for (unified in listOf(false, true)) {
                val card = budgets(repo, unified)
                assertEquals(spent, card.totalActual.plain())
                assertEquals(spent.toDouble(), card.dailyCumulativeSpending.last(), 0.01)
                assertTrue(card.dailyCumulativeSpending.all { it >= 0.0 })
            }
        }
    }
}
