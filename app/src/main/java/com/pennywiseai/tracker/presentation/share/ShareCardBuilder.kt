package com.pennywiseai.tracker.presentation.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.domain.achievements.Achievement
import com.pennywiseai.tracker.domain.goals.GoalPill
import com.pennywiseai.tracker.domain.goals.fraction
import com.pennywiseai.tracker.domain.goals.percent
import com.pennywiseai.tracker.domain.goals.pill
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.domain.review.percentChange
import com.pennywiseai.tracker.domain.share.budgetResult
import com.pennywiseai.tracker.domain.share.categoryChangePercent
import com.pennywiseai.tracker.domain.share.previousSavingsRate
import com.pennywiseai.tracker.domain.share.savingsRatePercent
import com.pennywiseai.tracker.domain.share.savingsTrend
import com.pennywiseai.tracker.domain.share.spendingDrivers
import com.pennywiseai.tracker.domain.share.topCategoryShares
import com.pennywiseai.tracker.ui.components.achievementDescription
import com.pennywiseai.tracker.ui.components.achievementName
import com.pennywiseai.tracker.ui.components.comparisonTarget
import com.pennywiseai.tracker.ui.components.shareLabel
import com.pennywiseai.tracker.ui.components.tierLabel
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import com.pennywiseai.tracker.utils.formatCount
import java.math.BigDecimal
import kotlin.math.abs

/** The card the studio can show right now. */
sealed interface ShareCardState {
    data object Loading : ShareCardState

    /** The selection has nothing honest to say, and [reason] tells the user why. */
    data class Unavailable(val reason: String) : ShareCardState

    data class Ready(val content: ShareCardContent) : ShareCardState
}

/** The currencies a review card can be about, most active first. */
fun currenciesOf(data: ShareStudioData): List<String> =
    (data as? ShareStudioData.Reviews)?.reviews?.firstOrNull()?.currencies
        ?.filter { it.hasActivity }?.map { it.currency }.orEmpty()

/**
 * Builds the selected card from its data. Everything that ends up on the image is
 * decided here, gated by the details the user left on; the card composables only draw it.
 *
 * Chart values are relative (the largest magnitude is 1; a ring's is its 0..1 fill) so
 * the content never carries a figure the user switched off — the bars' lengths are all a
 * chart shows anyway.
 */
@Composable
fun shareCardState(selection: ShareStudioSelection, data: ShareStudioData): ShareCardState {
    val details = effectiveDetails(selection.kind, selection.details)
    return when (data) {
        ShareStudioData.Loading -> ShareCardState.Loading
        is ShareStudioData.Reviews -> {
            val review = data.reviews.firstOrNull() ?: return ShareCardState.Loading
            val currencies = review.currencies.filter { it.hasActivity }
            val currency = currencies.firstOrNull { it.currency == selection.currency } ?: currencies.firstOrNull()
                ?: return ShareCardState.Unavailable(stringResource(R.string.share_unavailable_empty))
            when (selection.kind) {
                ShareKind.SPENDING -> spendingCard(review, currency, details)
                ShareKind.SAVINGS -> savingsCard(data.reviews, currency, details)
                else -> ShareCardState.Ready(recapCard(review, currency, details))
            }
        }
        is ShareStudioData.Goals -> {
            val goal = data.goals.firstOrNull { it.goal.id == selection.goalId } ?: data.goals.firstOrNull()
                ?: return ShareCardState.Unavailable(stringResource(R.string.share_unavailable_goals))
            ShareCardState.Ready(goalCard(goal, details))
        }
        is ShareStudioData.Budget -> budgetCard(data.summary, data.converted, details)
        is ShareStudioData.Achievements -> achievementsCard(data.earned, selection.achievements, details)
        ShareStudioData.Failed -> ShareCardState.Unavailable(stringResource(R.string.share_unavailable_failed))
    }
}

/** The achievements an achievements card shows: those picked, or every one earned. */
fun selectedAchievements(earned: List<Achievement>, picked: Set<String>?): List<Achievement> =
    if (picked == null) earned else earned.filter { it.id.key in picked }

// --- Formatting. Every amount leaves the phone, so none of them is masked.

private fun money(amount: BigDecimal, currency: String): String =
    CurrencyFormatter.formatCurrency(amount, currency, maskIfHidden = false)

