package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.withTransaction
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
 *  - **Rows from SMS and transactions are dropped**, the bank's reported
 *    balances included. Every one describes history the user just cleared, so
 *    keeping any would leave an account showing a total nothing explains. An
 *    SMS-tracked account with no hand-made rows goes with them and comes back
 *    with its next SMS. This also settles a manual *credit card*, which
 *    [AccountBalanceRepository.recomputeManualBalance] cannot own — its sum is
 *    income-positive and would invert a card's outstanding figure.
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

    /** Outcome of a delete-all attempt. */
    sealed interface Result {
        /** [deleted] rows were removed. */
        data class Deleted(val deleted: Int) : Result

        /**
         * Nothing was removed because the table no longer held [expected] rows.
         * A background SMS scan committed while the confirmation was open, so the
         * user would have been authorising a bigger delete than the one they were
         * shown. They get to look at the new number and decide again.
         */
        data class CountChanged(val expected: Int, val actual: Int) : Result
    }

    /**
     * @param expectedCount the row count the confirmation dialog was showing when
     *   the user confirmed. Re-checked inside the transaction so the delete either
     *   removes exactly what was authorised or removes nothing at all.
     */
    suspend operator fun invoke(expectedCount: Int): Result = runInTransaction {
        val actual = transactionRepository.countAllTransactions()
        if (actual != expectedCount) {
            return@runInTransaction Result.CountChanged(expectedCount, actual)
        }

        val accounts = accountBalanceRepository.getAllLatestBalancesOnce()

        // Must run before the delete: a manual account's opening balance is
        // back-solved from the transaction sum that is about to disappear.
        accounts.forEach { account ->
            accountBalanceRepository.ensureManualOpening(account.bankName, account.accountLast4)
        }

        val deleted = transactionRepository.deleteAllTransactions()

        // Clears every SMS and transaction balance row, bank-reported ones too.
        accountBalanceRepository.deleteSmsAndTransactionBalances()

        // No-op for SMS-tracked accounts and for credit cards (handled above by
        // dropping their deltas); lands a manual account back on its opening.
        accounts.forEach { account ->
            accountBalanceRepository.recomputeManualBalance(account.bankName, account.accountLast4)
        }

        Result.Deleted(deleted)
    }
}
