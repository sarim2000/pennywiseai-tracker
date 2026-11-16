package com.pennywiseai.tracker.presentation.insights

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightAction
import com.pennywiseai.tracker.domain.insights.InsightFact
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.spending.Runway
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.bidiIsolate
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate

/**
 * An insight in words: the number first, then why, then the figures behind it.
 * [body] never repeats a figure [metrics] already shows; null when they say it all.
 */
internal data class InsightText(
    val title: String,
    val body: String?,
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
                name(f.name),
            )
        } else {
            stringResource(R.string.insights_budget_used_up_title, name(f.name))
        },
        body = null,
        metrics = listOf(
            stringResource(R.string.insights_metric_spent) to f.spent.format(),
            stringResource(R.string.insights_metric_budget) to f.limit.format(),
        ),
        note = stringResource(R.string.insights_budget_incomplete).takeIf { f.incomplete },
    )
    is InsightFact.BudgetPace -> InsightText(
        title = stringResource(R.string.insights_budget_pace_title, f.perDayLeft.format(), name(f.name)),
        body = stringResource(R.string.insights_budget_pace_body, date(f.windowEnd)),
        // The title already says what's left a day.
        metrics = listOf(
            stringResource(R.string.insights_metric_spent) to f.spent.format(),
            stringResource(R.string.insights_metric_budget) to f.limit.format(),
            stringResource(R.string.insights_metric_projected) to f.projected.format(),
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
            R.string.insights_spike_title, f.spent.format(), name(categoryLabel(f.category)), percent(f.percentAbove),
        ),
        body = stringResource(R.string.insights_spike_body),
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
            body = stringResource(R.string.insights_savings_body),
            metrics = savingsMetrics(f),
        )
    } else {
        InsightText(
            title = stringResource(R.string.insights_savings_good_title, percent(f.percent)),
            body = stringResource(R.string.insights_savings_body),
            metrics = savingsMetrics(f),
        )
    }
    is InsightFact.LargeTransaction -> InsightText(
        title = stringResource(R.string.insights_large_title, f.amount.format(), merchant(f.merchant), times(f.times)),
        body = stringResource(R.string.insights_large_body, name(categoryLabel(f.category)), date(f.date)),
        metrics = listOf(
            stringResource(R.string.insights_metric_amount) to f.amount.format(),
            stringResource(R.string.insights_metric_usual) to f.usual.format(),
        ),
    )
    is InsightFact.PriceChange -> InsightText(
        title = stringResource(R.string.insights_price_title, merchant(f.name), f.charged.format()),
        body = stringResource(R.string.insights_price_body),
        metrics = listOf(
            stringResource(R.string.insights_metric_tracked) to f.tracked.format(),
            stringResource(R.string.insights_metric_charged) to f.charged.format(),
        ),
    )
    is InsightFact.CardUtilisation -> InsightText(
        title = stringResource(R.string.insights_card_title, percent(f.percent), name(f.label)),
        body = null,
        metrics = listOf(
            stringResource(R.string.insights_metric_used) to f.used.format(),
            stringResource(R.string.insights_metric_limit) to f.limit.format(),
        ),
    )
    is InsightFact.MoneyLasts -> InsightText(
        title = stringResource(R.string.insights_runway_title, months(f.months)),
        body = stringResource(
            if (insight.severity == InsightSeverity.GOOD) R.string.insights_runway_good_body
            else R.string.insights_runway_low_body,
            f.balance.format(),
            f.monthlySpend.format(),
            Runway.TARGET_MONTHS,
        ),
        metrics = listOf(
            stringResource(R.string.insights_metric_held) to f.balance.format(),
            stringResource(R.string.insights_metric_usual_month) to f.monthlySpend.format(),
        ),
        note = stringResource(R.string.insights_runway_estimate).takeIf { f.lowConfidence },
    )
    is InsightFact.GoalsFit -> InsightText(
        // With nothing left over, "need X more" would only restate the shortfall as a
        // bigger number than the goals ask for, so lead with what they need.
        title = when {
            f.shortBy == null -> stringResource(R.string.insights_goals_fit_title, f.needed.format())
            f.surplus.amount.signum() <= 0 -> stringResource(R.string.goals_need_per_month, f.needed.format())
            else -> stringResource(R.string.insights_goals_short_title, f.shortBy.format())
        },
        body = when {
            f.shortBy == null -> stringResource(R.string.insights_goals_fit_body, f.surplus.format())
            f.surplus.amount.signum() > 0 ->
                stringResource(R.string.insights_goals_short_body, f.needed.format(), f.surplus.format())
            else -> stringResource(R.string.insights_goals_no_surplus_body, f.needed.format())
        },
        metrics = listOf(
            stringResource(R.string.insights_metric_goals_need) to f.needed.format(),
            stringResource(R.string.insights_metric_left_over) to f.surplus.format(),
        ),
    )
    is InsightFact.BillsDue -> InsightText(
        title = pluralStringResource(R.plurals.insights_bills_title, f.withinDays, f.total.format(), f.withinDays),
        body = ListFormatter.getInstance(LocalConfiguration.current.locales[0]).format(f.names.map { merchant(it) })
            .let { names ->
                if (f.firstDue == LocalDate.now()) {
                    pluralStringResource(R.plurals.insights_bills_body_today, f.count, names)
                } else {
                    pluralStringResource(R.plurals.insights_bills_body, f.count, names, date(f.firstDue))
                }
            },
        metrics = emptyList(),
    )
}

