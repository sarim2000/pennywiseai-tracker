package com.pennywiseai.tracker.utils

import java.math.BigDecimal

/**
 * An amount paired with the currency it is denominated in.
 *
 * The whole point of this type is [plus]: it **refuses to add two different
 * currencies**. "₹500 + $10" is not "510" of anything, so a blind cross-currency
 * total throws loudly (in dev/tests) instead of silently producing a wrong number
 * with a wrong symbol — the bug that kept recurring in group / subscription totals.
 *
 * To total a list that may hold more than one currency, use [sumByCurrency],
 * which buckets per currency first and therefore only ever adds like-for-like.
 * Never `fold` raw [BigDecimal] amounts across rows that can differ in currency.
 */
data class Money(val amount: BigDecimal, val currency: String) {

    operator fun plus(other: Money): Money {
        require(currency.equals(other.currency, ignoreCase = true)) {
            "Cannot add Money in different currencies ($currency + ${other.currency}); total per-currency instead"
        }
        return Money(amount + other.amount, currency)
    }

    val isPositive: Boolean get() = amount.signum() > 0

    /** Renders with the correct symbol for [currency]. [signPrefix] is e.g. "-" / "+". */
    fun format(signPrefix: String = ""): String =
        "$signPrefix${CurrencyFormatter.formatCurrency(amount, currency)}"

    companion object {
        fun zero(currency: String) = Money(BigDecimal.ZERO, currency)
    }
}
