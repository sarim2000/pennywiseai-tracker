package com.pennywiseai.tracker.domain.usecase

import android.util.Log
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * User-initiated counterpart to [GenerateIncomeAutopayUseCase]: when the
 * user manually confirms they paid (or received) a subscription this cycle,
 * materialise the matching transaction and advance the subscription's
 * `next_payment_date` by its billing cycle. Issue #412.
 *
 * Direction handling:
 *  - EXPENSE — most expense subs auto-create a transaction from an SMS
 *    (UPI mandate, card auto-debit). This use case covers the manual-pay
 *    subset: cash, in-person card swipes, off-platform payments. Creates a
 *    TransactionType.EXPENSE row.
 *  - INCOME — already auto-created by GenerateIncomeAutopayUseCase on
 *    schedule. User-initiated marks here let early-payers confirm before
 *    the next scan; idempotent dedup ensures no double-count.
 *
 * Idempotency:
 *  - EXPENSE rows use `"subpay-<subId>-<scheduledDate>"` as the transaction
 *    hash, where `scheduledDate` is the subscription's CURRENT
 *    `nextPaymentDate` (not the user-chosen payment date). Tapping mark-as-
 *    paid twice for the same cycle dedup's via
 *    [TransactionRepository.getTransactionByHash].
 *  - INCOME rows use the SAME `"autopay-<subId>-<scheduledDate>"` shape as
 *    the autopay use case so manual + auto can't double-fire on the same
 *    cycle.
 *
 * The created transaction's `dateTime` is set to the user-chosen
 * `paymentDate` (so the spend lands on the right day in reports). The
 * subscription's `nextPaymentDate` is advanced by exactly one billing
 * cycle from the previously-scheduled date (NOT from `paymentDate`) so
 * cycle drift can't accumulate when the user pays a few days early/late.
 */
class MarkSubscriptionPaidUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val transactionRepository: TransactionRepository,
) {

    sealed class Result {
        data class Created(val transactionId: Long, val nextPaymentDate: LocalDate) : Result()
        data class AlreadyMarked(val nextPaymentDate: LocalDate) : Result()
        data object SubscriptionNotFound : Result()
        data object NoScheduledDate : Result()
    }

    suspend fun execute(
        subscriptionId: Long,
        paymentDate: LocalDate = LocalDate.now(),
    ): Result {
        val sub = subscriptionRepository.getSubscriptionById(subscriptionId)
            ?: return Result.SubscriptionNotFound
        val scheduled = sub.nextPaymentDate ?: return Result.NoScheduledDate

        val isIncome = sub.direction == SubscriptionDirection.INCOME
        val hash = if (isIncome) {
            "autopay-${sub.id}-$scheduled"
        } else {
            "subpay-${sub.id}-$scheduled"
        }

        val existing = transactionRepository.getTransactionByHash(hash)
        if (existing != null) {
            // Cycle already accounted for. Still advance the schedule —
            // the user clearly thinks they need to mark this cycle done.
            // (Without advancing, the subscription would stay "due" and
            // keep prompting them. Advancing is the recovery path.)
            val nextDate = subscriptionRepository.advance(scheduled, sub.billingCycle)
            subscriptionRepository.updateNextPaymentDate(sub.id, nextDate)
            return Result.AlreadyMarked(nextDate)
        }

        val transaction = TransactionEntity(
            amount = sub.amount,
            merchantName = sub.merchantName,
            category = sub.category ?: if (isIncome) "Income" else "Subscriptions",
            transactionType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
            dateTime = paymentDate.atStartOfDay(),
            description = if (isIncome) "Marked received" else "Marked paid",
            bankName = sub.bankName,
            currency = sub.currency,
            transactionHash = hash,
            isRecurring = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )
        val rowId = transactionRepository.insertTransaction(transaction)

        val nextDate = subscriptionRepository.advance(scheduled, sub.billingCycle)
        subscriptionRepository.updateNextPaymentDate(sub.id, nextDate)

        Log.i(
            TAG,
            "Marked sub=${sub.id} ${sub.merchantName} paid on $paymentDate " +
                "→ txn=$rowId, nextPaymentDate=$nextDate",
        )
        return if (rowId != -1L) {
            Result.Created(transactionId = rowId, nextPaymentDate = nextDate)
        } else {
            // Insert failed (unique-constraint race with autopay running at
            // the same instant). Treat as already-marked — schedule advanced.
            Result.AlreadyMarked(nextDate)
        }
    }

    private companion object {
        const val TAG = "MarkSubscriptionPaid"
    }
}
