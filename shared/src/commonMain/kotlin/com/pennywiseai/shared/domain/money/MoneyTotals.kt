package com.pennywiseai.shared.domain.money

import com.pennywiseai.shared.SharedAccountItem
import com.pennywiseai.shared.SharedRecentTransactionItem
import com.pennywiseai.shared.SharedSubscriptionItem

/** One currency's total. A list of these is never collapsed into one figure. */
data class CurrencyTotal(val currency: String, val amountMinor: Long)

/**
 * A total in [currency], plus the amounts that had no stored rate into it. Those
 * are never added in: the UI shows them as their own lines ("+ $12.50").
 */
data class ConvertedTotal(
    val currency: String,
    val amountMinor: Long,
    val unconverted: List<CurrencyTotal>
)

/**
 * Converts into [baseCurrency] with the stored rates. [ratesToBase] maps a currency
 * code to how many [baseCurrency] units one unit of it buys, in micros. Every
 * amountMinor in the app is value x 100, so the rate applies to it directly.
 */
class CurrencyConverter(val baseCurrency: String, private val ratesToBase: Map<String, Long>) {

    /** [amountMinor] in [baseCurrency], or null when there's no rate for [currency]. */
    fun convert(amountMinor: Long, currency: String): Long? {
        if (currency.equals(baseCurrency, ignoreCase = true)) return amountMinor
        val rate = ratesToBase[currency.uppercase()] ?: return null
        return applyRate(amountMinor, rate)
    }

    /**
     * Sum of [transactions] in [baseCurrency]. Each one is converted (and rounded)
     * on its own, the same way charts and breakdowns convert them, so every figure
     * built from the same rows agrees. Rows with no rate stay per currency.
     */
    fun totalOf(transactions: List<SharedRecentTransactionItem>): ConvertedTotal {
        var sum = 0L
        val unconverted = mutableListOf<SharedRecentTransactionItem>()
        for (t in transactions) {
            val converted = convert(t.amountMinor, t.currency)
            if (converted == null) unconverted += t else sum += converted
        }
        return ConvertedTotal(baseCurrency, sum, nonZero(MoneyTotals.transactionTotals(unconverted)))
    }

    /** income - expense, from the two already-converted totals (no second rounding). */
    fun net(income: ConvertedTotal, expense: ConvertedTotal): ConvertedTotal =
        ConvertedTotal(
            baseCurrency,
            income.amountMinor - expense.amountMinor,
            nonZero(MoneyTotals.netTotals(income.unconverted, expense.unconverted))
        )

    private fun nonZero(totals: List<CurrencyTotal>) = totals.filter { it.amountMinor != 0L }

    companion object {
        private const val MICROS = 1_000_000L

        /**
         * Builds the base-currency rate map from stored (from, to, rateMicros) rows.
         * A direct X->base row wins; otherwise a base->X row is inverted.
         */
        fun fromRates(baseCurrency: String, rates: List<Triple<String, String, Long>>): CurrencyConverter {
            val base = baseCurrency.uppercase()
            val map = mutableMapOf<String, Long>()
            rates.filter { it.second.equals(base, true) && it.third > 0 }
                .forEach { map[it.first.uppercase()] = it.third }
            rates.filter { it.first.equals(base, true) && it.third > 0 }
                .forEach { map.getOrPut(it.second.uppercase()) { (MICROS * MICROS + it.third / 2) / it.third } }
            return CurrencyConverter(base, map)
        }

        /** amountMinor x rateMicros / 1e6, rounded half away from zero, without overflowing on the product. */
        private fun applyRate(amountMinor: Long, rateMicros: Long): Long {
            val whole = amountMinor * (rateMicros / MICROS)
            val part = amountMinor * (rateMicros % MICROS)
            val half = if (part < 0) -MICROS / 2 else MICROS / 2
            return whole + (part + half) / MICROS
        }
    }
}

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

    /** Each currency's total of [transactions]. */
    fun transactionTotals(transactions: List<SharedRecentTransactionItem>): List<CurrencyTotal> =
        byCurrency(transactions.map { it.currency to it.amountMinor })

    /** income - expense, per currency. */
    fun netTotals(income: List<CurrencyTotal>, expense: List<CurrencyTotal>): List<CurrencyTotal> =
        byCurrency(income.map { it.currency to it.amountMinor } + expense.map { it.currency to -it.amountMinor })

    private fun byCurrency(entries: List<Pair<String, Long>>): List<CurrencyTotal> =
        entries.groupBy({ it.first }, { it.second })
            .map { (currency, amounts) -> CurrencyTotal(currency, amounts.sum()) }
}