/** "+₹1,200" / "−₹900", the sign kept on the left in an RTL card. */
private fun signedMoney(amount: BigDecimal, currency: String): String =
    CurrencyFormatter.signed(sign(amount.signum()), money(amount.abs(), currency))

@Composable
private fun percent(value: Int): String =
    if (value < 0) signedPercent(value) else stringResource(R.string.share_percent, value)

@Composable
private fun signedPercent(value: Int): String =
    CurrencyFormatter.signed(sign(value), stringResource(R.string.share_percent, abs(value)))

@Composable
private fun signedPoints(value: Int): String =
    stringResource(R.string.share_card_points, CurrencyFormatter.signed(sign(value), count(abs(value))))

private fun sign(signum: Int): String = when {
    signum > 0 -> "+"
    signum < 0 -> "−"
    else -> ""
}

private fun count(value: Int): String = formatCount(value)

/** Bar lengths, scaled so the largest magnitude is 1. */
private fun relative(values: List<Double>): List<Double> {
    val max = values.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: return values.map { 0.0 }
    return values.map { it / max }
}

/** Bar rows a portrait card has room for: one fewer when a row of stats sits under them. */
private fun chartRows(withStats: Boolean): Int = if (withStats) 3 else 4

@Composable
private fun periodOf(review: PeriodReview, details: Set<ShareDetail>): String? =
    if (ShareDetail.PERIOD in details) shareLabel(review) else null

// --- Cards

@Composable
private fun recapCard(review: PeriodReview, c: CurrencyReview, details: Set<ShareDetail>): ShareCardContent {
    val amounts = ShareDetail.AMOUNTS in details
    val percents = ShareDetail.PERCENTAGES in details
    val stats = buildList {
        if (amounts) add(ShareCardStat(stringResource(R.string.share_card_stat_spent), money(c.spent, c.currency)))
        if (amounts && c.income.signum() > 0) {
            add(ShareCardStat(stringResource(R.string.share_card_stat_income), money(c.income, c.currency)))
        }
        if (percents) {
            percentChange(c.spent, c.previousSpent)?.let {
                add(ShareCardStat(stringResource(R.string.share_card_stat_change), signedPercent(it)))
            }
        }
    }
    val shares = topCategoryShares(c, limit = chartRows(stats.isNotEmpty()))
    val chart = if (ShareDetail.CATEGORIES in details && shares.isNotEmpty()) {
        val values = relative(shares.map { it.amount.toDouble() })
        ShareChart(
            type = ShareChartType.BARS,
            points = shares.mapIndexed { i, share ->
                ShareChartPoint(
                    label = categoryLabel(share.name),
                    value = values[i],
                    display = when {
                        amounts -> money(share.amount, c.currency)
                        percents -> percent(share.sharePercent)
                        else -> null
                    },
                )
            },
            highlightIndex = 0,
        )
    } else null
    return ShareCardContent(
        kind = ShareKind.RECAP,
        title = stringResource(
            when (review.range.kind) {
                ReviewKind.WEEK -> R.string.share_card_recap_title_week
                ReviewKind.MONTH -> R.string.share_card_recap_title_month
                ReviewKind.YEAR -> R.string.share_card_recap_title_year
            }
        ),
        periodLabel = periodOf(review, details),
        hero = ShareCardHero(
            label = pluralStringResource(R.plurals.share_card_recap_hero, c.transactionCount),
            value = count(c.transactionCount),
        ),
        stats = stats,
        chart = chart,
    )
}

