package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.time.Duration

/**
 * A debit and a credit marked as one transfer (#385, #614) stay two rows, each
 * carrying the whole transfer's from/to. Unlike a standalone transfer row, which
 * moves both accounts, each row of a pair is one leg: it moves only its own
 * account, and the pair counts once in a total.
 */
val TransactionEntity.isPairedTransferLeg: Boolean
    get() = transactionType == TransactionType.TRANSFER && transferPairId != null

/** EXPENSE if this row is the transfer's source, INCOME if its destination, else TRANSFER. */
private fun TransactionEntity.ownLegType(): TransactionType {
    val isFrom = accountNumber == fromAccount && (fromBankName == null || bankName == fromBankName)
    val isTo = accountNumber == toAccount && (toBankName == null || bankName == toBankName)
    return when {
        isFrom && !isTo -> TransactionType.EXPENSE
        isTo && !isFrom -> TransactionType.INCOME
        else -> TransactionType.TRANSFER
    }
}

/** The type this row moves its account as: a paired leg is the debit or credit it was. */
fun TransactionEntity.ledgerType(): TransactionType =
    if (isPairedTransferLeg) ownLegType() else transactionType

/** Drops the second leg of each pair. The source leg is the one kept: with a fee it's what was sent. */
fun <T> Iterable<T>.countingTransferPairsOnce(row: (T) -> TransactionEntity): List<T> {
    val paired = filter { row(it).isPairedTransferLeg }
    if (paired.isEmpty()) return toList()
    val counted = paired.groupBy { row(it).transferPairId }.values
        .map { legs -> legs.firstOrNull { row(it).ledgerType() != TransactionType.INCOME } ?: legs.first() }
        .toSet()
    return filter { !row(it).isPairedTransferLeg || it in counted }
}

/**
 * Finds the pairs among TRANSFER rows marked before [TransactionEntity.transferPairId]
 * existed, as (source id, destination id). Rows that [lookMarkedTogether] come first:
 * that finds pairs with a fee or a long gap (#614), and keeps a fee pair from taking
 * another pair's equal amount. Then the same amount close in time, for a row edited
 * since. Each pass links the closest dates on a route first, so an older standalone
 * transfer can't take a nearer source's destination.
 *
 * A deleted leg isn't linked: the row left behind still stands for the whole transfer.
 */
fun findUnlinkedTransferPairs(rows: List<TransactionEntity>): List<Pair<Long, Long>> {
    val pairs = mutableListOf<Pair<Long, Long>>()
    rows.filter { it.transactionType == TransactionType.TRANSFER && it.transferPairId == null && !it.isDeleted }
        .groupBy { listOf(it.fromAccount, it.fromBankName, it.toAccount, it.toBankName) }
        .values
        .forEach { route ->
            val sources = route.filter { it.ownLegType() == TransactionType.EXPENSE }.toMutableSet()
            val destinations = route.filter { it.ownLegType() == TransactionType.INCOME }.toMutableSet()
            fun link(matches: (TransactionEntity, TransactionEntity) -> Boolean) {
                sources.flatMap { source -> destinations.filter { matches(source, it) }.map { source to it } }
                    .sortedBy { (source, destination) -> dateGap(source, destination) }
                    .forEach { (source, destination) ->
                        if (source in sources && destination in destinations) {
                            sources -= source
                            destinations -= destination
                            pairs += source.id to destination.id
                        }
                    }
            }
            link(::lookMarkedTogether)
            link { source, destination ->
                destination.currency == source.currency && destination.amount.compareTo(source.amount) == 0 &&
                    dateGap(source, destination) <= MATCH_WINDOW
            }
        }
    return pairs
}

/**
 * Marking stamps both rows at once, but so does a bulk edit (a category by merchant),
 * so the stamp alone proves nothing: the rows must also read as one transfer.
 * ponytail: a real pair outside these bounds stays unlinked and counts as it did before.
 */
private fun lookMarkedTogether(source: TransactionEntity, destination: TransactionEntity): Boolean =
    Duration.between(source.updatedAt, destination.updatedAt).abs() <= MARK_WINDOW &&
        dateGap(source, destination) <= MARKED_MAX_GAP &&
        (destination.currency != source.currency || destination.amount <= source.amount)

private fun dateGap(source: TransactionEntity, destination: TransactionEntity): Duration =
    Duration.between(source.dateTime, destination.dateTime).abs()

/** Matches the window that suggests a pair ([computeSelfTransferPairs]). */
private val MATCH_WINDOW: Duration = Duration.ofMinutes(60)

/** Marking saves the two rows one after the other, so their updatedAt differs by milliseconds. */
private val MARK_WINDOW: Duration = Duration.ofSeconds(1)

/** The longest a bank transfer plausibly takes to arrive. */
private val MARKED_MAX_GAP: Duration = Duration.ofDays(7)
