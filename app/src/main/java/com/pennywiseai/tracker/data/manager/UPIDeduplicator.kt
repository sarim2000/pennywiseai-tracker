package com.pennywiseai.tracker.data.manager

import android.util.Log
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.toParsedTransaction
import com.pennywiseai.tracker.data.mapper.toEntity
import com.pennywiseai.tracker.data.repository.TransactionRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.first // Make sure to import this

class UPIDeduplicator @Inject constructor(
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
     * Checks whether [parsed] is a duplicate of an already-saved transaction.
     * Does NOT save - caller is responsible for saving after performing additional processing.
     *
     * Deduplication order:
     *  1. Hash match  — exact same SMS body / computed hash
     *  2. Reference match — same UPI reference within ±[DEDUP_WINDOW_MINUTES]
     *  3. Account + amount + type + time fallback — catches cases where two banks
     *     (e.g. SBI + South Indian Bank) each send an SMS for the same UPI credit/debit
     *     but with slightly different reference numbers.
     */
    suspend fun checkForDuplicate(
        parsed: ParsedTransaction,
    ): DeduplicationResult {
        val transactionTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(parsed.timestamp),
            ZoneId.systemDefault()
        )

        val windowStart = transactionTime.minus(DEDUP_WINDOW_MINUTES, ChronoUnit.MINUTES)
        val windowEnd   = transactionTime.plus(DEDUP_WINDOW_MINUTES, ChronoUnit.MINUTES)

        // ── Check 1: Hash-based deduplication (exact match) ──────────────────────
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
        val accountLast4 = parsed.accountLast4
        if (accountLast4 != null) {
            val existingByAccountAmount = transactionRepository.getTransactionByAccountAmountTime(
                accountLast4 = accountLast4,
                amount       = parsed.amount,
                transactionType         = parsed.type,
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

        // No duplicate found
        Log.d(TAG, "No duplicate found for: hash=$hash, ref=$upiReference, acc=$accountLast4")
        return DeduplicationResult.NotDuplicate
    }

    suspend fun scanAndRemoveDuplicates(): Int {
        Log.d(TAG, "Starting deduplication scan...")
        var duplicatesRemoved = 0

        try {
            // 1. Get the current list from the Flow once (snapshot)
            val allTransactions = transactionRepository.getAllTransactions().first()

            // 2. Keep track of what we have already "verified" during this scan
            // This prevents the check from matching a transaction against itself.
            val seenHashes = mutableSetOf<String>()

            for (tx in allTransactions) {
                if (tx.isDeleted) continue

                val hash = tx.transactionHash ?: "" // Ensure your entity has the hash

                // 3. Logic: If we haven't seen this hash yet in this loop,
                // it's the "original". Don't delete it; just mark it as seen.
                if (seenHashes.contains(hash)) {
                    // If we HAVE seen it, it's a duplicate. Use your existing check:
                    val result = checkForDuplicate(tx.toParsedTransaction())

                    if (result is DeduplicationResult.Duplicate) {
                        transactionRepository.deleteTransaction(tx, true)
                        duplicatesRemoved++
                        Log.d(TAG, "Removed duplicate: ${tx.id}")
                    }
                } else {
                    seenHashes.add(hash)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during scan: ${e.message}")
        }

        return duplicatesRemoved
    }

}