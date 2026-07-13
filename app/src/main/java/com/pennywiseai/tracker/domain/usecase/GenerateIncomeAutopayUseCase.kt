package com.pennywiseai.tracker.domain.usecase

import android.util.Log
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Phantom-creates the INCOME transactions described by recurring income
 * subscriptions (#371). Some accounts — wallets, allowances, internal
 * top-ups — don't send an SMS when money arrives, so without this the
 * user has to manually log every cycle. Instead, the user declares
 * "₹X expected on the Nth of every month" once; this use case materialises
 * the matching transaction the moment it's due (and catches up if the user
 * was away for several cycles).
 *
 * Runs on every SMS scan completion. Idempotent — the per-cycle
 * `transactionHash` is `"autopay-<subId>-<scheduledDate>"`, so a second
 * pass on the same day for the same subscription is dedup'd by
 * `TransactionRepository.getTransactionByHash`.
 */
class GenerateIncomeAutopayUseCase @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) {

    /** Returns the count of phantom transactions inserted. */
    suspend fun execute(today: LocalDate = LocalDate.now()): Int {
        val due = subscriptionRepository.getDueIncomeSubscriptions(today)
        if (due.isEmpty()) return 0

        var inserted = 0
        for (sub in due) {
            var scheduled = sub.nextPaymentDate ?: continue
            // Materialise every missed cycle up to today, not just one — a
            // user who didn't open the app for two months should see both
            // months' phantom income, not just the most recent.
            val bankName = sub.bankName
            val accountLast4 = sub.accountLast4
            while (!scheduled.isAfter(today)) {
                val hash = "autopay-${sub.id}-$scheduled"
                if (transactionRepository.getTransactionByHash(hash) == null) {
                    val phantom = TransactionEntity(
                        amount = sub.amount,
                        merchantName = sub.merchantName,
                        category = sub.category ?: "Income",
                        transactionType = TransactionType.INCOME,
                        dateTime = scheduled.atStartOfDay(),
                        description = "Scheduled income (autopay)",
                        bankName = sub.bankName,
                        accountNumber = accountLast4,
                        currency = sub.currency,
                        transactionHash = hash,
                        isRecurring = true,
                        createdAt = LocalDateTime.now(),
                        updatedAt = LocalDateTime.now()
                    )
                    // Insert the phantom and credit the funding account atomically
                    // so a crash between the two can't leave the row present (its
                    // hash then blocking every retry) with the balance un-credited
                    // (#570). No-op balance side for SMS / unlinked subscriptions.
                    val rowId = accountBalanceRepository.insertTransactionWithBalance(
                        transaction = phantom,
                        bankName = bankName,
                        accountLast4 = accountLast4,
                    )
                    if (rowId != -1L) inserted++
                }
                scheduled = subscriptionRepository.advance(scheduled, sub.billingCycle)
            }
            // Persist the final advanced date so the next scan starts from
            // there instead of replaying everything.
            subscriptionRepository.updateNextPaymentDate(sub.id, scheduled)
        }
        if (inserted > 0) Log.i(TAG, "Materialised $inserted scheduled income transactions.")
        return inserted
    }

    private companion object {
        const val TAG = "GenerateIncomeAutopay"
    }
}
