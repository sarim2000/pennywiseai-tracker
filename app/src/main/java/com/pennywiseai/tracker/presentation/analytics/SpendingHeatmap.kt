package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.components.buildHeatmapMonthLabels
import com.pennywiseai.tracker.ui.components.rememberHeatmapSteps
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

/** One rounded cell per day. The text values are available from the trend card. */
@Composable
fun SpendingHeatmap(data: List<BalancePoint>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return

    val amounts = remember(data) { data.associate { it.timestamp.toLocalDate() to it.balance.toDouble() } }
    val maxAmount = remember(amounts) { amounts.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0 }
    val firstDate = data.first().timestamp.toLocalDate()
    val startDate = firstDate.with(DayOfWeek.MONDAY)
    val endDate = data.last().timestamp.toLocalDate()
    val totalWeeks = ChronoUnit.WEEKS.between(startDate, endDate).toInt() + 1
    val monthLabels = remember(startDate, endDate) { buildHeatmapMonthLabels(startDate, endDate).toMap() }
    val locale = LocalConfiguration.current.locales[0]
    val dayLabels = remember(locale) { DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale) } }
    val colors = MaterialTheme.colorScheme
    // The grid sits on the Insights chart card (surfaceContainerLow).
    val steps = rememberHeatmapSteps(colors.surfaceContainerHighest, listOf(colors.surfaceContainerLow))
    val textStyle = MaterialTheme.typography.labelSmall
    val gap = Spacing.xs

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = maxWidth - Dimensions.Icon.medium - gap
        val cellSize = ((availableWidth - gap * (totalWeeks - 1)) / totalWeeks)
            .coerceIn(Dimensions.Icon.inline, Dimensions.Icon.large)
        val gridFits = cellSize * totalWeeks + gap * (totalWeeks - 1) <= availableWidth
        val scrollState = rememberLazyListState()
        LaunchedEffect(totalWeeks, gridFits) {
            if (!gridFits) scrollState.scrollToItem(totalWeeks - 1)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(
                modifier = Modifier.clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier.width(Dimensions.Icon.medium).height(cellSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = textStyle, color = colors.onSurfaceVariant)
                    }
                }
            }
            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                items(totalWeeks) { week ->
                    Column(
                        modifier = Modifier.width(cellSize).clearAndSetSemantics {},
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        repeat(7) { day ->
                            val date = startDate.plusWeeks(week.toLong()).plusDays(day.toLong())
                            val intensity = (amounts[date] ?: 0.0) / maxAmount
                            val color = when {
                                date < firstDate || date > endDate -> colors.surfaceContainerLow
                                intensity == 0.0 -> colors.surfaceContainerHighest
                                intensity < 0.25 -> steps[0]
                                intensity < 0.5 -> steps[1]
                                intensity < 0.75 -> steps[2]
                                else -> steps[3]
                            }
                            Box(Modifier.size(cellSize).background(color, MaterialTheme.shapes.small))
                        }

                        Text(
                            text = monthLabels[week].orEmpty(),
                            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                            style = textStyle,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
