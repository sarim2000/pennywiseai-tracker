package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.inkOnWash
import com.pennywiseai.tracker.ui.theme.readableOn
import com.pennywiseai.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal

@Composable
fun CategoryPieChart(
    categories: List<CategoryData>,
    currency: String,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategoryData) -> Unit = {}
) {
    if (categories.isEmpty()) return

    val total = categories.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.amount }.toDouble()
    if (total == 0.0) return

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

    var chartData by remember(pieData) { mutableStateOf(pieData) }

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
                    val pieIndex = chartData.indexOf(clickedPie)
                    chartData = chartData.mapIndexed { index, pie ->
                        pie.copy(selected = index == pieIndex)
                    }
                    val categoryName = clickedPie.label ?: return@PieChart
                    categories.find { it.name == categoryName }?.let(onCategoryClick)
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

            val selectedPie = chartData.find { it.selected }
            BlurredAnimatedVisibility(
                visible = selectedPie != null,
                enter = fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.Center)
            ) {
                // Moving the selection to another slice crossfades the centre icon.
                Crossfade(
                    targetState = selectedPie,
                    animationSpec = motion.defaultEffectsSpec(),
                    label = "pie_centre"
                ) { pie ->
                    val label = pie?.label ?: return@Crossfade
                    CategoryIcon(
                        category = label,
                        size = Dimensions.Icon.large,
                        tint = MaterialTheme.colorScheme.inkOnWash(pie.color, 0.2f, GRAPHIC_CONTRAST),
                        modifier = Modifier
                            .size(Dimensions.Icon.hero)
                            .clip(CircleShape)
                            .background(pie.color.copy(alpha = 0.2f))
                            .padding(Spacing.smd)
                    )
                }
            }
        }

        ExpandableGroupedList(items = chartData.sortedByDescending { it.data }, visibleCount = 5) { pie, position ->
            PieLegendItem(
                position = position,
                label = pie.label ?: stringResource(R.string.analytics_pie_unknown),
                value = pie.data,
                color = pie.color,
                isSelected = pie.selected,
                currency = currency,
                totalAmount = total,
                onClick = {
                    val pieIndex = chartData.indexOf(pie)
                    chartData = chartData.mapIndexed { index, p ->
                        p.copy(selected = index == pieIndex)
                    }
                    val categoryName = pie.label ?: return@PieLegendItem
                    categories.find { it.name == categoryName }?.let(onCategoryClick)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PieLegendItem(
    position: ListItemPosition,
    label: String,
    value: Double,
    color: Color,
    currency: String,
    isSelected: Boolean,
    totalAmount: Double,
    onClick: () -> Unit
) {
    val percentage = (value / totalAmount * 100).toInt()

    val rowFill = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    GroupedRow(
        position = position,
        onClick = onClick,
        containerColor = rowFill,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val ink = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
        val subInk = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        // The category's own icon rather than a bare swatch (#827), in a disc of
        // the slice colour so the row still maps to the chart. The disc carries
        // the colour rather than the glyph, because a custom emoji (#760) can't
        // be tinted and would otherwise leave the row with no slice marker at
        // all. Same treatment as the category list below the chart.
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.medium)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            CategoryIcon(
                category = label,
                size = Dimensions.Icon.small,
                // Checked on the row it sits on: a selected row's secondaryContainer isn't neutral.
                tint = color.readableOn(listOf(color.copy(alpha = 0.18f).compositeOver(rowFill)), GRAPHIC_CONTRAST),
                modifier = Modifier.clearAndSetSemantics {}
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryLabel(label),
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
