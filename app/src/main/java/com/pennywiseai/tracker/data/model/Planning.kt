package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One spending limit as the Budgets screen shows it today: its own window ([start] to [end])
 * and the one [currency] that screen shows it in. Budgets are never added together: two
 * limits over different windows have no shared "remaining".
 */
data class BudgetStatus(
    val name: String,
    val currency: String,
    val limit: BigDecimal,
    val spent: BigDecimal,
    val start: LocalDate,
    val end: LocalDate,
) {
    /** Negative once the limit is passed. */
    val remaining: BigDecimal get() = limit - spent
}

/**
 * Recurring payments going out by [through]: [overdue] were due before today, within their last
 * cycle, and still wait (paying one moves its date on), [upcoming] fall due from today to
 * [through]. Each in its own currency; money coming in is not a bill.
 */
data class BillsDue(
    val through: LocalDate,
    val overdue: List<SubscriptionEntity>,
    val upcoming: List<SubscriptionEntity>,
)
