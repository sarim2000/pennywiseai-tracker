package com.pennywiseai.tracker.domain.goals

import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.domain.review.PeriodDetail
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.spending.CycleAverages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

/** The goals in progress, and every goal's contributions. */
data class GoalsToWeigh(val goals: List<GoalProgress>, val contributions: List<GoalContributionEntity>) {
    /** The contributions taken from a transaction. */
    val linked: List<GoalContributionEntity> get() = contributions.filter { it.transactionId != null }
}

/**
 * Loads [GoalAffordability] for the Goals list and Insights. Goals have no profile,
 * so they're weighed against every profile's surplus (roadmap Decision 3): nothing is
 * weighed while one profile is selected, when the history covers only that profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalAffordabilityLoader @Inject constructor(
    private val goalRepository: GoalRepository,
    private val periodReviewLoader: PeriodReviewLoader,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    /**
     * The goals in progress, or null when there's nothing to weigh: none in progress,
     * or one profile selected.
     */
    fun goalsToWeigh(today: LocalDate): Flow<GoalsToWeigh?> = combine(
        goalRepository.observeActiveGoals(today),
        goalRepository.observeContributions(),
        userPreferencesRepository.selectedProfileId,
    ) { goals, contributions, profileId ->
        val inProgress = goals.filter { it.status == GoalStatus.IN_PROGRESS }
        if (profileId != null || inProgress.isEmpty()) null else GoalsToWeigh(inProgress, contributions)
    }.distinctUntilChanged()

    /** Loads the cycles behind it too, and only while there's something to weigh. */
    fun observe(today: LocalDate = LocalDate.now()): Flow<List<GoalAffordability>> =
        goalsToWeigh(today).flatMapLatest { goals ->
            if (goals == null) {
                flowOf(emptyList())
            } else {
                periodReviewLoader.observe(ReviewKind.MONTH, 0, today, detail = true)
                    .map { affordability(goals, it.detail) }
                    // Goals still show if the history can't load, just without this line.
                    .catch { emit(emptyList()) }
            }
        }

    companion object {
        /** [goals] from [goalsToWeigh]; [detail] from a review of every profile. */
        fun affordability(goals: GoalsToWeigh?, detail: PeriodDetail?): List<GoalAffordability> =
            if (goals == null || detail == null) {
                emptyList()
            } else {
                GoalAffordability.of(
                    goals.goals,
                    CycleAverages.of(detail.cycles),
                    GoalAffordability.savedThroughSpending(detail, goals.linked),
                )
            }
    }
}
