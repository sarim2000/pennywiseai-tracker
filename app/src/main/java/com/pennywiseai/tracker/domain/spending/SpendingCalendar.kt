package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One day of the spending calendar. */
data class CalendarDay(
    /** Net spend by currency, by Analytics' rules; only currencies something was spent in. */
    val spent: Map<String, BigDecimal> = emptyMap(),
    /** Payments due this day. Planned, so never part of [spent]. */
    val planned: List<Bill> = emptyList(),
)

/** A month of what was spent each day and what's due, each amount in its own currency. */
object SpendingCalendar {

    /**
     * The days of [month] with spend or a payment due. [rows] are already scoped (profile,
     * ignored accounts); spend follows Analytics' default view ([defaultTypeFilter]): loans and
     * exclusions out, transfer pairs once, refunds netted off their day. [planned] comes from
     * [UpcomingPayments], so a payment that has posted is already gone from it.
     */
    suspend fun days(
        month: YearMonth,
        rows: List<TransactionWithSplits>,
        planned: List<Bill>,
        creditAsExpense: Boolean,
    ): Map<LocalDate, CalendarDay> {
        val inMonth = rows.filter { YearMonth.from(it.transaction.dateTime) == month }
        val filter = defaultTypeFilter(creditAsExpense)
        val spent = mutableMapOf<LocalDate, MutableMap<String, BigDecimal>>()
        inMonth.groupBy { it.transaction.currency }.forEach { (currency, ofCurrency) ->
            val gross = analyticsRowsFor(ofCurrency, filter)
                .groupBy({ it.transaction.dateTime.toLocalDate() }, { it.transaction.amount })
                .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }
            val refunds = refundNetting(ofCurrency, filter, null, emptyMap()) { it.amount }.byDay
            gross.forEach { (day, amount) ->
                val net = netOf(amount, refunds[day])
                if (net.signum() > 0) spent.getOrPut(day, ::mutableMapOf)[currency] = net
            }
        }
        val due = planned.filter { YearMonth.from(it.dueDate) == month }.groupBy { it.dueDate }
        return (spent.keys + due.keys).associateWith { day ->
            CalendarDay(spent = spent[day].orEmpty(), planned = due[day].orEmpty())
        }
    }

    /**
     * [month] as rows of seven days starting on [firstDayOfWeek]; the slots before the
     * 1st and after the last day are null.
     */
    fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
        val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDayOfWeek.value, 7)
        val slots = List(lead) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        return slots.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }

    /** Currencies spent in, the one spent in on the most days first. */
    fun currencies(days: Map<LocalDate, CalendarDay>): List<String> =
        days.values.flatMap { it.spent.keys }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /**
     * How strongly to shade a day: 0 for no spend, then 1..[LEVELS] by its share of the
     * month's biggest day ([max]), in the same currency.
     */
    fun level(amount: BigDecimal?, max: BigDecimal): Int {
        if (amount == null || amount.signum() <= 0 || max.signum() <= 0) return 0
        val scaled = amount.multiply(BigDecimal(LEVELS)).divide(max, 0, RoundingMode.CEILING).toInt()
        return scaled.coerceIn(1, LEVELS)
    }

    const val LEVELS = 4
}
