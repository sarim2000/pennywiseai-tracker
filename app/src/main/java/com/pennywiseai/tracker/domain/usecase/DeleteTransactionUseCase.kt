package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain Use Case for deleting financial transactions.
 * Atomically soft-deletes transactions and adjusts account balance ledgers.
 * Idempotent: safe against repeated invocation / duplicate broadcast intents.
 */
@Singleton
open class DeleteTransactionUseCase @Inject constructor(
    private val database: PennyWiseDatabase,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) {
    internal open suspend fun <R> runInTransaction(block: suspend () -> R): R =
        database.withTransaction(block)

    /**
     * Deletes a single [transaction] entity and updates the account balance.
     * No-op if transaction is already deleted (unless [hardDelete] is true).
     */
    suspend operator fun invoke(transaction: TransactionEntity, hardDelete: Boolean = false) {
        if (!hardDelete && transaction.isDeleted) return
        runInTransaction {
            val current = transactionRepository.getTransactionById(transaction.id) ?: transaction
            if (!hardDelete && current.isDeleted) return@runInTransaction
            transactionRepository.deleteTransaction(current, hardDelete = hardDelete)
            accountBalanceRepository.applyDeleteBalanceShift(current)
        }
    }

    /**
     * Looks up the transaction by [transactionId], then deletes it if found.
     * @return true if the transaction existed and was deleted, false if missing or already deleted.
     */
    suspend operator fun invoke(transactionId: Long, hardDelete: Boolean = false): Boolean {
        return runInTransaction {
            val transaction = transactionRepository.getTransactionById(transactionId) ?: return@runInTransaction false
            if (!hardDelete && transaction.isDeleted) return@runInTransaction false
            transactionRepository.deleteTransaction(transaction, hardDelete = hardDelete)
            accountBalanceRepository.applyDeleteBalanceShift(transaction)
            true
        }
    }

    /**
     * Bulk deletes a collection of [transactions] within a single database transaction.
     */
    suspend operator fun invoke(transactions: List<TransactionEntity>, hardDelete: Boolean = false) {
        val targets = if (hardDelete) transactions else transactions.filter { !it.isDeleted }
        if (targets.isEmpty()) return
        runInTransaction {
            targets.forEach { transaction ->
                val current = transactionRepository.getTransactionById(transaction.id) ?: transaction
                if (!hardDelete && current.isDeleted) return@forEach
                transactionRepository.deleteTransaction(current, hardDelete = hardDelete)
                accountBalanceRepository.applyDeleteBalanceShift(current)
            }
        }
    }
}
