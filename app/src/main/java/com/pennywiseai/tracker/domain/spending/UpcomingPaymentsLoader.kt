package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

/** What's committed before a window ends, and the cards whose bill isn't known yet. */
data class Upcoming(
    val bills: List<Bill>,
    /** Visible cards that owe something but whose bill (due day or amount) is missing or out of date. */
    val cardsNeedingBill: List<AccountBalanceEntity>,
)

/**
 * Loads [UpcomingPayments] in the active profile's scope: ignored accounts and those
 * in another profile neither pay anything nor have a bill counted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingPaymentsLoader @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val cardBillsLoader: CardBillsLoader,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    fun observe(today: LocalDate, end: LocalDate): Flow<Upcoming> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        ignoredAccountsStore.keysFlow,
        userPreferencesRepository.selectedProfileId,
        ::Triple,
    ).flatMapLatest { (accounts, ignored, profileId) ->
        val byKey = accounts.associateBy { it.bankName.lowercase() to it.accountLast4 }
        fun excluded(bank: String?, last4: String?): Boolean {
            if (IgnoredAccountsStore.isIgnored(ignored, bank, last4)) return true
            val account = if (bank == null || last4 == null) null else byKey[bank.lowercase() to last4]
            return profileId != null && account != null && account.profileId != profileId
        }
        val cards = accounts.filter { it.isCreditCard }
        val visibleCards = cards.filter { !excluded(it.bankName, it.accountLast4) }
        combine(
            subscriptionRepository.getActiveSubscriptions(),
            recurringTransactionRepository.getActive(),
            cardBillsLoader.observe(visibleCards, today),
            transactionRepository.getTransactionsBetweenDates(today.minusDays(RecurringCharge.DUE_WINDOW_DAYS), today),
        ) { subscriptions, recurring, cardBills, posted ->
            Upcoming(
                bills = UpcomingPayments.of(
                    subscriptions = subscriptions,
                    recurring = recurring.filter { profileId == null || it.profileId == profileId },
                    cardBills = cardBills,
                    cards = cards,
                    posted = posted,
                    today = today,
                    end = end,
                    isExcluded = ::excluded,
                ),
                cardsNeedingBill = cardBills.filter { it.needsBill }.map { it.card },
            )
        }
    }
}
