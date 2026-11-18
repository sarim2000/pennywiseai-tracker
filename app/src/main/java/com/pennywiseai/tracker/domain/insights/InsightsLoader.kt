package com.pennywiseai.tracker.domain.insights

import android.util.Log
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
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
import com.pennywiseai.tracker.utils.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
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
    /** What active subscriptions cost a month, per currency. */
    val subscriptionsMonthly: Map<String, Money> = emptyMap(),
) {
    val active: List<Insight> get() = insights.filter { it.id !in dismissed }
}

/**
 * Gathers [InsightInputs] from the sources the rest of the app already trusts: the
 * Period Review rows (profile and ignored accounts applied), the Budgets screen's
 * summary, and subscriptions and card balances scoped as those rows are. Nothing
 * leaves the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsLoader @Inject constructor(
    private val periodReviewLoader: PeriodReviewLoader,
    private val budgetSummaryProvider: BudgetSummaryProvider,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val insightPreferences: InsightPreferences,
) {
    /**
     * Rebuilt from scratch for each selected profile, so no snapshot ever pairs one
     * profile's review with another's subscriptions or cards: the review is pinned to the
     * branch's profile too. [profileIds] pins it for a caller holding one (the recap).
     */
    fun observe(today: LocalDate = LocalDate.now(), profileIds: Flow<Long?>? = null): Flow<InsightsSnapshot> =
        (profileIds ?: userPreferencesRepository.selectedProfileId).distinctUntilChanged().flatMapLatest { profileId ->
            combine(
                periodReviewLoader.observe(ReviewKind.MONTH, 0, today, detail = true, profileIds = flowOf(profileId)),
                budgets(today, profileId),
                subscriptions(profileId),
                creditCards(profileId),
                insightPreferences.dismissedInsights,
            ) { review, budgets, subscriptions, cards, dismissed ->
                val inputs = InsightInputs(today, review, budgets, subscriptions, cards, profileId)
                InsightsSnapshot(
                    review, InsightRules.evaluate(inputs), dismissed,
                    InsightRules.subscriptionsMonthly(subscriptions),
                )
            }
        }

    /**
     * Budgets span every profile, so with one profile selected they'd sit beside
     * spending they don't match: left out then.
     */
    private fun budgets(today: LocalDate, profileId: Long?): Flow<List<BudgetStatus>> =
        if (profileId != null) {
            flowOf(emptyList())
        } else {
            budgetSummaryProvider.summary(YearMonth.from(today), flagUncounted = true)
                .map { budgetStatuses(it, today) }
                .emptyOnFailure()
        }

    /** Ignored keys as the review rows use them: a card linked to an ignored account is ignored too. */
    private fun subscriptions(profileId: Long?): Flow<List<SubscriptionEntity>> = combine(
        subscriptionRepository.getActiveSubscriptions(),
        periodReviewLoader.ignoredKeys(),
        accountBalanceRepository.getAllLatestBalances(),
    ) { subscriptions, ignored, balances -> scopedSubscriptions(subscriptions, ignored, profileId, balances) }

    private fun creditCards(profileId: Long?): Flow<List<AccountBalanceEntity>> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        periodReviewLoader.ignoredKeys(),
    ) { balances, ignored ->
        balances.filter {
            it.isCreditCard &&
                !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                (profileId == null || it.profileId == profileId)
        }
    }
}

/**
 * A failure shows nothing rather than ending the flow: budget figures that can't load
 * mustn't cost the user every other insight, and they come back once a reload works.
 */
internal fun <T> Flow<List<T>>.emptyOnFailure(): Flow<List<T>> = retryWhen { cause, attempt ->
    if (cause is CancellationException) return@retryWhen false
    Log.w("InsightsLoader", "Budget insights failed to load", cause)
    emit(emptyList())
    delay(minOf(1_000L shl attempt.toInt().coerceAtMost(5), 30_000L))
    true
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
    // Accounts as the payment matcher compares them (RecurringCharge.couldBePaidFrom):
    // trimmed and ignoring case, as a subscription may spell its bank another way.
    fun accountKey(bank: String?, last4: String?) = "${bank?.trim()?.lowercase()}_${last4?.trim()}"
    val profiles = balances.groupBy({ accountKey(it.bankName, it.accountLast4) }, { it.profileId })
    return subscriptions.filter { sub ->
        val profile = profiles[accountKey(sub.bankName, sub.accountLast4)]?.firstOrNull() ?: ProfileEntity.PERSONAL_ID
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
