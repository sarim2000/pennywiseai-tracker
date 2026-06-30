package com.pennywiseai.tracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Locks down the budget-cycle math that the Home, Budget Groups, and widget surfaces
 * rely on. The cycle doesn't have to align with the calendar month — when a user's
 * salary lands on the 25th, the cycle is the 25th of one month through the 24th of
 * the next. These tests pin the exact window for the cases the spec calls out, plus
 * the clamp behaviour for short months (Feb in leap and non-leap years).
 */
class BudgetCycleTest {

    @Test
    fun `startDay 1 behaves like the calendar month`() {
        val today = LocalDate.of(2026, 10, 5)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 1)

        assertEquals(LocalDate.of(2026, 10, 1), start)
        assertEquals(LocalDate.of(2026, 10, 31), end)
    }

    @Test
    fun `startDay 25 today Oct 5 spans Sep 25 through Oct 24`() {
        val today = LocalDate.of(2026, 10, 5)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 25)

        assertEquals(LocalDate.of(2026, 9, 25), start)
        assertEquals(LocalDate.of(2026, 10, 24), end)
    }

    @Test
    fun `startDay 25 today Oct 26 spans Oct 25 through Nov 24`() {
        val today = LocalDate.of(2026, 10, 26)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 25)

        assertEquals(LocalDate.of(2026, 10, 25), start)
        assertEquals(LocalDate.of(2026, 11, 24), end)
    }

    @Test
    fun `startDay 29 in non-leap February falls back to Jan 29 through Feb 27`() {
        val today = LocalDate.of(2025, 2, 10)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 29)

        // Feb 10 is before the Feb 28 candidate, so the cycle started in January.
        // Next cycle begins Feb 28, so this cycle ends the day before (Feb 27).
        assertEquals(LocalDate.of(2025, 1, 29), start)
        assertEquals(LocalDate.of(2025, 2, 27), end)
    }

    @Test
    fun `startDay 29 in leap February falls back to Jan 29 through Feb 28`() {
        val today = LocalDate.of(2024, 2, 10)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 29)

        // Feb 10 is before the Feb 29 candidate, so the cycle started in January.
        // Next cycle begins Feb 29, so this cycle ends Feb 28.
        assertEquals(LocalDate.of(2024, 1, 29), start)
        assertEquals(LocalDate.of(2024, 2, 28), end)
    }

    @Test
    fun `startDay 31 today Feb 15 non-leap is Jan 31 through Feb 27`() {
        val today = LocalDate.of(2025, 2, 15)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 31)

        assertEquals(LocalDate.of(2025, 1, 31), start)
        assertEquals(LocalDate.of(2025, 2, 27), end)
    }

    @Test
    fun `startDay 31 today Feb 15 leap year is Jan 31 through Feb 28`() {
        val today = LocalDate.of(2024, 2, 15)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = 31)

        assertEquals(LocalDate.of(2024, 1, 31), start)
        assertEquals(LocalDate.of(2024, 2, 28), end)
    }

    @Test
    fun `currentCycleStartYearMonth returns September for startDay 25 today Oct 5`() {
        val today = LocalDate.of(2026, 10, 5)
        val ym = BudgetCycle.currentCycleStartYearMonth(today, startDay = 25)

        assertEquals(YearMonth.of(2026, 9), ym)
    }

    @Test
    fun `currentCycleStartYearMonth returns October for startDay 25 today Oct 26`() {
        val today = LocalDate.of(2026, 10, 26)
        val ym = BudgetCycle.currentCycleStartYearMonth(today, startDay = 25)

        assertEquals(YearMonth.of(2026, 10), ym)
    }

    @Test
    fun `clampStartDay clamps 0 to 1 and 32 to 31`() {
        assertEquals(1, BudgetCycle.clampStartDay(0))
        assertEquals(31, BudgetCycle.clampStartDay(32))
        assertEquals(15, BudgetCycle.clampStartDay(15))
    }

    @Test
    fun `previousCycle steps back exactly one cycle`() {
        val today = LocalDate.of(2026, 10, 5)
        val current = BudgetCycle.currentCycle(today, startDay = 25)
        val (prevStart, prevEnd) = BudgetCycle.previousCycle(current, startDay = 25)

        assertEquals(LocalDate.of(2026, 8, 25), prevStart)
        assertEquals(LocalDate.of(2026, 9, 24), prevEnd)
    }
}
