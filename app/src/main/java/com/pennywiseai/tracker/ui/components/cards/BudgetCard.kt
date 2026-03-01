package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.budget_danger_dark
import com.pennywiseai.tracker.ui.theme.budget_danger_light
import com.pennywiseai.tracker.ui.theme.budget_safe_dark
import com.pennywiseai.tracker.ui.theme.budget_safe_light
import com.pennywiseai.tracker.ui.theme.budget_warning_dark
import com.pennywiseai.tracker.ui.theme.budget_warning_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@Composable
fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pctUsed = groupSpending.percentageUsed
    val isDark = isSystemInDarkTheme()
    val isTarget = groupSpending.group.budget.groupType == BudgetGroupType.TARGET

    val safeColor = if (isDark) budget_safe_dark else budget_safe_light
    val warningColor = if (isDark) budget_warning_dark else budget_warning_light
    val dangerColor = if (isDark) budget_danger_dark else budget_danger_light

    val progressColor = when {
        pctUsed > 80f && !isTarget -> dangerColor
        pctUsed > 50f && !isTarget -> warningColor
        else -> safeColor
    }

    val budgetStatusLabel = when {
        isTarget -> if (pctUsed >= 100f) "Reached" else "In Progress"
        pctUsed > 80f -> "Over Budget"
        pctUsed > 50f -> "Warning"
        else -> "On Track"
    }

    val view = LocalView.current

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        animatedProgress = (pctUsed / 100f).coerceIn(0f, 1f)
        if (pctUsed > 80f && !isTarget) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    PennyWiseCardV2(
        modifier = modifier,
        onClick = onClick,
        contentPadding = Spacing.md
    ) {
        // Header: Budget name + status dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = groupSpending.group.budget.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(Spacing.sm))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = progressColor)
                }
                Text(
                    text = budgetStatusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = progressColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Semicircular arc gauge
        val remaining = if (groupSpending.remaining < BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else {
            groupSpending.remaining
        }

        val arcDescription = "Budget ${pctUsed.toInt()}% used, $budgetStatusLabel"
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = arcDescription }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val strokeWidth = 10.dp.toPx()
                val padding = strokeWidth / 2
                val arcSize = Size(
                    width = size.width - strokeWidth,
                    height = (size.height - padding) * 2
                )
                val topLeft = Offset(padding, padding)

                // Background track
                drawArc(
                    color = trackColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )

                // Progress arc with gradient sweep
                val arcCenter = Offset(
                    x = topLeft.x + arcSize.width / 2,
                    y = topLeft.y + arcSize.height / 2
                )
                // Sweep gradient maps 0f→1f to 0°→360° around center.
                // Our arc spans 180°→360°, which is 0.5f→1.0f in gradient space.
                val progressBrush = if (pctUsed <= 50f || isTarget) {
                    // Under 50% or target: solid safe color
                    Brush.sweepGradient(
                        colorStops = arrayOf(
                            0f to safeColor,
                            1f to safeColor
                        ),
                        center = arcCenter
                    )
                } else if (pctUsed <= 80f) {
                    // 50-80%: safe → warning gradient along the visible arc
                    Brush.sweepGradient(
                        colorStops = arrayOf(
                            0f to safeColor,
                            0.5f to safeColor,
                            0.85f to warningColor,
                            1f to warningColor
                        ),
                        center = arcCenter
                    )
                } else {
                    // Over 80%: safe → warning → danger gradient along the visible arc
                    Brush.sweepGradient(
                        colorStops = arrayOf(
                            0f to safeColor,
                            0.5f to safeColor,
                            0.7f to warningColor,
                            0.9f to dangerColor,
                            1f to dangerColor
                        ),
                        center = arcCenter
                    )
                }

                drawArc(
                    brush = progressBrush,
                    startAngle = 180f,
                    sweepAngle = 180f * animatedProgressState,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }

            // Center text inside the arc
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = CurrencyFormatter.formatCurrency(remaining, currency),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Stats row: Spent | Limit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spent column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isTarget) "Saved" else "Spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = Spacing.xs),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Limit column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isTarget) "Goal" else "Limit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Footer: days remaining
        Text(
            text = "${groupSpending.daysRemaining} days remaining",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
