package com.pennywiseai.tracker.presentation.budgetgroups

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.preferences.eligibleBudgetMonthDismissals
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** The card (and its one-time confetti) only for a month every spending limit survived. */
class MonthUnderBudgetTest {
    private val nov3 = LocalDate.parse("2026-11-03")

    private fun group(spent: String, limit: String = "500", type: BudgetGroupType = BudgetGroupType.LIMIT) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(
                name = "Budget", limitAmount = BigDecimal(limit), periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.parse("2026-10-01"), endDate = LocalDate.parse("2026-10-31"), groupType = type,
            ),
            emptyList(),
        ),
        categorySpending = emptyList(),
        totalBudget = BigDecimal(limit), totalActual = BigDecimal(spent), remaining = BigDecimal.ZERO,
        percentageUsed = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, daysElapsed = 31,
    )

    private fun summary(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, currency = "JOD",
    )

    @Test
    fun `every limit kept names last month and the page's currency`() {
        val win = monthUnderBudget(nov3, summary(group("480"), group("0", limit = "100")), dismissed = emptySet())
        assertEquals(MonthUnderBudget(YearMonth.of(2026, 10), "JOD"), win)
        assertEquals("budget_month:2026-10:JOD", win?.key)
    }

    @Test
    fun `spending exactly the limit still counts as under`() {
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(nov3, summary(group("500")), emptySet())?.month)
    }

    @Test
    fun `one limit over, no spending, or no limit at all is no win`() {
        assertNull(monthUnderBudget(nov3, summary(group("100"), group("500.01")), emptySet()))
        assertNull(monthUnderBudget(nov3, summary(group("0")), emptySet()))
        assertNull(monthUnderBudget(nov3, summary(group("10", limit = "0")), emptySet()))
        assertNull(monthUnderBudget(nov3, summary(), emptySet()))
        // Unconverted spend could hide an overspend.
        assertNull(monthUnderBudget(nov3, summary(group("100").copy(hasUnconvertedSpend = true)), emptySet()))
    }

    @Test
    fun `a category over its own limit is no win, even with the total under`() {
        fun cat(spent: String, limit: String) = BudgetCategorySpending(
            categoryName = "Food", budgetAmount = BigDecimal(limit), actualAmount = BigDecimal(spent),
            percentageUsed = 0f, dailySpend = BigDecimal.ZERO,
        )
        // Food 7,000 of 5,000 and Shopping 1,000 of 5,000: 8,000 of 10,000 overall.
        val overInFood = group("8000", limit = "10000").copy(categorySpending = listOf(cat("7000", "5000"), cat("1000", "5000")))
        assertNull(monthUnderBudget(nov3, summary(overInFood), emptySet()))
        // A category with no limit of its own can't be over one.
        val noCategoryLimit = group("8000", limit = "10000").copy(categorySpending = listOf(cat("7000", "0"), cat("1000", "5000")))
        assertEquals("JOD", monthUnderBudget(nov3, summary(noCategoryLimit), emptySet())?.currency)
    }

    @Test
    fun `savings and expected budgets neither count nor block`() {
        // A missed savings target isn't an overspend, and an income budget alone isn't a limit kept.
        assertEquals("JOD", monthUnderBudget(nov3, summary(group("100"), group("900", type = BudgetGroupType.TARGET)), emptySet())?.currency)
        assertNull(monthUnderBudget(nov3, summary(group("100", type = BudgetGroupType.TARGET)), emptySet()))
        assertNull(monthUnderBudget(nov3, summary(group("100", type = BudgetGroupType.EXPECTED)), emptySet()))
    }

    @Test
    fun `weekly limits neither count nor block`() {
        // The month's page shows only a Weekly budget's last week, which can hide a blown first one.
        val weekly = group("900").copy(periodType = BudgetPeriodType.WEEKLY)
        assertEquals("JOD", monthUnderBudget(nov3, summary(group("100"), weekly), emptySet())?.currency)
        assertNull(monthUnderBudget(nov3, summary(group("100").copy(periodType = BudgetPeriodType.WEEKLY)), emptySet()))
    }

    @Test
    fun `shows the first week only, and not once dismissed`() {
        val kept = summary(group("100"))
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(LocalDate.parse("2026-11-07"), kept, emptySet())?.month)
        assertNull(monthUnderBudget(LocalDate.parse("2026-11-08"), kept, emptySet()))
        assertNull(monthUnderBudget(nov3, kept, dismissed = setOf("budget_month:2026-10:JOD")))
        // Last month's dismissal doesn't hide this one, and neither does another currency.
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(nov3, kept, dismissed = setOf("budget_month:2026-09:JOD"))?.month)
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(nov3, kept, dismissed = setOf("budget_month:2026-10:USD"))?.month)
    }

    @Test
    fun `a late under-budget card shifts the focused budget down by one`() {
        val others = listOf(7L, 8L)
        assertEquals(2, budgetFocusIndex(7L, hasGoals = true, hasWin = false, heroId = null, otherIds = others))
        assertEquals(3, budgetFocusIndex(7L, hasGoals = true, hasWin = true, heroId = null, otherIds = others))
        assertEquals(2, budgetFocusIndex(7L, hasGoals = false, hasWin = true, heroId = 7L, otherIds = listOf(8L)))
    }

    @Test
    fun `dismissed keys drop once that month is no longer eligible`() {
        val october = setOf("budget_month:2026-10:JOD", "budget_month:2026-10:USD")
        val stored = october + "budget_month:2026-09:JOD"
        assertEquals(october, eligibleBudgetMonthDismissals(stored, LocalDate.parse("2026-11-03"), UNDER_BUDGET_CARD_DAYS))
        assertEquals(october, eligibleBudgetMonthDismissals(stored, LocalDate.parse("2026-11-07"), UNDER_BUDGET_CARD_DAYS))
        assertEquals(emptySet<String>(), eligibleBudgetMonthDismissals(stored, LocalDate.parse("2026-11-08"), UNDER_BUDGET_CARD_DAYS))
        assertEquals(
            setOf("budget_month:2026-11:JOD"),
            eligibleBudgetMonthDismissals(stored + "budget_month:2026-11:JOD", LocalDate.parse("2026-12-01"), UNDER_BUDGET_CARD_DAYS),
        )
    }
}
