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

    private fun tx(id: Long, amount: String, type: TransactionType, currency: String = "INR") = TransactionWithSplits(
        TransactionEntity(
            id = id, amount = BigDecimal(amount), merchantName = "Shop", category = "Others",
            transactionType = type, dateTime = today.atTime(9, 0), currency = currency, transactionHash = "h$id"
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
    fun `a category budget leaves investments out everywhere`() {
        assertSpent("3898", spentEverywhere(budget("Others")))
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
