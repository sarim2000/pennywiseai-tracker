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
                        cur - transactionAmount
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
                    TransactionType.EXPENSE, TransactionType.INVESTMENT -> cur - transactionAmount
                    TransactionType.TRANSFER, TransactionType.CREDIT -> {
                        // Transfers/credits on debit accounts do not change standard balance directly
                        cur
                    }
                }
            }
        }
    }

    /**
     * Signed delta a non-TRANSFER transaction applies to its own account's stored
     * balance. This is the single sign convention shared by manual add, edit and
     * delete (`AccountBalanceRepository`), so a revert can never disagree with the
     * apply that preceded it.
     *
     * Credit cards store outstanding owed, so spending raises the figure and
     * income (refund/repayment) lowers it. TRANSFER is zero here — transfers move
     * balances through their from/to legs ([transferLegEffect]), never through
     * the account the row happens to be filed under.
     *
     * Debit CREDIT differs from [calculateNewBalance] (which ignores it) on
     * purpose: ingestion can't hit that branch — a CREDIT-type SMS forces
     * isCreditCard=true — while the manual paths have always treated a CREDIT
     * row on a debit account as money out, and this preserves that.
     */
    fun signedBalanceEffect(
        isCreditCard: Boolean,
        transactionType: TransactionType,
        amount: BigDecimal
    ): BigDecimal = when (transactionType) {
        TransactionType.INCOME -> if (isCreditCard) amount.negate() else amount
        TransactionType.EXPENSE,
        TransactionType.CREDIT,
        TransactionType.INVESTMENT -> if (isCreditCard) amount else amount.negate()
        TransactionType.TRANSFER -> BigDecimal.ZERO
    }

    /**
     * Signed delta one TRANSFER leg applies to the leg's account. Money arriving
     * at a debit account raises its balance; arriving at a credit card it pays
     * down outstanding, so the sign flips.
     */
    fun transferLegEffect(
        isCreditCard: Boolean,
        incoming: Boolean,
        amount: BigDecimal
    ): BigDecimal {
        val debitEffect = if (incoming) amount else amount.negate()
        return if (isCreditCard) debitEffect.negate() else debitEffect
    }
}
