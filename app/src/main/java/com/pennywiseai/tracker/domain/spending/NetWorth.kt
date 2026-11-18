package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import java.math.BigDecimal

enum class NetWorthGroup { ASSETS, CARDS, LOANS }

/** Which part of net worth [account] counts in: a liability is taken off, the rest is held. */
val AccountBalanceEntity.netWorthGroup: NetWorthGroup
    get() = when {
        isLoan -> NetWorthGroup.LOANS
        isCreditCard -> NetWorthGroup.CARDS
        else -> NetWorthGroup.ASSETS
    }

/** One currency's net worth: what's held minus everything owed on cards and loan accounts. */
data class NetWorthTotal(
    val currency: String,
    val assets: BigDecimal,
    /** The full outstanding on cards, billed and not yet billed. */
    val cards: BigDecimal,
    val loans: BigDecimal,
) {
    val net: BigDecimal get() = assets - cards - loans
}

object NetWorth {
    /**
     * One total per currency, never summed across, the currency with most accounts
     * first. A liability's balance is what's owed ([AccountBalanceEntity.isLiability]),
     * so it is taken off; goals aren't assets and aren't here at all.
     */
    fun totals(accounts: List<AccountBalanceEntity>): List<NetWorthTotal> =
        accounts.groupBy { it.currency }.map { (currency, held) ->
            fun sum(group: NetWorthGroup) =
                held.filter { it.netWorthGroup == group }.fold(BigDecimal.ZERO) { total, it -> total + it.balance }
            NetWorthTotal(
                currency = currency,
                assets = sum(NetWorthGroup.ASSETS),
                cards = sum(NetWorthGroup.CARDS),
                loans = sum(NetWorthGroup.LOANS),
            )
        }.sortedByDescending { total -> accounts.count { it.currency == total.currency } }
}
