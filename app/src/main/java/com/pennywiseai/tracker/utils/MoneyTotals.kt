package com.pennywiseai.tracker.utils

import java.math.BigDecimal

/**
 * Totalling money the safe way.
 *
 * Amounts in different currencies must never be added together — "₹500 + $10"
 * is not "510" of anything. Whenever you need a total over a list that can hold
 * more than one currency (group totals, subscription totals, account lists …),
 * bucket it per currency with [sumByCurrency] and render the result with
 * [CurrencyFormatter.formatByCurrency], which shows a single figure for a
 * uniform set and per-currency subtotals for a mixed one.
 *
 * The alternative — folding into one [BigDecimal] and formatting with a single
 * currency code — silently produces a wrong number (and usually a wrong symbol)
 * the moment a user mixes currencies. See PRs around group / subscription totals.
 */
inline fun <T> Iterable<T>.sumByCurrency(
    currencySelector: (T) -> String,
    amountSelector: (T) -> BigDecimal
): Map<String, BigDecimal> =
    groupBy(currencySelector)
        .mapValues { (_, items) ->
            items.fold(BigDecimal.ZERO) { acc, item -> acc + amountSelector(item) }
        }
