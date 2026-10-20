package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * What the Analytics breakdown groups by (#422). Category and merchant are the
 * views Analytics always had, and account stays free because its "By account"
 * list shipped free (#421). Tag and day of week are Pro.
 */
enum class AnalyticsGroupBy(val isPro: Boolean) {
    CATEGORY(false),
    MERCHANT(false),
    ACCOUNT(false),
    TAG(true),
    DAY_OF_WEEK(true);

    /** This grouping when the user may see it, else Category (a lapsed Pro user falls back). */
    fun allowedFor(isProEntitled: Boolean): AnalyticsGroupBy =
        if (isPro && !isProEntitled) CATEGORY else this

    companion object {
        /** The stored grouping, or Category when nothing (or an unknown name) is stored. */
        fun fromSaved(saved: String?): AnalyticsGroupBy =
            entries.firstOrNull { it.name == saved } ?: CATEGORY
    }
}

/** A row the totals count ([com.pennywiseai.tracker.utils.forTotals]), with its amount in the view. */
internal class CountedRow(val tx: TransactionEntity, val money: Money)

/** One group: its total per currency, and the rows that fed it. */
internal class GroupTotal(val byCurrency: Map<String, Money>, val rows: List<CountedRow>) {
    fun amountIn(currency: String): BigDecimal = byCurrency[currency]?.amount ?: BigDecimal.ZERO
}

/**
 * Totals [rows] per key of [keysOf], and within each key per currency
 * ([sumByCurrency]), so two currencies never add up. A row with several keys
 * (a transaction with several tags) counts in full under each of them.
 */
internal fun <K> groupTotals(
    rows: List<CountedRow>,
    keysOf: (TransactionEntity) -> Collection<K>
): Map<K, GroupTotal> =
    rows.flatMap { row -> keysOf(row.tx).distinct().map { key -> key to row } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, group) ->
            GroupTotal(group.sumByCurrency({ it.money.currency }, { it.money.amount }), group)
        }

/** A row's tag keys: its tags, or one null key ("No tag") when it has none. */
internal fun tagKeys(tags: Collection<String>?): List<String?> =
    tags?.takeIf { it.isNotEmpty() }?.toList() ?: listOf(null)

/** The seven days in the order [locale] starts its week (Monday, or Sunday in the US). */
internal fun weekdayOrder(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

/** The account filter's key for a row: "bankName_accountNumber". */
internal fun accountKeyOf(tx: TransactionEntity): String = "${tx.bankName}_${tx.accountNumber}"

/** The account filter's alias-preferred label for [key], else "Bank ••1234" from [tx]. */
internal fun accountLabelFor(key: String, tx: TransactionEntity, labels: Map<String, String>): String =
    labels[key] ?: AccountBalanceEntity.accountLabel(tx.bankName ?: "Unknown", tx.accountNumber ?: "----")

/** [amount] as a percent of [total], capped at 100 (an over-refund can shrink the total). */
internal fun shareOf(amount: BigDecimal, total: BigDecimal): Float =
    if (total > BigDecimal.ZERO) {
        (amount.divide(total, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toFloat().coerceAtMost(100f)
    } else 0f
