package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pctUsed = groupSpending.percentageUsed
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        animatedProgress = (pctUsed / 100f).coerceIn(0f, 1f)
    }

    val statusColor: Color = when {
        pctUsed >= 90f -> MaterialTheme.colorScheme.error
        pctUsed >= 70f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    PennyWiseCardV2(
        modifier = modifier,
        onClick = onClick
    ) {
        // Row 1: Cadence pill + budget name + percentage pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f)
            ) {
                CadencePill(periodType = groupSpending.periodType)
                Text(
                    text = groupSpending.group.budget.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                Text(
                    text = "${pctUsed.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(
                            color = statusColor,
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Row 2: Custom rounded progress bar
        if (groupSpending.totalBudget > BigDecimal.ZERO) {
            val barShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(barShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = animatedProgressState)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(statusColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Row 3: Remaining amount (hero)
        val remainingAbs = groupSpending.remaining.abs()
        Text(
            text = if (isOverBudget) {
                "${CurrencyFormatter.formatCurrency(remainingAbs, currency)} over budget"
            } else {
                "${CurrencyFormatter.formatCurrency(groupSpending.remaining.coerceAtLeast(BigDecimal.ZERO), currency)} remaining"
            },
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Row 4: Per-cadence subtitle — always framed as a renewal
        // countdown ("Resets in X days" / "X days remaining") so the
        // user knows when the budget's window ends. The displayed
        // window comes from the per-budget current window (Weekly's
        // Jun 29..Jul 5 even when the user is on the July page), so the
        // number is consistent across month views.
        val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
        val subtitleText = when {
            groupSpending.daysRemaining == 0 && groupSpending.daysElapsed >= groupSpending.windowDays ->
                "Finished"
            groupSpending.isTrackingAllExpenses ->
                "Tracking all expenses"
            groupSpending.periodType == BudgetPeriodType.WEEKLY -> {
                val weekday = groupSpending.group.budget.weekStartDay?.let { DayOfWeek.of(it.coerceIn(1, 7)) }
                    ?: DayOfWeek.MONDAY
                // "Resets in X days" = "days until the current week ends".
                // Subtract 1 because the budget renews *after* the last
                // day, so the displayed week has (windowDays - daysRemaining)
                // days left after today. For a Wed-on-a-Mon-start week:
                // today=Wed, days remaining=5 (Thu..Mon), renewal in 4d.
                val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
                val weekdayName = weekday.name.lowercase().replaceFirstChar { it.titlecase() }
                when {
                    renewalIn == 0 -> "Resets today · ${weekdayName} renew"
                    renewalIn == 1 -> "Resets in 1 day · ${weekdayName} renew"
                    else -> "Resets in $renewalIn days · ${weekdayName} renew"
                }
            }
            groupSpending.periodType == BudgetPeriodType.MONTHLY -> {
                val startDay = groupSpending.group.budget.monthStartDay
                    ?: groupSpending.windowStart.dayOfMonth
                val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
                when {
                    renewalIn == 0 -> "Resets today · day $startDay"
                    renewalIn == 1 -> "Resets in 1 day · day $startDay"
                    else -> "Resets in $renewalIn days · day $startDay"
                }
            }
            groupSpending.periodType == BudgetPeriodType.CUSTOM -> {
                val range = "${groupSpending.windowStart.format(dateFormatter)} – ${groupSpending.windowEnd.format(dateFormatter)}"
                when {
                    isOverBudget -> "Over by ${CurrencyFormatter.formatCurrency(remainingAbs, currency)}"
                    groupSpending.daysRemaining > 1 -> "Runs $range · ${groupSpending.daysRemaining - 1} days remaining"
                    groupSpending.daysRemaining == 1 -> "Runs $range · 1 day remaining"
                    else -> "Finished"
                }
            }
            else -> "${groupSpending.daysRemaining} days remaining"
        }
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimensions.Alpha.subtitle)
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        // Row 5: Spent X of Y
        Text(
            text = "Spent ${CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)} of ${CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Small pill that names the budget's cadence at a glance: 🔁 Weekly /
 * 🗓 Monthly / 📌 One-time. Colour-coded so a user can spot which type
 * of budget they're looking at without reading the subtitle.
 */
@Composable
fun CadencePill(periodType: BudgetPeriodType) {
    val (label, bg, fg) = when (periodType) {
        BudgetPeriodType.WEEKLY -> Triple(
            "Weekly",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        BudgetPeriodType.MONTHLY -> Triple(
            "Monthly",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        BudgetPeriodType.CUSTOM -> Triple(
            "One-time",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
