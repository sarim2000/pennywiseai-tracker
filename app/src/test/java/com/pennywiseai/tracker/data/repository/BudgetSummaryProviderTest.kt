package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** A budget is marked incomplete only for its own spending that had no rate (#670). */
class BudgetSummaryProviderTest {

    private val today = LocalDate.of(2026, 11, 16)
    private val window = BudgetWindow(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 30)

    private fun budget(id: Long, name: String, vararg categories: String) = BudgetWithCategories(
        BudgetEntity(
            id = id, name = name, limitAmount = BigDecimal("500"), periodType = BudgetPeriodType.MONTHLY,
            startDate = window.start, endDate = window.end, groupType = BudgetGroupType.LIMIT
        ),
        categories.map { BudgetCategoryEntity(budgetId = id, categoryName = it) }
    )

    private fun tx(id: Long, amount: String, category: String, currency: String, type: TransactionType = TransactionType.EXPENSE) =
        TransactionWithSplits(
            TransactionEntity(
                id = id, amount = BigDecimal(amount), merchantName = "M", category = category,
                transactionType = type, dateTime = today.atTime(12, 0), currency = currency,
                transactionHash = "h$id"
            ),
            emptyList()
        )

    private fun provider(
        unified: Boolean,
        budgets: List<BudgetWithCategories>,
        transactions: List<TransactionWithSplits>,
        parentOf: Map<String, String> = emptyMap(),
        // USD converts 1:1; XYZ has never had a rate.
        rate: (amount: BigDecimal, from: String) -> BigDecimal? = { amount, from -> amount.takeIf { from != "XYZ" } }
    ): BudgetSummaryProvider {
        val prefs = mockk<CurrencyPreferences> {
            every { baseCurrency } returns flowOf("JOD")
            every { unifiedCurrencyMode } returns flowOf(unified)
            every { displayCurrency } returns flowOf("JOD")
        }
        val raw = BudgetGroupSpendingRaw(
            budgetsWithCategories = budgets,
            windowedSpend = budgets.map { WindowSpending(it.budget.id, window, transactions) },
            daysElapsed = 16,
            daysRemaining = 15,
            today = today,
            globalStartDay = 1,
            isCurrentMonth = true,
            currentWindows = budgets.associate { it.budget.id to window }
        )
        // The base-currency path's own figures; only the flag is under test here.
        val baseSummary = BudgetOverallSummary(
            groups = budgets.map {
                BudgetGroupSpending(
                    group = it, categorySpending = emptyList(), totalBudget = BigDecimal("500"),
                    totalActual = BigDecimal.ZERO, remaining = BigDecimal("500"), percentageUsed = 0f,
                    dailyAllowance = BigDecimal.ZERO, daysRemaining = 15, daysElapsed = 16,
                    windowStart = window.start, windowEnd = window.end, windowDays = window.days
                )
            },
            totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO, totalLimitSpent = BigDecimal.ZERO,
            totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
            totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO,
            netSavings = BigDecimal.ZERO, savingsRate = 0f, dailyAllowance = BigDecimal.ZERO,
            daysRemaining = 15, currency = "JOD"
        )
        val repo = mockk<BudgetGroupRepository> {
            every { getGroupSpendingAllCurrencies(any(), any()) } returns flowOf(raw)
            every { getGroupSpending(any(), any(), "JOD") } returns flowOf(baseSummary)
            coEvery { parentOf() } returns parentOf
        }
        val conversion = mockk<CurrencyConversionService> {
            coEvery { convertAmountOrNull(any(), any(), any(), any()) } answers { rate(firstArg(), secondArg()) }
        }
        return BudgetSummaryProvider(repo, prefs, conversion)
    }

    private fun flags(provider: BudgetSummaryProvider) = runBlocking {
        provider.summary(YearMonth.from(today), flagUncounted = true).first()
            .groups.associate { it.group.budget.name to it.hasUnconvertedSpend }
    }

    @Test
    fun `unified - only the budget whose category had no rate is incomplete`() {
        val flags = flags(
            provider(
                unified = true,
                budgets = listOf(budget(1, "Food", "Food"), budget(2, "Fun", "Fun")),
                transactions = listOf(tx(1, "10", "Food", "XYZ"), tx(2, "20", "Fun", "USD"))
            )
        )
        assertEquals(mapOf("Food" to true, "Fun" to false), flags)
    }

    @Test
    fun `unified - a budget with no categories ignores rows it never counts`() {
        val all = budget(1, "Everything")
        assertEquals(
            mapOf("Everything" to false),
            flags(provider(true, listOf(all), listOf(tx(1, "10", "Others", "XYZ", TransactionType.CREDIT))))
        )
        assertEquals(
            mapOf("Everything" to true),
            flags(provider(true, listOf(all), listOf(tx(1, "10", "Others", "XYZ"))))
        )
    }

    @Test
    fun `base currency only - this budget's spending in another currency is incomplete`() {
        // The base-currency figures leave USD out, though it would convert in unified mode.
        val flags = flags(
            provider(
                unified = false,
                budgets = listOf(budget(1, "Food", "Food"), budget(2, "Fun", "Fun")),
                transactions = listOf(tx(1, "10", "Food", "USD"), tx(2, "20", "Fun", "JOD"))
            )
        )
        assertEquals(mapOf("Food" to true, "Fun" to false), flags)
    }

    @Test
    fun `base currency only - a parent-only budget is incomplete for its sub-category's other currency`() {
        // The base-currency total counts Groceries under Food, so leaving out its USD reads low.
        val flags = flags(
            provider(
                unified = false,
                budgets = listOf(budget(1, "Food", "Food"), budget(2, "Fun", "Fun")),
                transactions = listOf(tx(1, "10", "Groceries", "USD")),
                parentOf = mapOf("Groceries" to "Food")
            )
        )
        assertEquals(mapOf("Food" to true, "Fun" to false), flags)
    }

    @Test
    fun `unified - a rate that turns up after the total was worked out still marks it incomplete`() {
        // The first fetch fails and the next succeeds: the total left the row out, so the flag says so.
        var calls = 0
        val flags = flags(
            provider(
                unified = true,
                budgets = listOf(budget(1, "Food", "Food")),
                transactions = listOf(tx(1, "10", "Food", "USD")),
                rate = { amount, _ -> amount.takeIf { calls++ > 0 } }
            )
        )
        assertEquals(mapOf("Food" to true), flags)
    }

    @Test
    fun `base currency only - a budget with no categories is incomplete for a card charge in another currency`() {
        // The base-currency total counts every non-income, non-transfer row, credit included.
        val all = budget(1, "Everything")
        assertEquals(
            mapOf("Everything" to true),
            flags(provider(false, listOf(all), listOf(tx(1, "10", "Others", "USD", TransactionType.CREDIT))))
        )
        assertEquals(
            mapOf("Everything" to false),
            flags(provider(false, listOf(all), listOf(tx(1, "10", "Others", "USD", TransactionType.INCOME))))
        )
    }
}
