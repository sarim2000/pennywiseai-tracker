package com.pennywiseai.tracker.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "This Month" has always resolved against the user's budget cycle while
 * "Last Month" resolved against the calendar month. With any cycle start day
 * other than the 1st the two windows stopped meeting, and the days in between
 * were unreachable from either chip — the Aug-31 report in #740, where "This
 * Month" was empty and "Last Month" showed July while August's transactions
 * were only visible under "Current FY".
 *
 * These tests pin the property that actually matters: the two windows tile.
 */
class CycleAwareDateRangeTest {

    @Test
    fun `issue 740 - on Aug 31 with a 31st cycle August stays reachable`() {
        val today = LocalDate.of(2026, 8, 31)

        val thisMonth = getCycleAwareDateRange(TimePeriod.THIS_MONTH, cycleStartDay = 31, today = today)!!
        val lastMonth = getCycleAwareDateRange(TimePeriod.LAST_MONTH, cycleStartDay = 31, today = today)!!

        // The current cycle only just started, so it holds a single day.
        assertEquals(LocalDate.of(2026, 8, 31), thisMonth.first)
        // "Last Month" now covers the rest of August instead of jumping to July.
        assertEquals(LocalDate.of(2026, 7, 31), lastMonth.first)
        assertEquals(LocalDate.of(2026, 8, 30), lastMonth.second)

        val aug15 = LocalDate.of(2026, 8, 15)
        assertTrue(
            "A mid-August transaction must be visible under one of the two month chips",
            aug15 in lastMonth.first..lastMonth.second || aug15 in thisMonth.first..thisMonth.second
        )
    }

    @Test
    fun `last month ends the day before this month starts, for every start day`() {
        val today = LocalDate.of(2026, 3, 10)
        for (startDay in 1..31) {
            val thisMonth = getCycleAwareDateRange(TimePeriod.THIS_MONTH, startDay, today)!!
            val lastMonth = getCycleAwareDateRange(TimePeriod.LAST_MONTH, startDay, today)!!

            assertEquals(
                "start day $startDay leaves a gap or an overlap between the two chips",
                thisMonth.first.minusDays(1),
                lastMonth.second
            )
        }
    }

    @Test
    fun `the default start day still yields the calendar months`() {
        val today = LocalDate.of(2026, 8, 31)

        val thisMonth = getCycleAwareDateRange(TimePeriod.THIS_MONTH, cycleStartDay = 1, today = today)!!
        val lastMonth = getCycleAwareDateRange(TimePeriod.LAST_MONTH, cycleStartDay = 1, today = today)!!

        assertEquals(LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31), thisMonth)
        assertEquals(LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 31), lastMonth)
    }

    @Test
    fun `periods that do not follow the cycle are unchanged`() {
        assertEquals(false, TimePeriod.ALL.followsBudgetCycle)
        assertEquals(false, TimePeriod.CURRENT_FY.followsBudgetCycle)
        assertEquals(false, TimePeriod.CUSTOM.followsBudgetCycle)
        assertEquals(true, TimePeriod.THIS_MONTH.followsBudgetCycle)
        assertEquals(true, TimePeriod.LAST_MONTH.followsBudgetCycle)

        assertEquals(
            getDateRangeForPeriod(TimePeriod.CURRENT_FY),
            getCycleAwareDateRange(TimePeriod.CURRENT_FY, cycleStartDay = 25)
        )
        assertEquals(null, getCycleAwareDateRange(TimePeriod.CUSTOM, cycleStartDay = 25))
    }
}
