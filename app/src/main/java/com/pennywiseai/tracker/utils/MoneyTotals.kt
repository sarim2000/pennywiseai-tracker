package com.pennywiseai.tracker.utils

import java.math.BigDecimal

/**
 * Totalling money the safe way.
 *
 * Amounts in different currencies must never be added together. [sumByCurrency]
 * buckets a list per currency and sums **within** each bucket via [Money.plus]
 * (so the addition is currency-checked and can never mix), returning a
 * `Map<currencyCode, Money>`. Render it with
 * [CurrencyFormatter.formatByCurrency], which shows a single figure for a
 * uniform set and per-currency subtotals (`₹1,250 · $600`) for a mixed one.
 *
 * The alternative — folding into one BigDecimal and formatting with a single
 * currency — silently produces a wrong number the moment currencies mix.
 */
inline fun <T> Iterable<T>.sumByCurrency(
    currencySelector: (T) -> String,
    amountSelector: (T) -> BigDecimal
): Map<String, Money> =
    groupBy(currencySelector)
        .mapValues { (currency, items) ->
            items.fold(Money.zero(currency)) { acc, item ->
                acc + Money(amountSelector(item), currency)
            }
        }