@Composable
private fun spendingCard(review: PeriodReview, c: CurrencyReview, details: Set<ShareDetail>): ShareCardState {
    val previous = c.previousSpent
    val amounts = ShareDetail.AMOUNTS in details
    val percents = ShareDetail.PERCENTAGES in details
    // Nothing spent before has no percent change, but still a difference in money.
    val change = percentChange(c.spent, previous)?.takeIf { percents }
    if (previous == null || (percents && change == null)) {
        return ShareCardState.Unavailable(stringResource(R.string.share_unavailable_compare))
    }
    val delta = c.spent - previous
    val direction = change?.let(Integer::signum) ?: delta.signum()
    val target = comparisonTarget(review.range)
    val drivers = if (ShareDetail.CATEGORIES in details) spendingDrivers(c, limit = chartRows(amounts)) else emptyList()
    val separator = stringResource(R.string.share_list_separator)

    val chart = if (drivers.isNotEmpty()) {
        val values = relative(drivers.map { (it.amount - (it.previous ?: BigDecimal.ZERO)).toDouble() })
        ShareChart(
            type = ShareChartType.DIVERGING,
            points = drivers.mapIndexed { i, line ->
                ShareChartPoint(
                    label = categoryLabel(line.name),
                    value = values[i],
                    display = when {
                        amounts -> signedMoney(line.amount - (line.previous ?: BigDecimal.ZERO), c.currency)
                        percents -> categoryChangePercent(line)?.let { signedPercent(it) }
                            ?: stringResource(R.string.share_card_category_new)
                        else -> null
                    },
                )
            },
        )
    } else null

    return ShareCardState.Ready(
        ShareCardContent(
            kind = ShareKind.SPENDING,
            title = stringResource(R.string.share_card_spending_title),
            subtitle = drivers.takeIf { it.isNotEmpty() }?.let { lines ->
                stringResource(R.string.share_card_spending_drivers, lines.take(2).map { categoryLabel(it.name) }.joinToString(separator))
            },
            periodLabel = periodOf(review, details),
            // Spending more is stated, not judged: the app can't know why it moved.
            hero = ShareCardHero(
                label = stringResource(
                    when {
                        direction > 0 -> R.string.share_card_spending_more
                        direction < 0 -> R.string.share_card_spending_less
                        else -> R.string.share_card_spending_same
                    },
                    target,
                ),
                value = change?.let { signedPercent(it) } ?: signedMoney(delta, c.currency),
                delta = if (percents && amounts && delta.signum() != 0) signedMoney(delta, c.currency) else null,
                sentiment = if (direction < 0) Sentiment.GOOD else Sentiment.NEUTRAL,
            ),
            stats = if (amounts) {
                listOf(
                    ShareCardStat(stringResource(R.string.share_card_stat_spent), money(c.spent, c.currency)),
                    ShareCardStat(stringResource(R.string.share_card_stat_before), money(previous, c.currency)),
                )
            } else emptyList(),
            chart = chart,
        )
    )
}

@Composable
private fun savingsCard(reviews: List<PeriodReview>, c: CurrencyReview, details: Set<ShareDetail>): ShareCardState {
    val review = reviews.first()
    val rate = c.savingsRatePercent() ?: return ShareCardState.Unavailable(
        // With income, no rate means spending ran past 100× it (see savingsRatePercent).
        stringResource(if (c.income.signum() > 0) R.string.share_unavailable_overspent else R.string.share_unavailable_income)
    )
    val amounts = ShareDetail.AMOUNTS in details
    val percents = ShareDetail.PERCENTAGES in details
    val previousRate = previousSavingsRate(c, reviews.getOrNull(1))
    val saved = c.income - c.spent
    val change = previousRate?.let { rate - it }
    // The trend's previous point is the whole period before, so a year so far is compared
    // with all of last year, not the same span of it.
    val target = if (review.range.kind == ReviewKind.YEAR) (review.range.start.year - 1).toString()
    else comparisonTarget(review.range)

    val trend = if (ShareDetail.CHART in details) savingsTrend(reviews, c.currency) else emptyList()
    val chart = if (trend.size >= 2) {
        val values = relative(trend.map { it.ratePercent.toDouble() })
        ShareChart(
            type = ShareChartType.LINE,
            points = trend.mapIndexed { i, point ->
                ShareChartPoint(
                    label = when (review.range.kind) {
                        ReviewKind.WEEK -> point.start.format(AppDates.pattern("d MMM"))
                        ReviewKind.MONTH ->
                            (if (isCalendarMonth(point.start, point.end)) point.start else point.end).format(AppDates.pattern("MMM"))
                        ReviewKind.YEAR -> point.start.year.toString()
                    },
                    value = values[i],
                    display = if (percents) percent(point.ratePercent) else null,
                )
            },
            highlightIndex = trend.indexOfLast { it.start == review.range.start }.takeIf { it >= 0 },
        )
    } else null

    return ShareCardState.Ready(
        ShareCardContent(
            kind = ShareKind.SAVINGS,
            title = stringResource(R.string.share_card_savings_title),
            // Said plainly when it fell, as when it rose.
            subtitle = change?.let {
                stringResource(
                    when {
                        it > 0 -> R.string.share_card_savings_up
                        it < 0 -> R.string.share_card_savings_down
                        else -> R.string.share_card_savings_same
                    },
                    target,
                )
            },
            periodLabel = periodOf(review, details),
            hero = ShareCardHero(
                label = stringResource(
                    when {
                        rate < 0 -> R.string.share_card_savings_negative
                        percents -> R.string.share_card_savings_hero
                        else -> R.string.share_card_savings_hero_amount
                    }
                ),
                value = if (percents) stringResource(R.string.share_percent, abs(rate)) else money(saved.abs(), c.currency),
                delta = if (percents && change != null) signedPoints(change) else null,
                sentiment = when {
                    change != null && change > 0 -> Sentiment.GOOD
                    change != null && change < 0 -> Sentiment.BAD
                    rate < 0 -> Sentiment.BAD
                    else -> Sentiment.NEUTRAL
                },
            ),
            stats = if (amounts) {
                listOfNotNull(
                    ShareCardStat(stringResource(R.string.share_card_stat_income), money(c.income, c.currency)),
                    ShareCardStat(stringResource(R.string.share_card_stat_spent), money(c.spent, c.currency)),
                    if (percents) {
                        ShareCardStat(
                            stringResource(R.string.share_card_stat_saved),
                            if (saved.signum() < 0) signedMoney(saved, c.currency) else money(saved, c.currency),
                        )
                    } else null,
                )
            } else emptyList(),
            chart = chart,
        )
    )
}

