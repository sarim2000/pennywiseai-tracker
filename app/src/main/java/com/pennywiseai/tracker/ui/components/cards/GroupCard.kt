package com.pennywiseai.tracker.ui.components.cards

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.income
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.sumByCurrency
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/**
 * A group's signed total per currency (expense/credit negative, income positive).
 * In unified mode ([displayCurrency] set) everything is in that one currency; a
 * foreign txn missing from [convertedAmounts] has no known rate, so it is left
 * out rather than added at face value (#670). In native mode each row keeps its
 * own currency, so mixed rows are never summed into one figure (#1514).
 */
internal fun groupCardTotals(
    transactions: List<TransactionEntity>,
    convertedAmounts: Map<Long, BigDecimal>,
    displayCurrency: String?,
): Map<String, Money> {
    val signed = transactions.mapNotNull { tx ->
        val amount = convertedAmounts[tx.id]
            ?: if (displayCurrency != null && !tx.currency.equals(displayCurrency, ignoreCase = true)) {
                return@mapNotNull null
            } else {
                tx.amount
            }
        val value = when (tx.transactionType) {
            TransactionType.EXPENSE, TransactionType.CREDIT -> amount.negate()
            TransactionType.INCOME -> amount
            else -> return@mapNotNull null
        }
        (displayCurrency ?: tx.currency) to value
    }
    val totals = signed.sumByCurrency({ it.first }, { it.second })
    if (totals.isNotEmpty()) return totals
    val currency = displayCurrency ?: transactions.firstOrNull()?.currency ?: "INR"
    return mapOf(currency to Money.zero(currency))
}

private const val MAX_CARD_CURRENCIES = 2

/** The card's amount slot is one line: the two largest totals, the rest stand behind an ellipsis. */
internal fun shownGroupCardTotals(totals: Map<String, Money>): Map<String, Money> =
    totals.entries.sortedByDescending { it.value.amount.abs() }.take(MAX_CARD_CURRENCIES).associate { it.toPair() }

@Composable
fun GroupCard(
    group: TransactionGroupEntity,
    transactions: List<TransactionEntity>,
    convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    displayCurrency: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** A row's place in a grouped list (Home's Recent); a lone card by default. */
    shape: CornerBasedShape = MaterialTheme.shapes.large,
) {
    val view = LocalView.current

    val totals = remember(transactions, convertedAmounts, displayCurrency) {
        groupCardTotals(transactions, convertedAmounts, displayCurrency)
    }
    val amountColor = if (totals.values.all { it.amount.signum() >= 0 }) {
        MaterialTheme.colorScheme.income
    } else {
        MaterialTheme.colorScheme.spending
    }

    val latestDate = remember(transactions) { transactions.map { it.dateTime }.maxOrNull() }
    val dateText = latestDate?.format(AppDates.dayMonthFormatter()) ?: ""

    val countText = pluralStringResource(R.plurals.group_card_transaction_count, transactions.size, transactions.size)
    val subtitle = if (dateText.isNotEmpty()) stringResource(R.string.group_card_subtitle, countText, dateText) else countText

    val formattedAmount = CurrencyFormatter.formatByCurrency(shownGroupCardTotals(totals), signPrefix = "+", keepSign = true) +
        // No hint of more currencies while amounts are hidden: the mask says nothing about how many there are.
        if (totals.size > MAX_CARD_CURRENCIES && !AmountPrivacy.hidden) " · …" else ""

    ListItemCardV2(
        title = group.name,
        subtitle = subtitle,
        amount = formattedAmount,
        amountColor = amountColor,
        shape = shape,
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
