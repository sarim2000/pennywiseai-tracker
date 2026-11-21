package com.pennywiseai.tracker.presentation.budgetgroups

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
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
        val win = monthUnderBudget(nov3, summary(group("480"), group("0", limit = "100")), dismissed = null)
        assertEquals(MonthUnderBudget(YearMonth.of(2026, 10), "JOD"), win)
        assertEquals("budget_month:2026-10:JOD", win?.key)
    }

    @Test
    fun `spending exactly the limit still counts as under`() {
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(nov3, summary(group("500")), null)?.month)
    }

    @Test
    fun `one limit over, no spending, or no limit at all is no win`() {
        assertNull(monthUnderBudget(nov3, summary(group("100"), group("500.01")), null))
        assertNull(monthUnderBudget(nov3, summary(group("0")), null))
        assertNull(monthUnderBudget(nov3, summary(group("10", limit = "0")), null))
        assertNull(monthUnderBudget(nov3, summary(), null))
        // Unconverted spend could hide an overspend.
        assertNull(monthUnderBudget(nov3, summary(group("100").copy(hasUnconvertedSpend = true)), null))
    }

    @Test
    fun `savings and expected budgets neither count nor block`() {
        // A missed savings target isn't an overspend, and an income budget alone isn't a limit kept.
        assertEquals("JOD", monthUnderBudget(nov3, summary(group("100"), group("900", type = BudgetGroupType.TARGET)), null)?.currency)
        assertNull(monthUnderBudget(nov3, summary(group("100", type = BudgetGroupType.TARGET)), null))
        assertNull(monthUnderBudget(nov3, summary(group("100", type = BudgetGroupType.EXPECTED)), null))
    }

    @Test
    fun `shows the first week only, and not once dismissed`() {
        val kept = summary(group("100"))
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(LocalDate.parse("2026-11-07"), kept, null)?.month)
        assertNull(monthUnderBudget(LocalDate.parse("2026-11-08"), kept, null))
        assertNull(monthUnderBudget(nov3, kept, dismissed = "budget_month:2026-10:JOD"))
        // Last month's dismissal doesn't hide this one.
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(nov3, kept, dismissed = "budget_month:2026-09:JOD")?.month)
    }
}
