package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

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

    @Test
    fun `over budget with no stored window is calm`() {
        // Written before the widget stored its window: remaining can already be over.
        val stale = BudgetWidgetData(
            totalSpent = BigDecimal(3001),
            totalLimit = BigDecimal(3000),
            remaining = BigDecimal("-1")
        )
        assertEquals(MascotMood.Calm, budgetMood(stale))
    }

    @Test
    fun `over a limit rollover carried to zero is worried`() {
        val carriedToZero = BudgetWidgetData(
            totalSpent = BigDecimal(100),
            totalLimit = BigDecimal.ZERO,
            hasLimit = true,
            remaining = BigDecimal("-100"),
            daysElapsed = 10,
            windowDays = 30
        )
        assertEquals(MascotMood.Worried, budgetMood(carriedToZero))
    }

    @Test
    fun `a weekly limit on day 2 of 7 is thinking`() {
        val (pace, mood) = paced(
            "2026-11-17",
            slice(spent = 300, limit = 700, start = "2026-11-16", end = "2026-11-22", daysElapsed = 2, windowDays = 7)
        )
        assertEquals(2, pace.daysElapsed)
        assertEquals(7, pace.windowDays)
        assertNull(pace.foldedDaysOver)
        assertEquals(MascotMood.Thinking, mood)
    }

    @Test
    fun `a budget that starts on the 25th keeps that cycle`() {
        val pace = widgetLimitPace(
            LocalDate.parse("2026-11-26"),
            listOf(slice(spent = 100, limit = 3000, start = "2026-11-25", end = "2026-12-24", daysElapsed = 2, windowDays = 30))
        )
        assertEquals(2, pace.daysElapsed)
        assertEquals(30, pace.windowDays)
        assertNull(pace.foldedDaysOver)
    }

    @Test
    fun `today outside the only window is calm`() {
        val (pace, mood) = paced(
            "2026-11-23",
            slice(spent = 800, limit = 700, start = "2026-11-16", end = "2026-11-22", daysElapsed = 7, windowDays = 7)
        )
        assertEquals(0, pace.daysElapsed)
        assertEquals(0, pace.windowDays)
        assertNull(pace.foldedDaysOver)
        assertEquals(MascotMood.Calm, mood)
    }

    @Test
    fun `mixed windows under an even spend are happy`() {
        val (pace, mood) = paced(
            "2026-11-17",
            slice(spent = 300, limit = 700, start = "2026-11-16", end = "2026-11-22", daysElapsed = 2, windowDays = 7),
            slice(spent = 100, limit = 3000, start = "2026-11-01", end = "2026-11-30", daysElapsed = 10, windowDays = 30)
        )
        assertEquals(-4, pace.foldedDaysOver)
        assertEquals(MascotMood.Happy, mood)
    }

    @Test
    fun `mixed windows past an even spend are thinking`() {
        val (pace, mood) = paced(
            "2026-11-17",
            slice(spent = 600, limit = 700, start = "2026-11-16", end = "2026-11-22", daysElapsed = 2, windowDays = 7),
            slice(spent = 2000, limit = 3000, start = "2026-11-01", end = "2026-11-30", daysElapsed = 10, windowDays = 30)
        )
        assertEquals(7, pace.foldedDaysOver)
        assertEquals(MascotMood.Thinking, mood)
    }

    @Test
    fun `a folded pace is not the page total`() {
        // 1,000 of 3,000 on day 10 of 30 is an even spend. The fold still says two days over.
        val onPaceForThePage = BudgetWidgetData(
            totalSpent = BigDecimal(1000),
            totalLimit = BigDecimal(3000),
            remaining = BigDecimal(2000),
            daysElapsed = 10,
            windowDays = 30,
            foldedDaysOver = 2
        )
        assertEquals(MascotMood.Thinking, budgetMood(onPaceForThePage))
    }

    private fun slice(
        spent: Int,
        limit: Int,
        start: String,
        end: String,
        daysElapsed: Int,
        windowDays: Int
    ) = WidgetLimitWindow(
        spent = BigDecimal(spent),
        limit = BigDecimal(limit),
        windowStart = LocalDate.parse(start),
        windowEnd = LocalDate.parse(end),
        daysElapsed = daysElapsed,
        windowDays = windowDays
    )

    /** The page total is the sum of the slices. The pace is not given a page window. */
    private fun paced(today: String, vararg slices: WidgetLimitWindow): Pair<WidgetLimitPace, MascotMood> {
        val pace = widgetLimitPace(LocalDate.parse(today), slices.toList())
        val spent = slices.fold(BigDecimal.ZERO) { acc, slice -> acc + slice.spent }
        val limit = slices.fold(BigDecimal.ZERO) { acc, slice -> acc + slice.limit }
        val mood = budgetMood(
            BudgetWidgetData(
                totalSpent = spent,
                totalLimit = limit,
                remaining = limit - spent,
                daysElapsed = pace.daysElapsed,
                windowDays = pace.windowDays,
                foldedDaysOver = pace.foldedDaysOver
            )
        )
        return pace to mood
    }
}
