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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.components.buildHeatmapMonthLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

/** A month or less reads as a calendar; anything longer as a week-column strip. */
private const val CALENDAR_MAX_WEEKS = 6

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
    val textStyle = MaterialTheme.typography.labelSmall
    val gap = Spacing.xs

    fun cellColor(date: LocalDate): Color {
        val intensity = (amounts[date] ?: 0.0) / maxAmount
        return when {
            date < firstDate || date > endDate -> colors.surfaceContainerLow
            intensity == 0.0 -> colors.surfaceContainerHighest
            intensity < 0.25 -> colors.primary.copy(alpha = 0.25f)
            intensity < 0.5 -> colors.primary.copy(alpha = 0.5f)
            intensity < 0.75 -> colors.primary.copy(alpha = 0.75f)
            else -> colors.primary
        }
    }

    if (totalWeeks <= CALENDAR_MAX_WEEKS) {
        // Weekdays across, one row per week: the cells share the full card width.
        Column(
            modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = textStyle,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            repeat(totalWeeks) { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(7) { day ->
                        val date = startDate.plusWeeks(week.toLong()).plusDays(day.toLong())
                        val inRange = date in firstDate..endDate
                        val strong = ((amounts[date] ?: 0.0) / maxAmount) >= 0.5
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.25f)
                                .background(cellColor(date), MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center
                        ) {
                            if (inRange) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = textStyle,
                                    color = if (strong) colors.onPrimary else colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableWidth = maxWidth - Dimensions.Icon.medium - gap
        // Columns stretch to fill the width; rows stay capped so the strip never gets tall.
        val cellWidth = ((availableWidth - gap * (totalWeeks - 1)) / totalWeeks)
            .coerceAtLeast(Dimensions.Icon.inline)
        val cellSize = cellWidth.coerceAtMost(Dimensions.Icon.large)
        val gridFits = cellWidth * totalWeeks + gap * (totalWeeks - 1) <= availableWidth + gap / 2
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
                        modifier = Modifier.width(cellWidth).clearAndSetSemantics {},
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        repeat(7) { day ->
                            val date = startDate.plusWeeks(week.toLong()).plusDays(day.toLong())
                            Box(
                                Modifier.width(cellWidth).height(cellSize)
                                    .background(cellColor(date), MaterialTheme.shapes.small)
                            )
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
