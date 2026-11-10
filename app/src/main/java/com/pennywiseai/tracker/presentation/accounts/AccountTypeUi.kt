package com.pennywiseai.tracker.presentation.accounts

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
import com.pennywiseai.tracker.domain.model.AccountType

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
