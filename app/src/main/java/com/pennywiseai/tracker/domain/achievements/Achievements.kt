package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.repository.GoalDetail
import com.pennywiseai.tracker.domain.goals.percent
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.percentChange
import com.pennywiseai.tracker.domain.share.savingsRatePercent
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// Achievements are worked out from what the app already stores, each time they're shown:
// nothing new is saved, so there is nothing to back up or migrate, and editing the
// history that earned one changes it too. Money rules look at one currency at a time.

/**
 * The achievements and the thresholds of their tiers, in each rule's own unit (a count,
 * a number of months, a whole percent). [key] is the share contract's `id`: never rename it.
 */
enum class AchievementId(val key: String, val thresholds: List<Int>) {
    /** Transactions recorded. */
    TRACKED("transactions_tracked", listOf(100, 500, 1_000, 5_000)),

    /** Calendar months in a row with at least one transaction. */
    STREAK("tracking_streak", listOf(3, 6, 12, 24)),

    /** Subscriptions found (or added), hidden ones aside. */
    SUBSCRIPTIONS("subscriptions_found", listOf(1, 5, 10)),

    /** A goal's saved total reached half its target. */
    GOAL_HALFWAY("goal_halfway", listOf(50)),

    /** Goals whose saved total reached their target. */
    GOALS_REACHED("goals_reached", listOf(1, 3, 5)),

    /** Finished months in a row under every spending limit. */
    BUDGET_KEPT("budget_kept", listOf(1, 3, 6, 12)),

    /** Share of a cycle's income kept, (income − spent) / income, in one currency. */
    SAVINGS_RATE("savings_rate", listOf(20, 30, 50)),

    /** Savings rate up on the cycle before, in percentage points, in one currency. */
    SAVINGS_UP("savings_rate_up", listOf(1)),

    /** Spending down on the cycle before, in whole percent, in one currency. */
    SPENDING_DOWN("spending_down", listOf(10));

    companion object {
        fun fromKey(key: String?): AchievementId? = entries.firstOrNull { it.key == key }
    }
}

