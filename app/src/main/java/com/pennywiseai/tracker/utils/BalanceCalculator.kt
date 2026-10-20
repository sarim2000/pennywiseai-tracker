package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal

object BalanceCalculator {
    /**
     * Carries an account's type over when its balance row is rebuilt from a new
     * SMS/transaction. The single implementation every balance-rebuild call site
     * shares, so a type can't silently drop on one path while staying fixed on
     * another (#798).
     */
    fun preservedAccountType(existing: AccountBalanceEntity?): String? = existing?.accountType

    /**
     * `is_credit_card` for a balance row rebuilt from an SMS. An account with no
     * type yet (or typed CREDIT) is marked by a card SMS and an existing card stays
     * one. An account with a non-card type (set on Add Account, or changed in Edit
     * Account) keeps that type: a later card SMS must not undo the user's choice.
     * A LOAN is never a card; its balance is still handled as owed, through
     * [AccountBalanceEntity.isLiability]. (#792)
     */
    fun resolvedIsCreditCard(smsIsCard: Boolean, existing: AccountBalanceEntity?): Boolean {
        val wasCard = existing?.isCreditCard == true
        val type = existing?.accountType
        val hasNonCardType = type != null && !type.equals("CREDIT", ignoreCase = true)
        return if (hasNonCardType) wasCard && existing?.isLoan != true else smsIsCard || wasCard
    }

    /**
     * Balance an account row should carry after a parsed SMS whose amount is in
     * [transactionCurrency] while the account is kept in [accountCurrency].
     *
     * A spend in another currency (USD on an INR card) can't be added to the
     * balance — that would sum across currencies, and the bank's own converted
     * figure only arrives with a later SMS. So it keeps the explicit balance the
     * SMS reports (that is the account's own figure), else the current balance.
     * Same-currency SMS go through [calculateNewBalance] unchanged. (#888)
     */
    fun balanceAfter(
        explicitBalance: BigDecimal?,
        isCreditCard: Boolean,
        transactionType: TransactionType,
        transactionAmount: BigDecimal,
        transactionCurrency: String,
        accountCurrency: String,
        currentBalance: BigDecimal?
    ): BigDecimal =
        if (transactionCurrency == accountCurrency) {
            calculateNewBalance(explicitBalance, isCreditCard, transactionType, transactionAmount, currentBalance)
        } else {
            explicitBalance ?: currentBalance ?: BigDecimal.ZERO
        }

    /**
     * Calculates the new account balance post-transaction.
     * For credit cards, balance represents outstanding debt.
     *
     * Throughout this object `isCreditCard` means "the balance is an amount
     * owed": pass [AccountBalanceEntity.isLiability], so loan accounts get the
     * credit-card rules (a payment lowers what's owed). (#792)
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
