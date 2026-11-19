package com.pennywiseai.tracker.presentation.transactions

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.ledger.forTotals
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.sumByCurrency
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToEditCategory(
    transaction: TransactionEntity,
    onRequestEdit: (TransactionEntity) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Not rememberSaveable: a restored row must start at rest, or onDismiss re-fires.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    // M3 re-runs onDismiss whenever the lambda changes while dismissed, so keep it stable.
    val currentTransaction by rememberUpdatedState(transaction)
    val currentOnRequestEdit by rememberUpdatedState(onRequestEdit)
    val onDismiss = remember {
        { _: SwipeToDismissBoxValue ->
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            currentOnRequestEdit(currentTransaction)
            scope.launch { dismissState.reset() }
            Unit
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        // The swipe is purely a gesture trigger: fire the edit, then animate back to rest.
        onDismiss = onDismiss,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart ->
                        MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "swipe_edit_background"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Dimensions.Padding.content),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.txn_list_change_category),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        content = { content() }
    )
}

/**
 * A day's heading in the Activity list: "Today", "Yesterday" or "Mon, Nov 17"
 * on the left, the day's [net] (money in less spent, already formatted per
 * currency) on the right. Painted on the page background so it can stick
 * while its rows scroll under it.
 */
@Composable
internal fun ActivityDayHeader(
    title: String,
    net: String?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.sm)
            .padding(top = Spacing.md, bottom = Spacing.sm)
    ) {
        Text(
            text = title,
            style = PennyWiseText.sectionHeader,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (net != null) {
            Text(
                text = net,
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The list's rows in display order, cut into days when sorted by date. */
internal data class DaySection(
    /** Null when the list is sorted by amount or merchant: one section, no heading. */
    val date: LocalDate?,
    val rows: List<TransactionEntity>
)

internal fun daySections(rows: List<TransactionEntity>, sort: SortOption): List<DaySection> = when {
    rows.isEmpty() -> emptyList()
    sort == SortOption.DATE_NEWEST || sort == SortOption.DATE_OLDEST ->
        // groupBy keeps first-seen order, so the days follow the sort.
        rows.groupBy { it.dateTime.toLocalDate() }.map { (day, dayRows) -> DaySection(day, dayRows) }
    else -> listOf(DaySection(null, rows))
}

/**
 * The day's net (money in less spent) as text, or null when nothing in it
 * counts (only transfers, say). Each row counts in the currency it's shown in:
 * its converted figure in unified mode when there is one, else its own. Buckets
 * are never added together, so a mixed day reads "−₹500 · +$10".
 */
internal fun dayNet(
    rows: List<TransactionEntity>,
    splitShares: CategorySplitShares?,
    convertedAmounts: Map<Long, BigDecimal>,
    displayCurrency: String?
): String? {
    val signed = rows.mapNotNull { tx ->
        val sign = when (tx.transactionType) {
            TransactionType.INCOME -> BigDecimal.ONE
            TransactionType.EXPENSE -> BigDecimal.ONE.negate()
            // Card spend, like transfers, stays out of Spent / Money in, so out of the net too.
            TransactionType.CREDIT, TransactionType.TRANSFER, TransactionType.INVESTMENT -> return@mapNotNull null
        }
        // As the totals count it: loans, lent shares and excluded rows drop out.
        val counted = (if (splitShares != null) splitShares.forTotals(tx) else tx.forTotals())?.amount
            ?: return@mapNotNull null
        val shown = splitShares?.shownAmount(tx) ?: tx.amount
        val converted = convertedAmounts[tx.id]
        if (converted != null && displayCurrency != null && shown.signum() != 0) {
            val inDisplay = if (counted.compareTo(shown) == 0) converted
            else converted.multiply(counted).divide(shown, 2, RoundingMode.HALF_UP)
            displayCurrency to inDisplay * sign
        } else {
            tx.currency to counted * sign
        }
    }
    val byCurrency = signed.sumByCurrency({ it.first }, { it.second })
    val nonZero = byCurrency.values.filter { it.amount.signum() != 0 }
    if (nonZero.isEmpty()) return null
    if (AmountPrivacy.hidden) return AmountPrivacy.MASK
    return nonZero
        .sortedByDescending { it.amount.abs() }
        .joinToString(" \u00B7 ") { money ->
            CurrencyFormatter.signed(
                if (money.amount.signum() > 0) "+" else "-",
                CurrencyFormatter.formatCurrency(money.amount.abs(), money.currency)
            )
        }
}

/** "Today", "Yesterday", "Mon, Nov 17", with the year when it isn't this one. */
@Composable
internal fun dayTitle(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> stringResource(R.string.txn_list_group_today)
    today.minusDays(1) -> stringResource(R.string.txn_list_group_yesterday)
    else -> {
        val date = if (day.year == today.year) AppDates.dayMonthFormatter() else AppDates.dateFormatter()
        AppDates.pattern("EEE").format(day) + ", " + date.format(day)
    }
}

@Composable
internal fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod,
    onAddClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val headline = when {
        searchQuery.isNotEmpty() -> stringResource(R.string.txn_list_empty_no_results, searchQuery)
        selectedPeriod != TimePeriod.ALL -> stringResource(R.string.txn_list_empty_period, selectedPeriod.label.lowercase())
        else -> stringResource(R.string.txn_list_empty_title)
    }
    val description = when {
        searchQuery.isNotEmpty() -> stringResource(R.string.txn_list_empty_search_hint)
        selectedPeriod != TimePeriod.ALL -> stringResource(R.string.txn_list_empty_period_hint)
        else -> stringResource(R.string.txn_list_empty_hint)
    }
    val actionLabel = if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) stringResource(R.string.txn_list_add_transaction) else null
    val onAction = if (actionLabel != null) onAddClick else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Padding.empty),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            headline = headline,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}
