package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Single source of truth for "how much of this transaction counts toward a
 * total?" (Home, Analytics, Budgets, widgets, AI summaries, webhooks).
 *
 * - Flagged `excludedFromAnalytics`: nothing, a deliberate opt-out (#451).
 * - Not linked to a loan: the whole amount.
 * - Linked to a loan: only the part that isn't the loan. Lending ₹1,000 in full
 *   counts nothing, but a ₹1,000 dinner where ₹400 was lent counts your ₹600,
 *   and ₹500 back on a ₹130 loan counts the extra ₹370 (#831). Before, any
 *   loan-linked row dropped out whole and that remainder vanished.
 *
 * Screen-specific conditions (expense-only, date range, category, ...) are
 * layered on by the caller; this only covers the universal rules (#800).
 */
fun TransactionEntity.countedAmount(): BigDecimal = when {
    excludedFromAnalytics -> BigDecimal.ZERO
    loanId == null -> amount
    else -> (amount - (loanContribution ?: amount)).coerceAtLeast(BigDecimal.ZERO)
}

/**
 * This transaction as a total should see it: [TransactionEntity.amount] cut to
 * [countedAmount], or null when nothing of it counts. Only for computing
 * totals — never display the result, its amount isn't what the user paid.
 *
 * The cut copy drops its loan link (the counted part isn't the loan), which
 * also makes this idempotent: callers that re-check rows already passed
 * through here don't subtract the loan part twice.
 */
fun TransactionEntity.forTotals(): TransactionEntity? {
    val counted = countedAmount()
    return when {
        counted.signum() == 0 -> null
        counted.compareTo(amount) == 0 -> this
        else -> copy(amount = counted, loanId = null, loanContribution = null)
    }
}

fun Iterable<TransactionEntity>.forTotals(): List<TransactionEntity> = mapNotNull { it.forTotals() }

/**
 * [forTotals] for a row with category splits: the splits shrink in proportion,
 * with the last one absorbing rounding so they still sum to the counted amount.
 */
@JvmName("forTotalsWithSplits")
fun TransactionWithSplits.forTotals(): TransactionWithSplits? {
    val counted = transaction.forTotals() ?: return null
    if (counted === transaction) return this
    if (splits.isEmpty()) return copy(transaction = counted)
    val ratio = counted.amount.divide(transaction.amount, 10, RoundingMode.HALF_EVEN)
    var assigned = BigDecimal.ZERO
    val scaled = splits.mapIndexed { i, split ->
        val amount = if (i == splits.lastIndex) {
            counted.amount - assigned
        } else {
            split.amount.multiply(ratio).setScale(2, RoundingMode.HALF_EVEN)
        }
        assigned += amount
        split.copy(amount = amount)
    }
    return TransactionWithSplits(counted, scaled)
}

@JvmName("forTotalsWithSplitsList")
fun Iterable<TransactionWithSplits>.forTotals(): List<TransactionWithSplits> = mapNotNull { it.forTotals() }
