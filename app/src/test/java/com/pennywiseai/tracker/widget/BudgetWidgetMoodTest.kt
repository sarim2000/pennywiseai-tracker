package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BudgetWidgetMoodTest {

    private fun mood(spent: String, limit: String = "3000", overPace: Boolean? = false) = budgetMood(
        BudgetWidgetData(
            totalSpent = BigDecimal(spent),
            totalLimit = BigDecimal(limit),
            remaining = BigDecimal(limit) - BigDecimal(spent),
            overPace = overPace
        )
    )

    @Test
    fun `the stored pace picks happy or thinking`() {
        assertEquals(MascotMood.Happy, mood("1000", overPace = false))
        assertEquals(MascotMood.Thinking, mood("1000", overPace = true))
    }

    @Test
    fun `over the limit as the figure shows it is worried`() {
        assertEquals(MascotMood.Worried, mood("3001"))
        // 0.3 over reads "Left · 0" on the widget, so the owl doesn't worry yet.
        assertEquals(MascotMood.Thinking, mood("3000.3", overPace = true))
    }

    @Test
    fun `the figure reads over or left as shown, and neither while amounts are hidden`() {
        assertEquals(true, budgetOver(BigDecimal("-1"), hidden = false))
        assertEquals(false, budgetOver(BigDecimal("-0.3"), hidden = false))
        assertEquals(false, budgetOver(BigDecimal("120"), hidden = false))
        assertNull(budgetOver(BigDecimal("-1"), hidden = true))
        assertNull(budgetOver(BigDecimal("120"), hidden = true))
    }

    @Test
    fun `an over-budget snapshot without a pace is still worried`() {
        assertEquals(MascotMood.Worried, mood("3001", overPace = null))
    }

    @Test
    fun `over a limit rollover carried to zero is worried`() {
        val carriedToZero = BudgetWidgetData(totalSpent = BigDecimal(100), hasLimit = true, remaining = BigDecimal(-100))
        assertEquals(MascotMood.Worried, budgetMood(carriedToZero))
    }

    @Test
    fun `no budget or no running limit is calm`() {
        assertEquals(MascotMood.Calm, budgetMood(BudgetWidgetData()))
        assertEquals(MascotMood.Calm, mood("400", overPace = null))
    }

    @Test
    fun `a weekly limit is paced on its week, not the month`() {
        // Day 2 of 7 with 3 days' budget spent. Over the calendar month it would look fine.
        val weekly = limit(spent = 300, budget = 700, start = "2026-11-16", end = "2026-11-22")
        assertEquals(true, widgetOverPace(LocalDate.parse("2026-11-17"), listOf(weekly)))
        assertEquals(false, widgetOverPace(LocalDate.parse("2026-11-20"), listOf(weekly)))
    }

    @Test
    fun `a cycle starting on the 25th is paced from the 25th`() {
        // Day 3 of 30: an even spend is 300, a day's budget 100.
        val today = LocalDate.parse("2026-11-27")
        val cycle = { spent: Int -> limit(spent = spent, budget = 3000, start = "2026-11-25", end = "2026-12-24") }
        assertEquals(true, widgetOverPace(today, listOf(cycle(400))))
        assertEquals(false, widgetOverPace(today, listOf(cycle(399))))
    }

    @Test
    fun `one budget over pace isn't hidden by another under it`() {
        val weeklyOver = limit(spent = 600, budget = 700, start = "2026-11-16", end = "2026-11-22")
        val monthlyUnder = limit(spent = 0, budget = 30000, start = "2026-11-01", end = "2026-11-30")
        assertEquals(true, widgetOverPace(LocalDate.parse("2026-11-17"), listOf(weeklyOver, monthlyUnder)))
    }

    @Test
    fun `only limits running today count`() {
        val today = LocalDate.parse("2026-11-23")
        val ended = limit(spent = 800, budget = 700, start = "2026-11-16", end = "2026-11-22")
        val future = limit(spent = 0, budget = 700, start = "2026-11-25", end = "2026-11-30")
        val savings = limit(spent = 900, budget = 100, start = "2026-11-01", end = "2026-11-30", type = BudgetGroupType.TARGET)
        assertNull(widgetOverPace(today, listOf(ended, future, savings)))
    }

    private fun limit(
        spent: Int,
        budget: Int,
        start: String,
        end: String,
        type: BudgetGroupType = BudgetGroupType.LIMIT
    ): BudgetGroupSpending {
        val from = LocalDate.parse(start)
        val to = LocalDate.parse(end)
        val entity = BudgetEntity(
            id = 1, name = "Budget", limitAmount = BigDecimal(budget), periodType = BudgetPeriodType.MONTHLY,
            startDate = from, endDate = to, currency = "INR", groupType = type
        )
        return BudgetGroupSpending(
            BudgetWithCategories(entity, emptyList()), emptyList(), BigDecimal(budget), BigDecimal(spent),
            BigDecimal(budget - spent), 0f, BigDecimal.ZERO, 0, 0,
            windowStart = from, windowEnd = to, windowDays = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
        )
    }
}
