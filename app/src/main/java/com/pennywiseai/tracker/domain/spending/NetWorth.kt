package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** What an account's balance rests on. No source at all means there's no evidence either way. */
sealed interface BalanceSource {
    /** The user gave [amount] as the balance at [at], typed or from a statement. */
    data class Entered(val amount: BigDecimal, val at: LocalDateTime, val fromStatement: Boolean) : BalanceSource

    /** The bank stated the balance at [at]: an SMS that carried it, a balance notice, or a linked card's. */
    data class Reported(val at: LocalDateTime) : BalanceSource

    /** Never pinned to a real figure: 0 plus every transaction since the first SMS. */
    data object Estimated : BalanceSource

    /** When the figure was last pinned; transactions after it moved the balance since. */
    val since: LocalDateTime?
        get() = when (this) {
            is Entered -> at
            is Reported -> at
            Estimated -> null
        }
}

/**
 * What the balance rows say about one account, worked out by the database: the newest
 * row the bank stated ([reportedAt]: an SMS that carried the balance, a balance notice,
 * a linked card's) and the newest row of any kind that isn't derived from a transaction
 * ([fixedAt]: those, plus a MANUAL row).
 */
data class BalanceEvidence(val reportedAt: LocalDateTime?, val fixedAt: LocalDateTime?)

enum class NetWorthGroup { ASSETS, CARDS, LOANS }

data class NetWorthAccount(
    val account: AccountBalanceEntity,
    val source: BalanceSource?,
    /** Transactions on the account after [BalanceSource.since]. */
    val transactionsSince: Int,
    /** Days since the account's newest balance row. */
    val daysSinceUpdate: Long,
) {
    val group: NetWorthGroup
        get() = when {
            account.isLoan -> NetWorthGroup.LOANS
            account.isCreditCard -> NetWorthGroup.CARDS
            else -> NetWorthGroup.ASSETS
        }

    val isStale: Boolean get() = daysSinceUpdate > NetWorth.STALE_DAYS
}

/** One currency's net worth: what's held minus everything owed on cards and loan accounts. */
data class NetWorthTotal(
    val currency: String,
    val assets: BigDecimal,
    /** The full outstanding on cards, billed and not yet billed. */
    val cards: BigDecimal,
    val loans: BigDecimal,
    /** Some balance in here is only an estimate. */
    val hasEstimates: Boolean,
) {
    val net: BigDecimal get() = assets - cards - loans
}

/** One day's net worth in one currency, as of the end of [date]. */
data class NetWorthPoint(val date: LocalDate, val net: BigDecimal)

/**
 * A manual account's past, which its rows don't keep (its one MANUAL row is rewritten
 * on every change): today's balance less what each later transaction moved it by.
 */
data class DerivedHistory(
    val currency: String,
    val current: BigDecimal,
    /**
     * The user has given its balance at some point. Every later balance moves only by
     * transactions, so the whole past follows from today's figure. An account never
     * given one (from before balances were remembered) has an unknown past.
     */
    val anchored: Boolean,
    /** Each transaction's signed effect on the balance, with when it happened. */
    val effects: List<Pair<LocalDateTime, BigDecimal>>,
    /**
     * When the account came to be: its first balance row, or an earlier transaction
     * the user dated before it. It adds nothing before then, like any account not seen yet.
     */
    val existsFrom: LocalDateTime? = null,
)

/** Person-to-person IOUs in one currency. Shown beside net worth, not counted in it. */
data class PeopleOwed(val currency: String, val owedToYou: BigDecimal, val youOwe: BigDecimal)

object NetWorth {
    /** An account whose newest balance is older than this is flagged as possibly out of date. */
    const val STALE_DAYS = 30L

    /**
     * One total per currency, never summed across. A liability's balance is what's owed
     * ([AccountBalanceEntity.isLiability]), so it is taken off; goals aren't assets and
     * aren't here at all.
     */
    fun totals(accounts: List<NetWorthAccount>): List<NetWorthTotal> =
        accounts.groupBy { it.account.currency }.map { (currency, held) ->
            fun sum(group: NetWorthGroup) =
                held.filter { it.group == group }.fold(BigDecimal.ZERO) { total, it -> total + it.account.balance }
            NetWorthTotal(
                currency = currency,
                assets = sum(NetWorthGroup.ASSETS),
                cards = sum(NetWorthGroup.CARDS),
                loans = sum(NetWorthGroup.LOANS),
                hasEstimates = held.any { it.source == BalanceSource.Estimated },
            )
        }.sortedByDescending { total -> accounts.count { it.account.currency == total.currency } }

