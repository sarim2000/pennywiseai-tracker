package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.mapper.toEntity
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalTime
import java.time.temporal.ChronoUnit

internal class StatementImportProcessor(
    private val transactionStore: TransactionStore
) {
    interface TransactionStore {
        suspend fun getTransactionByHash(transactionHash: String): TransactionEntity?
        suspend fun findStatementMergeCandidate(transaction: TransactionEntity): TransactionEntity?
        /** Active transactions carrying [reference] (a UPI/IMPS/NEFT reference or UTR). */
        suspend fun getTransactionsByReference(reference: String): List<TransactionEntity> = emptyList()
        suspend fun updateTransaction(transaction: TransactionEntity)
        suspend fun getTransactionByAmountAndDate(
            amount: BigDecimal,
            dateStart: java.time.LocalDateTime,
            dateEnd: java.time.LocalDateTime
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
     *
     * Each stored transaction absorbs at most one statement row ([claimed]):
     * two identical ₹20 teas on a day with one SMS are one match and one new
     * row, not two matches. Matching runs strongest-first across all rows —
     * exact re-imports, then references, then the row's own day, then a day
     * either side — so weaker evidence never takes a stored transaction that
     * stronger evidence for another row points at. An SMS stamped the day after
     * the statement's value date still matches, without stealing the SMS of
     * the row that really is from that day.
     */
    suspend fun plan(parsedTransactions: List<ParsedTransaction>): StatementImportPlan {
        val pending = HashMap<Long, TransactionEntity>()
        val claimed = HashSet<Long>()
        fun TransactionEntity.current() = pending[id] ?: this
        fun claim(existing: TransactionEntity) = claimed.add(existing.id)
        fun enrichDecision(existing: TransactionEntity, merged: TransactionEntity): StatementRowDecision {
            claim(existing)
            pending[existing.id] = merged
            return StatementRowDecision.Enrich(merged)
        }
        fun matchDecision(existing: TransactionEntity, statement: TransactionEntity, reason: StatementRowDecision.SkipReason): StatementRowDecision {
            val merged = StatementTransactionEnricher.enrich(existing, statement)
            return if (!existing.isDeleted && StatementTransactionEnricher.hasEnrichment(existing, merged)) {
                enrichDecision(existing, merged)
            } else {
                claim(existing)
                StatementRowDecision.Skip(reason)
            }
        }

        // The statement's running balance is for the closing-balance check only:
        // stored on a noon-stamped row it would read as a bank-reported balance.
        val entities = parsedTransactions.map { it.toEntity().copy(balanceAfter = null) }

        /** Same amount within [days] of the row, same direction/account/currency, not yet claimed. */
        suspend fun amountMatch(index: Int, days: Long): StatementRowDecision? {
            val statement = entities[index]
            val day = statement.dateTime.toLocalDate()
            val candidates = transactionStore.getTransactionByAmountAndDate(
                parsedTransactions[index].amount,
                day.minusDays(days).atStartOfDay(),
                day.plusDays(days).atTime(LocalTime.MAX)
            ).map { it.current() }
                .filter { it.id !in claimed && StatementTransactionEnricher.isPossibleAmountDateMatch(it, statement) }
                .sortedBy { ChronoUnit.DAYS.between(day, it.dateTime.toLocalDate()).let(Math::abs) }
            if (candidates.isEmpty()) return null
            // Only enrich when exactly one candidate could be this row; otherwise just match.
            val enrichable = candidates.filter { StatementTransactionEnricher.isAmountDateFallbackEnrichmentCandidate(it, statement) }
            if (enrichable.size == 1 && candidates.size == 1) {
                val existing = enrichable.single()
                return enrichDecision(existing, StatementTransactionEnricher.enrich(existing, statement))
            }
            claim(candidates.first())
            return StatementRowDecision.Skip(StatementRowDecision.SkipReason.AMOUNT_DATE)
        }

        // Strongest evidence first, for every row, before any weaker match may claim
        // a stored transaction: otherwise a row that only shares an amount could take
        // the very transaction another row is an exact re-import of.
        val decisions = arrayOfNulls<StatementRowDecision>(parsedTransactions.size)

        // 1. Exact re-imports.
        parsedTransactions.forEachIndexed { i, parsed ->
            val hash = parsed.transactionHash?.takeIf { it.isNotBlank() } ?: parsed.generateTransactionId()
            transactionStore.getTransactionByHash(hash)?.current()?.let { hashMatch ->
                decisions[i] = matchDecision(hashMatch, entities[i], StatementRowDecision.SkipReason.HASH)
            }
        }

        // 2. References. A reference is unique, so its match may sit a few days off:
        //    an SMS is stamped when it arrives, a statement row on its value date.
        parsedTransactions.forEachIndexed { i, parsed ->
            if (decisions[i] != null) return@forEachIndexed
            val ref = parsed.reference?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val statement = entities[i]
            val byReference = transactionStore.findStatementMergeCandidate(statement)?.current()
                ?.takeIf { it.id !in claimed }
                ?: transactionStore.getTransactionsByReference(ref)
                    .map { it.current() }
                    .filter { it.id !in claimed && StatementTransactionEnricher.isReferenceMatch(it, statement, REFERENCE_WINDOW) }
                    .minByOrNull { Duration.between(it.dateTime, statement.dateTime).abs() }
            if (byReference != null) {
                decisions[i] = matchDecision(byReference, statement, StatementRowDecision.SkipReason.REFERENCE)
            }
        }

        // 3. Same amount on the row's own day, then 4. a day either side.
        for (days in 0L..1L) {
            for (i in decisions.indices) {
                if (decisions[i] == null) decisions[i] = amountMatch(i, days)
            }
        }

        return StatementImportPlan(
            parsedTransactions.indices.map { i ->
                StatementPlanRow(parsedTransactions[i], decisions[i] ?: StatementRowDecision.Insert(entities[i]))
            }
        )
    }

    private companion object {
        val REFERENCE_WINDOW: Duration = Duration.ofDays(3)
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
