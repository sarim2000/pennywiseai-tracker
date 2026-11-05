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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.buildHeatmapMonthLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun HeatmapWidget(
    transactionHeatmap: Map<Long, Int>,
    modifier: Modifier = Modifier,
) {
    val weeksToShow = 26
    val today = LocalDate.now()
    val startDate = today.minusWeeks((weeksToShow - 1).toLong()).with(DayOfWeek.MONDAY)

    val monthLabels = remember(startDate, today) {
        buildHeatmapMonthLabels(startDate, today)
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    PennyWiseCardV2(modifier = modifier.fillMaxWidth()) {
        // No section header here: every caller places this card under an
        // "Activity" SectionHeaderV2, so a second one inside the card rendered
        // the word twice, one above the other.
        Column {
            val cellSize = HEATMAP_CELL_SIZE
            val gapSize = HEATMAP_CELL_GAP
            val dayLabelWidth = HEATMAP_DAY_LABEL_WIDTH

            Column {
                Row {
                    // Day-of-week labels (M, W, F)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(gapSize),
                        modifier = Modifier.width(dayLabelWidth)
                    ) {
                        // Rows: 0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri, 5=Sat, 6=Sun
                        for (d in 0 until 7) {
                            // Narrow weekday initials (M / W / F in English), locale-aware.
                            val label = if (d % 2 == 0 && d <= 4) {
                                java.time.DayOfWeek.of(d + 1).getDisplayName(
                                    java.time.format.TextStyle.NARROW,
                                    java.util.Locale.getDefault()
                                )
                            } else null
                            Box(
                                modifier = Modifier.size(cellSize),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (label != null) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Grid and month labels scroll together: labels outside the scroller
                    // stayed on the oldest months while the grid sat on the newest.
                    Column(modifier = Modifier.horizontalScroll(scrollState)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gapSize),
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        ) {
                            for (w in 0 until weeksToShow) {
                                Column(verticalArrangement = Arrangement.spacedBy(gapSize)) {
                                    for (d in 0 until 7) {
                                        val date = startDate.plusWeeks(w.toLong()).plusDays(d.toLong())
                                        val epochDay = date.toEpochDay()
                                        val count = transactionHeatmap[epochDay] ?: 0

                                        Box(
                                            modifier = Modifier
                                                .size(cellSize)
                                                .clip(MaterialTheme.shapes.extraSmall)
                                                .background(heatmapColor(if (date > today) 0 else count))
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.width((cellSize + gapSize) * weeksToShow)) {
                            monthLabels.forEach { (weekIndex, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(x = (cellSize + gapSize) * weekIndex)
                                )
                            }
                        }
                    }
                }

                // What the shading means: fewer to more transactions a day.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.heatmap_legend_less),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(0, 1, 2, 3, 5).forEach { count ->
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(heatmapColor(count))
                        )
                    }
                    Text(
                        text = stringResource(R.string.heatmap_legend_more),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun heatmapColor(count: Int): Color {
    val primary = MaterialTheme.colorScheme.primary
    return when {
        count == 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
        count == 1 -> primary.copy(alpha = 0.25f)
        count == 2 -> primary.copy(alpha = 0.5f)
        count in 3..4 -> primary.copy(alpha = 0.75f)
        else -> primary
    }
}

// Heatmap cell geometry. The grid is 7 rows tall and sized to fit a year of
// weeks across a phone, so these are laid out by hand rather than by a token —
// but they're named so the three of them stay related.
private val HEATMAP_CELL_SIZE = 14.dp
private val HEATMAP_CELL_GAP = Spacing.xs
private val HEATMAP_DAY_LABEL_WIDTH = Dimensions.Icon.inline
