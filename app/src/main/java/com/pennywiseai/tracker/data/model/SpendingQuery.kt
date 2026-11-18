package com.pennywiseai.tracker.data.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.domain.model.BudgetCycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

/**
 * What a spending figure is narrowed to: [window] (null for Home's period, this budget cycle so
 * far) and rows at [merchant] (its name or the user's alias for it).
 */
data class SpendingFilter(
    val window: SpendingComparison.Window? = null,
    val merchant: String? = null,
) {
    /** Narrowed to some rows: Home's figure, forgiven loans included, no longer applies. */
    val narrowed: Boolean get() = merchant != null

    fun keeps(tx: TransactionEntity, aliases: Map<String, String>): Boolean =
        merchant == null || tx.merchantName.contains(merchant, ignoreCase = true) || aliases[tx.merchantName]?.contains(merchant, ignoreCase = true) == true

    companion object {
        /** The transaction list's account filter keys a row the same way (presentation/common/Filters.kt). */
        fun accountKey(tx: TransactionEntity) = "${tx.bankName}_${tx.accountNumber}"
        fun accountKey(account: AccountBalanceEntity) = IgnoredAccountsStore.keyFor(account.bankName, account.accountLast4)
    }
}

/**
 * The chat's query_spending arguments, checked: dates and choices the app understands, or the
 * reason it can't answer. A period it can't read is said, never answered with this month.
 */
object SpendingQuery {
    enum class GroupBy { NONE, CATEGORY, MERCHANT, ACCOUNT }

    enum class Problem {
        /** A period name the app doesn't know. */
        PERIOD,
        /** A date it can't read, e.g. 2025-02-29. */
        DATE,
        /** An end date without a start. */
        NO_START,
        /** The end comes before the start. */
        ORDER,
        /** It hasn't started yet. */
        FUTURE,
    }

    sealed interface Dates {
        /** Home's period: the budget cycle so far. */
        data object Home : Dates
        data class Of(val window: SpendingComparison.Window) : Dates
        data class Invalid(val problem: Problem, val value: String = "", val other: String = "") : Dates
    }

    private val YEAR_MONTH = Regex("(?:19|20)\\d\\d-\\d\\d")
    private val YEAR = Regex("(?:19|20)\\d\\d")
    private val HOME = setOf("this_month", "month", "current_month", "this_cycle", "cycle")

    private fun text(value: Any?) = value?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    /** The model left the period to the app: no period beyond this month, and no dates. */
    fun defaultPeriod(period: Any?, from: Any?, to: Any?): Boolean =
        text(from) == null && text(to) == null && (text(period)?.let { normalized(it) in HOME } ?: true)

    private fun normalized(period: String) = period.lowercase().replace(Regex("[\\s-]+"), "_")

    /**
     * The days asked about, up to the end of [now]'s day: [from] and [to] (YYYY-MM-DD) when given,
     * else [period]. "this_month" and an empty period are Home's budget cycle; "last_month" is the
     * whole cycle before it; a month written YYYY-MM and a year are calendar ones.
     */
    fun dates(period: Any?, from: Any?, to: Any?, now: LocalDateTime, firstDayOfWeek: DayOfWeek, cycleStartDay: Int): Dates {
        val today = now.toLocalDate()
        val (f, t) = text(from) to text(to)
        if (f != null || t != null) {
            if (f == null) return Dates.Invalid(Problem.NO_START, t!!)
            val start = day(f) ?: return Dates.Invalid(Problem.DATE, f)
            val end = t?.let { day(it) ?: return Dates.Invalid(Problem.DATE, it) } ?: return window(start, today, today, f)
            if (end.isBefore(start)) return Dates.Invalid(Problem.ORDER, f, t)
            return window(start, end, today, f)
        }
        val raw = text(period) ?: return Dates.Home
        val p = normalized(raw)
        return when {
            p in HOME -> Dates.Home
            p == "last_month" || p == "previous_month" -> {
                val (start, end) = BudgetCycle.previousCycle(BudgetCycle.currentCycle(today, cycleStartDay), cycleStartDay)
                window(start, end, today, raw)
            }
            p == "this_week" || p == "week" -> window(today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)), today, today, raw)
            p == "last_week" || p == "previous_week" -> {
                val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks(1)
                window(start, start.plusDays(6), today, raw)
            }
            p == "today" -> window(today, today, today, raw)
            p == "yesterday" -> window(today.minusDays(1), today.minusDays(1), today, raw)
            p == "this_year" || p == "year" || p == "current_year" -> window(today.withDayOfYear(1), today, today, raw)
            p == "last_year" || p == "previous_year" -> Year.from(today).minusYears(1).let { window(it.atDay(1), it.atMonth(12).atEndOfMonth(), today, raw) }
            YEAR_MONTH.matches(raw) -> runCatching { YearMonth.parse(raw) }.getOrNull()
                ?.let { window(it.atDay(1), it.atEndOfMonth(), today, raw) } ?: Dates.Invalid(Problem.PERIOD, raw)
            YEAR.matches(raw) -> Year.parse(raw).let { window(it.atDay(1), it.atMonth(12).atEndOfMonth(), today, raw) }
            else -> Dates.Invalid(Problem.PERIOD, raw)
        }
    }

    /** [start] to [end], cut at the end of [today]: a figure never claims days still to come. */
    private fun window(start: LocalDate, end: LocalDate, today: LocalDate, asked: String): Dates =
        if (start.isAfter(today)) Dates.Invalid(Problem.FUTURE, asked)
        else Dates.Of(SpendingComparison.Window(start.atStartOfDay(), minOf(end, today).atTime(LocalTime.MAX)))

    /** ISO dates only, checked strictly: 2025-02-29 isn't a day. */
    private fun day(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    /** The grouping asked for; null for one the app doesn't offer. */
    fun groupBy(value: Any?): GroupBy? = when (text(value)?.lowercase()?.removeSuffix("s")) {
        null, "none", "total", "no" -> GroupBy.NONE
        "category", "categorie" -> GroupBy.CATEGORY
        "merchant", "shop", "store", "payee" -> GroupBy.MERCHANT
        "account", "bank", "card" -> GroupBy.ACCOUNT
        else -> null
    }

    /**
     * The grouping that answers the call: a ranking of the one merchant or category it already
     * names is that one's total.
     */
    fun grouping(by: GroupBy, category: String?, merchant: String?): GroupBy = when {
        by == GroupBy.MERCHANT && merchant != null -> GroupBy.NONE
        by == GroupBy.CATEGORY && category != null -> GroupBy.NONE
        else -> by
    }
}
