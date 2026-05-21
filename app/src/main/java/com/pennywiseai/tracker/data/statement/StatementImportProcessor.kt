package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.mapper.toEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal class StatementImportProcessor(
    private val transactionStore: TransactionStore
) {
    interface TransactionStore {
        suspend fun getTransactionByHash(transactionHash: String): TransactionEntity?
        suspend fun findStatementMergeCandidate(transaction: TransactionEntity): TransactionEntity?
        suspend fun updateTransaction(transaction: TransactionEntity)
        suspend fun getTransactionByAmountAndDate(
            amount: BigDecimal,
            dateStart: LocalDateTime,
            dateEnd: LocalDateTime
        ): List<TransactionEntity>

        suspend fun insertTransactions(transactions: List<TransactionEntity>)
    }

    suspend fun process(parsedTransactions: List<ParsedTransaction>): StatementImportResult.Success {
        var skippedByHash = 0
        var skippedByReference = 0
        var skippedByAmountDate = 0
        var enriched = 0

        val toInsert = parsedTransactions.mapNotNull { parsed ->
            val hash = parsed.transactionHash?.takeIf { it.isNotBlank() }
                ?: parsed.generateTransactionId()
            val statementEntity = parsed.toEntity()

            if (transactionStore.getTransactionByHash(hash) != null) {
                skippedByHash++
                return@mapNotNull null
            }

            val ref = parsed.reference
            if (!ref.isNullOrBlank()) {
                val existing = transactionStore.findStatementMergeCandidate(statementEntity)
                if (existing != null) {
                    val merged = StatementTransactionEnricher.enrich(existing, statementEntity)
                    if (StatementTransactionEnricher.hasEnrichment(existing, merged)) {
                        transactionStore.updateTransaction(merged)
                        enriched++
                    } else {
                        skippedByReference++
                    }
                    return@mapNotNull null
                }
            }

            // Reference-bearing statement rows can still match older SMS imports
            // that were saved before parsers extracted UPI references.
            val dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(parsed.timestamp),
                ZoneId.systemDefault()
            )
            val startOfDay = dateTime.toLocalDate().atStartOfDay()
            val endOfDay = dateTime.toLocalDate().atTime(LocalTime.MAX)

            val amountMatches = transactionStore.getTransactionByAmountAndDate(
                parsed.amount, startOfDay, endOfDay
            )
            val fallbackMergeCandidates = amountMatches.filter { candidate ->
                StatementTransactionEnricher.isFallbackStatementMatch(candidate, statementEntity)
            }
            if (fallbackMergeCandidates.size == 1) {
                val existing = fallbackMergeCandidates.single()
                transactionStore.updateTransaction(
                    StatementTransactionEnricher.enrich(existing, statementEntity)
                )
                enriched++
                return@mapNotNull null
            }

            if (amountMatches.isNotEmpty()) {
                skippedByAmountDate++
                return@mapNotNull null
            }

            statementEntity
        }

        if (toInsert.isNotEmpty()) {
            transactionStore.insertTransactions(toInsert)
        }

        val skippedDuplicates = skippedByHash + skippedByReference + skippedByAmountDate

        return StatementImportResult.Success(
            imported = toInsert.size,
            skippedDuplicates = skippedDuplicates,
            skippedByReference = skippedByReference,
            skippedByAmountDate = skippedByAmountDate,
            skippedByHash = skippedByHash,
            enriched = enriched,
            totalParsed = parsedTransactions.size
        )
    }
}
