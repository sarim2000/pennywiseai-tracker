package com.pennywiseai.tracker.domain.warranty

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.time.LocalDate

/**
 * Which warranties to remind about today. A reminder goes out 30 days and 1 day
 * before a warranty's last day. Each run covers the days since the last one
 * ([lastRun], exclusive) up to [today], so a day the worker missed (Doze, a phone
 * that was off) is caught up, and a warranty that has already ended is never
 * reminded. When both reminders fell in the gap, the purchase is listed once.
 *
 * A warranty entered after the morning's run, when its 1-day reminder day has
 * already come, would otherwise get no reminder at all; so a purchase that ends
 * today or tomorrow and was changed on or after [lastRun]'s day is reminded too.
 * Only dates are kept, so an edit on that day before its run can bring a second
 * "ends today" heads-up; that's the safer miss.
 */
object WarrantyReminders {

    /** Days before the last covered day that a reminder goes out. */
    val DAYS_BEFORE = listOf(30L, 1L)

    fun due(transactions: List<TransactionEntity>, today: LocalDate, lastRun: LocalDate?): List<TransactionEntity> {
        // First run, or the clock went back: only today's reminders.
        val since = lastRun?.takeIf { !it.isAfter(today) } ?: today.minusDays(1)
        if (since == today) return emptyList()
        return transactions.filter { txn ->
            val until = txn.warrantyUntil ?: return@filter false
            if (txn.isDeleted || until.isBefore(today)) return@filter false
            val onTheDay = DAYS_BEFORE.any { days ->
                val remindOn = until.minusDays(days)
                remindOn.isAfter(since) && !remindOn.isAfter(today)
            }
            val setTooLate = !until.minusDays(1).isAfter(today) && !txn.updatedAt.toLocalDate().isBefore(since)
            onTheDay || setTooLate
        }
    }

    /**
     * What the reminder calls the purchase: the first line of the user's note, which
     * usually names the item, else the merchant (for SMS rows often just the account).
     */
    fun itemName(txn: TransactionEntity): String =
        txn.description?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: txn.merchantName
}
