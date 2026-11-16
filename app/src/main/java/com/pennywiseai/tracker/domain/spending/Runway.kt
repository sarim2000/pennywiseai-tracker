package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.model.AccountType
import com.pennywiseai.tracker.domain.model.getAccountType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * How long the money held in one currency would last at a usual cycle's spending,
 * if nothing more came in.
 */
data class Runway(
    val currency: String,
    /** What savings, current and cash accounts hold together; can be below zero. */
    val balance: BigDecimal,
    /** A usual cycle's spending, always above zero. */
    val monthlySpend: BigDecimal,
    /** Some of [balance] was never anchored to a real figure, so it's an estimate. */
    val lowConfidence: Boolean,
) {
    /** Months, to one place and rounded down so it never overstates; 0 once nothing is held. */
    val months: BigDecimal get() = balance.max(BigDecimal.ZERO).divide(monthlySpend, 1, RoundingMode.DOWN)

    companion object {
        /** Months of spending a safety cushion commonly aims for. */
        const val TARGET_MONTHS = 6

        /** Money the user holds and can spend: savings, current and cash, never a card or a loan. */
        fun isLiquid(account: AccountBalanceEntity): Boolean = account.getAccountType() in LIQUID

        /**
         * One runway per currency held in a liquid account. [accounts] are already
         * scoped: the active profile, no ignored accounts. A currency without a usual
         * spend in [averages], or with none spent, has no runway, never an endless one.
         * [unanchored] are (bank, last 4) keys whose balance is only an estimate.
         */
        fun calculate(
            accounts: List<AccountBalanceEntity>,
            averages: Map<String, CycleAverages>,
            unanchored: Set<Pair<String, String>>,
        ): List<Runway> =
            accounts.filter(::isLiquid).groupBy { it.currency }.mapNotNull { (currency, held) ->
                val spend = averages[currency]?.spent?.takeIf { it.signum() > 0 } ?: return@mapNotNull null
                Runway(
                    currency = currency,
                    balance = held.fold(BigDecimal.ZERO) { sum, a -> sum + a.balance },
                    monthlySpend = spend,
                    lowConfidence = held.any { (it.bankName to it.accountLast4) in unanchored },
                )
            }

        private val LIQUID = setOf(AccountType.SAVINGS, AccountType.CURRENT, AccountType.CASH)
    }
}