@Composable
internal fun actionLabel(action: InsightAction): String = stringResource(
    when (action) {
        InsightAction.OpenBudgets -> R.string.insights_action_budgets
        is InsightAction.OpenCategory -> R.string.insights_action_transactions
        is InsightAction.OpenTransaction -> R.string.insights_action_transaction
        is InsightAction.OpenAccount -> R.string.insights_action_account
        InsightAction.OpenSubscriptions -> R.string.insights_action_subscriptions
        InsightAction.OpenGoals -> R.string.insights_action_goals
    }
)

/** A percent says how amounts relate, so it hides with them, as on Home and in reviews. */
@Composable
internal fun percent(value: Int): String =
    if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.insights_percent, value)

/**
 * "2.4 months", "1 month", "under a month". Months say how two amounts relate, so
 * they hide with them.
 */
@Composable
internal fun months(value: BigDecimal): String {
    if (AmountPrivacy.hidden) return AmountPrivacy.MASK
    if (value < BigDecimal.ONE) return stringResource(R.string.insights_months_under_one)
    val number = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
        .apply { maximumFractionDigits = 1 }
        .format(value)
    val whole = value.stripTrailingZeros().scale() <= 0
    return if (whole) pluralStringResource(R.plurals.insights_months, value.toInt(), number)
    else stringResource(R.string.insights_months_decimal, number)
}

/**
 * "4×" says how two amounts relate, so it hides with them too. A plural, since
 * Arabic's noun changes with the number (3–10, 11–99, 100 and up).
 */
@Composable
private fun times(value: Int): String =
    if (AmountPrivacy.hidden) AmountPrivacy.MASK else pluralStringResource(R.plurals.insights_times, value, value)

@Composable
private fun savingsMetrics(f: InsightFact.SavingsRate) = listOf(
    stringResource(R.string.insights_metric_income) to f.income.format(),
    stringResource(R.string.insights_metric_saved) to f.saved.format(),
)

@Composable
private fun merchant(raw: String): String = name(LocalMerchantDisplay.current(raw) ?: raw)

/**
 * Any name put into a sentence (merchant, budget, category, card), isolated as amounts
 * are, so a Latin name in an Arabic sentence keeps its place: without it, "A/c XX1234،
 * 4 أضعاف" ran left to right and the multiplier was pulled into the name's run.
 */
@Composable
private fun name(raw: String): String = LocalContext.current.bidiIsolate(raw)

private fun date(date: LocalDate): String = date.format(AppDates.dayMonthFormatter())
