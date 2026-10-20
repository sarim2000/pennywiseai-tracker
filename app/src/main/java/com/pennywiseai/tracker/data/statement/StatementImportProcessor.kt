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

    /** Match and write in one go: [plan] then [apply] with every new row kept. */
    suspend fun process(parsedTransactions: List<ParsedTransaction>): StatementImportResult.Success =
        apply(plan(parsedTransactions))

    /**
     * Decides, without writing anything, what importing [parsedTransactions]
     * would do to each row (#13). The preview shows this and [apply] executes
     * it, so "N matched, M new" is exactly what the import then does.
     *
     * Enrichments are kept in memory while planning, so a later row that hits
     * the same stored transaction sees it already enriched — as it would have
     * when every update was written straight away.
     */
    suspend fun plan(parsedTransactions: List<ParsedTransaction>): StatementImportPlan {
        val pending = HashMap<Long, TransactionEntity>()
        fun TransactionEntity.current() = pending[id] ?: this
        fun enrichDecision(existing: TransactionEntity, merged: TransactionEntity): StatementRowDecision {
            pending[existing.id] = merged
            return StatementRowDecision.Enrich(merged)
        }

        val rows = parsedTransactions.map { parsed ->
            val hash = parsed.transactionHash?.takeIf { it.isNotBlank() }
                ?: parsed.generateTransactionId()
            val statementEntity = parsed.toEntity()

            val decision = run decide@{
                val hashMatch = transactionStore.getTransactionByHash(hash)?.current()
                if (hashMatch != null) {
                    val merged = StatementTransactionEnricher.enrich(hashMatch, statementEntity)
                    return@decide if (!hashMatch.isDeleted && StatementTransactionEnricher.hasEnrichment(hashMatch, merged)) {
                        enrichDecision(hashMatch, merged)
                    } else {
                        StatementRowDecision.Skip(StatementRowDecision.SkipReason.HASH)
                    }
                }

                val ref = parsed.reference
                if (!ref.isNullOrBlank()) {
                    val existing = transactionStore.findStatementMergeCandidate(statementEntity)?.current()
                    if (existing != null) {
                        val merged = StatementTransactionEnricher.enrich(existing, statementEntity)
                        return@decide if (StatementTransactionEnricher.hasEnrichment(existing, merged)) {
                            enrichDecision(existing, merged)
                        } else {
                            StatementRowDecision.Skip(StatementRowDecision.SkipReason.REFERENCE)
                        }
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
                ).map { it.current() }
                val fallbackMergeCandidates = amountMatches.filter { candidate ->
                    StatementTransactionEnricher.isAmountDateFallbackEnrichmentCandidate(candidate, statementEntity)
                }
                if (fallbackMergeCandidates.size == 1) {
                    val existing = fallbackMergeCandidates.single()
                    return@decide enrichDecision(existing, StatementTransactionEnricher.enrich(existing, statementEntity))
                }

                if (amountMatches.isNotEmpty()) {
                    return@decide StatementRowDecision.Skip(StatementRowDecision.SkipReason.AMOUNT_DATE)
                }

                StatementRowDecision.Insert(statementEntity)
            }
            StatementPlanRow(parsed, decision)
        }
        return StatementImportPlan(rows)
    }

    /**
     * Writes [plan]: every enrichment, and the new rows except the indices in
     * [excluded] (rows the user unticked in the preview).
     */
    suspend fun apply(plan: StatementImportPlan, excluded: Set<Int> = emptySet()): StatementImportResult.Success {
        // The last enrichment of a stored transaction already carries the earlier ones.
        plan.rows.mapNotNull { (it.decision as? StatementRowDecision.Enrich)?.merged }
            .associateBy { it.id }
            .values
            .forEach { transactionStore.updateTransaction(it) }

        val toInsert = plan.rows.mapIndexedNotNull { i, row ->
            (row.decision as? StatementRowDecision.Insert)?.entity?.takeIf { i !in excluded }
        }
        if (toInsert.isNotEmpty()) {
            transactionStore.insertTransactions(toInsert)
        }

        val skippedByHash = plan.count(StatementRowDecision.SkipReason.HASH)
        val skippedByReference = plan.count(StatementRowDecision.SkipReason.REFERENCE)
        val skippedByAmountDate = plan.count(StatementRowDecision.SkipReason.AMOUNT_DATE)

        return StatementImportResult.Success(
            imported = toInsert.size,
            skippedDuplicates = skippedByHash + skippedByReference + skippedByAmountDate,
            skippedByReference = skippedByReference,
            skippedByAmountDate = skippedByAmountDate,
            skippedByHash = skippedByHash,
            enriched = plan.rows.count { it.decision is StatementRowDecision.Enrich },
            totalParsed = plan.rows.size
        )
    }
}

/** What importing one statement row does. */
sealed interface StatementRowDecision {
    /** New to PennyWise: written as [entity]. */
    data class Insert(val entity: TransactionEntity) : StatementRowDecision
    /** Already in PennyWise; the stored row gains the statement's details. */
    data class Enrich(val merged: TransactionEntity) : StatementRowDecision
    /** Already in PennyWise; nothing to write. */
    data class Skip(val reason: SkipReason) : StatementRowDecision

    enum class SkipReason { HASH, REFERENCE, AMOUNT_DATE }
}

data class StatementPlanRow(val parsed: ParsedTransaction, val decision: StatementRowDecision)

data class StatementImportPlan(val rows: List<StatementPlanRow>) {
    /** Rows already in PennyWise (skipped or enriched). */
    val matchedCount: Int get() = rows.count { it.decision !is StatementRowDecision.Insert }
    /** Indices of the rows that would be added. */
    val newIndices: List<Int> get() = rows.indices.filter { rows[it].decision is StatementRowDecision.Insert }

    internal fun count(reason: StatementRowDecision.SkipReason) =
        rows.count { (it.decision as? StatementRowDecision.Skip)?.reason == reason }
}