data class Achievement(
    val id: AchievementId,
    /** Tiers earned, 0 while locked. */
    val tier: Int,
    /**
     * Where the user stands against the next tier, in the rule's unit: a running total, a
     * current streak or a best rate, whichever can still reach it.
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

/** [found]: the day each subscription was first recorded. */
fun subscriptionsAchievement(found: List<LocalDate>): Achievement = countOf(AchievementId.SUBSCRIPTIONS, found)

/**
 * The day [goal]'s saved total first reached [percent] of its target, replaying its
 * contributions in order; null if it never has. A withdrawal later doesn't undo it.
 */
internal fun dateReaching(goal: GoalEntity, contributions: List<GoalContributionEntity>, percent: Int): LocalDate? {
    if (goal.targetAmount.signum() <= 0) return null
    val needed = goal.targetAmount.multiply(BigDecimal(percent)).divide(BigDecimal(100))
    var saved = goal.alreadySaved
    if (saved >= needed) return goal.createdAt.toLocalDate()
    for (c in contributions.sortedWith(compareBy({ it.date }, { it.id }))) {
        saved += c.amount
        if (saved >= needed) return c.date
    }
    return null
}

/** Each goal in its own currency; goals are counted, never added up. */
fun goalHalfwayAchievement(goals: List<GoalDetail>): Achievement {
    val first = goals.mapNotNull { dateReaching(it.progress.goal, it.contributions, 50) }.minOrNull()
    val best = goals.maxOfOrNull { it.progress.percent() } ?: 0
    return fromTimeline(AchievementId.GOAL_HALFWAY, listOfNotNull(first?.let { it to 50 }), best.coerceIn(0, 100))
}

fun goalsReachedAchievement(goals: List<GoalDetail>): Achievement =
    countOf(AchievementId.GOALS_REACHED, goals.mapNotNull { dateReaching(it.progress.goal, it.contributions, 100) })

/** One finished month of spending limits: [kept] when every limit ended at or under its amount, null with none set. */
data class BudgetMonth(val end: LocalDate, val kept: Boolean?)

/** [months] must be consecutive; a month without limits breaks the run. */
fun budgetKeptAchievement(months: List<BudgetMonth>): Achievement {
    val timeline = mutableListOf<Pair<LocalDate, Int>>()
    var run = 0
    for (month in months.sortedBy { it.end }) {
        run = if (month.kept == true) run + 1 else 0
        if (run > 0) timeline += month.end to run
    }
    return fromTimeline(AchievementId.BUDGET_KEPT, timeline, run)
}

/** [cycles]: finished budget cycles, any order. Each currency's rate is its own; the best one counts. */
fun savingsRateAchievement(cycles: List<PeriodReview>): Achievement {
    val timeline = cycles.sortedBy { it.range.start }.flatMap { cycle ->
        cycle.currencies.mapNotNull { c -> c.savingsRatePercent()?.let { cycle.range.end to it } }
    }
    return fromTimeline(AchievementId.SAVINGS_RATE, timeline, (timeline.maxOfOrNull { it.second } ?: 0).coerceAtLeast(0))
}

/** A cycle's rate against the cycle just before it, in the same currency. */
fun savingsUpAchievement(cycles: List<PeriodReview>): Achievement {
    val timeline = consecutive(cycles).flatMap { (previous, cycle) ->
        cycle.currencies.mapNotNull { c ->
            val rate = c.savingsRatePercent() ?: return@mapNotNull null
            val before = previous.currencies.firstOrNull { it.currency == c.currency }?.savingsRatePercent()
                ?: return@mapNotNull null
            cycle.range.end to rate - before
        }
    }
    return fromTimeline(AchievementId.SAVINGS_UP, timeline, (timeline.maxOfOrNull { it.second } ?: 0).coerceAtLeast(0))
}

/**
 * Spending against the cycle before, in the same currency, where the review found the
 * cycle before comparable. A currency with no spending this cycle doesn't count: it
 * wasn't spent less, it went unused.
 */
fun spendingDownAchievement(cycles: List<PeriodReview>): Achievement {
    val timeline = cycles.sortedBy { it.range.start }.flatMap { cycle ->
        cycle.currencies.mapNotNull { c ->
            if (c.spent.signum() <= 0) return@mapNotNull null
            percentChange(c.spent, c.previousSpent)?.let { cycle.range.end to -it }
        }
    }
    return fromTimeline(AchievementId.SPENDING_DOWN, timeline, (timeline.maxOfOrNull { it.second } ?: 0).coerceAtLeast(0))
}

private fun consecutive(cycles: List<PeriodReview>): List<Pair<PeriodReview, PeriodReview>> =
    cycles.sortedBy { it.range.start }.zipWithNext().filter { (a, b) -> a.range.end.plusDays(1) == b.range.start }

/** Everything the rules read, gathered by [AchievementLoader]. */
data class AchievementInputs(
    val recorded: List<LocalDate>,
    val cycles: List<PeriodReview>,
    val goals: List<GoalDetail>,
    val budgetMonths: List<BudgetMonth>,
    val subscriptions: List<LocalDate>,
)

/** Every achievement, in [AchievementId] order. */
fun achievementsOf(inputs: AchievementInputs, today: LocalDate): List<Achievement> = listOf(
    trackedAchievement(inputs.recorded),
    streakAchievement(inputs.recorded, today),
    subscriptionsAchievement(inputs.subscriptions),
    goalHalfwayAchievement(inputs.goals),
    goalsReachedAchievement(inputs.goals),
    budgetKeptAchievement(inputs.budgetMonths),
    savingsRateAchievement(inputs.cycles),
    savingsUpAchievement(inputs.cycles),
    spendingDownAchievement(inputs.cycles),
)
