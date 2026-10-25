package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.spending.RecurringCharge
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * "You paid this same bill a month ago — track it?" A spend that repeats an
 * earlier one (same merchant, currency and amount) after a billing-cycle gap
 * is offered as a subscription from its transaction alert.
 */
class TrackRepeatChargeUseCase @Inject constructor(
    private val database: PennyWiseDatabase,
    private val transactionDao: TransactionDao,
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository
) {

    /**
     * The billing cycle [transaction] repeats, or null when it isn't a repeat
     * worth offering: already recurring, not a spend, no earlier matching
     * charge at a cycle's gap, or a subscription (in any state, so a cancelled
     * one doesn't nag) already covers the merchant.
     */
    suspend fun suggestedCycle(transaction: TransactionEntity): String? {
        if (transaction.isRecurring || transaction.transactionType !in RecurringCharge.SPEND_TYPES) return null
        if (transaction.merchantName.isBlank()) return null
        val previous = transactionDao.getChargesInCurrencyBetween(
            currency = transaction.currency,
            types = RecurringCharge.SPEND_TYPES.map { it.name },
            from = transaction.dateTime.minusDays(LOOKBACK_DAYS),
            before = transaction.dateTime,
            excludeId = transaction.id
        ).firstOrNull {
            RecurringCharge.sameMerchant(it.merchantName, transaction.merchantName) &&
                RecurringCharge.sameAmount(it.amount, transaction.amount)
        } ?: return null
        val gap = ChronoUnit.DAYS.between(previous.dateTime.toLocalDate(), transaction.dateTime.toLocalDate())
        val cycle = RecurringCharge.cycleForGap(gap) ?: return null
        if (subscriptionRepository.hasAnySubscriptionFor(transaction.merchantName, transaction.currency)) return null
        return cycle
    }

    /**
     * Tracks [transactionId]'s merchant as a [cycle] bill paid by that charge,
     * and marks the charge recurring. Null when the transaction is gone or a
     * subscription already covers it (a second tap on the same alert).
     */
    // One write transaction serializes the check and the insert, so two taps (or
    // two outstanding alerts for the same bill) can't both create it.
    suspend fun track(transactionId: Long, cycle: String): SubscriptionEntity? = database.withTransaction {
        val transaction = transactionRepository.getTransactionById(transactionId)
            ?.takeUnless { it.isDeleted } ?: return@withTransaction null
        if (subscriptionRepository.hasAnySubscriptionFor(transaction.merchantName, transaction.currency)) return@withTransaction null
        val paidOn = transaction.dateTime.toLocalDate()
        val subscription = SubscriptionEntity(
            merchantName = transaction.merchantName,
            amount = transaction.amount,
            currency = transaction.currency,
            category = transaction.category,
            bankName = transaction.bankName,
            accountLast4 = transaction.accountNumber,
            billingCycle = cycle,
            nextPaymentDate = subscriptionRepository.advance(paidOn, cycle),
            lastPaidAt = paidOn,
            state = SubscriptionState.ACTIVE,
            direction = SubscriptionDirection.EXPENSE
        )
        val id = subscriptionRepository.insertSubscription(subscription)
        transactionRepository.updateTransaction(transaction.copy(isRecurring = true))
        subscription.copy(id = id)
    }

    private companion object {
        // An annual bill's gap tops out at 380 days; a little slack past that.
        const val LOOKBACK_DAYS = 400L
    }
}
