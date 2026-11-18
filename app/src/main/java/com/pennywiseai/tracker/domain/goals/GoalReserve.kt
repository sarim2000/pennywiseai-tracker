package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The goals in progress, and every goal's effective contributions. */
data class GoalsToReserve(val goals: List<GoalProgress>, val contributions: List<GoalContributionEntity>)

/**
 * Loads what [GoalReserve] works from. Goals have no profile, so they're set aside
 * against every profile's money: nothing is while one profile is selected.
 */
class GoalReserveLoader @Inject constructor(
    private val goalRepository: GoalRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    /** The goals in progress, or null when none are, or one profile is selected. */
    fun observe(today: LocalDate): Flow<GoalsToReserve?> = combine(
        goalRepository.observeActiveGoals(today),
        goalRepository.observeContributions(),
        userPreferencesRepository.selectedProfileId,
    ) { goals, contributions, profileId ->
        val inProgress = goals.filter { it.status == GoalStatus.IN_PROGRESS }
        if (profileId != null || inProgress.isEmpty()) null else GoalsToReserve(inProgress, contributions)
    }.distinctUntilChanged()
}

/**
 * What Safe to spend sets aside for goals, when the user asks it to: what's left
 * of each goal in progress's monthly saving (its [GoalProgress.perMonth], less
 * what was put in this cycle), for the share of the budget cycle still to run.
 * Per goal currency; whole units, rounded up so it never sets aside too little.
 */
object GoalReserve {

    /**
     * [cycleStart]..[cycleEnd] is the current budget cycle; [today] is inside it.
     * [contributions] are the goals' effective ones; those dated this cycle, up to
     * [today], already saved part of the month's amount.
     */
    fun of(
        goals: List<GoalProgress>,
        contributions: List<GoalContributionEntity>,
        today: LocalDate,
        cycleStart: LocalDate,
        cycleEnd: LocalDate,
    ): List<Money> {
        val cycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1
        val daysLeft = (ChronoUnit.DAYS.between(today, cycleEnd) + 1).coerceIn(0, cycleDays)
        if (cycleDays <= 0 || daysLeft == 0L) return emptyList()
        val savedThisCycle = contributions.filter { !it.date.isBefore(cycleStart) && !it.date.isAfter(today) }
            .groupBy { it.goalId }
            .mapValues { (_, c) -> c.fold(BigDecimal.ZERO) { sum, it -> sum + it.amount } }
        return goals.filter { it.status == GoalStatus.IN_PROGRESS }
            .sumByCurrency({ it.goal.currency }) { g ->
                val saved = (savedThisCycle[g.goal.id] ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
                val rest = (g.perMonth - saved).max(BigDecimal.ZERO)
                rest.multiply(BigDecimal(daysLeft)).divide(BigDecimal(cycleDays), 0, RoundingMode.UP)
            }
            .values.filter { it.amount.signum() > 0 }
    }
}
