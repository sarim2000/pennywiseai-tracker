package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.domain.model.BudgetCycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A resolved [start, end] date window for a budget at a given [reference] date.
 *
 * The start and end are inclusive. [days] is the inclusive day count, used by
 * the BudgetCard / widget for the daily-allowance / days-left math.
 */
data class BudgetWindow(
    val start: LocalDate,
    val end: LocalDate,
    val days: Int
)

/**
 * The single source of truth for "what window does this budget cover today?"
 *
 * The window is resolved **at read time** from the budget's [BudgetPeriodType]
 * and its cadence anchor:
 *
 *  - [BudgetPeriodType.WEEKLY] — rolls forward automatically every week.
 *    Window = `[reference.with(previousOrSame(weekday_anchor)), +6 days]`.
 *  - [BudgetPeriodType.MONTHLY] — rolls forward automatically every cycle.
 *    Window = `BudgetCycle.currentCycle(reference, month_anchor)`.
 *  - [BudgetPeriodType.CUSTOM] — the literal `[startDate, endDate]` the
 *    user picked. No rolling.
 *
 * For Weekly and Monthly the row's persisted [BudgetEntity.startDate] /
 * [BudgetEntity.endDate] are a "last computed" cache; the resolver is what
 * every read path uses, so a recurring budget always shows the *current*
 * window without needing DB writes.
 *
 * Legacy rows (created before the anchor columns existed) have null anchors.
 * Weekly falls back to Monday, Monthly falls back to [globalStartDay] (the
 * user's overall budget cycle start day preference, which defaults to 1).
 */
fun resolveBudgetWindow(
    budget: BudgetEntity,
    reference: LocalDate,
    globalStartDay: Int = BudgetCycle.DEFAULT_START_DAY
): BudgetWindow = when (budget.periodType) {
    BudgetPeriodType.WEEKLY -> {
        val dow = budget.weekStartDay?.let { DayOfWeek.of(it.coerceIn(1, 7)) }
            ?: DayOfWeek.MONDAY
        val start = reference.with(TemporalAdjusters.previousOrSame(dow))
        val end = start.plusDays(6)
        BudgetWindow(start, end, 7)
    }
    BudgetPeriodType.MONTHLY -> {
        val day = budget.monthStartDay?.let { BudgetCycle.clampStartDay(it) }
            ?: BudgetCycle.clampStartDay(globalStartDay)
        val (start, end) = BudgetCycle.currentCycle(reference, day)
        BudgetWindow(start, end, ChronoUnit.DAYS.between(start, end).toInt() + 1)
    }
    BudgetPeriodType.CUSTOM -> {
        val start = budget.startDate
        val end = budget.endDate
        BudgetWindow(start, end, ChronoUnit.DAYS.between(start, end).toInt() + 1)
    }
}

/**
 * True when [other] has any day in common with `this`. The BudgetGroups
 * screen uses this for the "Overlap" filter — a budget with a window
 * (Weekly, Monthly, or One-time) that intersects the selected year-month
 * is included in the list.
 */
fun BudgetWindow.overlaps(other: BudgetWindow): Boolean =
    !this.start.isAfter(other.end) && !this.end.isBefore(other.start)

/**
 * The list of windows for [budget] that intersect the [year]/[month] calendar
 * period. Drives the historical view on the Budgets page:
 *
 *  - **WEEKLY**: one [BudgetWindow] per ISO week that intersects the month
 *    (so September 2026 with a Mon-anchored budget yields 4–5 windows,
 *    each 7 days long). This is what powers the "Last week" / per-week
 *    sub-list on the expanded card.
 *  - **MONTHLY**: 0 or 1 window — the cycle that contains the most recent
 *    day in the selected month, intersected with the month's bounds.
 *  - **CUSTOM**: 0 or 1 window — the budget's literal range intersected
 *    with the selected month. A One-time Nov 5–Dec 4 budget shows in
 *    November and December (each call returns a 0-length clipped window
 *    the caller can drop), but not September.
 *
 * For the **current** month, the screen calls [resolveBudgetWindow] with
 * `reference = today` and shows the *single* current window — this helper
 * is the historical equivalent.
 */
fun windowsForMonth(
    budget: BudgetEntity,
    year: Int,
    month: Int,
    globalStartDay: Int = BudgetCycle.DEFAULT_START_DAY
): List<BudgetWindow> {
    val ym = java.time.YearMonth.of(year, month)
    val monthStart = ym.atDay(1)
    val monthEnd = ym.atEndOfMonth()
    return when (budget.periodType) {
        BudgetPeriodType.WEEKLY -> {
            val dow = budget.weekStartDay?.let { DayOfWeek.of(it.coerceIn(1, 7)) }
                ?: DayOfWeek.MONDAY
            val results = mutableListOf<BudgetWindow>()
            // Anchor the first week-start on or before the 1st of the month,
            // then step forward by 7 days as long as the week still
            // intersects the month. Includes a partial first week and a
            // partial last week so the sub-list on the card shows the full
            // month's coverage.
            var weekStart = monthStart.with(TemporalAdjusters.previousOrSame(dow))
            while (!weekStart.isAfter(monthEnd)) {
                val weekEnd = weekStart.plusDays(6)
                // Trim the window to the month so the spend query doesn't
                // pull transactions from the adjacent month.
                val clippedStart = if (weekStart.isBefore(monthStart)) monthStart else weekStart
                val clippedEnd = if (weekEnd.isAfter(monthEnd)) monthEnd else weekEnd
                if (!clippedStart.isAfter(clippedEnd)) {
                    val days = ChronoUnit.DAYS.between(clippedStart, clippedEnd).toInt() + 1
                    results.add(BudgetWindow(clippedStart, clippedEnd, days))
                }
                weekStart = weekStart.plusDays(7)
            }
            results
        }
        BudgetPeriodType.MONTHLY -> {
            val day = budget.monthStartDay?.let { BudgetCycle.clampStartDay(it) }
                ?: BudgetCycle.clampStartDay(globalStartDay)
            val reference = monthStart
            val (cycleStart, cycleEnd) = BudgetCycle.currentCycle(reference, day)
            // The cycle may straddle the month. We keep the full cycle
            // here so the user sees the actual window (e.g. for an
            // October view of a 25-anchored budget, the cycle is
            // Sept 25..Oct 24 — the start is in September, but the
            // October page is the right place to show it because the
            // cycle's "end" falls in October). The spend query inside
            // the repo is clipped to the page's month bounds when
            // needed, but the *displayed* window is the full cycle.
            if (cycleStart.isAfter(monthEnd) || cycleEnd.isBefore(monthStart)) {
                emptyList()
            } else listOf(
                BudgetWindow(
                    cycleStart,
                    cycleEnd,
                    ChronoUnit.DAYS.between(cycleStart, cycleEnd).toInt() + 1
                )
            )
        }
        BudgetPeriodType.CUSTOM -> {
            val start = budget.startDate
            val end = budget.endDate
            if (start.isAfter(monthEnd) || end.isBefore(monthStart)) emptyList()
            else {
                val clippedStart = if (start.isBefore(monthStart)) monthStart else start
                val clippedEnd = if (end.isAfter(monthEnd)) monthEnd else end
                listOf(
                    BudgetWindow(
                        clippedStart,
                        clippedEnd,
                        ChronoUnit.DAYS.between(clippedStart, clippedEnd).toInt() + 1
                    )
                )
            }
        }
    }
}
