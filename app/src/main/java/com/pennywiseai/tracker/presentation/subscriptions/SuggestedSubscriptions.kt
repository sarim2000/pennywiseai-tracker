package com.pennywiseai.tracker.presentation.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.spending.RecurringSuggestion
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
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

/** One suggested bill: what repeats and how often, with Review / Not recurring. */
@Composable
internal fun SuggestedSubscriptionItem(
    suggestion: RecurringSuggestion,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        onClick = onReview,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimensions.Padding.content, top = Dimensions.Padding.content, end = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandIcon(merchantName = suggestion.merchantName, category = suggestion.category, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.merchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.subscription_suggestion_pattern,
                        billingCycleLabel(suggestion.billingCycle),
                        pluralStringResource(
                            R.plurals.subscription_suggestion_payments,
                            suggestion.payments.size,
                            suggestion.payments.size
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = suggestionAmount(suggestion),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (suggestion.isVariable) {
                    Text(
                        text = stringResource(R.string.subscription_suggestion_varies),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.subscription_suggestion_not_recurring))
            }
            Spacer(Modifier.width(Spacing.xs))
            FilledTonalButton(onClick = onReview, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.subscription_suggestion_review))
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandIcon(merchantName = suggestion.merchantName, category = suggestion.category, size = 44.dp)
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
            shownPayments.forEachIndexed { index, payment ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = payment.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(payment.amount, suggestion.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
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

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text(stringResource(R.string.subscription_suggestion_sheet_amount, suggestion.currency)) },
                singleLine = true,
                isError = amount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = if (suggestion.isVariable) {
                    { Text(stringResource(R.string.subscription_suggestion_sheet_variable_hint)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
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
                shape = RoundedCornerShape(Dimensions.CornerRadius.large),
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

/** Payments listed in the review sheet before "Show all". */
private const val PAYMENTS_PREVIEW = 4
