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
 * Used by the budget repositories and the home/analytics surfaces to translate a
 * "month" (a [YearMonth] the user navigated to) into the actual spend window.
 */
object BudgetCycle {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 31
    const val DEFAULT_START_DAY = 1

    /**
     * Coerces an arbitrary integer to the valid range [[MIN_START_DAY], [MAX_START_DAY]].
     * Useful when reading a user-typed or migration-provided value.
     */
    fun clampStartDay(value: Int): Int =
        value.coerceIn(MIN_START_DAY, MAX_START_DAY)

    /**
     * The cycle that contains [today], for a cycle that starts on [startDay].
     *
     * If today.dayOfMonth >= startDay the cycle started earlier this month; otherwise
     * it started in the previous month.
     *
     * @return pair of (cycle start inclusive, cycle end inclusive).
     */
    fun currentCycle(today: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val startOfThisMonth = YearMonth.from(today).atDay(1)
        val candidateStart = startOfThisMonth.withDayOfMonthSafe(safeStart)
        val start = if (!today.isBefore(candidateStart)) {
            candidateStart
        } else {
            val prevMonth = YearMonth.from(today).minusMonths(1)
            prevMonth.atDay(1).withDayOfMonthSafe(safeStart)
        }
        val end = nextCycleStart(start, safeStart).minusDays(1)
        return start to end
    }

    /**
     * The cycle immediately before [cycle]. The returned start is exactly one
     * month earlier than [cycle.first], clamped to the month's length so a
     * `startDay = 31` doesn't overflow into March — this preserves the same
     * start-day cadence even across short months.
     */
    fun previousCycle(cycle: Pair<LocalDate, LocalDate>, startDay: Int): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        // Step one calendar month back from the current cycle's start, then
        // clamp to the month's length. The previous cycle is then "one month
        // earlier to one day before the current cycle's start".
        val prevStart = cycle.first.minusMonths(1).withDayOfMonthSafe(safeStart)
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
     * [startDay]. If [reference] is on or after its own month's [startDay] then the
     * answer is next month; otherwise it is the same month's [startDay].
     *
     * This is the cadence primitive — every other helper in this object is built on
     * it, which guarantees the cycles tile the timeline with no overlaps or gaps.
     */
    fun nextCycleStart(reference: LocalDate, startDay: Int): LocalDate {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(reference)
        val candidate = ym.atDay(1).withDayOfMonthSafe(safeStart)
        return if (!reference.isBefore(candidate)) {
            ym.plusMonths(1).atDay(1).withDayOfMonthSafe(safeStart)
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
        val candidate = ym.atDay(1).withDayOfMonthSafe(safeStart)
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
