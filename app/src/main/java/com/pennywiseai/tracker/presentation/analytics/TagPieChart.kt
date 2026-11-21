package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal

@Composable
fun TagPieChart(
    tags: List<TagData>,
    currency: String,
    modifier: Modifier = Modifier,
    onTagClick: (TagData) -> Unit = {}
) {
    if (tags.isEmpty()) return

    val total = tags.fold(BigDecimal.ZERO) { acc, tag -> acc + tag.amount }.toDouble()
    if (total == 0.0) return

    // Slices are data: every sweep, colour and select pop runs on effects specs,
    // so an arc never bounces past its share.
    val motion = MaterialTheme.motionScheme
    val pieData = remember(tags) {
        tags.map { tag ->
            Pie(
                label = tag.name,
                data = tag.amount.toDouble(),
                color = tagColor(tag.name),
                selectedColor = tagColor(tag.name).copy(alpha = 0.8f),
                selected = false,
                scaleAnimEnterSpec = motion.fastEffectsSpec(),
                colorAnimEnterSpec = motion.fastEffectsSpec(),
                spaceDegreeAnimEnterSpec = motion.fastEffectsSpec(),
                scaleAnimExitSpec = motion.fastEffectsSpec(),
                colorAnimExitSpec = motion.fastEffectsSpec(),
                spaceDegreeAnimExitSpec = motion.fastEffectsSpec()
            )
        }
    }

    var chartData by remember(pieData) { mutableStateOf(pieData) }
    // Slices line up with [tags] by position. Match a slice by identity, not label:
    // a user tag named like the "No tag" row shares its label (#422).
    fun indexOfPie(pie: Pie): Int =
        chartData.indexOfFirst { it === pie }.takeIf { it >= 0 } ?: chartData.indexOf(pie)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            PieChart(
                modifier = Modifier.size(Dimensions.Component.insightsChartHeight),
                data = chartData,
                onPieClick = { clickedPie ->
                    val pieIndex = indexOfPie(clickedPie)
                    chartData = chartData.mapIndexed { index, pie ->
                        pie.copy(selected = index == pieIndex)
                    }
                    tags.getOrNull(pieIndex)?.let(onTagClick)
                },
                selectedScale = 1.1f,
                scaleAnimEnterSpec = motion.fastEffectsSpec(),
                colorAnimEnterSpec = motion.fastEffectsSpec(),
                spaceDegreeAnimEnterSpec = motion.fastEffectsSpec(),
                scaleAnimExitSpec = motion.fastEffectsSpec(),
                colorAnimExitSpec = motion.fastEffectsSpec(),
                spaceDegreeAnimExitSpec = motion.fastEffectsSpec(),
                // The built-in label helper repeats the legend beside us, truncated to
                // "Groc…" / "Mobi…" — unreadable, and it squeezes the donut.
                labelHelperProperties = LabelHelperProperties(enabled = false),
                style = Pie.Style.Stroke(width = Spacing.smd)
            )
        }

        ExpandableGroupedList(items = chartData.sortedByDescending { it.data }, visibleCount = 5) { pie, position ->
            val tag = tags.getOrNull(indexOfPie(pie))
            TagLegendItem(
                position = position,
                label = pie.label ?: stringResource(R.string.analytics_pie_unknown),
                value = pie.data,
                color = pie.color,
                isSelected = pie.selected,
                currency = currency,
                totalAmount = total,
                onClick = if (tag == null || tag.isUntagged) null else ({
                    val pieIndex = indexOfPie(pie)
                    chartData = chartData.mapIndexed { index, p ->
                        p.copy(selected = index == pieIndex)
                    }
                    onTagClick(tag)
                })
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagLegendItem(
    position: ListItemPosition,
    label: String,
    value: Double,
    color: androidx.compose.ui.graphics.Color,
    currency: String,
    isSelected: Boolean,
    totalAmount: Double,
    onClick: (() -> Unit)?
) {
    val percentage = (value / totalAmount * 100).toInt()

    GroupedRow(
        position = position,
        onClick = onClick,
        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val ink = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        val subInk = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = Modifier
                .size(Dimensions.Component.legendDot)
                .clip(CircleShape)
                .background(color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = ink
            )
            // Each figure stays whole; the percentage moves to the next line when it
            // doesn't fit. In Arabic "(١٠٠%)" broke before its closing parenthesis (#1057).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = CurrencyFormatter.formatAbbreviated(value, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subInk,
                    softWrap = false
                )
                Text(
                    text = stringResource(R.string.analytics_pie_percent, percentage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subInk,
                    softWrap = false
                )
            }
        }
    }
}
