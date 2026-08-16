package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain Use Case for restoring soft-deleted financial transactions.
 * Atomically un-deletes transactions and reapplies account balance effects.
 * Idempotent: safe against repeated invocation / duplicate undo actions.
 */
@Singleton
open class RestoreTransactionUseCase @Inject constructor(
    private val database: PennyWiseDatabase,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) {
    internal open suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction(block)

    /**
     * Restores a single soft-deleted [transaction] entity and updates the account balance.
     * No-op if transaction is not soft-deleted.
     */
    suspend operator fun invoke(transaction: TransactionEntity) {
        if (!transaction.isDeleted) return
        runInTransaction {
            val current = transactionRepository.getTransactionById(transaction.id) ?: transaction
            if (!current.isDeleted) return@runInTransaction
            transactionRepository.undoDeleteTransaction(current)
            accountBalanceRepository.applyRestoreBalanceShift(current)
        }
    }

    /**
     * Looks up the transaction by [transactionId], then restores it if found.
     * @return true if the transaction existed and was restored, false if missing or not deleted.
     */
    suspend operator fun invoke(transactionId: Long): Boolean {
        return runInTransaction {
            val transaction = transactionRepository.getTransactionById(transactionId) ?: return@runInTransaction false
            if (!transaction.isDeleted) return@runInTransaction false
            transactionRepository.undoDeleteTransaction(transaction)
            accountBalanceRepository.applyRestoreBalanceShift(transaction)
            true
        }
    }

    /**
     * Bulk restores a collection of soft-deleted [transactions] within a single database transaction.
     */
    suspend operator fun invoke(transactions: List<TransactionEntity>) {
        val targets = transactions.filter { it.isDeleted }
        if (targets.isEmpty()) return
        runInTransaction {
            targets.forEach { transaction ->
                val current = transactionRepository.getTransactionById(transaction.id) ?: transaction
                if (!current.isDeleted) return@forEach
                transactionRepository.undoDeleteTransaction(current)
                accountBalanceRepository.applyRestoreBalanceShift(current)
            }
        }
    }
}
