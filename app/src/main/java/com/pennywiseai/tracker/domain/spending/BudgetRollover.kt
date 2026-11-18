package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.previousBudgetWindow
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.utils.Money
import java.time.LocalDate

/**
 * Budget rollover: what was left of last period's limit, or spent past it, carries
 * into this period. It looks back one period only and is capped at one limit either
 * way, so last week's leftover never piles up and an old overspend never follows
 * the user around. A period's carry that isn't used simply expires with it.
 */
object BudgetRollover {

    /** Only a weekly or monthly spending limit can roll over. */
    fun isAvailable(groupType: BudgetGroupType, periodType: BudgetPeriodType): Boolean =
        groupType == BudgetGroupType.LIMIT &&
            (periodType == BudgetPeriodType.WEEKLY || periodType == BudgetPeriodType.MONTHLY)

    /**
     * `clamp(base − previousSpent, −cap, +cap)`, measured against the [baseLimit],
     * never a limit that already carried something in. [cap] is the budget's own
     * limit: [baseLimit] may hold last period's extra budget too, which mustn't let an
     * overspend carry more than one limit and leave this period's below 0.
     * [previousSpent] is null when some of last period's spending couldn't be counted
     * (no exchange rate): that carries nothing, since a missing rate must never look
     * like money left over.
     */
    fun carry(baseLimit: Money, previousSpent: Money?, cap: Money = baseLimit): Money {
        if (previousSpent == null || baseLimit.amount.signum() <= 0) return Money.zero(baseLimit.currency)
        val left = (baseLimit + Money(previousSpent.amount.negate(), previousSpent.currency)).amount
        require(cap.currency == baseLimit.currency) { "carry cap in ${cap.currency}, limit in ${baseLimit.currency}" }
        return Money(left.coerceIn(cap.amount.negate(), cap.amount), baseLimit.currency)
    }

    /** The one limit every budget figure (remaining, pace, alerts, widget) is measured against. */
    fun effectiveLimit(baseLimit: Money, carry: Money): Money = baseLimit + carry

    /**
     * The period [window] carries from, or null when it carries nothing: rollover is
     * off or not available for [budget], or that period ended before rollover started
     * (the period holding [BudgetEntity.rolloverSince] is the first, with no carry).
     * Read fresh every time, so days the app wasn't opened change nothing.
     */
    fun sourceWindow(budget: BudgetEntity, window: BudgetWindow, globalStartDay: Int): BudgetWindow? {
        if (!budget.rolloverEnabled || !isAvailable(budget.groupType, budget.periodType)) return null
        val since = budget.rolloverSince ?: return null
        return previousBudgetWindow(budget, window, globalStartDay).takeIf { !it.end.isBefore(since) }
    }

    /**
     * Rollover is on for [budget], but the period [windowStart]..[windowEnd] holds
     * [BudgetEntity.rolloverSince]: it's the first, so nothing carries until the next.
     */
    fun isFirstPeriod(budget: BudgetEntity, windowStart: LocalDate, windowEnd: LocalDate): Boolean {
        if (!budget.rolloverEnabled || !isAvailable(budget.groupType, budget.periodType)) return false
        val since = budget.rolloverSince ?: return false
        return !since.isBefore(windowStart) && !since.isAfter(windowEnd)
    }

    /**
     * [BudgetEntity.rolloverSince] for [after], the budget as it's being saved on
     * [today] ([before]: as it was, null for a new one). Rollover turned on, or a
     * change of limit, currency, period or of what it counts ([sameScope] false: its
     * categories or type buckets changed), starts it again today, since last period
     * no longer measures this one; turned off, it's null; otherwise it stays.
     * Anchors are compared as the periods they resolve to ([globalStartDay] as
     * [resolveBudgetWindow] takes it), so a legacy null anchor saved as its default
     * isn't a new period.
     */
    fun sinceAfterEdit(
        before: BudgetEntity?,
        after: BudgetEntity,
        today: LocalDate,
        globalStartDay: Int = BudgetCycle.DEFAULT_START_DAY,
        sameScope: Boolean = true
    ): LocalDate? = when {
        !after.rolloverEnabled -> null
        before == null || !before.rolloverEnabled || before.rolloverSince == null || !sameScope -> today
        // Last period's leftover is measured against the limit it had: a new limit
        // would rewrite it (₹10,000 → ₹15,000 turns +₹1,000 into +₹6,000).
        before.limitAmount.compareTo(after.limitAmount) != 0 ||
            !before.currency.equals(after.currency, ignoreCase = true) ||
            before.periodType != after.periodType ||
            !samePeriods(before, globalStartDay, after, globalStartDay, today) -> today
        else -> before.rolloverSince
    }

    /**
     * [before] and [after] put [today] in the same period and the same last period,
     * the one a carry is measured from. Two rules can agree on this period alone (day
     * 31 and the last working day both start on Monday 30 November) yet not on the
     * last one (31 and 30 October).
     */
    private fun samePeriods(before: BudgetEntity, beforeStartDay: Int, after: BudgetEntity, afterStartDay: Int, today: LocalDate): Boolean {
        val current = resolveBudgetWindow(before, today, beforeStartDay)
        return current == resolveBudgetWindow(after, today, afterStartDay) &&
            previousBudgetWindow(before, current, beforeStartDay) == previousBudgetWindow(after, current, afterStartDay)
    }

    /**
     * [BudgetEntity.rolloverSince] once the global cycle start day moves from [before]
     * to [after]. A budget whose period follows it (a monthly one with no anchor of its
     * own) starts again [today]: its last period isn't the one it measured.
     */
    fun sinceAfterCycleStartDayChange(budget: BudgetEntity, today: LocalDate, before: Int, after: Int): LocalDate? =
        if (budget.rolloverEnabled && budget.rolloverSince != null && !samePeriods(budget, before, budget, after, today)) {
            today
        } else {
            budget.rolloverSince
        }
}
