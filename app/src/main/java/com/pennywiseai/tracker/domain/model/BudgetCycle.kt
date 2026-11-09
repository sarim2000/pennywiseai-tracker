package com.pennywiseai.tracker.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

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
 * last working day of each month, rolling back from weekend days according to the
 * active locale (e.g., Saturday/Sunday for most countries, Friday/Saturday in many
 * Middle Eastern countries).
 *
 * Used by the budget repositories and the home/analytics surfaces to translate a
 * "month" (a [YearMonth] the user navigated to) into the actual spend window.
 */
object BudgetCycle {

    const val MIN_START_DAY = 1
    const val MAX_START_DAY = 31
    const val DEFAULT_START_DAY = 1

    /**
     * Sentinel value representing the last working day of the month.
     * When selected, the cycle dynamically anchors to the final business day of each month,
     * rolling back if the month ends on a weekend day (adapting to the user's locale).
     */
    const val LAST_WORKING_DAY = -1

    /**
     * Countries where the weekend typically falls on Friday and Saturday (Sun–Thu work week).
     */
    val FRIDAY_SATURDAY_WEEKEND_COUNTRIES = setOf(
        "SA", // Saudi Arabia
        "EG", // Egypt
        "JO", // Jordan
        "KW", // Kuwait
        "QA", // Qatar
        "BH", // Bahrain
        "OM", // Oman
        "IQ", // Iraq
        "YE", // Yemen
        "SY", // Syria
        "LY", // Libya
        "SD", // Sudan
        "DZ", // Algeria
        "PS", // Palestine
        "IL", // Israel
        "BD", // Bangladesh
        "MV", // Maldives
    )

    /**
     * Countries where the weekend typically falls on Thursday and Friday.
     */
    val THURSDAY_FRIDAY_WEEKEND_COUNTRIES = setOf(
        "IR", // Iran
        "AF", // Afghanistan
    )

    /**
     * The SIM or network country, set at app start. The weekend is where the user is,
     * not their language: a phone in English in Riyadh still rests on Friday and Saturday.
     */
    @Volatile
    var weekendCountry: String? = null

    /** The locale whose country decides the weekend: [weekendCountry], else the device's. */
    fun weekendLocale(): Locale =
        weekendCountry?.takeIf { it.isNotBlank() }?.let { Locale("", it.uppercase()) } ?: Locale.getDefault()

    /**
     * Checks if the given start day represents the dynamic "last working day of the month".
     */
    fun isLastWorkingDay(value: Int): Boolean = value == LAST_WORKING_DAY

    /**
     * Checks if [dayOfWeek] is considered a weekend in the given [locale].
     */
    fun isWeekend(dayOfWeek: DayOfWeek, locale: Locale = weekendLocale()): Boolean {
        val country = locale.country.uppercase()
        return when {
            country in FRIDAY_SATURDAY_WEEKEND_COUNTRIES -> {
                dayOfWeek == DayOfWeek.FRIDAY || dayOfWeek == DayOfWeek.SATURDAY
            }
            country in THURSDAY_FRIDAY_WEEKEND_COUNTRIES -> {
                dayOfWeek == DayOfWeek.THURSDAY || dayOfWeek == DayOfWeek.FRIDAY
            }
            else -> {
                dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
            }
        }
    }

    /**
     * Coerces an arbitrary integer to the valid range [[MIN_START_DAY], [MAX_START_DAY]],
     * or preserves [LAST_WORKING_DAY].
     * Useful when reading a user-typed or migration-provided value.
     */
    fun clampStartDay(value: Int): Int =
        if (value == LAST_WORKING_DAY) LAST_WORKING_DAY else value.coerceIn(MIN_START_DAY, MAX_START_DAY)

    /**
     * Returns the last working day of [ym] according to [locale]'s weekend schedule.
     * If the month ends on a weekend, rolls back to the preceding working day.
     */
    fun lastWorkingDayOfMonth(ym: YearMonth, locale: Locale = weekendLocale()): LocalDate {
        var date = ym.atEndOfMonth()
        while (isWeekend(date.dayOfWeek, locale)) {
            date = date.minusDays(1)
        }
        return date
    }

    /**
     * Returns the start date of the cycle that begins in [ym] for the given [startDay].
     */
    fun cycleStartForMonth(
        ym: YearMonth,
        startDay: Int,
        locale: Locale = weekendLocale()
    ): LocalDate {
        val safeStart = clampStartDay(startDay)
        return if (safeStart == LAST_WORKING_DAY) {
            lastWorkingDayOfMonth(ym, locale)
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
    fun currentCycle(
        today: LocalDate,
        startDay: Int,
        locale: Locale = weekendLocale()
    ): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(today)
        val candidateStart = cycleStartForMonth(ym, safeStart, locale)
        val start = if (!today.isBefore(candidateStart)) {
            candidateStart
        } else {
            cycleStartForMonth(ym.minusMonths(1), safeStart, locale)
        }
        val end = nextCycleStart(start, safeStart, locale).minusDays(1)
        return start to end
    }

    /**
     * The cycle immediately before [cycle]. The returned start is exactly one
     * month earlier than [cycle.first], clamped to the month's length or working day
     * so it preserves the same start-day cadence even across short months.
     */
    fun previousCycle(
        cycle: Pair<LocalDate, LocalDate>,
        startDay: Int,
        locale: Locale = weekendLocale()
    ): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(cycle.first)
        val prevStart = cycleStartForMonth(ym.minusMonths(1), safeStart, locale)
        val end = cycle.first.minusDays(1)
        return prevStart to end
    }

    /**
     * The cycle immediately after [cycle]. The next start is always one [nextCycleStart]
     * step past [cycle.first], so cycles never overlap and never have gaps.
     */
    fun nextCycle(
        cycle: Pair<LocalDate, LocalDate>,
        startDay: Int,
        locale: Locale = weekendLocale()
    ): Pair<LocalDate, LocalDate> {
        val safeStart = clampStartDay(startDay)
        val nextStart = nextCycleStart(cycle.first, safeStart, locale)
        val end = nextCycleStart(nextStart, safeStart, locale).minusDays(1)
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
    fun nextCycleStart(
        reference: LocalDate,
        startDay: Int,
        locale: Locale = weekendLocale()
    ): LocalDate {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(reference)
        val candidate = cycleStartForMonth(ym, safeStart, locale)
        return if (!reference.isBefore(candidate)) {
            cycleStartForMonth(ym.plusMonths(1), safeStart, locale)
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
    fun currentCycleStartYearMonth(
        today: LocalDate,
        startDay: Int,
        locale: Locale = weekendLocale()
    ): YearMonth {
        val safeStart = clampStartDay(startDay)
        val ym = YearMonth.from(today)
        val candidate = cycleStartForMonth(ym, safeStart, locale)
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
