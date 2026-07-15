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
        val cur = currentBalance ?: BigDecimal.ZERO
        return when {
            explicitBalance != null -> explicitBalance
            isCreditCard -> {
                when (transactionType) {
                    TransactionType.INCOME -> {
                        // Refunds or repayments reduce outstanding debt
                        (cur - transactionAmount).max(BigDecimal.ZERO)
                    }
                    TransactionType.EXPENSE, TransactionType.INVESTMENT, TransactionType.CREDIT -> {
                        // Purchases or cash withdrawals increase outstanding debt
                        cur + transactionAmount
                    }
                    TransactionType.TRANSFER -> {
                        // Transfers between own accounts keep outstanding debt unchanged
                        // (Requires explicit balance or complex leg resolution to modify)
                        cur
                    }
                }
            }
            else -> {
                when (transactionType) {
                    TransactionType.INCOME -> cur + transactionAmount
                    TransactionType.EXPENSE, TransactionType.INVESTMENT -> (cur - transactionAmount).max(BigDecimal.ZERO)
                    TransactionType.TRANSFER, TransactionType.CREDIT -> {
                        // Transfers/credits on debit accounts do not change standard balance directly
                        cur
                    }
                }
            }
        }
    }
}
