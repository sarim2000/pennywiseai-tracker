package com.pennywiseai.tracker.presentation.budgetgroups

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.preferences.eligibleBudgetMonthDismissals
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.CarryGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** The card (and its one-time confetti) only for a month every spending limit survived. */
class MonthUnderBudgetTest {
    private val october = BudgetWindow(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), 31)

    private val september = LocalDateTime.parse("2026-09-01T10:00")

    private fun group(
        spent: String,
        limit: String = "500",
        type: BudgetGroupType = BudgetGroupType.LIMIT,
        createdAt: LocalDateTime = september,
        updatedAt: LocalDateTime = createdAt,
    ) = BudgetGroupSpending(
        group = BudgetWithCategories(
            BudgetEntity(
                name = "Budget", limitAmount = BigDecimal(limit), periodType = BudgetPeriodType.MONTHLY,
                startDate = LocalDate.parse("2026-10-01"), endDate = LocalDate.parse("2026-10-31"), groupType = type,
                createdAt = createdAt, updatedAt = updatedAt,
            ),
            emptyList(),
        ),
        categorySpending = emptyList(),
        totalBudget = BigDecimal(limit), totalActual = BigDecimal(spent), remaining = BigDecimal.ZERO,
        percentageUsed = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, daysElapsed = 31,
        windowStart = october.start, windowEnd = october.end, windowDays = october.days,
    )

    private fun summary(vararg groups: BudgetGroupSpending) = BudgetOverallSummary(
        groups = groups.toList(), totalIncome = BigDecimal.ZERO, totalLimitBudget = BigDecimal.ZERO,
        totalLimitSpent = BigDecimal.ZERO, totalTargetGoal = BigDecimal.ZERO, totalTargetActual = BigDecimal.ZERO,
        totalExpectedBudget = BigDecimal.ZERO, totalExpectedActual = BigDecimal.ZERO, netSavings = BigDecimal.ZERO,
        savingsRate = 0f, dailyAllowance = BigDecimal.ZERO, daysRemaining = 0, currency = "JOD",
    )

    @Test
    fun `every limit kept names last month and the page's currency`() {
        val win = monthUnderBudget(october, summary(group("480"), group("0", limit = "100")), dismissed = emptySet())
        assertEquals(MonthUnderBudget(october.start, october.end, "JOD"), win)
        assertEquals("budget_month:2026-10:JOD:2026-10-01", win?.key)
    }

    @Test
    fun `spending exactly the limit still counts as under`() {
        assertEquals(YearMonth.of(2026, 10), monthUnderBudget(october, summary(group("500")), emptySet())?.month)
    }

    @Test
    fun `one limit over, no spending, or no limit at all is no win`() {
        assertNull(monthUnderBudget(october, summary(group("100"), group("500.01")), emptySet()))
        assertNull(monthUnderBudget(october, summary(group("0")), emptySet()))
        assertNull(monthUnderBudget(october, summary(group("10", limit = "0")), emptySet()))
        assertNull(monthUnderBudget(october, summary(), emptySet()))
        // Unconverted spend could hide an overspend.
        assertNull(monthUnderBudget(october, summary(group("100").copy(hasUnconvertedSpend = true)), emptySet()))
        // Rollover's carry couldn't be counted, so the limit it was judged against may be too high.
        assertNull(monthUnderBudget(october, summary(group("100").copy(carryGap = CarryGap.NO_RATE)), emptySet()))
    }

    @Test
    fun `a category over its own limit is no win, even with the total under`() {
        fun cat(spent: String, limit: String) = BudgetCategorySpending(
            categoryName = "Food", budgetAmount = BigDecimal(limit), actualAmount = BigDecimal(spent),
            percentageUsed = 0f, dailySpend = BigDecimal.ZERO,
        )
        // Food 7,000 of 5,000 and Shopping 1,000 of 5,000: 8,000 of 10,000 overall.
        val overInFood = group("8000", limit = "10000").copy(categorySpending = listOf(cat("7000", "5000"), cat("1000", "5000")))
        assertNull(monthUnderBudget(october, summary(overInFood), emptySet()))
        // A category with no limit of its own can't be over one.
        val noCategoryLimit = group("8000", limit = "10000").copy(categorySpending = listOf(cat("7000", "0"), cat("1000", "5000")))
        assertEquals("JOD", monthUnderBudget(october, summary(noCategoryLimit), emptySet())?.currency)
    }

    @Test
    fun `savings and expected budgets neither count nor block`() {
        // A missed savings target isn't an overspend, and an income budget alone isn't a limit kept.
        assertEquals("JOD", monthUnderBudget(october, summary(group("100"), group("900", type = BudgetGroupType.TARGET)), emptySet())?.currency)
        assertNull(monthUnderBudget(october, summary(group("100", type = BudgetGroupType.TARGET)), emptySet()))
        assertNull(monthUnderBudget(october, summary(group("100", type = BudgetGroupType.EXPECTED)), emptySet()))
    }

    @Test
    fun `weekly limits neither count nor block`() {
        // The month's page shows only a Weekly budget's last week, which can hide a blown first one.
        val weekly = group("900").copy(periodType = BudgetPeriodType.WEEKLY)
        assertEquals("JOD", monthUnderBudget(october, summary(group("100"), weekly), emptySet())?.currency)
        assertNull(monthUnderBudget(october, summary(group("100").copy(periodType = BudgetPeriodType.WEEKLY)), emptySet()))
    }

    @Test
    fun `the card shows the week after a cycle ends, and not once dismissed`() {
        assertEquals(october, underBudgetCycle(LocalDate.parse("2026-11-01"), startDay = 1))
        assertEquals(october, underBudgetCycle(LocalDate.parse("2026-11-07"), startDay = 1))
        assertNull(underBudgetCycle(LocalDate.parse("2026-11-08"), startDay = 1))
        val kept = summary(group("100"))
        assertNull(monthUnderBudget(october, kept, dismissed = setOf("budget_month:2026-10:JOD:2026-10-01")))
        // Last month's dismissal doesn't hide this one, and neither does another currency.
        assertEquals(october.end, monthUnderBudget(october, kept, dismissed = setOf("budget_month:2026-09:JOD:2026-09-01"))?.end)
        assertEquals(october.end, monthUnderBudget(october, kept, dismissed = setOf("budget_month:2026-10:USD:2026-10-01"))?.end)
    }

    @Test
    fun `a cycle from the 25th is judged the week after the 24th, under its own dates`() {
        val cycle = BudgetWindow(LocalDate.parse("2026-09-25"), LocalDate.parse("2026-10-24"), 30)
        assertEquals(cycle, underBudgetCycle(LocalDate.parse("2026-10-25"), startDay = 25))
        assertEquals(cycle, underBudgetCycle(LocalDate.parse("2026-10-31"), startDay = 25))
        // A week into November is the old calendar rule, two weeks late for this cycle.
        assertNull(underBudgetCycle(LocalDate.parse("2026-11-03"), startDay = 25))
        val onCycle = group("100").copy(windowStart = cycle.start, windowEnd = cycle.end)
        val win = monthUnderBudget(cycle, summary(onCycle), emptySet())
        assertEquals(MonthUnderBudget(cycle.start, cycle.end, "JOD"), win)
        assertEquals("budget_month:2026-10:JOD:2026-09-25", win?.key)
    }

    @Test
    fun `a budget made after the cycle ended neither counts nor blocks`() {
        val madeNov3 = LocalDateTime.parse("2026-11-03T09:00")
        // A first budget on the 3rd isn't cheered for last month.
        assertNull(monthUnderBudget(october, summary(group("100", createdAt = madeNov3)), emptySet()))
        // Nor does one made since hide a held one; one made on the last day still counts.
        assertEquals("JOD", monthUnderBudget(october, summary(group("100"), group("900", createdAt = madeNov3)), emptySet())?.currency)
        val madeOct31 = LocalDateTime.parse("2026-10-31T22:00")
        assertNull(monthUnderBudget(october, summary(group("900", createdAt = madeOct31)), emptySet()))
    }

    @Test
    fun `a limit edited after the cycle ended can't be judged`() {
        // Raised on Nov 2 to cover October's overspend: today's limit says nothing about October's.
        val raised = group("900", limit = "1000", updatedAt = LocalDateTime.parse("2026-11-02T08:00"))
        assertNull(monthUnderBudget(october, summary(group("100"), raised), emptySet()))
        val editedInOctober = group("100", updatedAt = LocalDateTime.parse("2026-10-20T08:00"))
        assertEquals("JOD", monthUnderBudget(october, summary(editedInOctober), emptySet())?.currency)
    }

    @Test
    fun `a budget on another cycle neither counts nor blocks`() {
        // Its own start day: on Oct 31 its cycle (Oct 15 - Nov 14) is still running.
        val running = group("900").copy(windowStart = LocalDate.parse("2026-10-15"), windowEnd = LocalDate.parse("2026-11-14"))
        assertEquals("JOD", monthUnderBudget(october, summary(group("100"), running), emptySet())?.currency)
        assertNull(monthUnderBudget(october, summary(running), emptySet()))
    }

    @Test
    fun `a late under-budget card shifts the focused budget down by one`() {
        val others = listOf(7L, 8L)
        assertEquals(2, budgetFocusIndex(7L, hasGoals = true, hasWin = false, heroId = null, otherIds = others))
        assertEquals(3, budgetFocusIndex(7L, hasGoals = true, hasWin = true, heroId = null, otherIds = others))
        assertEquals(2, budgetFocusIndex(7L, hasGoals = false, hasWin = true, heroId = 7L, otherIds = listOf(8L)))
    }

    @Test
    fun `dismissed keys keep only the month whose card can still show`() {
        val october = setOf("budget_month:2026-10:JOD:2026-10-01", "budget_month:2026-10:USD:2026-10-01")
        val stored = october + "budget_month:2026-09:JOD:2026-09-01"
        assertEquals(october, eligibleBudgetMonthDismissals(stored, YearMonth.of(2026, 10)))
        assertEquals(emptySet<String>(), eligibleBudgetMonthDismissals(stored, keepMonth = null))
        assertEquals(
            setOf("budget_month:2026-11:JOD"),
            eligibleBudgetMonthDismissals(stored + "budget_month:2026-11:JOD", YearMonth.of(2026, 11)),
        )
    }

    @Test
    fun `a calendar month and a custom cycle ending in it keep their own keys`() {
        val custom = MonthUnderBudget(LocalDate.parse("2026-09-25"), LocalDate.parse("2026-10-24"), "JOD")
        val calendar = MonthUnderBudget(october.start, october.end, "JOD")
        assertEquals(custom.month, calendar.month)
        assertNotEquals(custom.key, calendar.key)
        // The prune still keeps both: they start with the same month prefix.
        assertEquals(setOf(custom.key, calendar.key), eligibleBudgetMonthDismissals(setOf(custom.key, calendar.key), custom.month))
    }

    @Test
    fun `a page left on the current month follows the date into the next one`() {
        val dec = YearMonth.of(2026, 12)
        // Kept alive from 28 Dec to 3 Jan: the page moves to January, where the card shows.
        assertEquals(YearMonth.of(2027, 1), followToday(dec, LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-03")))
        // Moved back to an older month: it stays there.
        assertEquals(YearMonth.of(2026, 11), followToday(YearMonth.of(2026, 11), LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-03")))
        // Same month: nothing changes.
        assertEquals(dec, followToday(dec, LocalDate.parse("2026-12-28"), LocalDate.parse("2026-12-29")))
    }
}
