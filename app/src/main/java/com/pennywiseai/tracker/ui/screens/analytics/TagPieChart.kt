package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.pennywiseai.tracker.ui.components.ExpandableList
import com.pennywiseai.tracker.ui.components.tagColor
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.Dimensions
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal

@Composable
fun TagPieChart(
    tags: List<TagData>,
    total: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier,
    onTagClick: (TagData) -> Unit = {}
) {
    if (tags.isEmpty()) return

    val slicesTotal = tags.fold(BigDecimal.ZERO) { acc, tag -> acc + tag.amount }.toDouble()
    if (slicesTotal == 0.0) return

    // Slice scale is the tap-to-select pop (1 → selectedScale), not the data sweep.
    val motion = MaterialTheme.motionScheme
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val pieData = remember(tags, surface) {
        tags.map { tag ->
            val sliceColor = CategoryMapping.visibleOn(tagColor(tag.name), surface)
            Pie(
                label = tag.name,
                data = tag.amount.toDouble(),
                color = sliceColor,
                selectedColor = sliceColor.copy(alpha = 0.8f),
                selected = false,
                scaleAnimEnterSpec = motion.fastSpatialSpec(),
                colorAnimEnterSpec = motion.fastEffectsSpec()
            )
        }
    }

    var chartData by remember(pieData) { mutableStateOf(pieData) }
    // Slices line up with [tags] by position. Match a slice by identity, not label:
    // a user tag named like the "No tag" row shares its label (#422).
    fun indexOfPie(pie: Pie): Int =
        chartData.indexOfFirst { it === pie }.takeIf { it >= 0 } ?: chartData.indexOf(pie)

    fun select(sliceIndex: Int) {
        chartData = chartData.mapIndexed { index, pie -> pie.copy(selected = index == sliceIndex) }
        tags.getOrNull(sliceIndex)?.let(onTagClick)
    }

    DonutBreakdown(
        slices = chartData,
        total = total,
        currency = currency,
        onSliceClick = { select(indexOfPie(it)) },
        modifier = modifier
    ) {
        val rows = remember(tags) { tags.indices.sortedByDescending { tags[it].amount } }
        ExpandableList(
            items = rows,
            visibleItemCount = donutLegendVisibleRows(rows.size),
            modifier = Modifier.fillMaxWidth()
        ) { index ->
            val tag = tags[index]
            val slice = chartData[index]
            DonutLegendRow(
                label = tag.name,
                amount = tag.amount,
                percentage = (slice.data / slicesTotal * 100).toInt(),
                currency = currency,
                isSelected = slice.selected,
                onClick = { select(index) }
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.Component.legendDot)
                        .clip(CircleShape)
                        .background(slice.color)
                )
            }
        }
    }
}
