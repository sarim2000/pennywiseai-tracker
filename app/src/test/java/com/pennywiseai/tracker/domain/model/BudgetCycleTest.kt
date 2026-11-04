package com.pennywiseai.tracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Locks down the budget-cycle math that the Home, Budget Groups, and widget surfaces
 * rely on. The cycle doesn't have to align with the calendar month — when a user's
 * salary lands on the 25th, the cycle is the 25th of one month through the 24th of
 * the next. These tests pin the exact window for the cases the spec calls out, plus
 * the clamp behaviour for short months (Feb in leap and non-leap years) and dynamic
 * last-working-day-of-the-month cycles.
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
    fun `clampStartDay clamps 0 to 1 and 32 to 31, preserving LAST_WORKING_DAY`() {
        assertEquals(1, BudgetCycle.clampStartDay(0))
        assertEquals(31, BudgetCycle.clampStartDay(32))
        assertEquals(15, BudgetCycle.clampStartDay(15))
        assertEquals(BudgetCycle.LAST_WORKING_DAY, BudgetCycle.clampStartDay(BudgetCycle.LAST_WORKING_DAY))
        assertTrue(BudgetCycle.isLastWorkingDay(BudgetCycle.LAST_WORKING_DAY))
    }

    @Test
    fun `previousCycle steps back exactly one cycle`() {
        val today = LocalDate.of(2026, 10, 5)
        val current = BudgetCycle.currentCycle(today, startDay = 25)
        val (prevStart, prevEnd) = BudgetCycle.previousCycle(current, startDay = 25)

        assertEquals(LocalDate.of(2026, 8, 25), prevStart)
        assertEquals(LocalDate.of(2026, 9, 24), prevEnd)
    }

    // ── LAST_WORKING_DAY Tests ──

    @Test
    fun `lastWorkingDayOfMonth rolls back from weekend correctly`() {
        // Oct 31, 2026 is Saturday -> rolls back to Friday Oct 30
        assertEquals(LocalDate.of(2026, 10, 30), BudgetCycle.lastWorkingDayOfMonth(YearMonth.of(2026, 10)))

        // May 31, 2026 is Sunday -> rolls back to Friday May 29
        assertEquals(LocalDate.of(2026, 5, 29), BudgetCycle.lastWorkingDayOfMonth(YearMonth.of(2026, 5)))

        // Nov 30, 2026 is Monday -> stays Monday Nov 30
        assertEquals(LocalDate.of(2026, 11, 30), BudgetCycle.lastWorkingDayOfMonth(YearMonth.of(2026, 11)))

        // Feb 28, 2026 is Saturday -> rolls back to Friday Feb 27
        assertEquals(LocalDate.of(2026, 2, 27), BudgetCycle.lastWorkingDayOfMonth(YearMonth.of(2026, 2)))

        // Feb 29, 2024 (leap year) is Thursday -> stays Thursday Feb 29
        assertEquals(LocalDate.of(2024, 2, 29), BudgetCycle.lastWorkingDayOfMonth(YearMonth.of(2024, 2)))
    }

    @Test
    fun `LAST_WORKING_DAY today Oct 15 2026 spans Sep 30 through Oct 29`() {
        val today = LocalDate.of(2026, 10, 15)
        val (start, end) = BudgetCycle.currentCycle(today, startDay = BudgetCycle.LAST_WORKING_DAY)

        // Sep 30, 2026 is Wednesday (last working day of Sep)
        // Oct 30, 2026 is Friday (last working day of Oct, starts next cycle)
        assertEquals(LocalDate.of(2026, 9, 30), start)
        assertEquals(LocalDate.of(2026, 10, 29), end)
    }

    @Test
    fun `LAST_WORKING_DAY on the last working day itself starts the new cycle`() {
        val today = LocalDate.of(2026, 10, 30) // Friday, last working day of Oct
        val (start, end) = BudgetCycle.currentCycle(today, startDay = BudgetCycle.LAST_WORKING_DAY)

        // Today is on candidate start (Oct 30), so cycle begins today through day before Nov's last working day (Nov 30)
        assertEquals(LocalDate.of(2026, 10, 30), start)
        assertEquals(LocalDate.of(2026, 11, 29), end)
    }

    @Test
    fun `LAST_WORKING_DAY on weekend after last working day belongs to new cycle`() {
        val today = LocalDate.of(2026, 10, 31) // Saturday
        val (start, end) = BudgetCycle.currentCycle(today, startDay = BudgetCycle.LAST_WORKING_DAY)

        assertEquals(LocalDate.of(2026, 10, 30), start)
        assertEquals(LocalDate.of(2026, 11, 29), end)
    }

    @Test
    fun `LAST_WORKING_DAY previousCycle and nextCycle tile seamlessly`() {
        val current = BudgetCycle.currentCycle(LocalDate.of(2026, 10, 30), BudgetCycle.LAST_WORKING_DAY)
        assertEquals(LocalDate.of(2026, 10, 30), current.first)
        assertEquals(LocalDate.of(2026, 11, 29), current.second)

        val (prevStart, prevEnd) = BudgetCycle.previousCycle(current, BudgetCycle.LAST_WORKING_DAY)
        assertEquals(LocalDate.of(2026, 9, 30), prevStart)
        assertEquals(LocalDate.of(2026, 10, 29), prevEnd)

        val (nextStart, nextEnd) = BudgetCycle.nextCycle(current, BudgetCycle.LAST_WORKING_DAY)
        assertEquals(LocalDate.of(2026, 11, 30), nextStart)
        // Dec 31, 2026 is Thursday (last working day of Dec), so Nov cycle ends Dec 30
        assertEquals(LocalDate.of(2026, 12, 30), nextEnd)
    }

    @Test
    fun `LAST_WORKING_DAY currentCycleStartYearMonth resolves accurately`() {
        // Oct 15 is before Oct 30 -> started in Sep 2026
        assertEquals(YearMonth.of(2026, 9), BudgetCycle.currentCycleStartYearMonth(LocalDate.of(2026, 10, 15), BudgetCycle.LAST_WORKING_DAY))

        // Oct 30 is on last working day -> started in Oct 2026
        assertEquals(YearMonth.of(2026, 10), BudgetCycle.currentCycleStartYearMonth(LocalDate.of(2026, 10, 30), BudgetCycle.LAST_WORKING_DAY))

        // Oct 31 is after last working day -> started in Oct 2026
        assertEquals(YearMonth.of(2026, 10), BudgetCycle.currentCycleStartYearMonth(LocalDate.of(2026, 10, 31), BudgetCycle.LAST_WORKING_DAY))
    }
}
