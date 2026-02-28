package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HeatmapWidget(
    transactionHeatmap: Map<Long, Int>,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState? = null,
) {
    val weeksToShow = 26
    val today = LocalDate.now()
    val startDate = today.minusWeeks((weeksToShow - 1).toLong()).with(DayOfWeek.MONDAY)

    val monthLabels = remember(startDate, today) {
        val allMonthStarts = mutableListOf<Pair<Int, String>>()
        var current = startDate
        var lastMonth = -1
        var weekIndex = 0

        while (current <= today) {
            if (current.monthValue != lastMonth) {
                val formatter = DateTimeFormatter.ofPattern("MMM")
                allMonthStarts.add(weekIndex to current.format(formatter))
                lastMonth = current.monthValue
            }
            current = current.plusWeeks(1)
            weekIndex++
        }

        val filteredLabels = mutableListOf<Pair<Int, String>>()
        for (i in allMonthStarts.indices) {
            val (week, label) = allMonthStarts[i]
            if (i == 0 && allMonthStarts.size > 1) {
                val nextWeek = allMonthStarts[1].first
                if (nextWeek - week < 4) continue
            }
            if (filteredLabels.isEmpty()) {
                filteredLabels.add(week to label)
            } else {
                val lastAddedWeek = filteredLabels.last().first
                if (week - lastAddedWeek >= 4) {
                    filteredLabels.add(week to label)
                }
            }
        }
        filteredLabels
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val containerColor = if (blurEffects)
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerLow

    PennyWiseCardV2(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                    .hazeEffect(
                        state = hazeState,
                        block = fun HazeEffectScope.() {
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                tint = HazeDefaults.tint(containerColor),
                                blurRadius = 20.dp,
                                noiseFactor = -1f,
                            )
                            blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                        }
                    )
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column {
            SectionHeaderV2(title = "Activity")

            Column(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(top = Spacing.sm)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    for (w in 0 until weeksToShow) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (d in 0 until 7) {
                                val date = startDate.plusWeeks(w.toLong()).plusDays(d.toLong())
                                val epochDay = date.toEpochDay()
                                val count = transactionHeatmap[epochDay] ?: 0

                                val primary = MaterialTheme.colorScheme.primary
                                val color = when {
                                    date > today -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    count == 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    count == 1 -> primary.copy(alpha = 0.25f)
                                    count == 2 -> primary.copy(alpha = 0.5f)
                                    count in 3..4 -> primary.copy(alpha = 0.75f)
                                    else -> primary
                                }

                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }

                // Month labels
                Box(modifier = Modifier.fillMaxWidth()) {
                    monthLabels.forEach { (weekIndex, label) ->
                        val xOffset = (weekIndex * 18).dp
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier.offset(x = xOffset)
                        )
                    }
                }
            }
        }
    }
}
