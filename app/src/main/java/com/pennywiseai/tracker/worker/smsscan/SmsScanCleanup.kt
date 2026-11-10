package com.pennywiseai.tracker.worker.smsscan

import android.util.Log
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.manager.TransactionDeduplication
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.repository.UnrecognizedSmsRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import kotlinx.coroutines.CancellationException

private const val TAG = "OptimizedSmsReaderWorker"

// GPay P2P is parsed by SBIBankParser and attributed to this partner bank; it is
// the one dedup collapses and the only one that leaves a phantom account (#456).
// Matches SBIBankParser.getBankName() and the literal in TransactionDeduplication.
private const val GPAY_PARTNER_BANK = "State Bank of India"

/** What a finished scan tidies up: stale unrecognized rows, duplicates left by earlier scans, the AI prompt. */
internal class SmsScanCleanup(
    private val unrecognizedSmsRepository: UnrecognizedSmsRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ruleRepository: RuleRepository,
    private val llmRepository: LlmRepository,
    private val database: PennyWiseDatabase,
) {
    suspend fun run(savedAny: Boolean) {
        try { unrecognizedSmsRepository.cleanupOldEntries() }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
        try {
            val smsDuplicates = cleanupSmsDuplicates()
            if (smsDuplicates > 0) {
                Log.i(TAG, "Removed $smsDuplicates duplicate rows of the same SMS")
            }
            val deletedDuplicates = cleanupExistingGPayDuplicates()
            if (deletedDuplicates > 0) {
                Log.i(TAG, "Cleaned up $deletedDuplicates existing GPay duplicate transactions")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "GPay duplicate cleanup error: ${e.message}")
        }
        if (savedAny) {
            try { llmRepository.updateSystemPrompt() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Prompt update error: ${e.message}")
            }
        }
    }

    /**
     * Earlier rescans could store one SMS twice when a parser fix changed its
     * amount (#886). Keep the oldest row — it carries the user's category, notes
     * and tags — repairing its amount from the newest read when the old one is a
     * known parser artifact, and remove the rest the same way GPay duplicates are
     * removed.
     */
    private suspend fun cleanupSmsDuplicates(): Int {
        var removed = 0
        transactionRepository.getActiveSmsDuplicateRows()
            .groupBy { it.smsBody to (it.smsSender ?: "") }
            .values
            .forEach { group ->
                val keep = group.minBy { it.id }
                val newest = group.maxBy { it.id }
                // One transaction per SMS: an interrupted run must not leave a
                // duplicate soft-deleted with its balance rows or rule
                // applications behind — once deleted it no longer shows up here.
                database.withTransaction {
                    if (TransactionDeduplication.isParseArtifact(keep, newest)) {
                        transactionRepository.correctParsedAmount(keep, newest)
                    }
                    group.filter { it.id != keep.id }.forEach { dup ->
                        // Goal contributions on the duplicate move to [keep] (#914); a
                        // duplicate whose split shares can't move is kept (#346).
                        if (!transactionRepository.deleteDuplicate(dup.id, keep.id)) return@forEach
                        accountBalanceRepository.deleteBalancesForTransaction(dup.id)
                        ruleRepository.deleteRuleApplicationsForTransaction(dup.id.toString())
                        removed++
                    }
                }
            }
        return removed
    }

    private suspend fun cleanupExistingGPayDuplicates(): Int {
        val duplicates = transactionRepository.findGPayDuplicatesForCleanup()
        var removed = 0
        duplicates.forEach { (id, keeperId) ->
            if (!transactionRepository.deleteDuplicate(id, keeperId)) return@forEach
            accountBalanceRepository.deleteBalancesForTransaction(id)
            ruleRepository.deleteRuleApplicationsForTransaction(id.toString())
            removed++
        }

        prunePhantomGPayAccounts()
        return removed
    }

    /**
     * Remove the phantom account GPay dedup leaves behind (#456). GPay P2P is attributed
     * to the partner bank ("State Bank of India"); once its transactions are deduped away
     * against the real bank's SMS, balance rows that were never linked to a transaction
     * (transaction_id NULL) survive [deleteBalancesForTransaction] and surface as an
     * account with an inflated balance but no transactions.
     *
     * Sweep the partner-bank accounts and delete any that is purely a residue. The
     * per-account qualification and delete run atomically in a single DB transaction
     * (see [AccountBalanceRepository.deletePhantomGPayAccountIfQualifies]) so a
     * concurrent real-time balance insert can't be erased between the check and the
     * delete. Also cleans phantoms that predate this fix, not just fresh ones.
     */
    private suspend fun prunePhantomGPayAccounts() {
        val partnerBank = GPAY_PARTNER_BANK
        accountBalanceRepository.getAccountLast4sForBank(partnerBank).forEach { account ->
            val removed = accountBalanceRepository.deletePhantomGPayAccountIfQualifies(partnerBank, account)
            if (removed > 0) {
                Log.i(TAG, "Removed phantom GPay account ($removed orphaned balance rows)")
            }
        }
    }
}
