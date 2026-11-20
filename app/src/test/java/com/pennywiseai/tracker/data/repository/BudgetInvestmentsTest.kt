package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.dao.BudgetDao
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.BudgetPreferences
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.presentation.home.homeBudgetSummary
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** Money put into an investment isn't spending: Budgets, Insights and Home leave it out of a budget alike. */
class BudgetInvestmentsTest {

    private val today = LocalDate.now()
    private val month = YearMonth.from(today)

    private fun budget(vararg categories: String) = BudgetWithCategories(
        BudgetEntity(
            id = 1, name = "Monthly", limitAmount = BigDecimal("10000"), periodType = BudgetPeriodType.MONTHLY,
            startDate = month.atDay(1), endDate = month.atEndOfMonth(), monthStartDay = 1,
            currency = "INR", groupType = BudgetGroupType.LIMIT
        ),
        categories.map { BudgetCategoryEntity(budgetId = 1, categoryName = it, budgetAmount = BigDecimal("10000")) }
    )

    // The built-in Investments target: one row tracking the INVESTMENT type. A target can't roll over.
    private fun investmentsTarget() = BudgetWithCategories(
        budget().budget.copy(name = "Investments", groupType = BudgetGroupType.TARGET),
        listOf(
            BudgetCategoryEntity(
                budgetId = 1, categoryName = "Investments", budgetAmount = BigDecimal("15000"),
                matchType = TransactionType.INVESTMENT.name
            )
        )
    )

    private fun tx(id: Long, amount: String, type: TransactionType, currency: String = "INR", date: LocalDate = today) = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Shop", category = "Others",
            transactionType = type, dateTime = date.atTime(9, 0), currency = currency, transactionHash = "h$id"
        ),
        emptyList()
    )

    // ₹3,898 spent and ₹1,700 invested this month, all filed under Others.
    private val txs = listOf(
        tx(1, "3898", TransactionType.EXPENSE),
        tx(2, "800", TransactionType.INVESTMENT),
        tx(3, "900", TransactionType.INVESTMENT)
    )

    private fun repository(group: BudgetWithCategories, txs: List<TransactionWithSplits>) = BudgetGroupRepository(
        mockk<BudgetDao> { every { getActiveBudgetsWithCategories() } returns flowOf(listOf(group)) },
        mockk<TransactionSplitDao> {
            every { getTransactionsWithSplitsAllCurrencies(any(), any()) } returns flowOf(txs)
            every { getTransactionsWithSplitsFiltered(any(), any(), any()) } answers {
                flowOf(txs).map { list -> list.filter { it.transaction.currency == thirdArg<String>() } }
            }
        },
        mockk<BudgetPreferences> {
            every { budgetCycleStartDay } returns flowOf(1)
            coEvery { getBudgetCycleStartDay() } returns 1
        },
        mockk<CategoryDao> { coEvery { getAllCategoriesList() } returns emptyList() }
    )

    // XYZ has never had a rate.
    private fun budgets(repo: BudgetGroupRepository, unified: Boolean): BudgetGroupSpending = runBlocking {
        val conversion = mockk<CurrencyConversionService> {
            coEvery { convertAmountOrNull(any(), any(), any(), any()) } answers { firstArg<BigDecimal>().takeIf { secondArg<String>() != "XYZ" } }
            every { getAllRatesFlow() } returns flowOf(emptyList())
        }
        val prefs = mockk<CurrencyPreferences> {
            every { baseCurrency } returns flowOf("INR")
            every { unifiedCurrencyMode } returns flowOf(unified)
            every { displayCurrency } returns flowOf("INR")
        }
        // Insights reads this too: InsightsLoader -> summary(month, flagUncounted = true).
        BudgetSummaryProvider(repo, prefs, conversion).summary(month, flagUncounted = true).first().groups.single()
    }

    /** Budgets screen in both currency modes, then Home outside and in unified mode. */
    private fun spentEverywhere(group: BudgetWithCategories): List<BigDecimal> = runBlocking {
        val repo = repository(group, txs)
        val raw = repo.getGroupSpendingAllCurrencies(month.year, month.monthValue).first()
        listOf(
            budgets(repo, unified = false).totalActual,
            budgets(repo, unified = true).totalActual,
            repo.getGroupSpending(month.year, month.monthValue, "INR").first().groups.single().totalActual,
            homeBudgetSummary(raw, displayCurrency = "INR", baseCurrency = "INR") { amount, _, _ -> amount }.groups.single().totalActual
        )
    }

    private fun assertSpent(expected: String, actual: List<BigDecimal>) =
        assertEquals("spent $actual", listOf(0, 0, 0, 0), actual.map { BigDecimal(expected).compareTo(it) })

    @Test
    fun `an all-spending budget leaves investments out everywhere`() {
        assertSpent("3898", spentEverywhere(budget()))
    }

    @Test
    fun `Plan's pace chart and total leave out the same investments`() {
        val repo = repository(budget(), txs)
        for (unified in listOf(false, true)) {
            val plan = budgets(repo, unified)
            assertEquals(3898.0, plan.dailyCumulativeSpending.last(), 0.01)
            assertEquals(plan.totalActual.toDouble(), plan.dailyCumulativeSpending.last(), 0.01)
        }
    }

    @Test
    fun `a category budget leaves investments out everywhere`() {
        assertSpent("3898", spentEverywhere(budget("Others")))
    }

    @Test
    fun `the Investments target counts investments everywhere, without rollover`() {
        assertSpent("1700", spentEverywhere(investmentsTarget()))
    }

    @Test
    fun `budget history leaves last month's investments out of an all-spending budget`() = runBlocking {
        val last = month.minusMonths(1)
        val group = budget()
        val repo = repository(
            group,
            listOf(
                tx(1, "100", TransactionType.EXPENSE, date = last.atDay(1)),
                tx(2, "50", TransactionType.INVESTMENT, date = last.atDay(1))
            )
        )
        val history = repo.getBudgetHistoryForMonth(group.budget, last.year, last.monthValue, "INR")
        assertEquals(0, BigDecimal("100").compareTo(history.single().spent))
    }

    @Test
    fun `an all-spending budget isn't incomplete for an investment it doesn't count`() {
        val invested = repository(budget(), listOf(tx(1, "3898", TransactionType.EXPENSE), tx(2, "100", TransactionType.INVESTMENT, "XYZ")))
        val spent = repository(budget(), listOf(tx(1, "3898", TransactionType.EXPENSE), tx(2, "100", TransactionType.EXPENSE, "XYZ")))
        for (unified in listOf(false, true)) {
            assertEquals(false, budgets(invested, unified).hasUnconvertedSpend)
            assertEquals(true, budgets(spent, unified).hasUnconvertedSpend)
        }
    }
}
