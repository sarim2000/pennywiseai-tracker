package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
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
 *  - **Manual / cash accounts are recomputed.** Their balance *is defined* as
 *    opening + Σ(transactions), so leaving it untouched would keep showing a
 *    total for transactions that no longer exist.
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
        val accounts = accountBalanceRepository.getAllLatestBalances().first()

        // Must run before the delete: a manual account's opening balance is
        // back-solved from the transaction sum that is about to disappear.
        accounts.forEach { account ->
            accountBalanceRepository.ensureManualOpening(account.bankName, account.accountLast4)
        }

        val deleted = transactionRepository.deleteAllTransactions()

        // No-ops for SMS-tracked accounts; lands a manual one back on its opening.
        accounts.forEach { account ->
            accountBalanceRepository.recomputeManualBalance(account.bankName, account.accountLast4)
        }

        deleted
    }
}
