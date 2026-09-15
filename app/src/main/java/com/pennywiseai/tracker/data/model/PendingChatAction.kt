package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

/** Something the AI proposed doing to the user's data (#170). Applied only after the user confirms. */
sealed class PendingChatAction {
    data class Add(val draft: TransactionDraft) : PendingChatAction()
    data class Delete(val transaction: TransactionEntity) : PendingChatAction()
    data class Update(
        val transaction: TransactionEntity,
        val newCategory: String?,
        val newMerchant: String?
    ) : PendingChatAction()
}

/** Picks the transaction the user most likely means from words, an optional amount and "days ago". */
object TransactionFinder {
    fun findBest(
        candidates: List<TransactionEntity>,
        words: String,
        amount: BigDecimal?,
        daysAgo: Int?,
        today: LocalDate = LocalDate.now()
    ): TransactionEntity? {
        val tokens = words.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in STOP }
        return candidates
            .filter { !it.isDeleted }
            .map { tx -> tx to score(tx, tokens, amount, daysAgo, today) }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy({ it.second }, { it.first.dateTime }))
            ?.first
    }

    private fun score(tx: TransactionEntity, tokens: List<String>, amount: BigDecimal?, daysAgo: Int?, today: LocalDate): Int {
        val hay = listOfNotNull(tx.merchantName, tx.category, tx.description).joinToString(" ").lowercase()
        var s = tokens.count { hay.contains(it) } * 3
        if (amount != null && amount.signum() > 0) {
            if (tx.amount.compareTo(amount) == 0) s += 4 else if (tokens.isEmpty()) return 0
        }
        if (daysAgo != null && daysAgo >= 0) {
            val d = abs(java.time.temporal.ChronoUnit.DAYS.between(tx.dateTime.toLocalDate(), today.minusDays(daysAgo.toLong())).toInt())
            s += when (d) { 0 -> 3; 1 -> 1; else -> 0 }
        }
        return s
    }

    private val STOP = setOf("the", "one", "from", "for", "and", "that", "this", "delete", "remove", "update", "change", "yesterday", "today", "transaction", "entry")
}
