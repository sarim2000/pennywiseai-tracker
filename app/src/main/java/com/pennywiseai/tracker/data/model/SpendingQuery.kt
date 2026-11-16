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
 * far), rows at [merchant] (its name or the user's alias for it) and on [account] (a
 * "bank_last4" key, as the transaction list's account filter keys it).
 */
data class SpendingFilter(
    val window: SpendingComparison.Window? = null,
    val merchant: String? = null,
    val account: String? = null,
) {
    /** Narrowed to some rows: Home's figure, forgiven loans included, no longer applies. */
    val narrowed: Boolean get() = merchant != null || account != null

    fun keeps(tx: TransactionEntity, aliases: Map<String, String>): Boolean =
        (account == null || accountKey(tx) == account) &&
            (merchant == null || tx.merchantName.contains(merchant, ignoreCase = true) || aliases[tx.merchantName]?.contains(merchant, ignoreCase = true) == true)

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

    sealed interface AccountMatch {
        data class One(val account: AccountBalanceEntity) : AccountMatch
        /** Several accounts answer to it, e.g. two cards ending in the same digits. */
        data class Several(val accounts: List<AccountBalanceEntity>) : AccountMatch
        data object None : AccountMatch
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

    /**
     * The account the user named: its alias or "Bank 1234" exactly first (or "Paytm WALLET", as
     * the chat's prompt lists a wallet), then its last digits
     * (with a word of its name first, when one was given), then the bank or alias alone. The
     * first of those that matches anything decides; two accounts there is a question for the
     * user, not a guess.
     */
    fun account(asked: String, accounts: List<AccountBalanceEntity>): AccountMatch {
        val wanted = norm(asked)
        if (wanted.isEmpty()) return AccountMatch.None
        val digits = asked.filter(Char::isDigit)
        val words = norm(asked.filterNot(Char::isDigit)).split(" ").filter { it.isNotEmpty() }
        fun names(a: AccountBalanceEntity) = listOfNotNull(a.alias?.takeIf { it.isNotBlank() }, a.bankName).map(::norm)
        fun endsIn(a: AccountBalanceEntity) = digits.length >= 3 && a.accountLast4.endsWith(digits.takeLast(4))
        val tiers = listOf<(AccountBalanceEntity) -> Boolean>(
            { a ->
                wanted == norm(a.displayLabel) || wanted == norm(AccountBalanceEntity.accountLabel(a.bankName, a.accountLast4)) ||
                    wanted == norm("${a.bankName} ${a.accountLast4}")
            },
            // "sbi card 1234": the digits, and a word of its name.
            { a -> endsIn(a) && words.any { w -> names(a).any { w in it.split(" ") } } },
            { a -> endsIn(a) },
            { a -> digits.isEmpty() && names(a).any { it == wanted || it.startsWith(wanted) } },
        )
        for (tier in tiers) {
            val hits = accounts.filter(tier).distinctBy { SpendingFilter.accountKey(it) }
            if (hits.size == 1) return AccountMatch.One(hits.single())
            if (hits.size > 1) return AccountMatch.Several(hits)
        }
        return AccountMatch.None
    }
}
