package com.pennywiseai.tracker.domain.warranty

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import java.time.LocalDate

/**
 * Which warranties to remind about today. A reminder goes out 30 days and 1 day
 * before a warranty's last day. Each run covers the days since the last one
 * ([lastRun], exclusive) up to [today], so a day the worker missed (Doze, a phone
 * that was off) is caught up, and a warranty that has already ended is never
 * reminded. When both reminders fell in the gap, the purchase is listed once.
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
            !txn.isDeleted && !until.isBefore(today) && DAYS_BEFORE.any { days ->
                val remindOn = until.minusDays(days)
                remindOn.isAfter(since) && !remindOn.isAfter(today)
            }
        }
    }
}
