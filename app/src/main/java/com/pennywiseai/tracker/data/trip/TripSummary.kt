package com.pennywiseai.tracker.data.trip

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.GroupSummary
import com.pennywiseai.tracker.data.repository.groupTotalsOf
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One category's spend within a single currency. */
data class CategorySpend(val category: String, val amount: Money)

/**
 * A trip's figures. Every money field is per currency: nothing here adds across currencies.
 * [totalDays] is null for a trip with no end yet.
 */
data class TripSummaryFigures(
    val daysSoFar: Int,
    val totalDays: Int?,
    val totalByCurrency: Map<String, Money>,
    val averagePerDay: Map<String, Money>,
    val topCategories: Map<String, List<CategorySpend>>
)

/** Whole days between two instants' dates, counting both the first and the last day. */
private fun inclusiveDays(from: LocalDateTime, to: LocalDateTime): Int =
    (ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1).toInt().coerceAtLeast(0)

/**
 * Days a trip has covered at [now], inclusive of its first and last day: 0 before it starts, up
 * to today while it runs (open-ended or not), and fixed at its end day once ended (a stopped
 * trip's end is the stop time). 0 for an ordinary group.
 */
fun tripDaysSoFar(trip: TransactionGroupEntity, now: LocalDateTime): Int {
    val start = trip.tripStart ?: return 0
    if (now.isBefore(start)) return 0
    val end = trip.tripEnd
    return inclusiveDays(start, if (end != null && end.isBefore(now)) end else now)
}

/** Days in the whole window, inclusive; null while it has no end. */
fun tripTotalDays(trip: TransactionGroupEntity): Int? {
    val start = trip.tripStart ?: return null
    val end = trip.tripEnd ?: return null
    return inclusiveDays(start, end)
}

/** Each currency's total divided by [days]; empty for zero days so nothing divides by zero. */
fun averagePerDay(totals: Map<String, Money>, days: Int): Map<String, Money> =
    if (days <= 0) {
        emptyMap()
    } else {
        totals.mapValues { (currency, money) ->
            Money(money.amount.divide(BigDecimal(days), 2, RoundingMode.HALF_UP), currency)
        }
    }

/** The biggest [limit] spending categories of each currency, largest first. */
fun topCategoriesByCurrency(transactions: List<TransactionEntity>, limit: Int = 3): Map<String, List<CategorySpend>> =
    transactions
        .filter { it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.CREDIT }
        .groupBy { it.currency }
        .mapValues { (_, rows) ->
            rows.sumByCurrency({ it.category }, { it.amount })
                .map { (category, money) -> CategorySpend(category, money) }
                .sortedByDescending { it.amount.amount }
                .take(limit)
        }

fun tripSummaryOf(trip: TransactionGroupEntity, transactions: List<TransactionEntity>, now: LocalDateTime): TripSummaryFigures {
    val days = tripDaysSoFar(trip, now)
    val totals = groupTotalsOf(transactions).expense.filterValues { it.isPositive }
    return TripSummaryFigures(days, tripTotalDays(trip), totals, averagePerDay(totals, days), topCategoriesByCurrency(transactions))
}

/**
 * The trip Home shows a chip for: the running one (windows can't overlap, so at most one; the
 * latest start wins if stored data ever disagrees). Null when none is running.
 */
fun runningTripOf(summaries: List<GroupSummary>, now: LocalDateTime): GroupSummary? =
    summaries.filter { it.group.tripStatus(now) == TripStatus.RUNNING }.maxByOrNull { it.group.tripStart!! }

/**
 * The rows from [candidates] that would have joined [trip] had it existed when they arrived.
 * Same rule as a new message ([joinsTrip]); [excluded] is the trip's removed-by-hash set.
 */
fun backfillRowsFor(
    candidates: List<TransactionEntity>,
    trip: TransactionGroupEntity,
    excluded: Set<String> = emptySet()
): List<TransactionEntity> = candidates.filter { joinsTrip(it, trip, excluded) }

/** Whether saving [after] over [before] should offer the rows its window now reaches: a new trip, or a start moved earlier. */
fun tripStartMovedEarlier(before: TransactionGroupEntity?, after: TransactionGroupEntity): Boolean {
    val newStart = after.tripStart ?: return false
    val oldStart = before?.tripStart ?: return true
    return newStart.isBefore(oldStart)
}
