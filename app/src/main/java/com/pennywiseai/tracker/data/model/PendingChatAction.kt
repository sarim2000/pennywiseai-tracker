package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.math.BigDecimal
import java.time.LocalDate

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
        // An amount or a day the user stated is a hard constraint, never a tie-breaker:
        // a wrong row on a delete/update card is worse than "not found".
        val wantedDay = daysAgo?.takeIf { it >= 0 }?.let { today.minusDays(it.toLong()) }
        val wantedAmount = amount?.takeIf { it.signum() > 0 }
        if (tokens.isEmpty() && wantedAmount == null && wantedDay == null) return null
        return candidates
            .asSequence()
            .filter { !it.isDeleted }
            .filter { wantedAmount == null || it.amount.compareTo(wantedAmount) == 0 }
            .filter { wantedDay == null || it.dateTime.toLocalDate() == wantedDay }
            .map { tx -> tx to score(tx, tokens) }
            .filter { (_, s) -> s > 0 || tokens.isEmpty() }
            .maxWithOrNull(compareBy({ it.second }, { it.first.dateTime }))
            ?.first
    }

    private fun score(tx: TransactionEntity, tokens: List<String>): Int {
        val hay = listOfNotNull(tx.merchantName, tx.category, tx.description).joinToString(" ").lowercase()
        return tokens.count { hay.contains(it) }
    }

    private val STOP = setOf("the", "one", "from", "for", "and", "that", "this", "delete", "remove", "update", "change", "yesterday", "today", "transaction", "entry")

    /** A [findBest] result; [loosened] when the model's amount had to be dropped to find it. */
    data class Match(val transaction: TransactionEntity, val loosened: Boolean)

    /**
     * [findBest] with the model's day and amount first, then without the day,
     * then without the amount, then without both. A small model fills those
     * fields even when the user never said them (often days_ago = 0, "today"),
     * and as hard filters a guess hides the row the user meant. Dropping the
     * day isn't flagged: it's almost always the model's own guess, and the card
     * shows the date. Dropping the amount is, so the reply says it's the
     * closest one. Nothing changes until the user taps the card.
     */
    fun findWithFallback(
        candidates: List<TransactionEntity>,
        words: String,
        amount: BigDecimal?,
        daysAgo: Int?,
        today: LocalDate = LocalDate.now()
    ): Match? {
        findBest(candidates, words, amount, daysAgo, today)?.let { return Match(it, false) }
        if (daysAgo != null) findBest(candidates, words, amount, null, today)?.let { return Match(it, false) }
        if (amount == null) return null
        return (findBest(candidates, words, null, daysAgo, today) ?: findBest(candidates, words, null, null, today))
            ?.let { Match(it, true) }
    }
}
