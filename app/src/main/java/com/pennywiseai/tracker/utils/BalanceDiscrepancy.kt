package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

/**
 * The bank's reported balance disagrees with what the ledger predicts (#734, #135).
 * [delta] > 0 means the bank has MORE than we expected (an untracked credit);
 * < 0 means untracked spending.
 */
data class BalanceDiscrepancy(
    val expected: BigDecimal,
    val reported: BigDecimal,
    val currency: String
) {
    val delta: BigDecimal get() = reported - expected

    companion object {
        /** Bank SMS balances are rounded; ignore sub-unit noise. */
        val TOLERANCE: BigDecimal = BigDecimal.ONE

        /**
         * Predicts the balance after applying [between] (every transaction on the
         * account after the [previousBalance] snapshot, up to and including the
         * one that reported [reported]) and compares. Debit/savings accounts only —
         * credit-card SMS report available limit, which has its own math.
         */
        fun compute(
            previousBalance: BigDecimal,
            between: List<Pair<TransactionType, BigDecimal>>,
            reported: BigDecimal,
            currency: String
        ): BalanceDiscrepancy? {
            val expected = between.fold(previousBalance) { acc, (type, amount) ->
                acc + BalanceCalculator.signedBalanceEffect(isCreditCard = false, transactionType = type, amount = amount)
            }
            val d = BalanceDiscrepancy(expected, reported, currency)
            return d.takeIf { it.delta.abs() > TOLERANCE }
        }
    }
}
