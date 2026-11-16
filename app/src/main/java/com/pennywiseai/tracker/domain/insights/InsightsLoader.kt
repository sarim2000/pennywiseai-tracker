package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.InsightPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.data.repository.BudgetSummaryProvider
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.goals.GoalAffordabilityLoader
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.spending.CycleAverages
import com.pennywiseai.tracker.domain.spending.Runway
import com.pennywiseai.tracker.utils.Money
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
    /** What active subscriptions cost a month, per currency. */
    val subscriptionsMonthly: Map<String, Money> = emptyMap(),
    /** How long the money held would last, per currency with enough history. */
    val runways: Map<String, Runway> = emptyMap(),
) {
    val active: List<Insight> get() = insights.filter { it.id !in dismissed }
}

/**
 * Gathers [InsightInputs] from the sources the rest of the app already trusts: the
 * Period Review rows (profile and ignored accounts applied), the Budgets screen's
 * summary, and subscriptions and account balances scoped as those rows are. Nothing
 * leaves the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsLoader @Inject constructor(
    private val periodReviewLoader: PeriodReviewLoader,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val goalAffordabilityLoader: GoalAffordabilityLoader,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val insightPreferences: InsightPreferences,
) {
    fun observe(today: LocalDate = LocalDate.now()): Flow<InsightsSnapshot> = combine(
        periodReviewLoader.observe(ReviewKind.MONTH, 0, today, detail = true),
        budgets(today),
        combine(subscriptions(), goalAffordabilityLoader.goalsToWeigh(today), ::Pair),
        accounts(),
        combine(insightPreferences.dismissedInsights, userPreferencesRepository.selectedProfileId, ::Pair),
    ) { review, budgets, (subscriptions, goals), (accounts, unanchored), (dismissed, profileId) ->
        val averages = CycleAverages.of(review.detail?.cycles.orEmpty())
        val runways = Runway.calculate(accounts, averages, unanchored)
        val inputs = InsightInputs(
            today, review, budgets, subscriptions, accounts.filter { it.isCreditCard }, profileId,
            runways = runways,
            goals = GoalAffordabilityLoader.affordability(goals, review.detail),
        )
        InsightsSnapshot(
            review, InsightRules.evaluate(inputs), dismissed, scores(review, budgets),
            InsightRules.subscriptionsMonthly(subscriptions), runways.associateBy { it.currency },
        )
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

    private fun subscriptions(): Flow<List<SubscriptionEntity>> = combine(
        subscriptionRepository.getActiveSubscriptions(),
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
        accountBalanceRepository.getAllLatestBalances(),
        ::scopedSubscriptions,
    )

    /**
     * The active profile's accounts the user hasn't ignored, with the (bank, last 4)
     * keys of those whose balance is only an estimate.
     */
    private fun accounts(): Flow<Pair<List<AccountBalanceEntity>, Set<Pair<String, String>>>> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
        accountBalanceRepository.getUnanchoredAccounts(),
    ) { balances, ignored, profileId, unanchored ->
        balances.filter {
            !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                (profileId == null || it.profileId == profileId)
        } to unanchored
    }
}

/**
 * [subscriptions] as Insights sees spending: none paid from an ignored account, and
 * with a profile selected only those paid from one of its accounts. One whose
 * account isn't known counts as Personal, as a transaction without one does
 * ([com.pennywiseai.tracker.domain.model.effectiveProfileId]).
 */
internal fun scopedSubscriptions(
    subscriptions: List<SubscriptionEntity>,
    ignored: Set<String>,
    profileId: Long?,
    balances: List<AccountBalanceEntity>,
): List<SubscriptionEntity> {
    val profileKeys = buildProfileAccountKeys(balances)
    return subscriptions.filter { sub ->
        val key = "${sub.bankName}_${sub.accountLast4}"
        val profile = profileKeys.entries.firstOrNull { (_, keys) -> key in keys }?.key ?: ProfileEntity.PERSONAL_ID
        !IgnoredAccountsStore.isIgnored(ignored, sub.bankName, sub.accountLast4) &&
            (profileId == null || profile == profileId)
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
                windowStart = it.windowStart,
                windowEnd = it.windowEnd,
                incomplete = it.hasUnconvertedSpend,
            )
        }
