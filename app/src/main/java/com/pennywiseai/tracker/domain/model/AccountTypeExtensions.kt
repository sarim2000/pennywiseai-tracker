package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

enum class AccountType {
    SAVINGS,
    CURRENT,
    CREDIT,
    CASH,
    /** Bank loan account (#792): the balance is the amount still owed, like a credit card. */
    LOAN
}

/**
 * Converts AccountType enum to database string representation
 */
fun AccountType.toDatabaseString(): String = this.name

/**
 * Converts database string to AccountType enum, with fallback
 */
fun String?.toAccountType(): AccountType = when (this?.uppercase()) {
    "SAVINGS" -> AccountType.SAVINGS
    "CURRENT" -> AccountType.CURRENT
    "CREDIT" -> AccountType.CREDIT
    "CASH" -> AccountType.CASH
    "LOAN" -> AccountType.LOAN
    else -> AccountType.SAVINGS  // Default fallback
}

/**
 * The type this account behaves as. Loan and credit card come from the flags
 * that drive balances and sections ([AccountBalanceEntity.isLoan],
 * [AccountBalanceEntity.isCreditCard]), so the label never disagrees with how
 * the account is treated; otherwise the stored `account_type`.
 */
fun AccountBalanceEntity.getAccountType(): AccountType = when {
    isLoan -> AccountType.LOAN
    isCreditCard -> AccountType.CREDIT
    else -> accountType.toAccountType()
}
