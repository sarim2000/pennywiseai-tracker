package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.ledger.countedAmount
import com.pennywiseai.tracker.data.ledger.keepTransferLeg
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.loan.LoanMath
import com.pennywiseai.tracker.domain.loan.SplitRules
import com.pennywiseai.tracker.domain.model.rule.RuleApplication
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.model.rule.applyTagActions
import com.pennywiseai.tracker.domain.model.rule.tagChanges
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
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
    private val ruleEngine: RuleEngine,
    private val tagRepository: TagRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val database: PennyWiseDatabase
) {
    /**
     * Writes a rule-edited row and moves the balances it touches, as a manual edit
     * does. Runs inside the row's transaction: a row that commits without its balance
     * shift can't be repaired by re-applying the rule, which then sees nothing to change.
     */
    private suspend fun updateWithBalances(original: TransactionEntity, updated: TransactionEntity) {
        accountBalanceRepository.pinManualOpenings(original, updated)
        transactionRepository.updateTransaction(updated)
        accountBalanceRepository.reflectTransactionEdit(original, updated)
    }

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
     * What a rule's result may write over [transaction]: a split bill (#346) keeps its
     * type and currency (SplitRules.keepSplitBill), a loan-linked row its money
     * direction (#876, LoanMath.keepLoanDirection), a paired transfer leg its type
     * (keepTransferLeg). Apply and preview both go through here so they agree.
     */
    private fun guarded(transaction: TransactionEntity, ruleResult: TransactionEntity): TransactionEntity =
        LoanMath.keepLoanDirection(
            transaction,
            keepTransferLeg(transaction, SplitRules.keepSplitBill(transaction, ruleResult))
        )

    /** Apply a specific rule to all past transactions */
    suspend fun applyRuleToAllTransactions(
        rule: TransactionRule,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchApplyResult = applyPaged(listOf(rule), onlyNeedingCategory = false, onProgress)

    /** Apply a specific rule to the transactions that still need a category */
    suspend fun applyRuleToUncategorizedTransactions(
        rule: TransactionRule,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchApplyResult = applyPaged(listOf(rule), onlyNeedingCategory = true, onProgress)

    /** Apply all active rules to all past transactions */
    suspend fun applyAllActiveRulesToTransactions(
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchApplyResult = applyPaged(ruleRepository.getActiveRules(), onlyNeedingCategory = false, onProgress)

    /** A row waiting for its write; [write] returns true when the row (and its history) changed. */
    private class PendingWrite(val id: Long, val isDelete: Boolean, val write: suspend () -> Boolean)

    /**
     * Walks the ledger a page at a time by id, so only one page is in memory. Rows that
     * need a write are committed in chunks of [WRITE_CHUNK], one transaction per chunk
     * (a transaction per row made big runs several times slower). A chunk that fails has
     * rolled back and is replayed row by row, each row with its rule-history rows in one
     * transaction: a failed history insert rolls that row back and it isn't counted. If
     * the cause is a rule deleted mid-run, the run stops there rather than failing every
     * remaining row.
     */
    private suspend fun applyPaged(
        rules: List<TransactionRule>,
        onlyNeedingCategory: Boolean,
        onProgress: (processed: Int, total: Int) -> Unit
    ): BatchApplyResult {
        // Rows that arrive during the run already had the rules applied on their way in.
        val upTo = transactionRepository.maxLiveTransactionId() ?: return BatchApplyResult(0, 0)
        val total = transactionRepository.countLiveTransactions()
        var scanned = 0
        var totalProcessed = 0
        var totalUpdated = 0
        var totalDeleted = 0
        val errors = mutableListOf<String>()
        var after = 0L
        val pending = mutableListOf<PendingWrite>()

        fun count(item: PendingWrite) {
            if (item.isDelete) totalDeleted++ else totalUpdated++
        }

        suspend fun ruleDeleted() = rules.any { ruleRepository.getRuleById(it.id) == null }

        // Commits the pending rows. Returns true when the run must stop (a rule was deleted).
        suspend fun flush(): Boolean {
            if (pending.isEmpty()) return false
            val chunk = pending.toList()
            pending.clear()
            try {
                val results = database.withTransaction { chunk.map { it.write() } }
                chunk.forEachIndexed { i, item -> if (results[i]) count(item) }
                return false
            } catch (e: Exception) {
                // A cancelled run stops; it doesn't replay.
                if (e is kotlinx.coroutines.CancellationException) throw e
                // The chunk rolled back; find the row at fault by writing them one by one.
            }
            for (item in chunk) {
                try {
                    if (database.withTransaction { item.write() }) count(item)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (ruleDeleted()) {
                        errors.add("A rule was deleted while it was being applied. The run stopped there.")
                        return true
                    }
                    errors.add("Error processing transaction ${item.id}: ${e.message}")
                }
            }
            return false
        }

        pages@ while (true) {
            val page = transactionRepository.getTransactionsPageAfter(after, upTo, PAGE_SIZE)
            if (page.isEmpty()) break
            after = page.last().id
            // One read for the page's tags, so rows a tag action wouldn't change open no transaction.
            val tagsByTransaction = tagRepository.getTagNamesFor(page.map { it.id })

            for (transaction in page) {
                scanned++
                onProgress(scanned, maxOf(total, scanned))

                // Skip deleted rows, and rows that aren't waiting for a category when asked (same test as the list's "?")
                if (transaction.isDeleted) continue
                if (onlyNeedingCategory && !transaction.awaitsCategoryForRules()) continue
                totalProcessed++

                try {
                    val smsBody = transaction.smsBody
                    if (ruleEngine.shouldBlockTransaction(transaction, smsBody, rules) != null) {
                        pending.add(
                            PendingWrite(transaction.id, isDelete = true) {
                                transactionRepository.updateTransaction(transaction.copy(isDeleted = true))
                                true
                            }
                        )
                    } else {
                        val (ruleResult, rawApplications) = ruleEngine.evaluateRules(transaction, smsBody, rules)

                        // A field change is real by construction; a tag action is recorded
                        // even when it changes nothing, so ask the tag write whether it did.
                        // Otherwise the completion count disagrees with the preview.
                        // What a guard undid isn't recorded as applied.
                        val updatedTransaction = guarded(transaction, ruleResult)
                        val ruleApplications = rawApplications.landedOn(updatedTransaction, ruleResult)
                        val entityChanged = ruleApplications.isNotEmpty() && updatedTransaction != transaction
                        // Same test as the preview. Most rows have nothing to write, and a
                        // transaction for each made a run take a minute whatever the rule matched.
                        val existingTags = tagsByTransaction[transaction.id].orEmpty()
                        val newTags = ruleApplications.applyTagActions(existingTags)
                        val tagsWouldChange = newTags !== existingTags && newTags.toSet() != existingTags.toSet()
                        if (entityChanged || tagsWouldChange) {
                            pending.add(
                                PendingWrite(transaction.id, isDelete = false) {
                                    if (entityChanged) updateWithBalances(transaction, updatedTransaction)
                                    val tagsChanged = persistTagActions(transaction.id, ruleApplications)
                                    (entityChanged || tagsChanged).also {
                                        if (it) ruleRepository.saveRuleApplications(ruleApplications)
                                    }
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (ruleDeleted()) {
                        if (!flush()) errors.add("A rule was deleted while it was being applied. The run stopped there.")
                        break@pages
                    }
                    errors.add("Error processing transaction ${transaction.id}: ${e.message}")
                }
                if (pending.size >= WRITE_CHUNK && flush()) break@pages
            }
            if (flush()) break@pages
        }

        return BatchApplyResult(
            totalProcessed = totalProcessed,
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
        val upTo = transactionRepository.maxLiveTransactionId()
            ?: return DryRunResult(0, 0, 0, 0, emptyList())
        val diffs = mutableListOf<TransactionDiff>()
        var totalScanned = 0
        var totalMatched = 0
        var totalWouldBlock = 0
        var after = 0L

        while (true) {
            val page = transactionRepository.getTransactionsPageAfter(after, upTo, PAGE_SIZE)
            if (page.isEmpty()) break
            after = page.last().id
            // One read for the page's tags rather than one query per row: the preview has
            // to compare a tag rule against what is already there, or a transaction that
            // already carries the tag is reported as an update.
            val tagsByTransaction = tagRepository.getTagNamesFor(page.map { it.id })

            for (transaction in page) {
                if (transaction.isDeleted) continue
                totalScanned++

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

                val (ruleResult, rawApplications) = ruleEngine.evaluateRules(
                    transaction, smsBody, listOf(rule)
                )
                // Preview what apply will really write.
                val updated = guarded(transaction, ruleResult)
                val applications = rawApplications.landedOn(updated, ruleResult)

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
        }

        return DryRunResult(
            totalScanned = totalScanned,
            totalMatched = totalMatched,
            totalWouldBlock = totalWouldBlock,
            totalWouldUpdate = totalMatched - totalWouldBlock,
            samples = diffs
        )
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val WRITE_CHUNK = 50
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

/**
 * These rule applications as they actually landed: when a guard
 * ([com.pennywiseai.tracker.domain.loan.SplitRules.keepSplitBill], loan direction or
 * transfer leg) turned [raw] into [kept], the type or amount change it undid isn't
 * recorded, and an application left with nothing is dropped.
 */
internal fun List<RuleApplication>.landedOn(kept: TransactionEntity, raw: TransactionEntity): List<RuleApplication> {
    if (kept == raw) return this
    val typeUndone = kept.transactionType != raw.transactionType
    val amountUndone = kept.amount.compareTo(raw.amount) != 0
    return mapNotNull { app ->
        val mods = app.fieldsModified.filterNot {
            (typeUndone && it.field == TransactionField.TYPE) || (amountUndone && it.field == TransactionField.AMOUNT)
        }
        if (mods.isEmpty()) null else app.copy(fieldsModified = mods)
    }
}

/** Blank, or Others under its current name, as before; plus #1475: a row that counts for nothing isn't asked about. */
fun TransactionEntity.awaitsCategoryForRules(): Boolean =
    (category.isBlank() || category == BuiltinCategoryNames.current("Others")) && countedAmount().signum() > 0
