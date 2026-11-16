package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import java.math.BigDecimal
import java.time.LocalDateTime
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
 * row the bank stated ([reportedAt]) and the newest row of any kind that isn't derived
 * from a transaction ([fixedAt]: a bank figure, a MANUAL or balance-notice row).
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
     * - otherwise nothing (an older MANUAL override, a balance notice), rather than a guess.
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
