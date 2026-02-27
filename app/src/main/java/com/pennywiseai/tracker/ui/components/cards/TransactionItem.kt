package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.ListItemCard
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun TransactionItem(
    transaction: TransactionEntity,
    convertedAmount: BigDecimal? = null,
    displayCurrency: String? = null,
    showDate: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val amountColor = remember(transaction.transactionType, isDark) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> if (!isDark) income_light else income_dark
            TransactionType.EXPENSE -> if (!isDark) expense_light else expense_dark
            TransactionType.CREDIT -> if (!isDark) credit_light else credit_dark
            TransactionType.TRANSFER -> if (!isDark) transfer_light else transfer_dark
            TransactionType.INVESTMENT -> if (!isDark) investment_light else investment_dark
        }
    }

    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("MMM d \u2022 h:mm a") }
    val dateTimeText = remember(transaction.dateTime) {
        transaction.dateTime.format(dateTimeFormatter)
    }

    val subtitle = remember(transaction, dateTimeText) {
        buildList {
            add(dateTimeText)
            when (transaction.transactionType) {
                TransactionType.CREDIT -> add("Credit")
                TransactionType.TRANSFER -> add("Transfer")
                TransactionType.INVESTMENT -> add("Investment")
                else -> {}
            }
            if (transaction.isRecurring) add("Recurring")
        }.joinToString(" \u2022 ")
    }

    ListItemCard(
        title = transaction.merchantName,
        subtitle = subtitle,
        amount = if (convertedAmount != null && displayCurrency != null) {
            CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
        } else {
            transaction.formatAmount()
        },
        amountColor = amountColor,
        onClick = onClick,
        modifier = modifier,
        leadingContent = {
            BrandIcon(
                merchantName = transaction.merchantName,
                size = 40.dp,
                showBackground = true
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Transaction type icon
                when (transaction.transactionType) {
                    TransactionType.INCOME -> Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Income",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    TransactionType.EXPENSE -> Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = "Expense",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    TransactionType.CREDIT -> Icon(
                        Icons.Default.CreditCard,
                        contentDescription = "Credit Card",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    TransactionType.TRANSFER -> Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Transfer",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                    TransactionType.INVESTMENT -> Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Investment",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = amountColor
                    )
                }

                // Amount display
                if (convertedAmount != null && displayCurrency != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = amountColor
                        )
                        Text(
                            text = "(${transaction.formatAmount()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = transaction.formatAmount(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = amountColor
                    )
                }
            }
        }
    )
}
