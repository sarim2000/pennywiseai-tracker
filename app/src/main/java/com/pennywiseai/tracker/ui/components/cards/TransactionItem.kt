package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.components.BrandIcon
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
    val view = LocalView.current
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

    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("d MMM \u00B7 h:mm a") }
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
        }.joinToString(" \u00B7 ")
    }

    val amountPrefix = remember(transaction.transactionType) {
        when (transaction.transactionType) {
            TransactionType.INCOME -> "+"
            TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.INVESTMENT -> "-"
            TransactionType.TRANSFER -> ""
        }
    }

    val formattedAmount = if (convertedAmount != null && displayCurrency != null) {
        CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)
    } else {
        transaction.formatAmount()
    }

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    ListItemCardV2(
        title = transaction.merchantName,
        subtitle = subtitle,
        amount = "$amountPrefix$formattedAmount",
        amountColor = amountColor,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = modifier,
        leadingContent = {
            val iconModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier.sharedElement(
                        sharedTransitionScope.rememberSharedContentState(
                            key = "brand_icon_${transaction.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            } else {
                Modifier
            }
            BrandIcon(
                merchantName = transaction.merchantName,
                modifier = iconModifier,
                size = 42.dp,
                showBackground = true
            )
        },
        trailingContent = {
            if (convertedAmount != null && displayCurrency != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$amountPrefix${CurrencyFormatter.formatCurrency(convertedAmount, displayCurrency)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
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
                    text = "$amountPrefix$formattedAmount",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }
        }
    )
}
