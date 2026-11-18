package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalDetail
import java.time.LocalDate
import java.time.YearMonth

// Achievements are worked out from what the app already stores, each time they're shown:
// nothing new is saved, so there is nothing to back up or migrate, and editing the
// history that earned one changes it too. Goals are counted, never their amounts added.

/**
 * The achievements and the thresholds of their tiers, in each rule's own unit (a count,
 * a number of months). [key] is the share contract's `id`: never rename it.
 */
enum class AchievementId(val key: String, val thresholds: List<Int>) {
    /** Transactions recorded. */
    TRACKED("transactions_tracked", listOf(100, 500, 1_000, 5_000)),

    /** Calendar months in a row with at least one transaction. */
    STREAK("tracking_streak", listOf(3, 6, 12, 24)),

    /** Goals whose saved total reached their target. */
    GOALS_REACHED("goals_reached", listOf(1, 3, 5));

    companion object {
        fun fromKey(key: String?): AchievementId? = entries.firstOrNull { it.key == key }
    }
}

data class Achievement(
    val id: AchievementId,
    /** Tiers earned, 0 while locked. */
    val tier: Int,
    /**
     * Where the user stands against the next tier, in the rule's unit: a running total or
     * the current streak.
     */
    val progress: Int,
    /** The day the highest earned tier was earned; null while locked. */
    val unlockedOn: LocalDate?,
) {
    val earned: Boolean get() = tier > 0
    val maxTier: Int get() = id.thresholds.size

    /** The next tier's threshold; null once every tier is earned. */
    val nextThreshold: Int? get() = id.thresholds.getOrNull(tier)

    /** The threshold of the tier earned, or of the first one while locked. */
    val threshold: Int get() = id.thresholds[(tier - 1).coerceAtLeast(0)]

    val fractionToNext: Float
        get() = nextThreshold?.let { (progress.toFloat() / it).coerceIn(0f, 1f) } ?: 1f
}

/** Earned first, newest first; then the ones closest to earning. */
fun List<Achievement>.forDisplay(): List<Achievement> =
    filter { it.earned }.sortedWith(compareByDescending<Achievement> { it.unlockedOn }.thenBy { it.id.ordinal }) +
        filterNot { it.earned }.sortedWith(compareByDescending<Achievement> { it.fractionToNext }.thenBy { it.id.ordinal })

/**
 * Tiers from a dated history of values, oldest first: each tier unlocks on the first day
 * its threshold was met. Thresholds rise, so the first day a higher one was met is never
 * before a lower one's.
 */
private fun fromTimeline(id: AchievementId, timeline: List<Pair<LocalDate, Int>>, progress: Int): Achievement {
    val unlocks = id.thresholds.map { threshold -> timeline.firstOrNull { it.second >= threshold }?.first }
    val tier = unlocks.takeWhile { it != null }.size
    return Achievement(id, tier, progress, unlocks.getOrNull(tier - 1))
}

/** A running count over [dates]: the Nth one is the day the count reached N. */
private fun countOf(id: AchievementId, dates: List<LocalDate>): Achievement {
    val sorted = dates.sorted()
    return fromTimeline(id, sorted.mapIndexed { i, day -> day to i + 1 }, sorted.size)
}

/** [recorded]: the day of every transaction. */
fun trackedAchievement(recorded: List<LocalDate>): Achievement = countOf(AchievementId.TRACKED, recorded)

/**
 * Months in a row with something recorded. A run reaches each length on its month's first
 * record. The current streak survives a month that has only just started.
 */
fun streakAchievement(recorded: List<LocalDate>, today: LocalDate): Achievement {
    val firstOfMonth = recorded.groupBy(YearMonth::from).mapValues { (_, days) -> days.min() }.toSortedMap()
    val timeline = mutableListOf<Pair<LocalDate, Int>>()
    var run = 0
    var last: YearMonth? = null
    for ((month, first) in firstOfMonth) {
        run = if (last != null && month == last.plusMonths(1)) run + 1 else 1
        last = month
        timeline += first to run
    }
    val now = YearMonth.from(today)
    val current = if (last == now || last == now.minusMonths(1)) run else 0
    return fromTimeline(AchievementId.STREAK, timeline, current)
}

/**
 * The day [goal]'s saved total first reached its target, replaying its contributions in
 * order; null if it never has. A withdrawal later doesn't undo it, and a contribution
 * dated after [today] hasn't happened yet.
 */
internal fun dateReached(goal: GoalEntity, contributions: List<GoalContributionEntity>, today: LocalDate): LocalDate? {
    val needed = goal.targetAmount
    if (needed.signum() <= 0) return null
    var saved = goal.alreadySaved
    if (saved >= needed) return goal.createdAt.toLocalDate()
    for (c in contributions.filter { !it.date.isAfter(today) }.sortedWith(compareBy({ it.date }, { it.id }))) {
        saved += c.amount
        if (saved >= needed) return c.date
    }
    return null
}

/** Each goal in its own currency; goals are counted, never added up. */
fun goalsReachedAchievement(goals: List<GoalDetail>, today: LocalDate): Achievement =
    countOf(AchievementId.GOALS_REACHED, goals.mapNotNull { dateReached(it.progress.goal, it.contributions, today) })

/** Every achievement, in [AchievementId] order. [recorded]: the day of every transaction. */
fun achievementsOf(recorded: List<LocalDate>, goals: List<GoalDetail>, today: LocalDate): List<Achievement> = listOf(
    trackedAchievement(recorded),
    streakAchievement(recorded, today),
    goalsReachedAchievement(goals, today),
)
