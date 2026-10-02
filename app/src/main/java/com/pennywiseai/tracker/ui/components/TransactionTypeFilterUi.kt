package com.pennywiseai.tracker.ui.components

import androidx.compose.runtime.Composable
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter

@Composable
fun TransactionTypeFilter.shortLabel(): String = stringResource(
    when (this) {
        TransactionTypeFilter.ALL -> R.string.type_filter_all
        TransactionTypeFilter.INCOME -> R.string.type_filter_income
        TransactionTypeFilter.EXPENSE -> R.string.type_filter_expense
        TransactionTypeFilter.CREDIT -> R.string.type_filter_credit
        TransactionTypeFilter.TRANSFER -> R.string.type_filter_transfer
        TransactionTypeFilter.INVESTMENT -> R.string.type_filter_investment
    }
)

fun TransactionTypeFilter.filterIcon(): ImageVector = when (this) {
    TransactionTypeFilter.ALL -> Icons.AutoMirrored.Filled.ReceiptLong
    TransactionTypeFilter.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
    TransactionTypeFilter.EXPENSE -> Icons.AutoMirrored.Filled.TrendingDown
    TransactionTypeFilter.CREDIT -> Icons.Default.CreditCard
    TransactionTypeFilter.TRANSFER -> Icons.Default.SwapHoriz
    TransactionTypeFilter.INVESTMENT -> Icons.AutoMirrored.Filled.ShowChart
}
