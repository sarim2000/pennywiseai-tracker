package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.domain.spending.budgetPace
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import java.math.BigDecimal

data class BudgetWidgetData(
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val totalLimit: BigDecimal = BigDecimal.ZERO,
    /** A spending limit is set, even if rollover carried it down to a [totalLimit] of 0. */
    val hasLimit: Boolean = totalLimit.signum() > 0,
    val remaining: BigDecimal = BigDecimal.ZERO,
    val percentageUsed: Float = 0f,
    val dailyAllowance: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val netSavings: BigDecimal = BigDecimal.ZERO,
    val savingsRate: Float = 0f,
    val savingsDelta: BigDecimal? = null,
    val currency: String = "INR",
    /** The budget window's days so far, today included, and its length; 0 when unknown. */
    val daysElapsed: Int = 0,
    val windowDays: Int = 0
)

/**
 * The owl's read of the budget: over the limit (as the figure says it) is Worried, more
 * than a day's budget past an even spend Thinking, otherwise Happy. Calm when there's no
 * budget or no running window to pace it against.
 */
internal fun budgetMood(data: BudgetWidgetData): MascotMood {
    if (!data.hasLimit) return MascotMood.Calm
    // A snapshot from before the window was stored can be over budget with both counts at 0.
    // Calm until a refresh supplies a running window. A real window whose rollover carried
    // the limit to 0 is still Worried: budgetPace refuses a zero budget, so this check is first.
    val windowRunning = data.windowDays >= 1 && data.daysElapsed in 1..data.windowDays
    if (!windowRunning) return MascotMood.Calm
    if (wholeUnits(data.remaining) < BigDecimal.ZERO) return MascotMood.Worried
    val pace = budgetPace(data.totalSpent, data.totalLimit, data.daysElapsed, data.windowDays) ?: return MascotMood.Calm
    return if (pace.daysOver > 0) MascotMood.Thinking else MascotMood.Happy
}
