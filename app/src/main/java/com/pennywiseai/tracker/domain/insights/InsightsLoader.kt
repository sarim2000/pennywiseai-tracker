package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.InsightPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The current cycle as the Insights page shows it. */
data class InsightsSnapshot(
    /** The cycle so far, with [PeriodReview.detail]. */
    val review: PeriodReview,
    /** Every insight, dismissed ones included, most severe first. */
    val insights: List<Insight>,
    val dismissed: Set<String>,
    /** Per currency; a currency with too little data has none. Dismissals don't touch it. */
    val scores: Map<String, PeriodScore> = emptyMap(),
) {
    val active: List<Insight> get() = insights.filter { it.id !in dismissed }
}

/**
 * Gathers [InsightInputs] from the sources the rest of the app already trusts: the
 * Period Review rows (profile and ignored accounts applied), the Budgets screen's
 * summary, subscriptions and card balances. Nothing leaves the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsLoader @Inject constructor(
    private val periodReviewLoader: PeriodReviewLoader,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val insightPreferences: InsightPreferences,
) {
    fun observe(today: LocalDate = LocalDate.now()): Flow<InsightsSnapshot> = combine(
        periodReviewLoader.observe(ReviewKind.MONTH, 0, today, detail = true),
        budgets(today),
        subscriptionRepository.getActiveSubscriptions(),
        creditCards(),
        insightPreferences.dismissedInsights,
    ) { review, budgets, subscriptions, cards, dismissed ->
        val inputs = InsightInputs(today, review, budgets, subscriptions, cards)
        InsightsSnapshot(review, InsightRules.evaluate(inputs), dismissed, scores(review, budgets))
    }

    private fun scores(review: PeriodReview, budgets: List<BudgetStatus>): Map<String, PeriodScore> {
        val uncategorised = BuiltinCategoryNames.current("Others")
        val spends = review.detail?.spendRows.orEmpty().map { it.transaction }
            .filter { it.dateTime.toLocalDate() in review.range.start..review.range.end }
            .groupBy { it.currency }
        return review.currencies.mapNotNull { c ->
            PeriodScore.of(c.currency, c.income, c.spent, budgets, spends[c.currency].orEmpty()) {
                it.isBlank() || it == uncategorised
            }
        }.associateBy { it.currency }
    }

    /**
     * Budgets span every profile, so with one profile selected they'd sit beside
     * spending they don't match: left out then, as the evening recap does.
     */
    private fun budgets(today: LocalDate): Flow<List<BudgetStatus>> =
        userPreferencesRepository.selectedProfileId.distinctUntilChanged().flatMapLatest { profileId ->
            if (profileId != null) {
                flowOf(emptyList())
            } else {
                budgetSummaryProvider.summary(YearMonth.from(today), flagUncounted = true)
                    .map { budgetStatuses(it, today) }
                    // Budget figures that can't load mustn't cost the user every other insight.
                    .catch { emit(emptyList()) }
            }
        }

    private fun creditCards(): Flow<List<AccountBalanceEntity>> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
    ) { balances, ignored, profileId ->
        balances.filter {
            it.isCreditCard &&
                !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                (profileId == null || it.profileId == profileId)
        }
    }
}

/** Each LIMIT budget running today, in the summary's one currency. */
internal fun budgetStatuses(summary: BudgetOverallSummary, today: LocalDate): List<BudgetStatus> =
    summary.groups
        .filter {
            it.group.budget.groupType == BudgetGroupType.LIMIT &&
                !today.isBefore(it.windowStart) && !today.isAfter(it.windowEnd)
        }
        .map {
            BudgetStatus(
                id = it.group.budget.id,
                name = it.group.budget.name,
                currency = summary.currency,
                spent = it.totalActual,
                limit = it.totalBudget,
                daysElapsed = it.daysElapsed,
                windowDays = it.windowDays,
                windowEnd = it.windowEnd,
                incomplete = it.hasUnconvertedSpend,
            )
        }
