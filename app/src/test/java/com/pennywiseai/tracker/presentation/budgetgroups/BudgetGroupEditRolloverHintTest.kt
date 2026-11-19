package com.pennywiseai.tracker.presentation.budgetgroups

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The edit screen says rollover starts next period exactly when saving would start it now. */
class BudgetGroupEditRolloverHintTest {

    // Tuesday 17 Nov 2026, in the week of Monday 16 Nov.
    private val today = LocalDate.of(2026, 11, 17)
    private val weekStart = LocalDate.of(2026, 11, 16)

    private fun saved(enabled: Boolean, since: LocalDate?) = BudgetEntity(
        id = 1, name = "Food", limitAmount = BigDecimal("100"), periodType = BudgetPeriodType.WEEKLY,
        startDate = weekStart, endDate = weekStart.plusDays(6), weekStartDay = 1, currency = "JOD",
        groupType = BudgetGroupType.LIMIT, rolloverEnabled = enabled, rolloverSince = since
    )

    private val food = CategoryBudgetItem(categoryName = "Food", amount = BigDecimal("100"), currentSpending = BigDecimal.ZERO)

    private fun state(saved: BudgetEntity?, rollover: Boolean = true) = BudgetGroupEditUiState(
        groupId = saved?.id, currency = "JOD", periodType = BudgetPeriodType.WEEKLY, weekStartDay = 1,
        startDate = weekStart, endDate = weekStart.plusDays(6), rolloverEnabled = rollover, saved = saved,
        overallAmount = "100", categories = listOf(food), savedScope = setOf("Food" to null)
    )

    @Test
    fun `turning it on, on a new or existing budget, starts it next period`() {
        assertTrue(state(saved = null).rolloverStartsNextPeriod(today))
        assertTrue(state(saved(enabled = false, since = null)).rolloverStartsNextPeriod(today))
    }

    @Test
    fun `turned on earlier this week, it still starts next week`() {
        assertTrue(state(saved(enabled = true, since = weekStart)).rolloverStartsNextPeriod(today))
    }

    @Test
    fun `already running from an earlier week, nothing to say`() {
        assertFalse(state(saved(enabled = true, since = LocalDate.of(2026, 10, 1))).rolloverStartsNextPeriod(today))
    }

    @Test
    fun `a new cadence or currency restarts it`() {
        val running = saved(enabled = true, since = LocalDate.of(2026, 10, 1))
        assertTrue(state(running).copy(weekStartDay = 3, startDate = LocalDate.of(2026, 11, 11)).rolloverStartsNextPeriod(today))
        assertTrue(state(running).copy(currency = "USD").rolloverStartsNextPeriod(today))
    }

    @Test
    fun `a new limit or new categories restart it`() {
        val running = saved(enabled = true, since = LocalDate.of(2026, 10, 1))
        assertFalse(state(running).copy(overallAmount = "100.00").rolloverStartsNextPeriod(today))
        assertTrue(state(running).copy(overallAmount = "150").rolloverStartsNextPeriod(today))
        assertTrue(state(running).copy(categories = listOf(food.copy(categoryName = "Fuel"))).rolloverStartsNextPeriod(today))
        // A category's own amount isn't what it counts.
        assertFalse(state(running).copy(categories = listOf(food.copy(amount = BigDecimal("60")))).rolloverStartsNextPeriod(today))
    }

    @Test
    fun `off, or a budget that can't roll over, says nothing`() {
        assertFalse(state(saved = null, rollover = false).rolloverStartsNextPeriod(today))
        assertFalse(state(saved = null).copy(periodType = BudgetPeriodType.CUSTOM).rolloverStartsNextPeriod(today))
        assertFalse(state(saved = null).copy(groupType = BudgetGroupType.TARGET).rolloverStartsNextPeriod(today))
    }
}
