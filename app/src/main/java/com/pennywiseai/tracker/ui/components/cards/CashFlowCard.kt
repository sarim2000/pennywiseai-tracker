package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.credit
import com.pennywiseai.tracker.ui.theme.investment
import com.pennywiseai.tracker.ui.theme.transfer
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * Home-screen "Money in motion" card (#384).
 *
 * Complements [com.pennywiseai.tracker.presentation.home.HomeScreen]'s
 * BalanceCard, which already owns the headline "spent / earned this month"
 * numbers. This card adds the one thing BalanceCard doesn't show: the
 * **channels outside the net cash flow** — credit-card spend, investments,
 * and transfers. It deliberately omits a duplicate "net" hero so the two
 * cards don't compete.
 *
 * The card honours the global [isBalanceHidden] flag (same eye toggle as
 * BalanceCard), masking amounts when the user has hidden their balances. If
 * every channel is zero the card renders nothing so the feed stays quiet on
 * dormant months.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashFlowCard(
    currency: String,
    creditCardSpend: BigDecimal,
    investments: BigDecimal,
    transfers: BigDecimal,
    isBalanceHidden: Boolean,
    modifier: Modifier = Modifier
) {
    val channels = remember(creditCardSpend, investments, transfers) {
        listOf(
            ChannelSlot("Credit", creditCardSpend),
            ChannelSlot("Invested", investments),
            ChannelSlot("Transferred", transfers)
        ).filter { it.amount.signum() != 0 }
    }
    if (channels.isEmpty()) return

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.md
    ) {
        Text(
            text = "Money in motion",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Channels outside your net cash flow this month",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Spacing.sm))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            for (ch in channels) {
                val dotColor = when (ch.label) {
                    "Credit" -> MaterialTheme.colorScheme.credit
                    "Invested" -> MaterialTheme.colorScheme.investment
                    "Transferred" -> MaterialTheme.colorScheme.transfer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                ChannelChip(
                    label = ch.label,
                    amount = ch.amount,
                    currency = currency,
                    dotColor = dotColor,
                    isHidden = isBalanceHidden
                )
            }
        }
    }
}

@Composable
private fun ChannelChip(
    label: String,
    amount: BigDecimal,
    currency: String,
    dotColor: Color,
    isHidden: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // Mirror BalanceCard's masking so the eye toggle works the
                // same way across the whole home feed.
                text = if (isHidden) "••••" else CurrencyFormatter.formatCurrency(amount.abs(), currency),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

private data class ChannelSlot(val label: String, val amount: BigDecimal)