@Composable
private fun goalCard(goal: GoalProgress, details: Set<ShareDetail>): ShareCardContent {
    val amounts = ShareDetail.AMOUNTS in details
    val percents = ShareDetail.PERCENTAGES in details
    val currency = goal.goal.currency
    val heroValue = if (percents) stringResource(R.string.share_percent, goal.percent()) else money(goal.saved, currency)
    val pace: ShareCardStat? = if (ShareDetail.PACE in details) {
        val label = stringResource(R.string.share_card_stat_pace)
        when (val pill = goal.pill()) {
            GoalPill.Reached -> ShareCardStat(label, stringResource(R.string.share_card_pace_reached), Sentiment.GOOD)
            is GoalPill.MissedBy -> ShareCardStat(label, stringResource(R.string.share_card_pace_missed), Sentiment.BAD)
            is GoalPill.Behind -> ShareCardStat(
                label,
                if (amounts) stringResource(R.string.share_card_pace_behind_by, money(pill.amount, currency))
                else stringResource(R.string.share_card_pace_behind),
                Sentiment.BAD,
            )
            is GoalPill.PerMonth -> ShareCardStat(
                label,
                if (amounts) stringResource(R.string.share_card_pace_per_month, money(pill.amount, currency))
                else stringResource(R.string.share_card_pace_on_track),
                Sentiment.GOOD,
            )
            GoalPill.Archived -> null
        }
    } else null
    return ShareCardContent(
        kind = ShareKind.GOAL,
        title = if (ShareDetail.GOAL_NAME in details) goal.goal.name else stringResource(R.string.share_card_goal_title),
        periodLabel = if (ShareDetail.PERIOD in details) {
            stringResource(R.string.share_card_goal_due, goal.goal.dueDate.format(AppDates.pattern("MMM yyyy")))
                .uppercase(LocalConfiguration.current.locales[0])
        } else null,
        hero = ShareCardHero(
            label = if (amounts) stringResource(R.string.share_card_goal_of, money(goal.goal.targetAmount, currency))
            else stringResource(R.string.share_card_goal_saved),
            value = heroValue,
        ),
        stats = listOfNotNull(
            if (amounts && percents) ShareCardStat(stringResource(R.string.share_card_stat_saved), money(goal.saved, currency)) else null,
            pace,
        ),
        chart = if (ShareDetail.CHART in details) {
            ShareChart(ShareChartType.RING, listOf(ShareChartPoint("", goal.fraction().toDouble(), heroValue)))
        } else null,
    )
}

