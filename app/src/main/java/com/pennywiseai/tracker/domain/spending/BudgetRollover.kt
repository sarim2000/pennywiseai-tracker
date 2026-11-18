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
     * `clamp(base − previousSpent, −base, +base)`, measured against the [baseLimit],
     * never a limit that already carried something in. [previousSpent] is null when
     * some of last period's spending couldn't be counted (no exchange rate): that
     * carries nothing, since a missing rate must never look like money left over.
     */
    fun carry(baseLimit: Money, previousSpent: Money?): Money {
        if (previousSpent == null || baseLimit.amount.signum() <= 0) return Money.zero(baseLimit.currency)
        val left = (baseLimit + Money(previousSpent.amount.negate(), previousSpent.currency)).amount
        return Money(left.coerceIn(baseLimit.amount.negate(), baseLimit.amount), baseLimit.currency)
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
     * change of currency or period, starts it again today, since last period no
     * longer measures this one; turned off, it's null; otherwise it stays.
     * Anchors are compared as the periods they resolve to ([globalStartDay] as
     * [resolveBudgetWindow] takes it), so a legacy null anchor saved as its default
     * isn't a new period.
     */
    fun sinceAfterEdit(
        before: BudgetEntity?,
        after: BudgetEntity,
        today: LocalDate,
        globalStartDay: Int = BudgetCycle.DEFAULT_START_DAY
    ): LocalDate? = when {
        !after.rolloverEnabled -> null
        before == null || !before.rolloverEnabled || before.rolloverSince == null -> today
        !before.currency.equals(after.currency, ignoreCase = true) ||
            before.periodType != after.periodType ||
            resolveBudgetWindow(before, today, globalStartDay) != resolveBudgetWindow(after, today, globalStartDay) -> today
        else -> before.rolloverSince
    }
}
