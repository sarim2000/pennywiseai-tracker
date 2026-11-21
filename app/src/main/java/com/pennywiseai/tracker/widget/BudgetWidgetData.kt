package com.pennywiseai.tracker.widget

import com.pennywiseai.tracker.domain.spending.budgetPace
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

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
    /**
     * The shared window's days so far, today included, and its length.
     * Both 0 when nothing is running, or when the limits don't share one window.
     */
    val daysElapsed: Int = 0,
    val windowDays: Int = 0,
    /**
     * Whole days past an even spend when counted limits don't share one window.
     * Null when they do, or when nothing is running. An old snapshot leaves this
     * unset and [budgetMood] keeps pacing [totalSpent] against [daysElapsed].
     */
    val foldedDaysOver: Int? = null
)

/**
 * One counted LIMIT group's own window: the spend and the limit the page total
 * already summed, and that group's days. Not the page's union window.
 */
internal data class WidgetLimitWindow(
    val spent: BigDecimal,
    val limit: BigDecimal,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val daysElapsed: Int,
    val windowDays: Int
)

/** Days to store for the widget, and a folded pace when the limits don't share a window. */
internal data class WidgetLimitPace(
    val daysElapsed: Int,
    val windowDays: Int,
    val foldedDaysOver: Int? = null
)

private val MATH = MathContext.DECIMAL64

/**
 * How the non-unified widget should pace [limits]. [today] is passed in.
 *
 * A limit is running when its window covers [today] and its own day count is
 * inside that window. Nothing running stores no days and no fold, so the mood
 * stays Calm. One shared running window stores that window's days and no fold:
 * the page totals already match it. Different windows fold only the running
 * ones, and the page sum is not paced against any one of them.
 */
internal fun widgetLimitPace(today: LocalDate, limits: List<WidgetLimitWindow>): WidgetLimitPace {
    val running = limits.filter { it.isRunning(today) }
    if (running.isEmpty()) return WidgetLimitPace(0, 0, null)
    val first = limits.first()
    if (limits.all { it.sameWindow(first) }) {
        val slice = running.first()
        return WidgetLimitPace(slice.daysElapsed, slice.windowDays, null)
    }
    var expected = BigDecimal.ZERO
    var oneDay = BigDecimal.ZERO
    var spent = BigDecimal.ZERO
    for (slice in running) {
        val days = BigDecimal(slice.windowDays)
        expected += slice.limit.multiply(BigDecimal(slice.daysElapsed)).divide(days, MATH)
        oneDay += slice.limit.divide(days, MATH)
        spent += slice.spent
    }
    val daysOver = if (oneDay.signum() == 0) 0 else spent.subtract(expected).divide(oneDay, MATH).toInt()
    return WidgetLimitPace(0, 0, daysOver)
}

private fun WidgetLimitWindow.isRunning(today: LocalDate): Boolean =
    windowDays >= 1 &&
        daysElapsed in 1..windowDays &&
        !today.isBefore(windowStart) &&
        !today.isAfter(windowEnd)

private fun WidgetLimitWindow.sameWindow(other: WidgetLimitWindow): Boolean =
    windowStart == other.windowStart && windowEnd == other.windowEnd && windowDays == other.windowDays

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
    // Different windows store no day pair. The fold is the running pace, so it is not Calm.
    if (!windowRunning && data.foldedDaysOver == null) return MascotMood.Calm
    if (wholeUnits(data.remaining) < BigDecimal.ZERO) return MascotMood.Worried
    data.foldedDaysOver?.let { daysOver ->
        return if (daysOver > 0) MascotMood.Thinking else MascotMood.Happy
    }
    val pace = budgetPace(data.totalSpent, data.totalLimit, data.daysElapsed, data.windowDays) ?: return MascotMood.Calm
    return if (pace.daysOver > 0) MascotMood.Thinking else MascotMood.Happy
}
