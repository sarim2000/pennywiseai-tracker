package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.builtinKey
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import com.pennywiseai.tracker.domain.spending.RecurringSuggestion
import com.pennywiseai.tracker.domain.spending.RecurringSuggestions
import com.pennywiseai.tracker.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bills the user seems to pay on a schedule but doesn't track yet, from their
 * history ([RecurringSuggestions]), and the two answers: track it or "not
 * recurring".
 */
@Singleton
class RecurringSuggestionsUseCase @Inject constructor(
    private val database: PennyWiseDatabase,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val categoryDao: CategoryDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationScope scope: CoroutineScope,
) {

    /**
     * Re-runs whenever the history, subscriptions, categories or dismissals
     * change. One shared run for Home and Subscriptions, kept for a few
     * seconds after the last screen stops watching.
     */
    val suggestions: Flow<List<RecurringSuggestion>> = buildSuggestions()
        .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun buildSuggestions(): Flow<List<RecurringSuggestion>> {
        // Open-ended, so a screen kept alive past midnight still sees new days'
        // payments; "today" is read on each run for the same reason.
        val from = LocalDate.now().minusDays(RecurringSuggestions.LOOKBACK_DAYS).atStartOfDay()
        return combine(
            transactionRepository.getTransactionsBetweenDates(from, LocalDateTime.of(9999, 12, 31, 0, 0)),
            subscriptionRepository.getAllSubscriptions(),
            categoryDao.getAllCategories(),
            userPreferencesRepository.dismissedRecurringSuggestions,
        ) { transactions, subscriptions, categories, dismissed ->
            val builtinByName = categories.associate { it.name to it.builtinKey }
            val covered = subscriptions.map { RecurringSuggestions.keyFor(it.merchantName, it.currency) }.toSet()
            RecurringSuggestions.detect(
                transactions = transactions,
                today = LocalDate.now(),
                // A category missing from the table is an auto-categorizer name
                // that hasn't been renamed: it is its own built-in key.
                builtinKeyOf = { name -> if (name in builtinByName) builtinByName[name] else name },
                isCovered = { merchant, currency -> RecurringSuggestions.keyFor(merchant, currency) in covered },
                dismissed = dismissed,
            )
        }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }

    /**
     * Tracks [suggestion] as an active bill at [amount], paid on its last
     * payment, and marks its payments recurring. Null when a subscription
     * already covers the merchant (a second tap).
     */
    suspend fun accept(suggestion: RecurringSuggestion, amount: BigDecimal): SubscriptionEntity? =
        database.withTransaction {
            if (subscriptionRepository.hasAnySubscriptionFor(suggestion.merchantName, suggestion.currency)) {
                return@withTransaction null
            }
            val subscription = SubscriptionEntity(
                merchantName = suggestion.merchantName,
                amount = amount,
                currency = suggestion.currency,
                category = suggestion.category,
                bankName = suggestion.bankName,
                accountLast4 = suggestion.accountLast4,
                billingCycle = suggestion.billingCycle,
                nextPaymentDate = suggestion.nextDueDate,
                lastPaidAt = suggestion.lastPaidOn,
                state = SubscriptionState.ACTIVE,
                direction = SubscriptionDirection.EXPENSE,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
            val id = subscriptionRepository.insertSubscription(subscription)
            for (payment in suggestion.payments) {
                val transaction = transactionRepository.getTransactionById(payment.transactionId) ?: continue
                if (transaction.isDeleted || transaction.isRecurring) continue
                if (!RecurringCharge.sameMerchant(transaction.merchantName, suggestion.merchantName)) continue
                transactionRepository.updateTransaction(transaction.copy(isRecurring = true))
            }
            subscription.copy(id = id)
        }

    /** "Not recurring": [suggestion]'s merchant is never suggested again. */
    suspend fun dismiss(suggestion: RecurringSuggestion) {
        userPreferencesRepository.addDismissedRecurringSuggestions(listOf(suggestion.key))
    }

    /** Undo for [dismiss]. */
    suspend fun undoDismiss(suggestion: RecurringSuggestion) {
        userPreferencesRepository.removeDismissedRecurringSuggestion(suggestion.key)
    }
}
