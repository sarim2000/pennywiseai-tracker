package com.pennywiseai.shared.domain.money

import com.pennywiseai.shared.SharedAccountItem
import com.pennywiseai.shared.SharedSubscriptionItem

/** One currency's total. A list of these is never collapsed into one figure. */
data class CurrencyTotal(val currency: String, val amountMinor: Long)

/**
 * Money math the iOS screens share, so every screen totals and rounds the same
 * way. Never sums across currencies (CLAUDE.md hard constraint #2): totals come
 * back one per currency, in first-seen order.
 */
object MoneyTotals {

    /** Net balance per currency. A card's balance is money owed, so it subtracts. */
    fun accountTotals(accounts: List<SharedAccountItem>): List<CurrencyTotal> =
        byCurrency(accounts.map { it.currency to if (it.isCreditCard) -it.balanceMinor else it.balanceMinor })

    /** Monthly cost of the active subscriptions, per currency. */
    fun activeSubscriptionTotals(subscriptions: List<SharedSubscriptionItem>): List<CurrencyTotal> =
        byCurrency(subscriptions.filter { isActiveSubscription(it.state) }.map { it.currency to it.amountMinor })

    /** The entity stores "ACTIVE"; compare case-insensitively so "active" can't slip through as inactive. */
    fun isActiveSubscription(state: String): Boolean = state.equals("ACTIVE", ignoreCase = true)

    /**
     * Whole percent of a budget used, rounded down like Android (99.6% reads 99%,
     * never "100% used" before the limit is hit). 0 when there is no limit.
     */
    fun percentUsed(spentMinor: Long, limitMinor: Long): Int =
        if (limitMinor <= 0) 0 else (spentMinor * 100 / limitMinor).toInt()

    /**
     * Types that count as spending in charts: EXPENSE plus credit-card spend
     * (Android's SPENDING filter). Income, transfers and investments never do.
     */
    fun isSpendingType(transactionType: String): Boolean =
        transactionType == "EXPENSE" || transactionType == "CREDIT"

    private fun byCurrency(entries: List<Pair<String, Long>>): List<CurrencyTotal> =
        entries.groupBy({ it.first }, { it.second })
            .map { (currency, amounts) -> CurrencyTotal(currency, amounts.sum()) }
}
