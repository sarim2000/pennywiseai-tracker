package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money

/**
 * A per-currency total, one currency per line, as [CurrencyFormatter.formatByCurrency]
 * orders them. Each line stays on one line and shrinks to fit rather than wrapping
 * mid-number. Hidden amounts are one mask, not one per currency.
 */
@Composable
fun AmountByCurrency(
    totals: Map<String, Money>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    // formatByCurrency joins the currencies with " · "; a single figure or the mask has none.
    val lines = CurrencyFormatter.formatByCurrency(totals).split(" · ")
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        lines.forEach { line ->
            Text(
                text = line,
                style = style,
                color = color,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, style.fontSize),
            )
        }
    }
}
