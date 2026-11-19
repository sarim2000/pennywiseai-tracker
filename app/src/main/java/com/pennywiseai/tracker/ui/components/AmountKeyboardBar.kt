package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pennywiseai.shared.domain.money.MinorUnits
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.AmountExpression
import com.pennywiseai.tracker.domain.model.AmountInput
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter

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
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            keys.forEach { (key, description) ->
                OperatorKey(key.toString(), description) { onOperator(key) }
            }
            OperatorKey("=", equals, onEquals)
            Spacer(Modifier.weight(1f))
            FilledIconButton(
                onClick = onDone,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(Dimensions.Component.minTouchTarget)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.amount_bar_done))
            }
        }
    }
}

@Composable
private fun OperatorKey(label: String, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(Dimensions.Component.minTouchTarget)
    ) {
        // "Divide", not "÷", for TalkBack.
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description }
        )
    }
}

/** Where an amount field's cursor goes when its text is set from outside: the end. */
val AmountCursorAtEnd = TextRange(Int.MAX_VALUE)

/**
 * An amount field's value: typing keeps the cursor where it was ([cursor] is the last
 * selection); text set from outside (an operator from the bar, a folded sum) uses
 * [AmountCursorAtEnd], so what's typed next lands after it.
 */
fun amountFieldValue(text: String, cursor: TextRange): TextFieldValue =
    TextFieldValue(text, TextRange(cursor.start.coerceIn(0, text.length), cursor.end.coerceIn(0, text.length)))

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
