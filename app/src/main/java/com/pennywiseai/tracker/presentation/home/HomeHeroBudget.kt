package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.heroBudgetGroup
import com.pennywiseai.tracker.domain.spending.SpendForecast
import com.pennywiseai.tracker.ui.components.cards.HomeHeroBudget
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The budget Home's hero leads with ([heroBudgetGroup]). Null when there's none, and the
 * hero shows this month's spending instead. The summary is in one currency, so its
 * figures never mix currencies.
 */
internal fun BudgetOverallSummary.heroBudget(today: LocalDate): HomeHeroBudget? {
    val group = heroBudgetGroup(today) ?: return null
    return HomeHeroBudget(
        budgetId = group.group.budget.id,
        name = group.group.budget.name.takeUnless {
            group.periodType == BudgetPeriodType.MONTHLY && group.windowStart.dayOfMonth == 1 &&
                group.windowEnd == group.windowStart.plusMonths(1).minusDays(1)
        },
        windowStart = group.windowStart,
        spent = group.totalActual,
        limit = group.totalBudget,
        currency = currency,
        daysLeft = ChronoUnit.DAYS.between(today, group.windowEnd).toInt() + 1,
        pace = paceAgainstLimit(group.totalActual, group.totalBudget, group.windowStart, group.windowEnd, today),
    )
}

/**
 * How far under (positive) or over (negative) [limit] a window should end at the pace so
 * far: [spent] / days elapsed × days in the window. Null until [SpendForecast.MIN_DAYS]
 * days have passed (too early to call) and on the last day (nothing left to project).
 */
internal fun paceAgainstLimit(
    spent: BigDecimal,
    limit: BigDecimal,
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
): BigDecimal? {
    val elapsed = ChronoUnit.DAYS.between(start, today) + 1
    val total = ChronoUnit.DAYS.between(start, end) + 1
    if (elapsed < SpendForecast.MIN_DAYS || elapsed >= total) return null
    val projected = spent.multiply(BigDecimal(total)).divide(BigDecimal(elapsed), 0, RoundingMode.HALF_UP)
    return limit - projected
}
