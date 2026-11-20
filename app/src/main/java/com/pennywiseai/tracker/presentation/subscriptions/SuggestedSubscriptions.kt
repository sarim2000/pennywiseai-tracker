package com.pennywiseai.tracker.presentation.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.RecurringSuggestion
import com.pennywiseai.tracker.ui.components.AmountBarHost
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.orSupporting
import com.pennywiseai.tracker.ui.components.rememberAmountEntry
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** A billing cycle as a capitalized label ("Monthly"). */
@Composable
internal fun billingCycleLabel(cycle: String): String = stringResource(
    when (cycle) {
        "Weekly" -> R.string.add_sub_cycle_weekly
        "Quarterly" -> R.string.add_sub_cycle_quarterly
        "Semi-Annual" -> R.string.add_sub_cycle_semi_annual
        "Annual" -> R.string.add_sub_cycle_annual
        else -> R.string.add_sub_cycle_monthly
    }
)

/**
 * The typed amount, or null. The last separator is the decimal point and any
 * before it group thousands, so "1,234.50" and "1.234,50" both read as 1234.50.
 * A lone separator is always decimal: "19,900" is a 3-decimal dinar amount.
 */
internal fun parseAmount(text: String): BigDecimal? {
    val trimmed = text.trim().replace(" ", "")
    val decimalAt = maxOf(trimmed.lastIndexOf('.'), trimmed.lastIndexOf(','))
    val normalized = if (decimalAt < 0) trimmed else {
        trimmed.substring(0, decimalAt).replace(".", "").replace(",", "") + "." + trimmed.substring(decimalAt + 1)
    }
    return normalized.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
}

/** "15.49" for a fixed bill, "27.10–31.40" for one whose amount moves. */
@Composable
private fun suggestionAmount(suggestion: RecurringSuggestion): String =
    if (suggestion.isVariable && suggestion.minAmount.compareTo(suggestion.maxAmount) != 0) {
        stringResource(
            R.string.subscription_suggestion_amount_range,
            CurrencyFormatter.formatCurrency(suggestion.minAmount, suggestion.currency),
            CurrencyFormatter.formatCurrency(suggestion.maxAmount, suggestion.currency)
        )
    } else {
        CurrencyFormatter.formatCurrency(suggestion.amount, suggestion.currency)
    }

/**
 * One suggested bill: what repeats and how often, with Review / Not recurring.
 * [position] places it in the segmented Suggested block.
 */
@Composable
internal fun SuggestedSubscriptionItem(
    suggestion: RecurringSuggestion,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    position: ListItemPosition = ListItemPosition.Single,
) {
    GroupedRow(position = position, modifier = modifier) {
        BrandIcon(merchantName = suggestion.merchantName, category = suggestion.category, size = Dimensions.Icon.avatar)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(suggestion.merchantName, style = PennyWiseText.rowTitle)
            Text(
                stringResource(
                    R.string.subscription_suggestion_pattern,
                    billingCycleLabel(suggestion.billingCycle),
                    pluralStringResource(
                        R.plurals.subscription_suggestion_payments,
                        suggestion.payments.size,
                        suggestion.payments.size
                    )
                ),
                style = PennyWiseText.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(suggestionAmount(suggestion), style = PennyWiseText.amountRow)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                TextButton(onClick = onReview, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.subscription_suggestion_review))
                }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.subscription_suggestion_not_recurring))
                }
            }
        }
    }
}

/**
 * Review before tracking: the payments the suggestion is built on, the amount
 * to track (editable — a bill that varies has no single right answer) and the
 * next expected date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurringSuggestionSheet(
    suggestion: RecurringSuggestion,
    onDismiss: () -> Unit,
    onTrack: (BigDecimal) -> Unit,
    onNotRecurring: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    var amountText by remember(suggestion.key) { mutableStateOf(suggestion.amount.toPlainString()) }
    val amount = parseAmount(amountText)

    val closeThen: (() -> Unit) -> Unit = { action ->
        action()
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Scrolls, so a year of payments on a small screen can't push the amount
        // and Track bill out of reach.
        AmountBarHost {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandIcon(merchantName = suggestion.merchantName, category = suggestion.category, size = Dimensions.Icon.avatar)
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.merchantName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.subscription_suggestion_sheet_subtitle,
                                billingCycleLabel(suggestion.billingCycle),
                                categoryLabel(suggestion.category)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))

                Text(
                    text = pluralStringResource(
                        R.plurals.subscription_suggestion_sheet_found,
                        suggestion.payments.size,
                        suggestion.payments.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                val dateFormatter = AppDates.dateFormatter()
                var showAllPayments by remember(suggestion.key) { mutableStateOf(false) }
                val newestFirst = suggestion.payments.asReversed()
                val shownPayments = if (showAllPayments) newestFirst else newestFirst.take(PAYMENTS_PREVIEW)
                GroupedList {
                    shownPayments.forEachIndexed { index, payment ->
                        GroupedRow(position = ListItemPosition.from(index, shownPayments.size)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                Text(payment.date.format(dateFormatter), style = PennyWiseText.rowSubtitle)
                                Text(
                                    CurrencyFormatter.formatCurrency(payment.amount, suggestion.currency),
                                    style = PennyWiseText.amountRow
                                )
                            }
                        }
                    }
                }
                if (newestFirst.size > PAYMENTS_PREVIEW) {
                    TextButton(onClick = { showAllPayments = !showAllPayments }, shapes = ButtonDefaults.shapes()) {
                        Text(
                            if (showAllPayments) stringResource(R.string.subscriptions_suggested_show_less)
                            else stringResource(R.string.subscriptions_suggested_show_all, newestFirst.size)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md))

                val amountEntry = rememberAmountEntry(amountText, { amountText = it }, suggestion.currency)
                val amountSum = amountEntry.sumHint
                OutlinedTextField(
                    shape = RoundedField.shape,
                    state = amountEntry.state,
                    inputTransformation = amountEntry.inputTransformation,
                    label = { Text(stringResource(R.string.subscription_suggestion_sheet_amount, suggestion.currency)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    isError = amountSum?.isError ?: (amount == null),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = amountSum.orSupporting(
                        if (suggestion.isVariable) {
                            { Text(stringResource(R.string.subscription_suggestion_sheet_variable_hint)) }
                        } else null
                    ),
                    modifier = Modifier.fillMaxWidth().then(amountEntry.modifier),
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(
                        R.string.subscription_suggestion_sheet_next,
                        suggestion.nextDueDate.format(dateFormatter)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))

                Button(
                    onClick = { amount?.let { closeThen { onTrack(it) } } },
                    enabled = amount != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.Component.buttonHeight),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = stringResource(R.string.subscription_suggestion_sheet_track),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                TextButton(
                    onClick = { closeThen(onNotRecurring) },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.subscription_suggestion_not_recurring))
                }
                Text(
                    text = stringResource(R.string.subscription_suggestion_sheet_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.sm))
            }
        }
    }
}

/** Payments listed in the review sheet before "Show all". */
private const val PAYMENTS_PREVIEW = 4
