package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** How far back the cycle rules look: two years of reviews is plenty to have earned them in. */
internal const val ACHIEVEMENT_CYCLES = 24

/** How far back budget months are read; a month's summary is a query of its own. */
internal const val ACHIEVEMENT_BUDGET_MONTHS = 12

/**
 * Gathers what the achievement rules read, over the same rows as Home and the reviews
 * (selected profile, no ignored accounts), and works them out. Nothing is stored.
 */
class AchievementLoader @Inject constructor(
    private val reviewLoader: PeriodReviewLoader,
    private val goalRepository: GoalRepository,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val subscriptionRepository: SubscriptionRepository,
) {
    suspend fun load(today: LocalDate = LocalDate.now()): List<Achievement> = coroutineScope {
        val recorded = reviewLoader.recordedDates(today)
        // Only months since the first record can have earned anything.
        val months = recorded.firstOrNull()
            ?.let { ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(today)).toInt() + 1 } ?: 0
        val cycles = async { reviewLoader.finishedCycles(months.coerceAtMost(ACHIEVEMENT_CYCLES), today) }
        val goals = async { goalRepository.observeGoalDetails(today).first() }
        val budgets = async { budgetMonths(months.coerceAtMost(ACHIEVEMENT_BUDGET_MONTHS), today, recorded) }
        val subscriptions = async {
            subscriptionRepository.getAllSubscriptions().first()
                .filter { it.state != SubscriptionState.HIDDEN && it.direction == SubscriptionDirection.EXPENSE }
                .map { it.createdAt.toLocalDate() }
        }
        achievementsOf(
            AchievementInputs(recorded, cycles.await(), goals.await(), budgets.await(), subscriptions.await()),
            today,
        )
    }

    /**
     * The finished months before this one, oldest first, as the Budgets screen shows them,
     * judged by [budgetMonthOf].
     */
    private suspend fun budgetMonths(count: Int, today: LocalDate, recorded: List<LocalDate>): List<BudgetMonth> = coroutineScope {
        // Each month is a query of its own; read them side by side.
        (count downTo 1).map { back ->
            async {
                val month = YearMonth.from(today).minusMonths(back.toLong())
                budgetMonthOf(budgetSummaryProvider.summary(month).first(), month.atEndOfMonth(), recorded)
            }
        }.awaitAll()
    }
}
