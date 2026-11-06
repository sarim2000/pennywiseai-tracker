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
 * existed, as (source id, destination id). Rows updated together, as marking does,
 * come first: that finds pairs with a fee or a long gap (#614), and keeps a fee pair
 * from taking another pair's equal amount. Then the same amount close in time, for a
 * row edited since.
 */
fun findUnlinkedTransferPairs(rows: List<TransactionEntity>): List<Pair<Long, Long>> {
    val pairs = mutableListOf<Pair<Long, Long>>()
    rows.filter { it.transactionType == TransactionType.TRANSFER && it.transferPairId == null }
        .groupBy { it.fromAccount to it.toAccount }
        .values
        .forEach { route ->
            val sources = route.filter { it.ownLegType() == TransactionType.EXPENSE }.sortedBy { it.dateTime }
            val destinations = route.filter { it.ownLegType() == TransactionType.INCOME }.toMutableList()
            fun link(source: TransactionEntity, matches: (TransactionEntity) -> Boolean): Boolean {
                val match = destinations.filter(matches)
                    .minByOrNull { Duration.between(source.dateTime, it.dateTime).abs() } ?: return false
                destinations.remove(match)
                pairs += source.id to match.id
                return true
            }
            sources
                .filterNot { source ->
                    link(source) { Duration.between(source.updatedAt, it.updatedAt).abs() <= MARK_WINDOW }
                }
                .forEach { source ->
                    link(source) {
                        it.currency == source.currency && it.amount.compareTo(source.amount) == 0 &&
                            Duration.between(source.dateTime, it.dateTime).abs() <= MATCH_WINDOW
                    }
                }
        }
    return pairs
}

/** Matches the window that suggests a pair ([computeSelfTransferPairs]). */
private val MATCH_WINDOW: Duration = Duration.ofMinutes(60)

/** Marking saves the two rows one after the other, so their updatedAt differs by milliseconds. */
private val MARK_WINDOW: Duration = Duration.ofSeconds(1)
