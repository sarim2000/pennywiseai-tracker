package com.pennywiseai.tracker.data.manager

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class UPIDeduplicator(
    private val transactionRepository: TransactionRepository
) {
    companion object {
        private const val TAG = "UPIDeduplicator"

        // Both windows unified at 10 minutes so reference check and
        // account+amount+time check cover the same SMS delivery lag
        private const val DEDUP_WINDOW_MINUTES = 10L
    }

    sealed class DeduplicationResult {
        data object NotDuplicate : DeduplicationResult()
        data class Duplicate(val reason: String) : DeduplicationResult()
    }

    /**
     * Checks whether [parsed] is a duplicate of an already-saved transaction,
     * and if not, saves it.
     *
     * Deduplication order:
     *  1. Hash match  — exact same SMS body / computed hash
     *  2. Reference match — same UPI reference within ±[DEDUP_WINDOW_MINUTES]
     *  3. Account + amount + type + time fallback — catches cases where two banks
     *     (e.g. SBI + South Indian Bank) each send an SMS for the same UPI credit/debit
     *     but with slightly different reference numbers.
     */
    suspend fun checkDuplicateAndSave(
        parsed: ParsedTransaction,
        timestamp: Long
    ): DeduplicationResult {
        val transactionTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )

        val windowStart = transactionTime.minus(DEDUP_WINDOW_MINUTES, ChronoUnit.MINUTES)
        val windowEnd   = transactionTime.plus(DEDUP_WINDOW_MINUTES, ChronoUnit.MINUTES)

        // ── Check 1: Hash-based deduplication (exact match) ──────────────────────
        // Compute the hash without allocating a full entity yet.
        val hash = parsed.generateTransactionId()
        val existingByHash = transactionRepository.getTransactionByHash(hash)
        if (existingByHash != null) {
            val reason = if (existingByHash.isDeleted) {
                "Transaction was previously deleted by user"
            } else {
                "Duplicate transaction (same hash)"
            }
            Log.d(TAG, "Hash duplicate detected [$hash]: $reason")
            return DeduplicationResult.Duplicate(reason)
        }

        // ── Check 2: Reference-based deduplication ────────────────────────────────
        // UPI transactions often generate SMS from both the UPI provider (e.g. SBI)
        // and the linked bank (e.g. South Indian Bank). When references match exactly
        // we can identify the duplicate regardless of which bank sent first.
        val upiReference = parsed.reference
        if (!upiReference.isNullOrBlank()) {
            val existingByRef = transactionRepository.getTransactionByReference(
                reference  = upiReference,
                amount     = parsed.amount,
                startDate  = windowStart,
                endDate    = windowEnd
            )
            if (existingByRef != null) {
                Log.d(TAG, "Reference duplicate detected [$upiReference]")
                return DeduplicationResult.Duplicate("Duplicate UPI transaction (same reference: $upiReference)")
            }
        }

        // ── Check 3: Account + amount + type + time window fallback ───────────────
        // Catches the case where reference numbers differ between banks for the same
        // underlying UPI transaction (e.g. SBI ref 528902998134 vs SIB 528908998134).
        // Applied to both INCOME and EXPENSE since debit SMS can also arrive from
        // multiple sources (UPI app notification + bank SMS).
        val accountLast4 = parsed.accountLast4
        if (accountLast4 != null) {
            val entityType = TransactionType.valueOf(parsed.type.name)
            val existingByAccountAmount = transactionRepository.getTransactionByAccountAmountTime(
                accountLast4 = accountLast4,
                amount       = parsed.amount,
                transactionType         = entityType,
                startDate  = windowStart,
                endDate    = windowEnd
            )
            if (existingByAccountAmount != null) {
                Log.d(TAG, "Account+amount+time duplicate detected [acc=$accountLast4, amt=${parsed.amount}, type=${parsed.type}]")
                return DeduplicationResult.Duplicate(
                    "Duplicate transaction (account $accountLast4, amount ${parsed.amount}, within ${DEDUP_WINDOW_MINUTES}min window)"
                )
            }
        }

        // ── No duplicate found — save and return ─────────────────────────────────
        val entity = parsed.toEntity()
        transactionRepository.insertTransaction(entity)
        Log.d(TAG, "Saved new transaction: hash=$hash, ref=$upiReference, acc=$accountLast4")
        return DeduplicationResult.NotDuplicate
    }

}