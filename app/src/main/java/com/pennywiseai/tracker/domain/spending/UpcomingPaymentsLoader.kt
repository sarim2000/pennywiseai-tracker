package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.domain.model.buildProfileAccountKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import javax.inject.Inject

/**
 * Loads [UpcomingPayments] in the active profile's scope: nothing paid from an ignored
 * account (or a debit card linked to one, as spending hides it) or one in another
 * profile is shown, and no such card's bill. A subscription
 * whose account isn't known is Personal's, as a transaction without one is
 * ([com.pennywiseai.tracker.domain.model.effectiveProfileId]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingPaymentsLoader @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardBillsLoader: CardBillsLoader,
    private val ignoredAccountsStore: IgnoredAccountsStore,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cardRepository: CardRepository,
) {
    /** [from]: see [UpcomingPayments.of]. */
    fun observe(today: LocalDate, end: LocalDate, from: LocalDate = today): Flow<List<Bill>> = combine(
        accountBalanceRepository.getAllLatestBalances(),
        ignoredAccountsStore.keysFlow,
        cardRepository.getAllCards(),
        userPreferencesRepository.selectedProfileId,
    ) { accounts, keys, linkedCards, profileId ->
        Triple(accounts, IgnoredAccountsStore.withLinkedCards(keys, linkedCards, accounts), profileId)
    }.flatMapLatest { (accounts, ignored, profileId) ->
        // Matched exactly, as a transaction's profile and ignored keys are, so a subscription
        // is never hidden from the profile it's counted in.
        val byKey = accounts.associateBy { it.bankName to it.accountLast4 }
        fun excluded(bank: String?, last4: String?): Boolean {
            if (IgnoredAccountsStore.isIgnored(ignored, bank, last4)) return true
            val account = if (bank == null || last4 == null) null else byKey[bank to last4]
            return profileId != null && account != null && account.profileId != profileId
        }
        val profileKeys = buildProfileAccountKeys(accounts)
        fun subscriptionProfile(bank: String?, last4: String?): Long =
            profileKeys.entries.firstOrNull { (_, keys) -> "${bank}_$last4" in keys }?.key ?: ProfileEntity.PERSONAL_ID
        val cards = accounts.filter { it.isCreditCard }
        val visibleCards = cards.filter { !excluded(it.bankName, it.accountLast4) }
        combine(
            subscriptionRepository.getActiveSubscriptions(),
            recurringTransactionRepository.getActive(),
            cardBillsLoader.observe(visibleCards, today),
        ) { subscriptions, recurring, cardBills ->
            UpcomingPayments.of(
                subscriptions = subscriptions.filter { profileId == null || subscriptionProfile(it.bankName, it.accountLast4) == profileId },
                recurring = recurring.filter { profileId == null || it.profileId == profileId },
                cardBills = cardBills,
                cards = cards,
                today = today,
                end = end,
                isExcluded = ::excluded,
                from = from,
            )
        }
    }
}
