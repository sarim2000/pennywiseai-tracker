package com.pennywiseai.tracker.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
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

/**
 * Formats account type for UI display
 */
@Composable
@ReadOnlyComposable
fun AccountType.displayName(): String = stringResource(
    when (this) {
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CURRENT -> R.string.account_type_current
        AccountType.CREDIT -> R.string.account_type_credit_card
        AccountType.CASH -> R.string.account_type_cash
        AccountType.LOAN -> R.string.account_type_loan
    }
)

/** Icon for an account type, shared by every account picker. */
val AccountType.icon: ImageVector
    get() = when (this) {
        AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
        AccountType.CREDIT -> Icons.Default.CreditCard
        AccountType.CASH -> Icons.Default.Money
        AccountType.LOAN -> Icons.Default.RequestQuote
    }
