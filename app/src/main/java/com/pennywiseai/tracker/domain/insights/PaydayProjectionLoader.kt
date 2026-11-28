package com.pennywiseai.tracker.domain.insights

import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.domain.review.PeriodReviewLoader
import com.pennywiseai.tracker.domain.spending.PaydayProjection
import com.pennywiseai.tracker.domain.spending.UpcomingPaymentsLoader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * [PaydayProjection] per currency for the Insights page, in the selected profile's scope
 * and without ignored accounts: the accounts as the runway reads them, the bills the
 * calendar shows ([UpcomingPaymentsLoader]), and the income subscriptions and templates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaydayProjectionLoader @Inject constructor(
    private val upcomingPaymentsLoader: UpcomingPaymentsLoader,
    private val subscriptionRepository: SubscriptionRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val periodReviewLoader: PeriodReviewLoader,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    fun observe(today: LocalDate): Flow<Map<String, PaydayProjection>> =
        combine(userPreferencesRepository.selectedProfileId, userPreferencesRepository.budgetCycleStartDay, ::Pair)
            .flatMapLatest { (profileId, cycleStartDay) ->
                val cycleEnd = BudgetCycle.currentCycle(today, cycleStartDay).second
                combine(
                    accountBalanceRepository.getAllLatestBalances(),
                    accountBalanceRepository.getUnanchoredAccounts(),
                    periodReviewLoader.ignoredKeys(),
                    upcomingPaymentsLoader.observe(today, today.plusDays(PaydayProjection.MAX_DAYS)),
                    combine(subscriptionRepository.getActiveSubscriptions(), recurringTransactionRepository.getActive(), ::Pair),
                ) { balances, unanchored, ignored, bills, (subscriptions, recurring) ->
                    val accounts = balances.filter {
                        !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                            (profileId == null || it.profileId == profileId)
                    }
                    val incomes = PaydayProjection.incomesFrom(
                        scopedSubscriptions(subscriptions, ignored, profileId, balances),
                        recurring.filter {
                            !IgnoredAccountsStore.isIgnored(ignored, it.bankName, it.accountLast4) &&
                                (profileId == null || it.profileId == profileId)
                        },
                        today,
                    )
                    PaydayProjection.calculate(accounts, unanchored, bills, incomes, today, cycleEnd)
                        .associateBy { it.currency }
                }
            }
}
