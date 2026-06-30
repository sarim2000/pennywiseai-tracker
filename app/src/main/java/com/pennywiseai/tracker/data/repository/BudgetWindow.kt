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
