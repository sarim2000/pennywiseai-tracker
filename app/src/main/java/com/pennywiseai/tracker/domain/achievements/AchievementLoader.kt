package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * Gathers what the achievement rules read, over the same rows as Home and the reviews
 * (selected profile, no ignored accounts). Nothing is stored: badges describe the data
 * as it is now, so deleting a goal can take one back.
 */
class AchievementLoader @Inject constructor(
    private val reviewLoader: PeriodReviewLoader,
    private val goalRepository: GoalRepository,
) {
    // Off the main thread: the rules work over every transaction's day.
    suspend fun load(today: LocalDate = LocalDate.now()): List<Achievement> = withContext(Dispatchers.Default) {
        achievementsOf(reviewLoader.recordedDates(today), goalRepository.observeGoalDetails(today).first(), today)
    }
}