    /**
     * Where [account]'s balance comes from, claiming only what the rows prove:
     * - the figure the user gave, unless the bank stated a newer one;
     * - the bank's newest stated balance, unless something other than the bank set it after;
     * - an estimate, when nothing ever pinned it ([unanchored]);
     * - otherwise nothing (a MANUAL override after the bank's figure), rather than a guess.
     */
    fun source(account: AccountBalanceEntity, evidence: BalanceEvidence?, unanchored: Boolean): BalanceSource? {
        val reportedAt = evidence?.reportedAt
        val fixedAt = evidence?.fixedAt
        val enteredAt = account.anchorAt
        val enteredAmount = account.anchorAmount
        if (enteredAt != null && enteredAmount != null && (reportedAt == null || !reportedAt.isAfter(enteredAt))) {
            return BalanceSource.Entered(
                enteredAmount, enteredAt, account.anchorSource == AccountBalanceRepository.ANCHOR_STATEMENT
            )
        }
        if (reportedAt != null && (fixedAt == null || !fixedAt.isAfter(reportedAt))) {
            return BalanceSource.Reported(reportedAt)
        }
        return if (unanchored) BalanceSource.Estimated else null
    }

    /**
     * Net worth per currency at the end of each day from [start] to [end].
     *
     * [rows] are the balance rows of the accounts in scope from [start] on, plus each
     * one's newest row before it (OPENING rows left out): every account carries its
     * last balance forward day by day. A row counts in its own currency, so an account
     * whose currency changed leaves one line and joins the other; no line crosses it.
     * Accounts in [derived] (manual accounts) are worked out from their transactions
     * instead, once the user has given their balance; a currency with one never given
     * a balance has no line at all, rather than a guess. An account not seen yet on a
     * day (a manual one before [DerivedHistory.existsFrom]) adds nothing to it.
     */
    fun trend(
        rows: List<AccountBalanceEntity>,
        derived: Map<Pair<String, String>, DerivedHistory>,
        start: LocalDate,
        end: LocalDate,
    ): Map<String, List<NetWorthPoint>> {
        if (end.isBefore(start)) return emptyMap()
        val histories = rows.filter { (it.bankName to it.accountLast4) !in derived }
            .groupBy { it.bankName to it.accountLast4 }
            .values.map { it.sortedBy { row -> row.timestamp } }
        val next = IntArray(histories.size)
        val manual = derived.values.map { history ->
            history to history.effects.sortedBy { it.first }
        }
        val applied = IntArray(manual.size)
        val appliedSum = Array(manual.size) { BigDecimal.ZERO }
        val totals = manual.map { (_, effects) -> effects.fold(BigDecimal.ZERO) { sum, it -> sum + it.second } }

        val unknown = derived.values.filterNot { it.anchored }.map { it.currency }.toSet()
        val points = mutableMapOf<String, MutableList<NetWorthPoint>>()
        var day = start
        while (!day.isAfter(end)) {
            val endOfDay = day.atTime(LocalTime.MAX)
            val net = mutableMapOf<String, BigDecimal>()
            histories.forEachIndexed { i, history ->
                while (next[i] < history.size && !history[next[i]].timestamp.isAfter(endOfDay)) next[i]++
                val row = history.getOrNull(next[i] - 1) ?: return@forEachIndexed
                val signed = if (row.isLiability) row.balance.negate() else row.balance
                net[row.currency] = (net[row.currency] ?: BigDecimal.ZERO) + signed
            }
            manual.forEachIndexed { i, (history, effects) ->
                while (applied[i] < effects.size && !effects[applied[i]].first.isAfter(endOfDay)) {
                    appliedSum[i] += effects[applied[i]].second
                    applied[i]++
                }
                if (history.existsFrom?.toLocalDate()?.isAfter(day) == true) return@forEachIndexed
                // Today's balance less everything after this day.
                val value = history.current - (totals[i] - appliedSum[i])
                net[history.currency] = (net[history.currency] ?: BigDecimal.ZERO) + value
            }
            net.forEach { (currency, value) ->
                if (currency !in unknown) points.getOrPut(currency) { mutableListOf() } += NetWorthPoint(day, value)
            }
            day = day.plusDays(1)
        }
        return points
    }

    fun daysSince(at: LocalDateTime, now: LocalDateTime): Long = ChronoUnit.DAYS.between(at, now).coerceAtLeast(0)

    /** Active IOUs per currency; each side only what's still outstanding. */
    fun peopleOwed(loans: List<LoanEntity>): List<PeopleOwed> =
        loans.filter { it.status == LoanStatus.ACTIVE && it.remainingAmount.signum() > 0 }
            .groupBy { it.currency }
            .map { (currency, inCurrency) ->
                fun sum(direction: LoanDirection) =
                    inCurrency.filter { it.direction == direction }.fold(BigDecimal.ZERO) { total, it -> total + it.remainingAmount }
                PeopleOwed(currency, owedToYou = sum(LoanDirection.LENT), youOwe = sum(LoanDirection.BORROWED))
            }
}
