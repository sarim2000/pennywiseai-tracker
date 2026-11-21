package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class BudgetWidgetMoodTest {

    /** A 3,000 limit over 30 days, 10 days in: an even spend is 1,000, a day's budget 100. */
    private fun mood(spent: Int, limit: Int = 3000, daysElapsed: Int = 10, windowDays: Int = 30) = budgetMood(
        BudgetWidgetData(
            totalSpent = BigDecimal(spent),
            totalLimit = BigDecimal(limit),
            remaining = BigDecimal(limit - spent),
            daysElapsed = daysElapsed,
            windowDays = windowDays
        )
    )

    @Test
    fun `at or under an even spend is happy`() {
        assertEquals(MascotMood.Happy, mood(spent = 400))
        assertEquals(MascotMood.Happy, mood(spent = 1000))
        // Less than a whole day's budget past the line is still on pace.
        assertEquals(MascotMood.Happy, mood(spent = 1099))
    }

    @Test
    fun `a day's budget past an even spend is thinking`() {
        assertEquals(MascotMood.Thinking, mood(spent = 1100))
        assertEquals(MascotMood.Thinking, mood(spent = 3000))
    }

    @Test
    fun `over the limit as the figure shows it is worried`() {
        assertEquals(MascotMood.Worried, mood(spent = 3001))
        // 0.3 over reads "Left · 0" on the widget, so the owl doesn't worry yet.
        val barelyOver = BudgetWidgetData(
            totalSpent = BigDecimal("3000.3"),
            totalLimit = BigDecimal(3000),
            remaining = BigDecimal("-0.3"),
            daysElapsed = 10,
            windowDays = 30
        )
        assertEquals(MascotMood.Thinking, budgetMood(barelyOver))
    }

    @Test
    fun `no budget or no window is calm`() {
        assertEquals(MascotMood.Calm, budgetMood(BudgetWidgetData()))
        // A snapshot from before the widget stored its window.
        assertEquals(MascotMood.Calm, mood(spent = 400, daysElapsed = 0, windowDays = 0))
    }
}
