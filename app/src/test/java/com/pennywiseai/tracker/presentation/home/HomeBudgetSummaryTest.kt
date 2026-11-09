package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetCategoryEntity
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.repository.BudgetGroupSpendingRaw
import com.pennywiseai.tracker.data.repository.BudgetWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class HomeBudgetSummaryTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val march = window(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))

    private fun window(start: LocalDate, end: LocalDate) =
        BudgetWindow(start, end, ChronoUnit.DAYS.between(start, end).toInt() + 1)

    private fun budget(
        id: Long,
        limit: String = "0",
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        period: BudgetPeriodType = BudgetPeriodType.MONTHLY,
        categories: List<Pair<String, String>> = emptyList(),
    ) = BudgetWithCategories(
        BudgetEntity(
            id = id,
            name = "Budget $id",
            limitAmount = BigDecimal(limit),
            periodType = period,
            startDate = march.start,
            endDate = march.end,
            groupType = type,
        ),
        categories.map { (name, amount) -> BudgetCategoryEntity(budgetId = id, categoryName = name, budgetAmount = BigDecimal(amount)) },
    )

    private fun raw(
        budgets: List<BudgetWithCategories>,
        transactions: List<TransactionEntity>,
        windows: Map<Long, BudgetWindow> = budgets.associate { it.budget.id to march },
    ) = BudgetGroupSpendingRaw(
        budgetsWithCategories = budgets,
        windowedSpend = emptyList(),
        daysElapsed = 0,
        daysRemaining = 0,
        today = today,
        globalStartDay = 1,
        isCurrentMonth = true,
        allTransactions = transactions.map { TransactionWithSplits(it, emptyList()) },
        currentWindows = windows,
    )

    private suspend fun summary(
        raw: BudgetGroupSpendingRaw,
        display: String = "INR",
        base: String = "INR",
        convert: ConvertAmount = fakeConvert(),
    ) = homeBudgetSummary(raw, displayCurrency = display, baseCurrency = base, convert = convert)

    private fun spend(amount: String, category: String = "Food", date: LocalDate = today, currency: String = "INR") =
        tx(amount, TransactionType.EXPENSE, currency = currency, date = date, category = category)

    @Test fun `categorised refund is not income but an orphan refund is`() = runTest {
        val s = summary(
            raw(
                emptyList(),
                listOf(
                    tx("5000", TransactionType.INCOME),
                    tx("200", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Food"),
                    tx("100", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT),
                ),
            )
        )
        assertAmount("5100", s.totalIncome)
    }

    @Test fun `refund comes off its category and extra budget raises its limit`() = runTest {
        val s = summary(
            raw(
                listOf(budget(1, categories = listOf("Food" to "1000"))),
                listOf(
                    spend("600"),
                    tx("100", TransactionType.INCOME, impact = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Food"),
                    tx("200", TransactionType.INCOME, impact = BudgetImpactType.ADD_TO_LIMIT, budgetCategory = "Food"),
                ),
            )
        )
        val group = s.groups.single()
        val food = group.categorySpending.single()
        assertAmount("500", food.actualAmount)
        assertAmount("1200", food.budgetAmount)
        assertAmount("1200", group.totalBudget)
        assertAmount("700", group.remaining)
    }

    @Test fun `weekly budget counts only its current window`() = runTest {
        val week = window(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15))
        val s = summary(
            raw(
                listOf(budget(1, limit = "1000", period = BudgetPeriodType.WEEKLY)),
                listOf(spend("300", date = LocalDate.of(2026, 3, 3)), spend("100", date = LocalDate.of(2026, 3, 10))),
                windows = mapOf(1L to week),
            )
        )
        val group = s.groups.single()
        assertAmount("100", group.totalActual)
        assertEquals(week.start, group.windowStart)
        assertEquals(week.end, group.windowEnd)
        assertEquals(7, group.windowDays)
        assertEquals(BudgetPeriodType.WEEKLY, group.periodType)
    }

    @Test fun `tracking-all budget sums every category and has no category rows`() = runTest {
        val s = summary(
            raw(listOf(budget(1, limit = "1000")), listOf(spend("100"), spend("50", category = "Travel")))
        )
        val group = s.groups.single()
        assertTrue(group.isTrackingAllExpenses)
        assertTrue(group.categorySpending.isEmpty())
        assertAmount("150", group.totalActual)
    }

    @Test fun `group limit wins over the category sum, no limit falls back to it`() = runTest {
        val s = summary(
            raw(
                listOf(
                    budget(1, limit = "500", categories = listOf("Food" to "1000")),
                    budget(2, categories = listOf("Food" to "300", "Travel" to "250")),
                ),
                listOf(spend("100"), spend("40", category = "Travel"), spend("70", category = "Rent")),
            )
        )
        assertAmount("500", s.groups[0].totalBudget)
        assertAmount("100", s.groups[0].totalActual)
        assertAmount("550", s.groups[1].totalBudget)
        assertAmount("140", s.groups[1].totalActual)
    }

    @Test fun `base-currency limit and foreign spend are converted, a never-rated pair is left out`() = runTest {
        val s = summary(
            raw(
                listOf(budget(1, limit = "10000")),
                listOf(
                    spend("10", currency = "USD"),
                    spend("1000", currency = "INR"),
                    spend("5", currency = "XYZ"),
                    tx("50", TransactionType.INCOME, currency = "USD"),
                    tx("100", TransactionType.INCOME, currency = "XYZ"),
                ),
            ),
            display = "USD",
            base = "INR",
            convert = fakeConvert(mapOf("INR>USD" to "0.012")),
        )
        val group = s.groups.single()
        assertAmount("120", group.totalBudget)
        assertAmount("22", group.totalActual)
        assertAmount("50", s.totalIncome)
        assertEquals("USD", s.currency)
    }

    @Test fun `an unrated limit falls back to the category sum`() = runTest {
        // Limits are stored in the base currency, here one with no rate: the group limit and
        // the category limit both read as zero, leaving only the extra budget added in USD.
        val s = summary(
            raw(
                listOf(budget(1, limit = "10000", categories = listOf("Food" to "300"))),
                listOf(tx("40", TransactionType.INCOME, currency = "USD", impact = BudgetImpactType.ADD_TO_LIMIT, budgetCategory = "Food")),
            ),
            display = "USD",
            base = "XYZ",
            convert = fakeConvert(),
        )
        assertAmount("40", s.groups.single().totalBudget)
    }

    @Test fun `days remaining, elapsed and allowance come from each budget's own window`() = runTest {
        val week = window(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15))
        val s = summary(
            raw(
                listOf(
                    budget(1, limit = "1000", categories = listOf("Food" to "1000")),
                    budget(2, limit = "100", period = BudgetPeriodType.WEEKLY),
                ),
                listOf(spend("305")),
                windows = mapOf(1L to march, 2L to week),
            )
        )
        val (monthly, weekly) = s.groups
        assertEquals(22, monthly.daysRemaining) // Mar 10..31
        assertEquals(10, monthly.daysElapsed) // Mar 1..10
        assertAmount("32", monthly.dailyAllowance) // 695 / 22 = 31.59
        assertAmount("31", monthly.categorySpending.single().dailySpend) // 305 / 10 = 30.5
        assertEquals(6, weekly.daysRemaining) // Mar 10..15
        assertEquals(2, weekly.daysElapsed) // Mar 9..10
        assertAmount("0", weekly.dailyAllowance) // over its limit
        // The page figures read the first budget's window.
        assertEquals(22, s.daysRemaining)
        assertAmount("22", s.dailyAllowance) // (1100 - 610) / 22 = 22.27
    }

    @Test fun `limit, target and expected budgets total separately, and savings use limit spend only`() = runTest {
        val s = summary(
            raw(
                listOf(
                    budget(1, limit = "1000"),
                    budget(2, limit = "500", type = BudgetGroupType.TARGET),
                    budget(3, limit = "200", type = BudgetGroupType.EXPECTED),
                ),
                listOf(spend("300"), tx("1000", TransactionType.INCOME)),
            )
        )
        assertAmount("1000", s.totalLimitBudget)
        assertAmount("300", s.totalLimitSpent)
        assertAmount("500", s.totalTargetGoal)
        assertAmount("300", s.totalTargetActual)
        assertAmount("200", s.totalExpectedBudget)
        assertAmount("300", s.totalExpectedActual)
        assertAmount("700", s.netSavings)
        assertEquals(70f, s.savingsRate, 0.001f)
    }

    @Test fun `budget without a window counts every row and has no days`() = runTest {
        val s = summary(
            raw(
                listOf(budget(1, limit = "1000")),
                listOf(spend("100", date = LocalDate.of(2026, 2, 1)), spend("50")),
                windows = emptyMap(),
            )
        )
        val group = s.groups.single()
        assertAmount("150", group.totalActual)
        assertEquals(0, group.windowDays)
        assertEquals(0, group.daysRemaining)
        assertEquals(0, group.daysElapsed)
        assertAmount("0", group.dailyAllowance)
    }

    @Test fun `no budgets give an empty summary in the display currency`() = runTest {
        val s = summary(raw(emptyList(), emptyList()), display = "AED", base = "AED")
        assertTrue(s.groups.isEmpty())
        assertAmount("0", s.totalIncome)
        assertAmount("0", s.totalLimitBudget)
        assertAmount("0", s.dailyAllowance)
        assertEquals(0, s.daysRemaining)
        assertEquals(0f, s.savingsRate, 0f)
        assertEquals("AED", s.currency)
    }
}
