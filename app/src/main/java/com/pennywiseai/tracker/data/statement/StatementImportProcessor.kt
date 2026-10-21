package com.pennywiseai.tracker.data.statement

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.toEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal class StatementImportProcessor(
    private val transactionStore: TransactionStore
) {
    interface TransactionStore {
        /** Stored transactions (deleted ones included) with any of [hashes]. */
        suspend fun getTransactionsByHashes(hashes: List<String>): List<TransactionEntity>
        /** Active transactions dated within [start]..[end]. */
        suspend fun getTransactionsBetween(start: LocalDateTime, end: LocalDateTime): List<TransactionEntity>
        suspend fun updateTransaction(transaction: TransactionEntity)
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
     * Two reads, whatever the statement's size: the rows' hashes, and every
     * transaction in the statement's date range (plus the matching windows),
     * indexed in memory by reference and by amount.
     *
     * Enrichments are kept in memory while planning, so a later row that hits
     * the same stored transaction sees it already enriched — as it would have
     * when every update was written straight away.
     *
     * Each stored transaction absorbs at most one statement row ([claimed]):
     * two identical ₹20 teas on a day with one SMS are one match and one new
     * row, not two matches. Matching runs strongest-first across all rows —
     * exact re-imports, then references, then amounts — so weaker evidence
     * never takes a stored transaction that stronger evidence for another row
     * points at.
     */
    suspend fun plan(parsedTransactions: List<ParsedTransaction>): StatementImportPlan {
        if (parsedTransactions.isEmpty()) return StatementImportPlan(emptyList())
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
        val days = entities.map { it.dateTime.toLocalDate() }

        val hashes = parsedTransactions.map { it.transactionHash?.takeIf { h -> h.isNotBlank() } ?: it.generateTransactionId() }
        val byHash = transactionStore.getTransactionsByHashes(hashes.distinct()).associateBy { it.transactionHash }
        val nearby = transactionStore.getTransactionsBetween(
            days.min().minusDays(REFERENCE_WINDOW.toDays()).atStartOfDay(),
            days.max().plusDays(REFERENCE_WINDOW.toDays()).atTime(LocalTime.MAX)
        )
        val byReference = nearby.groupBy { StatementTransactionEnricher.normalizedReference(it.reference) }
        // Money in vs out, as [StatementTransactionEnricher.isPossibleAmountDateMatch] compares it.
        fun amountKey(t: TransactionEntity) = t.amount.stripTrailingZeros() to (t.transactionType == TransactionType.INCOME)
        val byAmount = nearby.groupBy(::amountKey)

        /** Unclaimed same-amount transactions on [from]..[to] that could be row [index], earliest first. */
        fun amountCandidates(index: Int, from: LocalDate, to: LocalDate): List<TransactionEntity> =
            byAmount[amountKey(entities[index])].orEmpty()
                .map { it.current() }
                .filter {
                    it.id !in claimed && it.dateTime.toLocalDate() in from..to &&
                        StatementTransactionEnricher.isPossibleAmountDateMatch(it, entities[index])
                }
                .sortedBy { it.dateTime }

        /** A same-day match; it enriches only when exactly one transaction could be this row. */
        fun sameDayMatch(index: Int, candidates: List<TransactionEntity>): StatementRowDecision {
            val only = candidates.singleOrNull()
            if (only != null && StatementTransactionEnricher.isAmountDateFallbackEnrichmentCandidate(only, entities[index])) {
                return enrichDecision(only, StatementTransactionEnricher.enrich(only, entities[index]))
            }
            claim(candidates.first())
            return StatementRowDecision.Skip(StatementRowDecision.SkipReason.AMOUNT_DATE)
        }

        val decisions = arrayOfNulls<StatementRowDecision>(parsedTransactions.size)

        // 1. Exact re-imports.
        hashes.forEachIndexed { i, hash ->
            byHash[hash]?.current()?.let { hashMatch ->
                decisions[i] = matchDecision(hashMatch, entities[i], StatementRowDecision.SkipReason.HASH)
            }
        }

        // 2. References. A reference is unique, so its match may sit a few days off:
        //    an SMS is stamped when it arrives, a statement row on its value date.
        entities.forEachIndexed { i, statement ->
            if (decisions[i] != null) return@forEachIndexed
            val ref = StatementTransactionEnricher.normalizedReference(statement.reference) ?: return@forEachIndexed
            byReference[ref].orEmpty()
                .map { it.current() }
                .filter { it.id !in claimed && StatementTransactionEnricher.isReferenceMatch(it, statement, REFERENCE_WINDOW) }
                .minByOrNull { Duration.between(it.dateTime, statement.dateTime).abs() }
                ?.let { decisions[i] = matchDecision(it, statement, StatementRowDecision.SkipReason.REFERENCE) }
        }

        // 3. Same amount on the row's own day, for rows with a real time.
        val dateOnly = parsedTransactions.map { it.sender in DATE_ONLY_SENDERS }
        for (i in decisions.indices) {
            if (decisions[i] != null || dateOnly[i]) continue
            val candidates = amountCandidates(i, days[i], days[i])
            if (candidates.isNotEmpty()) decisions[i] = sameDayMatch(i, candidates)
        }

        // 4. A date-only row (a bank's value date) may have its SMS on that day or
        //    the next. Latest row first, each taking the latest unclaimed one:
        //    that matches as many rows as possible, so a run of SMS that all lag
        //    a day matches one to one, and a row never takes the next day's SMS
        //    from the row that is really from that day. A next-day SMS is only
        //    evidence of a duplicate, never of its merchant.
        val nextDay = HashMap<Int, TransactionEntity>()
        for (i in decisions.indices.filter { dateOnly[it] }.sortedByDescending { days[it] }) {
            if (decisions[i] != null) continue
            val candidates = amountCandidates(i, days[i], days[i].plusDays(1))
            val latest = candidates.lastOrNull() ?: continue
            decisions[i] = if (latest.dateTime.toLocalDate() == days[i]) {
                sameDayMatch(i, candidates)
            } else {
                claim(latest)
                nextDay[i] = latest
                StatementRowDecision.Skip(StatementRowDecision.SkipReason.AMOUNT_DATE)
            }
        }
        // A row that took a next-day SMS while its own day's SMS went unused
        // swaps to its own day: no fewer matches, and that one can be enriched.
        for ((i, taken) in nextDay) {
            val sameDay = amountCandidates(i, days[i], days[i])
            if (sameDay.isEmpty()) continue
            claimed.remove(taken.id)
            decisions[i] = sameDayMatch(i, sameDay)
        }

        return StatementImportPlan(
            parsedTransactions.indices.map { i ->
                StatementPlanRow(parsedTransactions[i], decisions[i] ?: StatementRowDecision.Insert(entities[i]))
            }
        )
    }

    private companion object {
        val REFERENCE_WINDOW: Duration = Duration.ofDays(3)

        /** Sources whose rows carry a date but no time (stamped at noon): spreadsheets and ICICI PDFs. */
        val DATE_ONLY_SENDERS = setOf(StatementTableParser.STATEMENT_SENDER, ICICI_PDF_SENDER)
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
