package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.credit
import com.pennywiseai.tracker.ui.theme.expense
import com.pennywiseai.tracker.ui.theme.income
import com.pennywiseai.tracker.ui.theme.investment
import com.pennywiseai.tracker.ui.theme.transfer
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

/**
 * Home-screen "This Month" cash-flow card (#384).
 *
 * Frames the month as one headline number — net cash flow — plus the channels
 * that net flow deliberately doesn't include: credit-card spend, investments,
 * and transfers. The hero answers "how am I doing?", the channel chips answer
 * "what's moving outside that number?". A channel chip is omitted when its
 * amount is zero; if every value is zero the card returns nothing so the home
 * feed stays quiet on dormant months.
 *
 * Aesthetic is editorial-calm: small-caps section labels, one display-size
 * hero, and a single row of semantic-color-dotted chips for the breakdown.
 * The card deliberately doesn't duplicate BalanceCard (overall balance + MoM)
 * — it complements it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashFlowCard(
    month: String,
    currency: String,
    netCashFlow: BigDecimal,
    creditCardSpend: BigDecimal,
    investments: BigDecimal,
    transfers: BigDecimal,
    modifier: Modifier = Modifier
) {
    val channels = remember(creditCardSpend, investments, transfers) {
        listOf(
            ChannelSlot("Credit", creditCardSpend),
            ChannelSlot("Invested", investments),
            ChannelSlot("Transferred", transfers)
        ).filter { it.amount.signum() != 0 }
    }

    // Stay quiet on dormant months.
    if (netCashFlow.signum() == 0 && channels.isEmpty()) return

    // Three-way: negative → expense red, positive → income green, exactly zero
    // → neutral (a month with only a credit-card spend nets to 0 but isn't
    // really "positive", so we don't paint it green or sign it with "+").
    val heroSignum = netCashFlow.signum()
    val heroColor = when (heroSignum) {
        -1 -> MaterialTheme.colorScheme.expense
        1 -> MaterialTheme.colorScheme.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    val heroSign = when (heroSignum) {
        -1 -> "-"
        1 -> "+"
        else -> ""
    }

    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.lg
    ) {
        // Header: small-caps section label + month on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SmallCapsLabel("This Month")
            Text(
                text = month,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(Spacing.md))

        // Hero: net cash flow.
        Text(
            text = "Net cash flow",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "$heroSign${CurrencyFormatter.formatCurrency(netCashFlow.abs(), currency)}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = heroColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (channels.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(Spacing.md))

            SmallCapsLabel("Also flowed")
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
                        dotColor = dotColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelChip(
    label: String,
    amount: BigDecimal,
    currency: String,
    dotColor: Color
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
                text = CurrencyFormatter.formatCurrency(amount.abs(), currency),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Tracking-spaced uppercase label — a small-caps stand-in (Compose doesn't
 * have a native font-feature for small caps). Used as the section header.
 */
@Composable
private fun SmallCapsLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

private data class ChannelSlot(val label: String, val amount: BigDecimal)
