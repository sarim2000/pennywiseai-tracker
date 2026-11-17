package com.pennywiseai.tracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.achievements.Achievement
import com.pennywiseai.tracker.domain.achievements.AchievementId
import com.pennywiseai.tracker.utils.formatCount

@Composable
fun achievementName(id: AchievementId): String = stringResource(
    when (id) {
        AchievementId.TRACKED -> R.string.achievement_tracked_name
        AchievementId.STREAK -> R.string.achievement_streak_name
        AchievementId.GOALS_REACHED -> R.string.achievement_goals_reached_name
    }
)

/** What the tier at [threshold] asks for, said as done: "500 transactions tracked". */
@Composable
fun achievementDescription(id: AchievementId, threshold: Int): String {
    val n = formatCount(threshold)
    return when (id) {
        AchievementId.TRACKED -> pluralStringResource(R.plurals.achievement_tracked_desc, threshold, n)
        AchievementId.STREAK -> pluralStringResource(R.plurals.achievement_streak_desc, threshold, n)
        AchievementId.GOALS_REACHED -> pluralStringResource(R.plurals.achievement_goals_reached_desc, threshold, n)
    }
}

/** The tier earned, or while locked the first one: what the badge stands for. */
@Composable
fun achievementDescription(a: Achievement): String = achievementDescription(a.id, a.threshold)

/** "Tier 2 of 4"; null for an achievement with a single tier. */
@Composable
fun tierLabel(a: Achievement): String? =
    if (a.maxTier > 1 && a.earned) stringResource(R.string.achievements_tier, a.tier, a.maxTier) else null

/** "120 / 500". */
@Composable
fun progressLabel(a: Achievement): String? {
    val next = a.nextThreshold ?: return null
    return stringResource(R.string.achievements_progress, formatCount(a.progress.coerceAtMost(next)), formatCount(next))
}
