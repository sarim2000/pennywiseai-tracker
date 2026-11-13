package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal

/**
 * Top-categories donut.
 *
 * A long category list used to sit in its own vertical list beside the ring, so
 * the card scrolled inside the analytics list. The ring stays put, the focused
 * category is one full-width row, and the rest wrap as chips in the page's scroll.
 */
@Composable
fun CategoryPieChart(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {}
) {
    if (categories.isEmpty()) return
    val total = categories.fold(BigDecimal.ZERO) { acc, category -> acc + category.amount }.toDouble()
    if (total == 0.0) return

    CategoryPieChartCard(
        categories = categories,
        currency = currency,
        modifier = modifier,
        onCategoryClick = onCategoryClick
    )
}

@Composable
private fun CategoryPieChartCard(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier,
    onCategoryClick: (CategoryData) -> Unit
) {
    // Slices are data: every sweep, colour and select pop runs on effects specs,
    // so an arc never bounces past its share.
    val motion = MaterialTheme.motionScheme
    val pieData = remember(categories) {
        categories.map { category ->
            val sliceColor = CategoryMapping.colorFor(category.name, category.color)
            Pie(
                label = category.name,
                data = category.amount.toDouble(),
                color = sliceColor,
                selectedColor = sliceColor.copy(alpha = 0.8f),
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
    val categoryKey = remember(categories) { categories.joinToString("\u0000") { it.name } }
    var selectedName by rememberSaveable(categoryKey) { mutableStateOf(categories.first().name) }
    val selectedIndex = categories.indexOfFirst { it.name == selectedName }.coerceAtLeast(0)
    val selected = categories[selectedIndex]
    val chartData = remember(pieData, selectedIndex) {
        pieData.mapIndexed { index, pie -> pie.copy(selected = index == selectedIndex) }
    }

    PennyWiseCardV2(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CategoryDonut(
                chartData = chartData,
                selected = selected,
                onSliceClick = { pie ->
                    val index = chartData.indexOfFirst { it === pie }.takeIf { it >= 0 }
                        ?: chartData.indexOf(pie)
                    val category = categories.getOrNull(index) ?: return@CategoryDonut
                    // The first tap focuses a slice. Tapping the focused slice opens it,
                    // so the largest category (focused on arrival) still opens in one tap.
                    if (category.name == selected.name) onCategoryClick(category)
                    else selectedName = category.name
                },
                onOpenSelected = { onCategoryClick(selected) }
            )
            CategoryFocusRow(
                category = selected,
                currency = currency,
                onClick = { onCategoryClick(selected) }
            )
            if (categories.size > 1) {
                CategoryChipRow(
                    categories = categories,
                    selectedName = selected.name,
                    onSelect = { selectedName = it.name }
                )
            }
        }
    }
}

@Composable
private fun CategoryDonut(
    chartData: List<Pie>,
    selected: CategoryData,
    onSliceClick: (Pie) -> Unit,
    onOpenSelected: () -> Unit
) {
    val openLabel = categoryLabel(selected.name)
    val motion = MaterialTheme.motionScheme
    // Room for the selected slice to pop without clipping, same diameter and
    // stroke as the large goal ring so the two rings in the app match.
    val donut = Dimensions.Component.goalRingLarge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(donut + Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        PieChart(
            modifier = Modifier.size(donut),
            data = chartData,
            onPieClick = onSliceClick,
            selectedScale = 1.1f,
            scaleAnimEnterSpec = motion.fastEffectsSpec(),
            colorAnimEnterSpec = motion.fastEffectsSpec(),
            spaceDegreeAnimEnterSpec = motion.fastEffectsSpec(),
            scaleAnimExitSpec = motion.fastEffectsSpec(),
            colorAnimExitSpec = motion.fastEffectsSpec(),
            spaceDegreeAnimExitSpec = motion.fastEffectsSpec(),
            // The built-in label helper repeats the chips, truncated — unreadable,
            // and it squeezes the donut.
            labelHelperProperties = LabelHelperProperties(enabled = false),
            style = Pie.Style.Stroke(width = Dimensions.Component.goalRingLargeStroke)
        )
        Box(
            modifier = Modifier
                .size(donut - Dimensions.Component.goalRingLargeStroke * 2 - Spacing.sm)
                .clip(CircleShape)
                .clickable(onClickLabel = openLabel, onClick = onOpenSelected),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = motion.defaultEffectsSpec(),
                label = "donut_percent"
            ) { category ->
                Text(
                    text = stringResource(R.string.analytics_percent, category.percentage.toInt()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CategoryFocusRow(
    category: CategoryData,
    currency: String,
    onClick: () -> Unit
) {
    Crossfade(
        targetState = category,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "category_focus"
    ) { focused ->
        val color = CategoryMapping.colorFor(focused.name, focused.color)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.Component.minTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatar)
                    .clip(CircleShape)
                    .background(color.copy(alpha = Dimensions.Alpha.selectedIndicator)),
                contentAlignment = Alignment.Center
            ) {
                CategoryIcon(
                    category = focused.name,
                    size = Dimensions.Icon.medium,
                    tint = color
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryLabel(focused.name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(focused.amount, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.analytics_transaction_count,
                            focused.transactionCount,
                            focused.transactionCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChipRow(
    categories: List<CategoryData>,
    selectedName: String,
    onSelect: (CategoryData) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        categories.forEach { category ->
            val selected = category.name == selectedName
            val sliceColor = CategoryMapping.colorFor(category.name, category.color)
            FilterChip(
                selected = selected,
                onClick = { onSelect(category) },
                shapes = FilterChipDefaults.shapes(),
                label = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categoryLabel(category.name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = Dimensions.Component.goalRingLarge)
                        )
                        Text(
                            text = stringResource(R.string.analytics_percent, category.percentage.toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(Dimensions.Component.legendDot)
                            .clip(CircleShape)
                            .background(sliceColor)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderWidth = 0.dp,
                    selected = selected,
                    enabled = true
                )
            )
        }
    }
}
