package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

object BalanceCalculator {
    /**
     * Calculates the new account balance post-transaction.
     * For credit cards, balance represents outstanding debt.
     */
    fun calculateNewBalance(
        explicitBalance: BigDecimal?,
        isCreditCard: Boolean,
        transactionType: TransactionType,
        transactionAmount: BigDecimal,
        currentBalance: BigDecimal?
    ): BigDecimal {
        return when {
            explicitBalance != null -> explicitBalance
            isCreditCard && transactionType == TransactionType.INCOME -> {
                val cur = currentBalance ?: BigDecimal.ZERO
                (cur - transactionAmount).max(BigDecimal.ZERO)
            }
            isCreditCard -> {
                val cur = currentBalance ?: BigDecimal.ZERO
                cur + transactionAmount
            }
            else -> {
                val cur = currentBalance ?: BigDecimal.ZERO
                when (transactionType) {
                    TransactionType.INCOME -> cur + transactionAmount
                    TransactionType.EXPENSE, TransactionType.INVESTMENT -> (cur - transactionAmount).max(BigDecimal.ZERO)
                    else -> cur
                }
            }
        }
    }
}
