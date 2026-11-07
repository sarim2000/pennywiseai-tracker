package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.ExpandableList
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Dimensions
import ir.ehsannarmani.compose_charts.models.Pie
import com.pennywiseai.tracker.ui.icons.categoryLabel

private val BreakdownStripHeight = 8.dp
private val BreakdownStripGap = 2.dp

private const val LEGEND_COLLAPSED_ROWS = 5

/** Rows shown before "View all": one row hidden behind a button would only cost a row. */
internal fun donutLegendVisibleRows(rows: Int): Int =
    if (rows <= LEGEND_COLLAPSED_ROWS + 1) rows else LEGEND_COLLAPSED_ROWS

@Composable
fun CategoryPieChart(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {}
) {
    if (categories.isEmpty()) return

    val slicesTotal = categories.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.amount }.toDouble()
    if (slicesTotal == 0.0) return

    // Slice scale is the tap-to-select pop (1 → selectedScale), not the data sweep.
    val motion = MaterialTheme.motionScheme
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val pieData = remember(categories, surface) {
        categories.map { category ->
            val sliceColor = CategoryMapping.visibleOn(
                CategoryMapping.colorFor(category.name, category.color),
                surface
            )
            Pie(
                label = category.name,
                data = category.amount.toDouble(),
                color = sliceColor,
                selectedColor = sliceColor.copy(alpha = 0.8f),
                selected = false,
                scaleAnimEnterSpec = motion.fastSpatialSpec(),
                colorAnimEnterSpec = motion.fastEffectsSpec()
            )
        }
    }

    var chartData by remember(pieData) { mutableStateOf(pieData) }
    fun select(sliceIndex: Int) {
        chartData = chartData.mapIndexed { index, pie -> pie.copy(selected = index == sliceIndex) }
        categories.getOrNull(sliceIndex)?.let(onCategoryClick)
    }

    BreakdownStrip(
        slices = chartData,
        onSliceClick = { select(chartData.indexOf(it)) },
        modifier = modifier
    ) {
        val rows = remember(categories) { categories.indices.sortedByDescending { categories[it].amount } }
        ExpandableList(
            items = rows,
            visibleItemCount = donutLegendVisibleRows(rows.size),
            modifier = Modifier.fillMaxWidth()
        ) { index ->
            val category = categories[index]
            val slice = chartData[index]
            DonutLegendRow(
                label = categoryLabel(category.name),
                amount = category.amount,
                percentage = (slice.data / slicesTotal * 100).toInt(),
                currency = currency,
                isSelected = slice.selected,
                onClick = { select(index) }
            ) {
                // The category's own icon rather than a bare swatch (#827), in a disc of
                // the slice colour so the row still maps to the chart. The disc carries
                // the colour rather than the glyph, because a custom emoji (#760) can't
                // be tinted and would otherwise leave the row with no slice marker at
                // all. Same treatment as the category list below the chart.
                Box(
                    modifier = Modifier
                        .size(Dimensions.Icon.avatar)
                        .clip(CircleShape)
                        .background(slice.color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryIcon(
                        category = category.name,
                        size = Dimensions.Icon.medium,
                        tint = slice.color
                    )
                }
            }
        }
    }
}

/**
 * Each slice as a length of one strip, with its legend under it. Lengths on one line compare
 * at a glance, where the arcs of a donut don't, and it spends a third of the height. The
 * total isn't repeated here: the summary above the chart states it.
 */
@Composable
internal fun BreakdownStrip(
    slices: List<Pie>,
    onSliceClick: (Pie) -> Unit,
    modifier: Modifier = Modifier,
    legend: @Composable () -> Unit
) {
    val sum = slices.sumOf { it.data }.takeIf { it > 0.0 } ?: 1.0
    val gap = BreakdownStripGap
    val anySelected = slices.any { it.selected }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Not while hidden: the lengths give the split away.
        if (!AmountPrivacy.hidden) Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(BreakdownStripHeight)
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        // The same geometry as the drawing below, or a tap lands one slice off.
                        val gapPx = gap.toPx()
                        val usable = size.width - gapPx * (slices.size - 1).coerceAtLeast(0)
                        val fromStart = if (rtl) size.width - offset.x else offset.x
                        var end = 0f
                        val hit = slices.firstOrNull { pie ->
                            end += (pie.data / sum).toFloat() * usable + gapPx
                            fromStart <= end
                        }
                        hit?.let(onSliceClick)
                    }
                }
        ) {
            val gapPx = gap.toPx()
            val usable = size.width - gapPx * (slices.size - 1).coerceAtLeast(0)
            var x = 0f
            // RTL: the largest share starts on the right, with the list's reading order.
            scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
                slices.forEach { pie ->
                    val width = (pie.data / sum).toFloat() * usable
                    drawRect(
                        color = if (anySelected && !pie.selected) pie.color.copy(alpha = 0.35f) else pie.color,
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height)
                    )
                    x += width + gapPx
                }
            }
        }
        legend()
    }
}

/** A legend row: the name wraps, the figure stays whole. */
@Composable
internal fun DonutLegendRow(
    label: String,
    amount: BigDecimal,
    percentage: Int,
    currency: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    marker: @Composable () -> Unit
) {
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isSelected) {
        MaterialTheme.colorScheme.inverseOnSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (isSelected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent)
            .clickable(onClick = onClick)
            .sizeIn(minHeight = Dimensions.Component.minTouchTarget)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        marker()
        Text(
            text = label,
            style = PennyWiseText.rowTitle,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = CurrencyFormatter.formatCurrency(amount, currency),
                style = PennyWiseText.amountRow,
                color = textColor,
                maxLines = 1,
                autoSize = PennyWiseText.fit(PennyWiseText.amountRow)
            )
            Text(
                text = if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.analytics_percent, percentage),
                style = PennyWiseText.amountSmall,
                color = secondaryColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
