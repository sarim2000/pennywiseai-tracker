package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun SummaryCardV2(
    title: String,
    amount: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    amountColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    sparklineSlot: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        colors = colors,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label first, figure second: the label is the quieter element, so
            // it sits above at label weight while the figure carries the size.
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = Dimensions.Alpha.subtitle
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = amount,
                style = PennyWiseText.heroAmount,
                color = amountColor,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                        alpha = Dimensions.Alpha.subtitle
                    ),
                    textAlign = TextAlign.Center
                )
            }
            if (sparklineSlot != null) {
                Spacer(modifier = Modifier.height(Spacing.md))
                sparklineSlot()
            }
        }
    }
}