@Composable
private fun budgetCard(summary: BudgetOverallSummary, converted: Boolean, details: Set<ShareDetail>): ShareCardState {
    val result = budgetResult(summary, converted)
        ?: return ShareCardState.Unavailable(stringResource(R.string.share_unavailable_budget))
    val amounts = ShareDetail.AMOUNTS in details
    val percents = ShareDetail.PERCENTAGES in details
    val currency = result.currency
    val heroValue = if (percents) stringResource(R.string.share_percent, result.usedPercent) else money(result.spent, currency)
    val window = summary.pageWindow
    return ShareCardState.Ready(
        ShareCardContent(
            kind = ShareKind.BUDGET,
            title = stringResource(R.string.share_card_budget_title),
            subtitle = stringResource(if (result.isOver) R.string.share_card_budget_over else R.string.share_card_budget_under),
            periodLabel = if (ShareDetail.PERIOD in details) {
                (
                    if (isCalendarMonth(window.start, window.end)) window.start.format(AppDates.pattern("MMMM yyyy"))
                    else DateRangeUtils.formatDateRange(window.start, window.end)
                    ).uppercase(LocalConfiguration.current.locales[0])
            } else null,
            hero = ShareCardHero(
                label = when {
                    percents && amounts -> stringResource(R.string.share_card_budget_used_of, money(result.limit, currency))
                    percents -> stringResource(R.string.share_card_budget_used)
                    else -> stringResource(R.string.share_card_budget_spent_of, money(result.limit, currency))
                },
                value = heroValue,
                sentiment = if (result.isOver) Sentiment.BAD else Sentiment.GOOD,
            ),
            stats = listOfNotNull(
                if (amounts) {
                    if (result.isOver) {
                        ShareCardStat(stringResource(R.string.share_card_stat_over), money(result.difference, currency), Sentiment.BAD)
                    } else {
                        ShareCardStat(stringResource(R.string.share_card_stat_left), money(result.difference, currency), Sentiment.GOOD)
                    }
                } else null,
                if (result.total > 1) {
                    ShareCardStat(
                        stringResource(R.string.share_card_stat_kept),
                        stringResource(R.string.share_card_kept, result.kept, result.total),
                    )
                } else null,
            ),
            chart = if (ShareDetail.CHART in details) {
                ShareChart(
                    ShareChartType.RING,
                    listOf(ShareChartPoint("", (result.usedPercent / 100.0).coerceIn(0.0, 1.0), heroValue)),
                )
            } else null,
        )
    )
}

/**
 * One achievement is a medal of its own; several are a set under how many there are.
 * Names and descriptions say what was done, never with an amount, so there is nothing
 * here for the amount switches to hide.
 */
@Composable
private fun achievementsCard(earned: List<Achievement>, picked: Set<String>?, details: Set<ShareDetail>): ShareCardState {
    if (earned.isEmpty()) return ShareCardState.Unavailable(stringResource(R.string.share_unavailable_achievements))
    val shown = selectedAchievements(earned, picked)
    if (shown.isEmpty()) return ShareCardState.Unavailable(stringResource(R.string.share_unavailable_achievements_none))
    val single = shown.singleOrNull()
    // Only the single card has room to print its date; one that isn't drawn isn't sent.
    val dated = single != null && ShareDetail.PERIOD in details
    val badges = shown.map {
        ShareAchievement(
            id = it.id.key,
            title = achievementName(it.id),
            description = achievementDescription(it),
            tier = it.tier,
            unlockedOn = if (dated) it.unlockedOn else null,
        )
    }
    val content = if (single != null) {
        ShareCardContent(
            kind = ShareKind.ACHIEVEMENTS,
            title = stringResource(R.string.share_card_achievement_title),
            subtitle = tierLabel(single),
            periodLabel = single.unlockedOn?.takeIf { dated }
                ?.format(AppDates.pattern("d MMM yyyy"))?.uppercase(LocalConfiguration.current.locales[0]),
            achievements = badges,
        )
    } else {
        ShareCardContent(
            kind = ShareKind.ACHIEVEMENTS,
            title = stringResource(R.string.share_card_achievements_title),
            hero = ShareCardHero(
                label = pluralStringResource(R.plurals.share_card_achievements_hero, shown.size),
                value = count(shown.size),
            ),
            achievements = badges,
        )
    }
    return ShareCardState.Ready(content)
}
