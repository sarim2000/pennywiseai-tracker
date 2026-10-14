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

    /**
     * The day to search, only if the user's own [userText] names one. A small
     * model fills `days_ago` even when no day was said (often 0 = "today"),
     * and a day is a hard filter, so a guessed one hides every older row.
     * Today / yesterday / "N days ago" are read from the text itself; any
     * other time phrase (a weekday, a date) keeps the model's value.
     */
    fun statedDaysAgo(modelDaysAgo: Int?, userText: String): Int? {
        val text = userText.lowercase()
        Regex("""\b(\d{1,3})\s+days?\s+ago\b""").find(text)?.let { return it.groupValues[1].toInt() }
        if (Regex("""\byesterday\b""").containsMatchIn(text)) return 1
        if (Regex("""\b(today|tonight|this morning|this afternoon|this evening)\b""").containsMatchIn(text)) return 0
        return modelDaysAgo?.takeIf { it >= 0 && OTHER_DAY_WORDS.containsMatchIn(text) }
    }

    /** The amount to search, only if that number appears in the user's own [userText]. */
    fun statedAmount(modelAmount: BigDecimal?, userText: String): BigDecimal? {
        val wanted = modelAmount?.takeIf { it.signum() > 0 } ?: return null
        val said = Regex("""\d[\d,]*(?:\.\d+)?""").findAll(userText)
            .mapNotNull { it.value.replace(",", "").toBigDecimalOrNull() }
        return wanted.takeIf { w -> said.any { it.compareTo(w) == 0 } }
    }

    private val OTHER_DAY_WORDS = Regex(
        """\b(ago|last|monday|tuesday|wednesday|thursday|friday|saturday|sunday|""" +
            """jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|\d{1,2}(st|nd|rd|th)|\d{1,2}[/-]\d{1,2})"""
    )
}
