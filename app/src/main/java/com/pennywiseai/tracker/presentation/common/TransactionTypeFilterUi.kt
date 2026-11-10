package com.pennywiseai.tracker.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter

@get:StringRes
val TransactionTypeFilter.labelRes: Int
    get() = when (this) {
        TransactionTypeFilter.ALL -> R.string.filter_type_all
        TransactionTypeFilter.INCOME -> R.string.filter_type_income
        TransactionTypeFilter.EXPENSE -> R.string.filter_type_expense
        TransactionTypeFilter.CREDIT -> R.string.filter_type_credit
        TransactionTypeFilter.SPENDING -> R.string.filter_type_spending
        TransactionTypeFilter.TRANSFER -> R.string.filter_type_transfer
        TransactionTypeFilter.INVESTMENT -> R.string.filter_type_investment
    }

val TransactionTypeFilter.label: String
    @Composable @ReadOnlyComposable get() = stringResource(labelRes)

@Composable
fun TransactionTypeFilter.shortLabel(): String = stringResource(
    when (this) {
        TransactionTypeFilter.ALL -> R.string.type_filter_all
        TransactionTypeFilter.INCOME -> R.string.type_filter_income
        TransactionTypeFilter.EXPENSE -> R.string.type_filter_expense
        TransactionTypeFilter.CREDIT -> R.string.type_filter_credit
        TransactionTypeFilter.SPENDING -> R.string.type_filter_spending
        TransactionTypeFilter.TRANSFER -> R.string.type_filter_transfer
        TransactionTypeFilter.INVESTMENT -> R.string.type_filter_investment
    }
)

fun TransactionTypeFilter.filterIcon(): ImageVector = when (this) {
    TransactionTypeFilter.ALL -> Icons.AutoMirrored.Filled.ReceiptLong
    TransactionTypeFilter.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
    TransactionTypeFilter.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
    TransactionTypeFilter.CREDIT -> Icons.Default.CreditCard
    TransactionTypeFilter.SPENDING -> Icons.Default.ShoppingCart
    TransactionTypeFilter.TRANSFER -> Icons.Default.SwapHoriz
    TransactionTypeFilter.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
}
