package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.AmountExpression
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.isAppInDarkTheme
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/**
 * A keypad for working an amount out ("12.5+30×2"), with the result live below
 * it. Apply hands the result, rounded to [currency]'s decimals, to the caller's
 * own amount path; Cancel and dismissing leave the form as it was. A zero or
 * negative result can't be applied: no amount field takes one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountCalculatorSheet(
    initialAmount: String,
    currency: String,
    onApply: (BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    // Kept with '.' as the point; shown with the locale's. Never cut short: dropping
    // digits changes the amount, so an over-long start is left for check() to reject.
    var expression by rememberSaveable { mutableStateOf(initialAmount) }
    val outcome = AmountExpression.check(expression, MinorUnits.digits(currency))
    val result = (outcome as? AmountExpression.Outcome.Amount)?.value
    val canApply = result != null && result.signum() > 0
    // Why there's nothing to use, when it's worth saying; null shows the result.
    val problem = when {
        canApply -> null
        result != null -> stringResource(R.string.calculator_must_be_positive)
        outcome == AmountExpression.Outcome.DividesByZero -> stringResource(R.string.calculator_divide_by_zero)
        outcome == AmountExpression.Outcome.TooLarge -> stringResource(R.string.calculator_too_large)
        expression.isNotEmpty() -> stringResource(R.string.calculator_unfinished)
        else -> null
    }
    val locale = LocalConfiguration.current.locales[0]
    val point = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator }
    fun shown(text: String) = text.replace('.', point)
    fun type(key: String) {
        if (expression.length + key.length <= AmountExpression.MAX_LENGTH) expression += key
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(bottom = Spacing.lg)
                // Large fonts and landscape: scroll rather than run off the sheet.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.calculator_title),
                style = MaterialTheme.typography.titleLarge
            )
            // Sums read left to right in every language, as on a calculator.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = shown(expression).ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium.money(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = when {
                            // With its currency, so it's clear what Apply writes. Not masked
                            // when amounts are hidden: the sum being typed is on screen anyway.
                            canApply -> "= " + CurrencyFormatter.formatCurrency(result, currency, maskIfHidden = false)
                            else -> problem ?: " "
                        },
                        style = MaterialTheme.typography.titleMedium.money(),
                        // A sum still being typed isn't a mistake, so it isn't red.
                        color = if (problem != null && outcome != AmountExpression.Outcome.Unfinished) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    Keypad(
                        point = point,
                        onKey = ::type,
                        onClear = { expression = "" },
                        onBackspace = { expression = expression.dropLast(1) },
                        // "=" folds the sum into its result, to keep going from there.
                        onEquals = { result?.let { expression = it.toPlainString() } }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            ) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.add_cancel))
                }
                Button(
                    onClick = { result?.let(onApply) },
                    enabled = canApply,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.calculator_apply))
                }
            }
        }
    }
}

@Composable
private fun Keypad(
    point: Char,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onEquals: () -> Unit
) {
    val clear = stringResource(R.string.calculator_clear)
    val divide = stringResource(R.string.calculator_divide)
    val multiply = stringResource(R.string.calculator_multiply)
    val minus = stringResource(R.string.calculator_minus)
    val plus = stringResource(R.string.calculator_plus)
    val equals = stringResource(R.string.calculator_equals)
    val decimal = stringResource(R.string.calculator_decimal_point)
    val backspace = stringResource(R.string.calculator_backspace)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Key("C", Modifier.weight(1f), operator = true, description = clear, onClick = onClear)
            Key("(", Modifier.weight(1f), operator = true) { onKey("(") }
            Key(")", Modifier.weight(1f), operator = true) { onKey(")") }
            Key("÷", Modifier.weight(1f), operator = true, description = divide) { onKey("÷") }
        }
        listOf("789" to ("×" to multiply), "456" to ("−" to minus), "123" to ("+" to plus)).forEach { (digits, op) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                digits.forEach { d -> Key(d.toString(), Modifier.weight(1f)) { onKey(d.toString()) } }
                Key(op.first, Modifier.weight(1f), operator = true, description = op.second) { onKey(op.first) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Key(point.toString(), Modifier.weight(1f), description = decimal) { onKey(".") }
            Key("0", Modifier.weight(1f)) { onKey("0") }
            FilledTonalButton(
                onClick = onBackspace,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f).heightIn(min = Dimensions.Component.minTouchTarget)
            ) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = backspace)
            }
            Key("=", Modifier.weight(1f), operator = true, description = equals, onClick = onEquals)
        }
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier,
    operator: Boolean = false,
    description: String? = null,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = if (operator) {
            // In light themes primaryContainer is as pale as the digit keys' fill, so
            // operators take primary there; in dark, primaryContainer already stands out.
            val dark = isAppInDarkTheme()
            ButtonDefaults.filledTonalButtonColors(
                containerColor = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        modifier = modifier.heightIn(min = Dimensions.Component.minTouchTarget)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            // "Divide", not "÷", for TalkBack.
            modifier = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier
        )
    }
}
