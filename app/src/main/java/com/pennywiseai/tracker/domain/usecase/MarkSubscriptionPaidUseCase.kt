package com.pennywiseai.tracker.domain.usecase

import android.util.Log
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
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
    private val accountBalanceRepository: AccountBalanceRepository,
) {

    sealed class Result {
        data class Created(val transactionId: Long, val nextPaymentDate: LocalDate) : Result()
        /**
         * User linked an existing bank-derived transaction to this cycle.
         * No new phantom row inserted; schedule advanced. The linked
         * transaction stays as-is so anything else (split, recategorise,
         * delete) still works on it.
         */
        data class Linked(val transactionId: Long, val nextPaymentDate: LocalDate) : Result()
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

        // Same-cycle re-tap guard, anchored on TODAY not on the schedule.
        // Earlier version anchored on the (sliding) schedule, which broke
        // the moment we advanced it — lastPaidAt fell BEHIND the new cycle
        // window so the guard stopped triggering and the user could keep
        // tapping forever, marching the schedule forward each time.
        //
        // Real semantic: a subscription can't legitimately be paid twice
        // within one billing cycle. So if today < lastPaidAt + 1 cycle,
        // it's a re-tap of the cycle we just paid.
        sub.lastPaidAt?.let { lastPaid ->
            val nextLegitMark = subscriptionRepository.advance(lastPaid, sub.billingCycle)
            if (LocalDate.now().isBefore(nextLegitMark)) {
                return Result.AlreadyMarked(nextPaymentDate = scheduled)
            }
        }

        val isIncome = sub.direction == SubscriptionDirection.INCOME
        val hash = if (isIncome) {
            "autopay-${sub.id}-$scheduled"
        } else {
            "subpay-${sub.id}-$scheduled"
        }

        // Hash-level guard catches the unlikely race where lastPaidAt
        // wasn't yet written (crash between insert and markPaid).
        val existing = transactionRepository.getTransactionByHash(hash)
        if (existing != null) {
            val nextDate = subscriptionRepository.advance(scheduled, sub.billingCycle)
            subscriptionRepository.markPaid(sub.id, paymentDate, nextDate)
            return Result.AlreadyMarked(nextDate)
        }

        val txnType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE
        val txnDate = paymentDate.atStartOfDay()
        val transaction = TransactionEntity(
            amount = sub.amount,
            merchantName = sub.merchantName,
            category = sub.category ?: if (isIncome) "Income" else "Subscriptions",
            transactionType = txnType,
            dateTime = txnDate,
            description = if (isIncome) "Marked received" else "Marked paid",
            bankName = sub.bankName,
            accountNumber = sub.accountLast4,
            currency = sub.currency,
            transactionHash = hash,
            isRecurring = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

        // Insert the payment and move the funding account's balance atomically.
        // Previously the transaction was inserted raw with no account and no
        // balance update, so marking a subscription paid never touched the
        // account it was paid from (#570). No-op balance side for SMS accounts
        // and when the subscription has no funding account.
        val rowId = accountBalanceRepository.insertTransactionWithBalance(
            transaction = transaction,
            bankName = sub.bankName,
            accountLast4 = sub.accountLast4,
        )

        val nextDate = subscriptionRepository.advance(scheduled, sub.billingCycle)
        subscriptionRepository.markPaid(sub.id, paymentDate, nextDate)

        Log.i(
            TAG,
            "Marked sub=${sub.id} ${sub.merchantName} paid on $paymentDate " +
                "→ txn=$rowId, nextPaymentDate=$nextDate",
        )
        return if (rowId != -1L) {
            Result.Created(transactionId = rowId, nextPaymentDate = nextDate)
        } else {
            Result.AlreadyMarked(nextDate)
        }
    }

    /**
     * Link an existing bank-derived transaction to this subscription's
     * current cycle — used when an auto-pay charge already arrived via
     * SMS and the user wants to acknowledge it as the cycle payment
     * (avoiding a duplicate phantom row). Advances `nextPaymentDate` by
     * the billing cycle; does NOT mutate the linked transaction itself.
     */
    suspend fun linkExisting(subscriptionId: Long, transactionId: Long): Result {
        val sub = subscriptionRepository.getSubscriptionById(subscriptionId)
            ?: return Result.SubscriptionNotFound
        val scheduled = sub.nextPaymentDate ?: return Result.NoScheduledDate

        // Same-cycle re-tap guard — see [execute] for the semantic.
        sub.lastPaidAt?.let { lastPaid ->
            val nextLegitMark = subscriptionRepository.advance(lastPaid, sub.billingCycle)
            if (LocalDate.now().isBefore(nextLegitMark)) {
                return Result.AlreadyMarked(nextPaymentDate = scheduled)
            }
        }

        val linkedTxn = transactionRepository.getTransactionById(transactionId)
        val paidAt = linkedTxn?.dateTime?.toLocalDate() ?: LocalDate.now()

        val nextDate = subscriptionRepository.advance(scheduled, sub.billingCycle)
        subscriptionRepository.markPaid(sub.id, paidAt, nextDate)

        Log.i(
            TAG,
            "Linked existing txn=$transactionId to sub=${sub.id} ${sub.merchantName} " +
                "→ nextPaymentDate=$nextDate (no new transaction created)",
        )
        return Result.Linked(transactionId = transactionId, nextPaymentDate = nextDate)
    }

    private companion object {
        const val TAG = "MarkSubscriptionPaid"
    }
}
