package com.pennywiseai.tracker.domain.spending

import java.math.BigDecimal
import java.math.MathContext

/**
 * Where a live budget window stands against an even spend across it.
 *
 * [todayFraction] is the share of the window elapsed, today included: where the
 * meter's "today" tick sits. [daysOver] is how many whole days of budget have
 * been spent beyond that even line; negative means under it, 0 means on pace.
 */
data class BudgetPace(
    val todayFraction: Float,
    val daysOver: Int,
    val isOverBudget: Boolean,
)

/**
 * [spent] and [budget] must be in the same currency (a budget group's totals
 * are). [daysElapsed] counts today, as [com.pennywiseai.tracker.data.repository.dayCounts]
 * does. Null when there's no budget or the window isn't running.
 */
fun budgetPace(spent: BigDecimal, budget: BigDecimal, daysElapsed: Int, windowDays: Int): BudgetPace? {
    if (budget.signum() <= 0 || windowDays < 1 || daysElapsed !in 1..windowDays) return null
    // Spend expressed in days of budget: a 3000/30-day budget with 500 spent = 5 days.
    val spentDays = spent.multiply(BigDecimal(windowDays)).divide(budget, MathContext.DECIMAL64)
    return BudgetPace(
        todayFraction = daysElapsed.toFloat() / windowDays,
        // Truncates toward zero, so "1 day over" needs a full day's budget past the line.
        daysOver = (spentDays - BigDecimal(daysElapsed)).toInt(),
        isOverBudget = spent > budget,
    )
}
