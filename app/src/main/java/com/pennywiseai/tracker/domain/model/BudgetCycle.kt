package com.pennywiseai.tracker.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * A user-configurable "monthly" budget cycle that does not have to align with the
 * calendar month.
 *
 * For example, with [startDay] = 25 the current cycle spans from the 25th of the
 * previous month through the 24th of the current month.
 *
 * Days are 1..31. Months that don't have the requested day (e.g. February with
 * [startDay] = 30) clamp the cycle start to the last day of that month — so a
 * 30th-of-the-month start yields a Feb-28 / Feb-29 start, and the cycle still
 * ends on the day before the next cycle's start.
 *
 * Special sentinel [LAST_WORKING_DAY] (-1) dynamically anchors the cycle to the
 * last weekday (Monday through Friday) of each month (rolling back from Saturday/Sunday).
 *
 * Used by the budget repositories and the home/analytics surfaces to translate a
 * "month" (a [YearMonth] the user navigated to) into the actual spend window.
 */
object BudgetCycle {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 31
    const val DEFAULT_START_DAY = 1

    /**
     * Sentinel value representing the last working day (Monday through Friday) of the month.
     * When selected, the cycle dynamically anchors to the final business day of each month,
     * rolling back to Friday if the month ends on a Saturday or Sunday.
     */
    const val LAST_WORKING_DAY = -1

    /**
     * Checks if the given start day represents the dynamic "last working day of the month".
     */
    fun isLastWorkingDay(value: Int): Boolean = value == LAST_WORKING_DAY

    /**
     * Coerces an arbitrary integer to the valid range [[MIN_START_DAY], [MAX_START_DAY]],
     * or preserves [LAST_WORKING_DAY].
     * Useful when reading a user-typed or migration-provided value.
     */
    fun clampStartDay(value: Int): Int =
        if (value == LAST_WORKING_DAY) LAST_WORKING_DAY else value.coerceIn(MIN_START_DAY, MAX_START_DAY)

    /**
     * Returns the last working day (Monday through Friday) of [ym].
     * If the month ends on Saturday or Sunday, rolls back to Friday.
     */
    fun lastWorkingDayOfMonth(ym: YearMonth): LocalDate {
        val lastDay = ym.atEndOfMonth()
        return when (lastDay.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> lastDay.minusDays(1)
            java.time.DayOfWeek.SUNDAY -> lastDay.minusDays(2)
            else -> lastDay
        }
    }

    /**
     * Returns the start date of the cycle that begins in [ym] for the given [startDay].
     */
    fun cycleStartForMonth(ym: YearMonth, startDay: Int): LocalDate {
        val safeStart = clampStartDay(startDay)
        return if (safeStart == LAST_WORKING_DAY) {
            lastWorkingDayOfMonth(ym)
        } else {
            ym.atDay(1).withDayOfMonthSafe(safeStart)
        }
    }

    /**
     * The cycle that contains [today], for a cycle that starts on [startDay].
     *
     * If today is on or after candidate start, the cycle started earlier this month; otherwise
     * it started in the previous month.
     *
     * @return pair of (cycle start inclusive, cycle end inclusive).
     */
    fun currentCycle(today: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(today)
        val candidateStart = cycleStartForMonth(ym, safeStart)
        val start = if (!today.isBefore(candidateStart)) {
            candidateStart
        } else {
            cycleStartForMonth(ym.minusMonths(1), safeStart)
        }
        val end = nextCycleStart(start, safeStart).minusDays(1)
        return start to end
    }

    /**
     * The cycle immediately before [cycle]. The returned start is exactly one
     * month earlier than [cycle.first], clamped to the month's length or working day
     * so it preserves the same start-day cadence even across short months.
     */
    fun previousCycle(cycle: Pair<LocalDate, LocalDate>, startDay: Int): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(cycle.first)
        val prevStart = cycleStartForMonth(ym.minusMonths(1), safeStart)
        val end = cycle.first.minusDays(1)
        return prevStart to end
    }

    /**
     * The cycle immediately after [cycle]. The next start is always one [nextCycleStart]
     * step past [cycle.first], so cycles never overlap and never have gaps.
     */
    fun nextCycle(cycle: Pair<LocalDate, LocalDate>, startDay: Int): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val nextStart = nextCycleStart(cycle.first, safeStart)
        val end = nextCycleStart(nextStart, safeStart).minusDays(1)
        return nextStart to end
    }

    /**
     * The first day of the cycle that *starts* on or after [reference] for the given
     * [startDay]. If [reference] is on or after its own month's cycle start then the
     * answer is next month; otherwise it is the same month's cycle start.
     *
     * This is the cadence primitive — every other helper in this object is built on
     * it, which guarantees the cycles tile the timeline with no overlaps or gaps.
     */
    fun nextCycleStart(reference: LocalDate, startDay: Int): LocalDate {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(reference)
        val candidate = cycleStartForMonth(ym, safeStart)
        return if (!reference.isBefore(candidate)) {
            cycleStartForMonth(ym.plusMonths(1), safeStart)
        } else {
            candidate
        }
    }

    /**
     * Returns the [YearMonth] in which the cycle that *contains* [today] starts.
     * This is the value callers should pass to historical month navigation (the
     * Budget Groups screen) so the user lands on their actual current cycle, not
     * on the calendar month that happens to contain today's date.
     */
    fun currentCycleStartYearMonth(today: LocalDate, startDay: Int): YearMonth {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(today)
        val candidate = cycleStartForMonth(ym, safeStart)
        return if (!today.isBefore(candidate)) ym else ym.minusMonths(1)
    }

    /**
     * `LocalDate.withDayOfMonth(...)` throws when the requested day exceeds the
     * month's length. This helper clamps to the last day of the month so a
     * `startDay = 31` works for February, April, etc.
     */
    private fun LocalDate.withDayOfMonthSafe(day: Int): LocalDate {
        val max = lengthOfMonth()
        return withDayOfMonth(day.coerceAtMost(max))
    }
}
