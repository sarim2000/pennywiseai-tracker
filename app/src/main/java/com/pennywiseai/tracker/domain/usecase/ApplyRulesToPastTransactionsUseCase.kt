package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.model.rule.applyTagActions
import com.pennywiseai.tracker.domain.model.rule.tagChanges
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class BatchApplyResult(
    val totalProcessed: Int,
    val totalUpdated: Int,
    val totalDeleted: Int = 0,
    val errors: List<String> = emptyList()
)

class ApplyRulesToPastTransactionsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val tagRepository: TagRepository
) {
    /**
     * ADD_TAG / REMOVE_TAG live in the tag table, not on the row (#748).
     * @return true when a tag was actually added or removed.
     */
    private suspend fun persistTagActions(transactionId: Long, applications: List<RuleApplication>): Boolean {
        val (add, remove) = applications.tagChanges()
        if (add.isEmpty() && remove.isEmpty()) return false
        return tagRepository.applyTagChanges(transactionId, add, remove)
    }

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

                    // A field change is real by construction; a tag action is recorded
                    // even when it changes nothing, so ask the tag write whether it did.
                    // Otherwise the completion count disagrees with the preview.
                    val entityChanged = ruleApplications.isNotEmpty() && updatedTransaction != transaction
                    if (entityChanged) transactionRepository.updateTransaction(updatedTransaction)
                    val tagsChanged = persistTagActions(transaction.id, ruleApplications)
                    if (entityChanged || tagsChanged) {
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

                    // A field change is real by construction; a tag action is recorded
                    // even when it changes nothing, so ask the tag write whether it did.
                    // Otherwise the completion count disagrees with the preview.
                    val entityChanged = ruleApplications.isNotEmpty() && updatedTransaction != transaction
                    if (entityChanged) transactionRepository.updateTransaction(updatedTransaction)
                    val tagsChanged = persistTagActions(transaction.id, ruleApplications)
                    if (entityChanged || tagsChanged) {
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
        // One read for every transaction's tags rather than one query per row: the
        // preview has to compare a tag rule against what is already there, or a
        // transaction that already carries the tag is reported as an update.
        val tagsByTransaction = tagRepository.observeTransactionTagNames().first()
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

            // A field change is a real change by construction (the engine only records
            // one when the value differs). A tag action is recorded unconditionally,
            // so diff it against the tags the row already has: adding a tag that is
            // already present is not an update.
            val existingTags = tagsByTransaction[transaction.id].orEmpty()
            val newTags = applications.applyTagActions(existingTags)
            val tagChanges = if (newTags === existingTags) emptyList() else {
                (newTags - existingTags.toSet()).map { "+" + it } +
                    (existingTags - newTags.toSet()).map { "-" + it }
            }
            if (updated != transaction || tagChanges.isNotEmpty()) {
                totalMatched++
                if (diffs.size < maxSamples) {
                    diffs.add(
                        TransactionDiff(
                            original = transaction,
                            modified = updated,
                            isBlock = false,
                            tagChanges = tagChanges
                        )
                    )
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
    val isBlock: Boolean,
    /** Tag actions the rule would apply, as "+Name" / "-Name" (tags aren't on the entity). */
    val tagChanges: List<String> = emptyList()
)