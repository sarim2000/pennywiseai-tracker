package com.pennywiseai.tracker.domain.achievements

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.GoalRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** How many budget months are summarised at once. */
private const val BUDGET_MONTHS_AT_ONCE = 4

/**
 * Gathers what the achievement rules read, over the same rows as Home and the reviews
 * (selected profile, no ignored accounts), except budgets, which are judged exactly as the
 * Budgets screen shows them (base-currency limits, every account), so a badge never
 * disagrees with the screen it's about. Nothing is stored: badges
 * describe the data as it is now, so turning off a budget or deleting a goal can take one back.
 */
class AchievementLoader @Inject constructor(
    private val reviewLoader: PeriodReviewLoader,
    private val goalRepository: GoalRepository,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val subscriptionRepository: SubscriptionRepository,
) {
    // Off the main thread: with every month read, the rules work over years of rows.
    suspend fun load(today: LocalDate = LocalDate.now()): List<Achievement> = withContext(Dispatchers.Default) {
        val recorded = reviewLoader.recordedDates(today)
        // Every month since the first record: nothing is stored, so a tier earned in an older
        // month would be lost the moment a shorter window stopped reaching it.
        // ponytail: one budget summary per month, a few at a time; persist earned tiers if years of history make this slow.
        val months = recorded.firstOrNull()
            ?.let { ChronoUnit.MONTHS.between(YearMonth.from(it), YearMonth.from(today)).toInt() + 1 } ?: 0
        val cycles = async { reviewLoader.finishedCycles(months, today) }
        val goals = async { goalRepository.observeGoalDetails(today).first() }
        val budgets = async { budgetMonths(months, today, recorded) }
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
        // Each month is a summary of its own: a few side by side, not years of them at once.
        val reading = Semaphore(BUDGET_MONTHS_AT_ONCE)
        (count downTo 1).map { back ->
            async {
                reading.withPermit {
                    val month = YearMonth.from(today).minusMonths(back.toLong())
                    budgetMonthOf(budgetSummaryProvider.summary(month).first(), month.atEndOfMonth(), recorded)
                }
            }
        }.awaitAll()
    }
}
