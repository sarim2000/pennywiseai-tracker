package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears the user's whole transaction history and settles the account-balance
 * ledger behind it, as one database transaction — a crash or cancellation
 * part-way through can't leave the transactions gone but a manual account still
 * displaying a total for them.
 *
 * Balances get different treatment per account kind, on purpose:
 *
 *  - **SMS-tracked accounts keep their last reported figure.** That number is
 *    the bank's own word, not something derived from the local history, so
 *    clearing the history shouldn't rewrite it. (Deleting a *single* transaction
 *    does shift it, via [AccountBalanceRepository.applyDeleteBalanceShift].
 *    Replaying that for every row would unwind every expense at once and leave
 *    the user staring at an inflated balance right after asking to clear their
 *    history.)
 *  - **Rows the app derived from a transaction are dropped.** Every delta
 *    snapshot written when a transaction landed is an orphan once that
 *    transaction is gone, and would otherwise keep the account displaying a
 *    total for history that no longer exists. This is what settles a manual
 *    *credit card*, which [AccountBalanceRepository.recomputeManualBalance]
 *    cannot own — its sum is income-positive and would invert a card's
 *    outstanding figure.
 *  - **Manual / cash accounts are recomputed.** Their balance *is defined* as
 *    opening + Σ(transactions); with the transactions gone that lands them back
 *    on their opening balance.
 *
 * Everything else a transaction hangs off — splits, tags, rule applications —
 * goes with it through the cascading foreign keys. Accounts, budgets, loans,
 * categories and rules are kept.
 */
@Singleton
open class DeleteAllTransactionsUseCase @Inject constructor(
    private val database: PennyWiseDatabase,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) {
    internal open suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction(block)

    /**
     * @return the number of transaction rows actually removed — the DELETE's own
     *   affected-row count, so a row written after the confirmation dialog read
     *   its preview count is still reported accurately.
     */
    suspend operator fun invoke(): Int = runInTransaction {
        val accounts = accountBalanceRepository.getAllLatestBalancesOnce()

        // Must run before the delete: a manual account's opening balance is
        // back-solved from the transaction sum that is about to disappear.
        accounts.forEach { account ->
            accountBalanceRepository.ensureManualOpening(account.bankName, account.accountLast4)
        }

        val deleted = transactionRepository.deleteAllTransactions()

        // Clears the now-orphaned delta rows. Bank-reported balances survive.
        accountBalanceRepository.deleteTransactionDerivedBalances()

        // No-op for SMS-tracked accounts and for credit cards (handled above by
        // dropping their deltas); lands a manual account back on its opening.
        accounts.forEach { account ->
            accountBalanceRepository.recomputeManualBalance(account.bankName, account.accountLast4)
        }

        deleted
    }
}
