package com.pennywiseai.tracker.domain.model

import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity

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
    else -> AccountType.SAVINGS  // Default fallback
}

/**
 * Gets AccountType from AccountBalanceEntity, using accountType field
 * or falling back to isCreditCard flag for backward compatibility
 */
fun AccountBalanceEntity.getAccountType(): AccountType {
    return when {
        accountType != null -> accountType.toAccountType()
        isCreditCard -> AccountType.CREDIT
        else -> AccountType.SAVINGS
    }
}

/**
 * Formats account type for UI display
 */
fun AccountType.displayName(): String = when (this) {
    AccountType.SAVINGS -> "Savings"
    AccountType.CURRENT -> "Current"
    AccountType.CREDIT -> "Credit Card"
    AccountType.CASH -> "Cash"
}
