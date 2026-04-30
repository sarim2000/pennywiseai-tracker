package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.expense_light
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.income_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Composable
fun GroupCard(
    group: TransactionGroupEntity,
    transactions: List<TransactionEntity>,
    convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    displayCurrency: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    val total = remember(transactions, convertedAmounts, displayCurrency) {
        transactions.fold(BigDecimal.ZERO) { acc, tx ->
            val amount = convertedAmounts[tx.id] ?: tx.amount
            when (tx.transactionType) {
                TransactionType.EXPENSE, TransactionType.CREDIT -> acc - amount
                TransactionType.INCOME -> acc + amount
                else -> acc
            }
        }
    }
    val isPositive = total >= BigDecimal.ZERO
    val amountColor = if (isPositive) {
        if (isDark) income_dark else income_light
    } else {
        if (isDark) expense_dark else expense_light
    }

    val latestDate = remember(transactions) { transactions.maxOfOrNull { it.dateTime } }
    val dateText = latestDate?.format(DateTimeFormatter.ofPattern("d MMM")) ?: ""
    val currency = displayCurrency ?: transactions.firstOrNull()?.currency ?: "INR"

    val countText = "${transactions.size} transaction${if (transactions.size != 1) "s" else ""}"
    val subtitle = if (dateText.isNotEmpty()) "$countText · $dateText" else countText

    val sign = if (isPositive) "+" else "-"

    val formattedAmount = "$sign${CurrencyFormatter.formatCurrency(total.abs(), currency)}"

    ListItemCardV2(
        title = group.name,
        subtitle = subtitle,
        amount = formattedAmount,
        amountColor = amountColor,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.list)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}
