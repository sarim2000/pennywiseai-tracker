package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class BatchApplyResult(
    val totalProcessed: Int,
    val totalUpdated: Int,
    val totalDeleted: Int = 0,
    val errors: List<String> = emptyList()
)

class ApplyRulesToPastTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine
) {
    /**
     * Apply a specific rule to all past transactions
     */
    suspend fun applyRuleToAllTransactions(
        rule: TransactionRule,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchApplyResult {
        val allTransactions = transactionRepository.getAllTransactionsList()
        return processTransactionsWithRule(allTransactions, rule, onProgress)
    }

    /**
     * Apply a specific rule to uncategorized transactions only
     */
    suspend fun applyRuleToUncategorizedTransactions(
        rule: TransactionRule,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchApplyResult {
        val uncategorizedTransactions = transactionRepository.getUncategorizedTransactions()
        return processTransactionsWithRule(uncategorizedTransactions, rule, onProgress)
    }

    /**
     * Apply all active rules to all past transactions
     */
    suspend fun applyAllActiveRulesToTransactions(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchApplyResult {
        val activeRules = ruleRepository.getActiveRules()
        val allTransactions = transactionRepository.getAllTransactionsList()

        var totalUpdated = 0
        var totalDeleted = 0
        val errors = mutableListOf<String>()

        allTransactions.forEachIndexed { index, transaction ->
            onProgress(index + 1, allTransactions.size)

            try {
                // Skip already deleted transactions
                if (transaction.isDeleted) {
                    return@forEachIndexed
                }

                // Get SMS body if available
                val smsBody = transaction.smsBody

                // Check if any rule would block this transaction
                val blockingRule = ruleEngine.shouldBlockTransaction(
                    transaction,
                    smsBody,
                    activeRules
                )

                if (blockingRule != null) {
                    // Soft delete the transaction
                    val deletedTransaction = transaction.copy(isDeleted = true)
                    transactionRepository.updateTransaction(deletedTransaction)
                    totalDeleted++
                } else {
                    // Apply rules to transaction
                    val (updatedTransaction, ruleApplications) = ruleEngine.evaluateRules(
                        transaction,
                        smsBody,
                        activeRules
                    )

                    // If transaction was modified, update it
                    if (ruleApplications.isNotEmpty()) {
                        transactionRepository.updateTransaction(updatedTransaction)
                        ruleRepository.saveRuleApplications(ruleApplications)
                        totalUpdated++
                    }
                }
            } catch (e: Exception) {
                errors.add("Error processing transaction ${transaction.id}: ${e.message}")
            }
        }

        return BatchApplyResult(
            totalProcessed = allTransactions.size,
            totalUpdated = totalUpdated,
            totalDeleted = totalDeleted,
            errors = errors
        )
    }

    private suspend fun processTransactionsWithRule(
        transactions: List<TransactionEntity>,
        rule: TransactionRule,
        onProgress: (processed: Int, total: Int) -> Unit
    ): BatchApplyResult {
        var totalUpdated = 0
        var totalDeleted = 0
        val errors = mutableListOf<String>()

        transactions.forEachIndexed { index, transaction ->
            onProgress(index + 1, transactions.size)

            try {
                // Skip already deleted transactions
                if (transaction.isDeleted) {
                    return@forEachIndexed
                }

                // Get SMS body if available
                val smsBody = transaction.smsBody

                // Check if this transaction should be blocked
                val shouldBlock = ruleEngine.shouldBlockTransaction(
                    transaction,
                    smsBody,
                    listOf(rule)
                ) != null

                if (shouldBlock) {
                    // Soft delete the transaction
                    val deletedTransaction = transaction.copy(isDeleted = true)
                    transactionRepository.updateTransaction(deletedTransaction)
                    totalDeleted++
                } else {
                    // Apply regular rule actions
                    val (updatedTransaction, ruleApplications) = ruleEngine.evaluateRules(
                        transaction,
                        smsBody,
                        listOf(rule)
                    )

                    // If transaction was modified, update it
                    if (ruleApplications.isNotEmpty()) {
                        transactionRepository.updateTransaction(updatedTransaction)
                        ruleRepository.saveRuleApplications(ruleApplications)
                        totalUpdated++
                    }
                }
            } catch (e: Exception) {
                errors.add("Error processing transaction ${transaction.id}: ${e.message}")
            }
        }

        return BatchApplyResult(
            totalProcessed = transactions.size,
            totalUpdated = totalUpdated,
            totalDeleted = totalDeleted,
            errors = errors
        )
    }

    /**
     * Preview what would happen if a rule were applied (without actually applying).
     * Returns pairs of (original, modified) for affected transactions,
     * plus a count of how many would be blocked.
     */
    suspend fun previewRuleApplication(
        rule: TransactionRule,
        maxSamples: Int = 20
    ): DryRunResult {
        val allTransactions = transactionRepository.getAllTransactionsList()
        val diffs = mutableListOf<TransactionDiff>()
        var totalMatched = 0
        var totalWouldBlock = 0

        for (transaction in allTransactions) {
            if (transaction.isDeleted) continue

            val smsBody = transaction.smsBody

            val wouldBlock = ruleEngine.shouldBlockTransaction(
                transaction, smsBody, listOf(rule)
            ) != null

            if (wouldBlock) {
                totalWouldBlock++
                totalMatched++
                if (diffs.size < maxSamples) {
                    diffs.add(TransactionDiff(original = transaction, modified = null, isBlock = true))
                }
                continue
            }

            val (updated, applications) = ruleEngine.evaluateRules(
                transaction, smsBody, listOf(rule)
            )

            if (applications.isNotEmpty()) {
                totalMatched++
                if (diffs.size < maxSamples) {
                    diffs.add(TransactionDiff(original = transaction, modified = updated, isBlock = false))
                }
            }
        }

        return DryRunResult(
            totalScanned = allTransactions.count { !it.isDeleted },
            totalMatched = totalMatched,
            totalWouldBlock = totalWouldBlock,
            totalWouldUpdate = totalMatched - totalWouldBlock,
            samples = diffs
        )
    }
}

data class DryRunResult(
    val totalScanned: Int,
    val totalMatched: Int,
    val totalWouldBlock: Int,
    val totalWouldUpdate: Int,
    val samples: List<TransactionDiff>
)

data class TransactionDiff(
    val original: TransactionEntity,
    val modified: TransactionEntity?,
    val isBlock: Boolean
)