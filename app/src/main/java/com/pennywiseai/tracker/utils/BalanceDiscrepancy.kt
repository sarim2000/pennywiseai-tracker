package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * The bank's reported balance disagrees with what the ledger predicts (#734, #135).
 * [delta] > 0 means the bank has MORE than we expected (an untracked credit);
 * < 0 means untracked spending. [since] is the previous ledger snapshot the
 * prediction started from.
 */
data class BalanceDiscrepancy(
    val expected: BigDecimal,
    val reported: BigDecimal,
    val currency: String,
    val since: LocalDateTime? = null
) {
    val delta: BigDecimal get() = reported - expected

    companion object {
        /** Bank SMS balances are rounded; ignore sub-unit noise. */
        val TOLERANCE: BigDecimal = BigDecimal.ONE

        /**
         * Signed effect of [tx] on the debit account [accountLast4]: transfers move
         * money by leg (in on the TO side, out on the FROM side), everything else by
         * type. Mirrors what the ledger itself applies.
         */
        fun effectOn(tx: TransactionEntity, accountLast4: String): BigDecimal = when (tx.transactionType) {
            TransactionType.TRANSFER -> when {
                tx.toAccount == accountLast4 && tx.fromAccount != accountLast4 ->
                    BalanceCalculator.transferLegEffect(isCreditCard = false, incoming = true, amount = tx.amount)
                tx.fromAccount == accountLast4 && tx.toAccount != accountLast4 ->
                    BalanceCalculator.transferLegEffect(isCreditCard = false, incoming = false, amount = tx.amount)
                else -> BigDecimal.ZERO
            }
            else -> BalanceCalculator.signedBalanceEffect(isCreditCard = false, transactionType = tx.transactionType, amount = tx.amount)
        }

        /**
         * Predicts the balance after applying [effects] (one signed amount per
         * transaction on the account after the [previousBalance] snapshot, up to and
         * including the one that reported [reported]) and compares. Debit/savings
         * accounts only — credit-card SMS report available limit, which has its own math.
         */
        fun compute(
            previousBalance: BigDecimal,
            effects: List<BigDecimal>,
            reported: BigDecimal,
            currency: String,
            since: LocalDateTime? = null
        ): BalanceDiscrepancy? {
            val expected = effects.fold(previousBalance) { acc, e -> acc + e }
            val d = BalanceDiscrepancy(expected, reported, currency, since)
            return d.takeIf { it.delta.abs() > TOLERANCE }
        }
    }
}
