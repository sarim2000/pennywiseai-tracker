package com.pennywiseai.tracker.presentation.insights

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightAction
import com.pennywiseai.tracker.domain.insights.InsightFact
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.Money
import java.time.LocalDate

/** An insight in words: the number first, then why, then the figures behind it. */
internal data class InsightText(
    val title: String,
    val body: String,
    val metrics: List<Pair<String, String>>,
    val note: String? = null,
)

@Composable
internal fun insightText(insight: Insight): InsightText = when (val f = insight.fact) {
    is InsightFact.BudgetOver -> InsightText(
        title = if (f.spent.amount > f.limit.amount) {
            stringResource(
                R.string.insights_budget_over_title,
                Money(f.spent.amount - f.limit.amount, f.spent.currency).format(),
                f.name,
            )
        } else {
            stringResource(R.string.insights_budget_used_up_title, f.name)
        },
        body = stringResource(R.string.insights_budget_over_body, f.spent.format(), f.limit.format()),
        metrics = listOf(
            stringResource(R.string.insights_metric_spent) to f.spent.format(),
            stringResource(R.string.insights_metric_budget) to f.limit.format(),
        ),
        note = stringResource(R.string.insights_budget_incomplete).takeIf { f.incomplete },
    )
    is InsightFact.BudgetPace -> InsightText(
        title = stringResource(R.string.insights_budget_pace_title, f.perDayLeft.format(), f.name),
        body = stringResource(
            R.string.insights_budget_pace_body, f.projected.format(), date(f.windowEnd), f.limit.format(),
        ),
        metrics = listOf(
            stringResource(R.string.insights_metric_spent) to f.spent.format(),
            stringResource(R.string.insights_metric_budget) to f.limit.format(),
            stringResource(R.string.insights_metric_projected) to f.projected.format(),
            stringResource(R.string.insights_metric_per_day) to f.perDayLeft.format(),
        ),
        note = stringResource(R.string.insights_budget_incomplete).takeIf { f.incomplete },
    )
    is InsightFact.BudgetsOnTrack -> InsightText(
        title = pluralStringResource(R.plurals.insights_budgets_on_track_title, f.count, f.count),
        body = stringResource(R.string.insights_budgets_on_track_body),
        metrics = emptyList(),
    )
    is InsightFact.CategorySpike -> InsightText(
        title = stringResource(
            R.string.insights_spike_title, f.spent.format(), categoryLabel(f.category), percent(f.percentAbove),
        ),
        body = stringResource(R.string.insights_spike_body, f.usual.format()),
        metrics = listOf(
            stringResource(R.string.insights_metric_this_cycle) to f.spent.format(),
            stringResource(R.string.insights_metric_usual) to f.usual.format(),
        ),
    )
    is InsightFact.SavingsRate -> if (f.saved.amount.signum() < 0) {
        InsightText(
            title = stringResource(
                R.string.insights_savings_negative_title,
                Money(f.saved.amount.negate(), f.saved.currency).format(),
            ),
            body = stringResource(R.string.insights_savings_negative_body, f.income.format()),
            metrics = savingsMetrics(f),
        )
    } else {
        InsightText(
            title = stringResource(R.string.insights_savings_good_title, percent(f.percent)),
            body = stringResource(R.string.insights_savings_good_body, f.saved.format(), f.income.format()),
            metrics = savingsMetrics(f),
        )
    }
    is InsightFact.LargeTransaction -> InsightText(
        title = stringResource(R.string.insights_large_title, f.amount.format(), merchant(f.merchant), times(f.times)),
        body = stringResource(R.string.insights_large_body, categoryLabel(f.category), f.usual.format(), date(f.date)),
        metrics = listOf(
            stringResource(R.string.insights_metric_amount) to f.amount.format(),
            stringResource(R.string.insights_metric_usual) to f.usual.format(),
        ),
    )
    is InsightFact.PriceChange -> InsightText(
        title = stringResource(R.string.insights_price_title, merchant(f.name), f.charged.format()),
        body = stringResource(R.string.insights_price_body, f.tracked.format()),
        metrics = listOf(
            stringResource(R.string.insights_metric_tracked) to f.tracked.format(),
            stringResource(R.string.insights_metric_charged) to f.charged.format(),
        ),
    )
    is InsightFact.CardUtilisation -> InsightText(
        title = stringResource(R.string.insights_card_title, percent(f.percent), f.label),
        body = stringResource(R.string.insights_card_body, f.used.format(), f.limit.format()),
        metrics = listOf(
            stringResource(R.string.insights_metric_used) to f.used.format(),
            stringResource(R.string.insights_metric_limit) to f.limit.format(),
        ),
    )
    is InsightFact.BillsDue -> InsightText(
        title = pluralStringResource(R.plurals.insights_bills_title, f.withinDays, f.total.format(), f.withinDays),
        body = pluralStringResource(R.plurals.insights_bills_body, f.count, f.count, date(f.firstDue)),
        metrics = emptyList(),
    )
}

@Composable
internal fun actionLabel(action: InsightAction): String = stringResource(
    when (action) {
        is InsightAction.OpenBudget -> R.string.insights_action_budgets
        is InsightAction.OpenCategory -> R.string.insights_action_transactions
        is InsightAction.OpenTransaction -> R.string.insights_action_transaction
        is InsightAction.OpenAccount -> R.string.insights_action_account
        InsightAction.OpenSubscriptions -> R.string.insights_action_subscriptions
    }
)

/** A percent says how amounts relate, so it hides with them, as on Home and in reviews. */
@Composable
internal fun percent(value: Int): String =
    if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.insights_percent, value)

/** "4×" says how two amounts relate, so it hides with them too. */
@Composable
private fun times(value: Int): String =
    if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.insights_times, value)

@Composable
private fun savingsMetrics(f: InsightFact.SavingsRate) = listOf(
    stringResource(R.string.insights_metric_income) to f.income.format(),
    stringResource(R.string.insights_metric_saved) to f.saved.format(),
)

@Composable
private fun merchant(raw: String): String = LocalMerchantDisplay.current(raw) ?: raw

private fun date(date: LocalDate): String = date.format(AppDates.dayMonthFormatter())
