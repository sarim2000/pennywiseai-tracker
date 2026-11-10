package com.pennywiseai.tracker.presentation.analytics

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Dimensions
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            PieChart(
                modifier = Modifier.size(160.dp),
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
                style = Pie.Style.Stroke(width = 12.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = Spacing.md)
        ) {
            items(chartData.sortedByDescending { it.data }) { pie ->
                TagLegendItem(
                    label = pie.label ?: stringResource(R.string.analytics_pie_unknown),
                    value = pie.data,
                    color = pie.color,
                    isSelected = pie.selected,
                    currency = currency,
                    totalAmount = total,
                    onClick = {
                        val pieIndex = indexOfPie(pie)
                        chartData = chartData.mapIndexed { index, p ->
                            p.copy(selected = index == pieIndex)
                        }
                        tags.getOrNull(pieIndex)?.let(onTagClick)
                    }
                )
            }
        }
    }
}

@Composable
private fun TagLegendItem(
    label: String,
    value: Double,
    color: androidx.compose.ui.graphics.Color,
    currency: String,
    isSelected: Boolean,
    totalAmount: Double,
    onClick: () -> Unit
) {
    val percentage = (value / totalAmount * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(Spacing.xs)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.Component.legendDot)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = CurrencyFormatter.formatAbbreviated(value, currency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.analytics_pie_percent, percentage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
