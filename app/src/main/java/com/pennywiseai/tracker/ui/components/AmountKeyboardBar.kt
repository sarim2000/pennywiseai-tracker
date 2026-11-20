package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.AmountExpression
import com.pennywiseai.tracker.domain.model.AmountInput
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.text.DecimalFormatSymbols

/**
 * The row above the keyboard while an amount field has focus: `+ − × ÷` type a
 * sum straight into the field ("120+35"), `=` folds it into its result, and the
 * check finishes the amount and puts the keyboard away. Number keyboards have
 * no operators of their own.
 */
@Composable
fun AmountKeyboardBar(
    onOperator: (Char) -> Unit,
    onEquals: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        '+' to stringResource(R.string.calculator_plus),
        '−' to stringResource(R.string.calculator_minus),
        '×' to stringResource(R.string.calculator_multiply),
        '÷' to stringResource(R.string.calculator_divide),
    )
    val equals = stringResource(R.string.calculator_equals)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            // Six keys and the six gaps around the spacer. A dialog is narrower than that:
            // the keys shrink there rather than cut the check off.
            val keySize = minOf(Dimensions.Component.minTouchTarget, (maxWidth - Spacing.sm * 6) / 6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                keys.forEach { (key, description) ->
                    OperatorKey(key.toString(), description, keySize) { onOperator(key) }
                }
                OperatorKey("=", equals, keySize, onEquals)
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = onDone,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(keySize)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.amount_bar_done))
                }
            }
        }
    }
}

@Composable
private fun OperatorKey(label: String, description: String, size: Dp, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(size)
    ) {
        // "Divide", not "÷", for TalkBack.
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description }
        )
    }
}

/**
 * Keeps an amount field's text in [AmountInput]'s form: a keystroke that can't be an
 * amount or a sum, or is finer than [currency] counts ("1.234" in INR), is turned away.
 * A ',' is the point where the device writes it so.
 */
@Composable
fun rememberAmountInputTransformation(currency: String): InputTransformation {
    val locale = LocalConfiguration.current.locales[0]
    val decimalComma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    return remember(currency, decimalComma) { amountInputTransformation({ MinorUnits.digits(currency) }, { decimalComma }) }
}

internal fun amountInputTransformation(fractionDigits: () -> Int, decimalComma: () -> Boolean) = InputTransformation {
    val typed = asCharSequence().toString()
    when (val text = normalizeAmount(typed, fractionDigits(), decimalComma(), before = originalText.toString())) {
        null -> revertAllChanges()
        typed -> Unit
        else -> replace(0, length, text)
    }
}

/**
 * [AmountInput.normalize], also turning away an edit that leaves an operand with more decimals
 * than [fractionDigits] and more than [before] had: after a switch to a currency with fewer
 * decimals, deleting them still works.
 */
internal fun normalizeAmount(typed: String, fractionDigits: Int, decimalComma: Boolean, before: String = ""): String? {
    fun decimals(text: String) = text.split(*AmountInput.OPERATORS.toCharArray()).maxOf { it.substringAfter('.', "").length }
    return AmountInput.normalize(typed, decimalComma)?.takeIf { text ->
        decimals(text) <= fractionDigits || decimals(text) <= decimals(AmountInput.normalize(before, decimalComma).orEmpty())
    }
}

/** What an amount field holding a sum says under it; [isError] for a sum that can't be an amount. */
class AmountSumHint(val text: String, val isError: Boolean)

/**
 * "= ₹155.00" under a field holding a sum, or why it isn't an amount yet; null for a
 * plain amount. Not masked while amounts are hidden: the sum being typed is on screen.
 */
@Composable
fun amountSumHint(text: String, currency: String): AmountSumHint? {
    if (!AmountInput.hasOperator(text)) return null
    return when (val outcome = AmountExpression.check(text, MinorUnits.digits(currency))) {
        is AmountExpression.Outcome.Amount -> if (outcome.value.signum() > 0) {
            AmountSumHint("= " + CurrencyFormatter.formatCurrency(outcome.value, currency, maskIfHidden = false), isError = false)
        } else {
            AmountSumHint(stringResource(R.string.calculator_must_be_positive), isError = true)
        }
        AmountExpression.Outcome.DividesByZero -> AmountSumHint(stringResource(R.string.calculator_divide_by_zero), isError = true)
        AmountExpression.Outcome.TooLarge -> AmountSumHint(stringResource(R.string.calculator_too_large), isError = true)
        // A sum still being typed isn't a mistake.
        AmountExpression.Outcome.Unfinished -> AmountSumHint(stringResource(R.string.calculator_unfinished), isError = false)
    }
}
