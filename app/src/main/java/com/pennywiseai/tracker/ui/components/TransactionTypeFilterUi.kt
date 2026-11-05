package com.pennywiseai.tracker.ui.components

import androidx.compose.runtime.Composable
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter

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
    TransactionTypeFilter.ALL -> Icons.AutoMirrored.Outlined.ReceiptLong
    TransactionTypeFilter.INCOME -> Icons.AutoMirrored.Outlined.TrendingUp
    TransactionTypeFilter.EXPENSE -> Icons.AutoMirrored.Outlined.TrendingDown
    TransactionTypeFilter.CREDIT -> Icons.Outlined.CreditCard
    TransactionTypeFilter.SPENDING -> Icons.Outlined.ShoppingCart
    TransactionTypeFilter.TRANSFER -> Icons.Outlined.SwapHoriz
    TransactionTypeFilter.INVESTMENT -> Icons.AutoMirrored.Outlined.ShowChart
}
