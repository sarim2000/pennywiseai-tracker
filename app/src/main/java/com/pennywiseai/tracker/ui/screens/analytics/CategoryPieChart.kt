package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.ExpandableList
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Dimensions
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.Pie
import com.pennywiseai.tracker.ui.icons.categoryLabel

private val DonutSize = 136.dp
private val DonutRingWidth = 12.dp

/** From this width the legend sits beside the donut rather than under it. */
private val DonutSideBySideMinWidth = 600.dp

private const val LEGEND_COLLAPSED_ROWS = 5

/** Rows shown before "View all": one row hidden behind a button would only cost a row. */
internal fun donutLegendVisibleRows(rows: Int): Int =
    if (rows <= LEGEND_COLLAPSED_ROWS + 1) rows else LEGEND_COLLAPSED_ROWS

@Composable
fun CategoryPieChart(
    categories: List<CategoryData>,
    total: BigDecimal,
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

    DonutBreakdown(
        slices = chartData,
        total = total,
        currency = currency,
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
 * The donut with the period total in its middle, and its legend: below on a phone, where
 * every row needs the full width for name and figure, and beside it on a wide screen.
 */
@Composable
internal fun DonutBreakdown(
    slices: List<Pie>,
    total: BigDecimal,
    currency: String,
    onSliceClick: (Pie) -> Unit,
    modifier: Modifier = Modifier,
    legend: @Composable () -> Unit
) {
    val motion = MaterialTheme.motionScheme
    // Grows with large fonts so the total keeps a readable size inside the ring.
    val donutSize = DonutSize * LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    val donut: @Composable () -> Unit = {
        Box(modifier = Modifier.size(donutSize), contentAlignment = Alignment.Center) {
            PieChart(
                modifier = Modifier.fillMaxSize(),
                data = slices,
                onPieClick = onSliceClick,
                selectedScale = 1.1f,
                scaleAnimEnterSpec = motion.fastSpatialSpec(),
                colorAnimEnterSpec = motion.fastEffectsSpec(),
                // The built-in label helper repeats the legend beside us, truncated to
                // "Groc…" / "Mobi…" — unreadable, and it squeezes the donut.
                labelHelperProperties = LabelHelperProperties(enabled = false),
                style = Pie.Style.Stroke(width = DonutRingWidth)
            )
            Column(
                modifier = Modifier.width(donutSize - (DonutRingWidth + Spacing.sm) * 2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.analytics_summary_total),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                // Shrinks to fit the ring: a figure is never cut.
                Text(
                    text = CurrencyFormatter.formatCurrency(total, currency),
                    style = PennyWiseText.amountLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    autoSize = PennyWiseText.fit(PennyWiseText.amountLarge)
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= DonutSideBySideMinWidth) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                donut()
                Box(modifier = Modifier.weight(1f)) { legend() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                donut()
                legend()
            }
        }
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
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
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
                text = stringResource(R.string.analytics_percent, percentage),
                style = PennyWiseText.amountSmall,
                color = secondaryColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
