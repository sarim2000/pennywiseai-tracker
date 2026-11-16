package com.pennywiseai.tracker.presentation.achievements

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.achievements.Achievement
import com.pennywiseai.tracker.domain.achievements.AchievementId
import com.pennywiseai.tracker.presentation.share.count

@Composable
fun achievementName(id: AchievementId): String = stringResource(
    when (id) {
        AchievementId.TRACKED -> R.string.achievement_tracked_name
        AchievementId.STREAK -> R.string.achievement_streak_name
        AchievementId.SUBSCRIPTIONS -> R.string.achievement_subscriptions_name
        AchievementId.GOAL_HALFWAY -> R.string.achievement_goal_halfway_name
        AchievementId.GOALS_REACHED -> R.string.achievement_goals_reached_name
        AchievementId.BUDGET_KEPT -> R.string.achievement_budget_kept_name
        AchievementId.SAVINGS_RATE -> R.string.achievement_savings_rate_name
        AchievementId.SAVINGS_UP -> R.string.achievement_savings_up_name
        AchievementId.SPENDING_DOWN -> R.string.achievement_spending_down_name
    }
)

/** What the tier at [threshold] asks for, said as done: "500 transactions tracked". */
@Composable
fun achievementDescription(id: AchievementId, threshold: Int): String {
    val n = count(threshold)
    return when (id) {
        AchievementId.TRACKED -> pluralStringResource(R.plurals.achievement_tracked_desc, threshold, n)
        AchievementId.STREAK -> pluralStringResource(R.plurals.achievement_streak_desc, threshold, n)
        AchievementId.SUBSCRIPTIONS -> pluralStringResource(R.plurals.achievement_subscriptions_desc, threshold, n)
        AchievementId.GOAL_HALFWAY -> stringResource(R.string.achievement_goal_halfway_desc)
        AchievementId.GOALS_REACHED -> pluralStringResource(R.plurals.achievement_goals_reached_desc, threshold, n)
        AchievementId.BUDGET_KEPT -> pluralStringResource(R.plurals.achievement_budget_kept_desc, threshold, n)
        AchievementId.SAVINGS_RATE -> stringResource(R.string.achievement_savings_rate_desc, percentText(threshold))
        AchievementId.SAVINGS_UP -> stringResource(R.string.achievement_savings_up_desc)
        AchievementId.SPENDING_DOWN -> stringResource(R.string.achievement_spending_down_desc, percentText(threshold))
    }
}

/** The tier earned, or while locked the first one: what the badge stands for. */
@Composable
fun achievementDescription(a: Achievement): String = achievementDescription(a.id, a.threshold)

/** "Tier 2 of 4"; null for an achievement with a single tier. */
@Composable
fun tierLabel(a: Achievement): String? =
    if (a.maxTier > 1 && a.earned) stringResource(R.string.achievements_tier, a.tier, a.maxTier) else null

/** "120 / 500", or "18% / 20%" for a rule counted in percent. */
@Composable
fun progressLabel(a: Achievement): String? {
    val next = a.nextThreshold ?: return null
    val inPercent = a.id == AchievementId.SAVINGS_RATE || a.id == AchievementId.SPENDING_DOWN || a.id == AchievementId.GOAL_HALFWAY
    // Points up are a yes or no: the progress figure would only ever read "0 / 1".
    if (a.id == AchievementId.SAVINGS_UP) return null
    val format: @Composable (Int) -> String = { if (inPercent) percentText(it) else count(it) }
    return stringResource(R.string.achievements_progress, format(a.progress.coerceAtMost(next)), format(next))
}

@Composable
private fun percentText(value: Int): String = stringResource(R.string.share_percent, value)
