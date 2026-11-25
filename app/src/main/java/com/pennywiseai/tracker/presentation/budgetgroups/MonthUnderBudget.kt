package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.ui.components.celebration.CelebrationKeys
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** How many days after a budget cycle ends its "ended under budget" card stays up. */
const val UNDER_BUDGET_CARD_DAYS = 7

/**
 * The budget cycle that ended most recently, while [today] is at most [UNDER_BUDGET_CARD_DAYS]
 * days past its end; else null. [startDay] is the Budgets cycle start day (1: calendar months),
 * so a cycle starting on the 25th is judged from the 25th, not a week into the next month.
 */
fun underBudgetCycle(today: LocalDate, startDay: Int): BudgetWindow? {
    val (start, end) = BudgetCycle.previousCycle(BudgetCycle.currentCycle(today, startDay), startDay)
    if (ChronoUnit.DAYS.between(end, today) > UNDER_BUDGET_CARD_DAYS) return null
    return BudgetWindow(start, end, ChronoUnit.DAYS.between(start, end).toInt() + 1)
}

/**
 * The Budgets page month after the date moves from [before] to [now]. A page left on the
 * current month moves on with it (the app kept alive from 28 Dec to 3 Jan lands on January,
 * where the under-budget card shows); a page the user moved to an older month stays put.
 */
internal fun followToday(selected: YearMonth?, before: LocalDate, now: LocalDate): YearMonth? {
    val current = YearMonth.from(now)
    return if (selected == YearMonth.from(before) && current.isAfter(selected)) current else selected
}

/** The cycle [start]..[end] ended with every monthly spending limit kept, in [currency]. */
data class MonthUnderBudget(val start: LocalDate, val end: LocalDate, val currency: String) {
    /** The month the cycle ends in: one per cycle, as the Review prompt keys it. */
    val month: YearMonth get() = YearMonth.from(end)

    /** Names the moment for both the one-time celebration and the card's dismissal. */
    val key: String get() = CelebrationKeys.budgetMonth(month, currency, start)
}

/**
 * The win for [cycle] (from [underBudgetCycle]), or null. [previous] is the Budgets page as
 * it stood on the cycle's last day, in one currency (the page's), so nothing is added across
 * currencies. Only spending limits count: a TARGET (savings, investments) or EXPECTED budget
 * isn't one to stay under. Needs at least one limit, some spending against them, and none over.
 */
fun monthUnderBudget(cycle: BudgetWindow, previous: BudgetOverallSummary, dismissed: Set<String>): MonthUnderBudget? {
    // Monthly limits on this cycle only: a Weekly budget's window is its last week, and a
    // One-time budget is clipped to the month, so neither says whether it held. A budget
    // with its own start day is on a different cycle, still running or judged elsewhere.
    // ponytail: weekly and one-time limits neither count nor block; judge their own windows if asked.
    // Budgets are today's rows, not a record of the cycle: one made after it ended wasn't
    // there to keep (a first budget on the 3rd isn't cheered for last month).
    val limits = previous.groups.filter {
        it.group.budget.groupType == BudgetGroupType.LIMIT && it.periodType == BudgetPeriodType.MONTHLY &&
            it.hasLimit && it.windowEnd == cycle.end && !it.group.budget.createdAt.toLocalDate().isAfter(cycle.end)
    }
    // One edited since may have had a lower limit then (raised to make room), so the
    // cycle can't be judged. ponytail: a budget deleted since leaves no trace to check.
    val editedSince = limits.any { it.group.budget.updatedAt.toLocalDate().isAfter(cycle.end) }
    val kept = limits.isNotEmpty() &&
        !editedSince &&
        limits.any { it.totalActual.signum() > 0 } &&
        limits.all { it.held() }
    return MonthUnderBudget(cycle.start, cycle.end, previous.currency)
        .takeIf { kept && it.key !in dismissed }
}

/**
 * The limit and every category limit inside it held, all spending counted. A budget
 * under its total can still have a category over its own limit (its meter is red).
 * With rollover, a carry that couldn't be counted leaves [totalBudget] short of the
 * real limit (an overspent last period would have lowered it), so it can't be judged.
 */
private fun BudgetGroupSpending.held(): Boolean =
    totalActual <= totalBudget &&
        !carryIncomplete &&
        !hasUnconvertedSpend &&
        categorySpending.none { it.budgetAmount.signum() > 0 && it.actualAmount > it.budgetAmount }

/**
 * [ready] is false until this visit has decided whether [win] exists. The Budgets list
 * waits for that before a deep link scrolls, so a late card can't shift the row.
 */
data class UnderBudgetCardState(val win: MonthUnderBudget? = null, val ready: Boolean = false)

/**
 * Index of [focusBudgetId] in the Budgets lazy list. Keys match that list: goals, the
 * under-budget card, the month selector, then the hero and the other budgets.
 */
internal fun budgetFocusIndex(
    focusBudgetId: Long,
    hasGoals: Boolean,
    hasWin: Boolean,
    heroId: Long?,
    otherIds: List<Long>,
): Int? {
    val keys = ArrayList<Any>(otherIds.size + 3)
    if (hasGoals) keys.add("goals")
    if (hasWin) keys.add("under-budget")
    keys.add("month")
    if (heroId != null) keys.add(heroId)
    keys.addAll(otherIds)
    return keys.indexOf(focusBudgetId).takeIf { it >= 0 }
}

/** A small tonal note at the top of the Budgets list. No figures, so it's safe while amounts are hidden. */
@Composable
fun MonthUnderBudgetCard(win: MonthUnderBudget, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(
                size = Dimensions.Icon.extraLarge,
                mood = MascotMood.Happy,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                // A cycle from the 25th names its dates: "October" would be wrong for 25 Sep - 24 Oct.
                val label = if (isCalendarMonth(win.start, win.end)) {
                    win.start.format(AppDates.pattern("MMMM"))
                } else {
                    val day = AppDates.dayMonthFormatter()
                    stringResource(R.string.budgets_date_range, win.start.format(day), win.end.format(day))
                }
                Text(
                    text = stringResource(R.string.budget_month_kept_title, label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.budget_month_kept_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.budget_month_kept_dismiss),
                    modifier = Modifier.size(Dimensions.Icon.small),
                )
            }
        }
    }
}
