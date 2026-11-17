package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.AnimationMode
import ir.ehsannarmani.compose_charts.models.DividerProperties
import ir.ehsannarmani.compose_charts.models.DotProperties
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.LineProperties
import ir.ehsannarmani.compose_charts.models.StrokeStyle
import ir.ehsannarmani.compose_charts.models.ZeroLineProperties
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class BalancePoint(
    val timestamp: LocalDateTime,
    val balance: BigDecimal,
    val currency: String = "INR"
)

@Composable
fun BalanceChart(
    primaryCurrency: String,
    balanceHistory: List<BalancePoint>,
    modifier: Modifier = Modifier,
    height: Int = 200,
    /** The legend's name for the line. */
    seriesLabel: String = stringResource(R.string.balance_chart_label),
    /** False joins the points with straight lines, so the line claims no values between them. */
    curved: Boolean = true,
) {
    if (balanceHistory.isEmpty()) return

    val sortedHistory = remember(balanceHistory) {
        balanceHistory.sortedBy { it.timestamp }
    }

    val smoothedHistory = remember(sortedHistory) {
        smoothBalanceData(sortedHistory)
    }

    val themeColors = MaterialTheme.colorScheme
    // One style for every label this chart draws — axis ticks, the value
    // indicator and the legend — so they can't drift apart.
    val chartLabel = PennyWiseText.chartLabel

    val chartValues = remember(smoothedHistory) {
        smoothedHistory.map { it.balance.toDouble() }
    }

    // Every point's date; the axis names as many as fit, spread from the first to the last.
    val dates = remember(smoothedHistory) {
        val isYearly = smoothedHistory.size > 1 && smoothedHistory.all { it.timestamp.dayOfYear == 1 }
        val isMonthly = !isYearly && smoothedHistory.all { it.timestamp.dayOfMonth == 1 }
        val spansMultipleYears = if (smoothedHistory.isNotEmpty()) {
            smoothedHistory.first().timestamp.year != smoothedHistory.last().timestamp.year
        } else false

        smoothedHistory.map {
            val date = it.timestamp
            when {
                isYearly -> date.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> date.format(AppDates.pattern("MMM yy"))
                isMonthly -> date.format(AppDates.pattern("MMM"))
                else -> date.format(AppDates.pattern("dd MMM"))
            }
        }
    }

    val indicatorText: (Double) -> String = { value ->
        // Keep the sign: a net worth or an overdraft can be below zero.
        val figure = CurrencyFormatter.formatAbbreviated(abs(value), primaryCurrency)
        if (value < 0 && figure != AmountPrivacy.MASK) "-$figure" else figure
    }
    val (minValue, maxValue) = valueRange(chartValues)

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val animationsEnabled = rememberAnimationsEnabled()
    // The line is drawn left to right in every locale; mirrored labels would sit under the wrong points.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            // Fit the labels to the font size: at 200% five value ticks and five dates
            // overran the chart and were cut off.
            val lineHeight = textMeasurer.measure("0", chartLabel).size.height.toFloat()
            // Text grows with the font size and the chart's frame doesn't: grow the chart by
            // what its two rows of text (legend, dates) gained, so the plot keeps its height.
            val chartHeight = height.dp +
                with(density) { (2 * lineHeight * (1f - 1f / density.fontScale).coerceAtLeast(0f)).toDp() }
            val plotHeight = with(density) {
                chartHeight.toPx() - (Spacing.md * 2 + LEGEND_GAP + DATE_GAP).toPx() - 2 * lineHeight
            }
            val tickCount = yTicksThatFit(plotHeight, lineHeight)
            val ticks = (0 until tickCount).map { maxValue - it * (maxValue - minValue) / (tickCount - 1) }
            // The chart starts its dates where its value column ends, which it measures
            // in the default text style plus the indicator padding.
            val valueColumn = ticks.maxOf { textMeasurer.measure(indicatorText(it)).size.width } +
                with(density) { INDICATOR_PADDING.toPx() }
            val dateRow = constraints.maxWidth - with(density) { (Spacing.sm * 2).toPx() } - valueColumn
            val dateCount = xLabelsThatFit(
                labelWidth = dates.maxOf { textMeasurer.measure(it, chartLabel).size.width }.toFloat(),
                available = dateRow,
                gap = with(density) { Spacing.sm.toPx() },
            )
            val labels = xLabelIndices(dates.size, dateCount).map { dates[it] }

            LineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.md),
                textMeasurer = textMeasurer,
                labelHelperPadding = LEGEND_GAP,
                data = listOf(
                    Line(
                        label = seriesLabel,
                        values = chartValues,
                        color = SolidColor(themeColors.primary),
                        firstGradientFillColor = themeColors.primary.copy(alpha = 0.3f),
                        secondGradientFillColor = Color.Transparent,
                        strokeAnimationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                        gradientAnimationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                        gradientAnimationDelay = 750,
                        drawStyle = DrawStyle.Stroke(width = 2.dp),
                        curvedEdges = curved,
                        dotProperties = DotProperties(
                            enabled = true,
                            color = SolidColor(themeColors.primary),
                            strokeWidth = 3.dp,
                            radius = 4.dp,
                            strokeColor = SolidColor(themeColors.surface)
                        )
                    )
                ),
                dividerProperties = DividerProperties(
                    enabled = true,
                    xAxisProperties = LineProperties(
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                        thickness = 0.dp
                    ),
                    yAxisProperties = LineProperties(
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0f)),
                        thickness = 0.dp
                    )
                ),
                minValue = minValue,
                maxValue = maxValue,
                indicatorProperties = HorizontalIndicatorProperties(
                    enabled = true,
                    textStyle = chartLabel.copy(
                        color = themeColors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    ),
                    padding = INDICATOR_PADDING,
                    indicators = ticks,
                    contentBuilder = indicatorText
                ),
                labelHelperProperties = LabelHelperProperties(
                    enabled = true,
                    textStyle = chartLabel.copy(
                        color = themeColors.onSurface,
                        textAlign = TextAlign.End
                    ),
                ),
                labelProperties = LabelProperties(
                    enabled = true,
                    textStyle = chartLabel.copy(
                        color = themeColors.onSurfaceVariant,
                        textAlign = TextAlign.End
                    ),
                    labels = labels,
                    padding = DATE_GAP,
                    // Level, and only as many as fit: a tilted date hangs below the chart and is cut off.
                    rotation = LabelProperties.Rotation(
                        mode = LabelProperties.Rotation.Mode.IfNecessary,
                        degree = 0f
                    ),
                    builder = { labelModifier, label, _, _ ->
                        BasicText(
                            text = label,
                            modifier = labelModifier,
                            style = chartLabel.copy(color = themeColors.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                ),
                zeroLineProperties = ZeroLineProperties(
                    enabled = true,
                    style = StrokeStyle.Dashed(),
                    color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f)),
                ),
                gridProperties = GridProperties(
                    enabled = true,
                    xAxisProperties = GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
                    ),
                    yAxisProperties = GridProperties.AxisProperties(
                        enabled = true,
                        style = StrokeStyle.Dashed(),
                        color = SolidColor(themeColors.onSurface.copy(alpha = 0.1f))
                    )
                ),
                // The library's coroutine delays ignore Remove animations; None draws it at once.
                animationMode = if (animationsEnabled) AnimationMode.Together(delayBuilder = { it * 200L }) else AnimationMode.None,
            )
        }
    }
}

/** Space between the legend and the plot. */
private val LEGEND_GAP: Dp = 26.dp

/** Space between the plot and its dates. */
private val DATE_GAP: Dp = 16.dp

/** Space between the value column and the plot. */
private val INDICATOR_PADDING: Dp = 12.dp

/**
 * The value axis for [values]: from zero, or from the lowest point when one is below
 * it, up to the highest. The chart divides by the range, so a flat line that has none
 * (all zero, or one steady debt) is given one: up to zero below it, else up to one.
 */
internal fun valueRange(values: List<Double>): Pair<Double, Double> {
    val min = values.min().coerceAtMost(0.0)
    val max = values.max()
    return when {
        max > min -> min to max
        min < 0.0 -> min to 0.0
        else -> min to min + 1.0
    }
}

/** Most values the Y axis names. */
private const val MAX_Y_TICKS = 5

/**
 * How many value ticks, each a [lineHeight] line, fit a plot [plotHeight] tall:
 * [MAX_Y_TICKS] at normal sizes, fewer at a large font, never under the top and bottom.
 */
internal fun yTicksThatFit(plotHeight: Float, lineHeight: Float): Int =
    (plotHeight / lineHeight).toInt().coerceIn(2, MAX_Y_TICKS)

/**
 * How many dates [labelWidth] wide fit side by side in [available] with [gap] between
 * them: up to [MAX_X_LABELS], and at least the first and the last.
 */
internal fun xLabelsThatFit(labelWidth: Float, available: Float, gap: Float): Int =
    (MAX_X_LABELS downTo 2).firstOrNull { it * labelWidth + (it - 1) * gap <= available } ?: 2

/** Most dates the X axis names; more crowd past the chart's width. */
private const val MAX_X_LABELS = 5

/**
 * Which of [count] evenly spaced points get a date: all of a short series, else
 * [max] spread from the first point to the last, so each label sits under its point.
 */
internal fun xLabelIndices(count: Int, max: Int = MAX_X_LABELS): List<Int> =
    if (count <= max) {
        (0 until count).toList()
    } else {
        (0 until max).map { j -> ((j.toLong() * (count - 1) + (max - 1) / 2) / (max - 1)).toInt() }.distinct()
    }

/**
 * Smooth balance data to reduce noise in the chart
 * Applies time-based aggregation and moving average smoothing
 */
private fun smoothBalanceData(
    balanceHistory: List<BalancePoint>,
    maxPoints: Int = 50
): List<BalancePoint> {
    if (balanceHistory.size <= maxPoints) {
        return balanceHistory
    }

    val timeSpan = balanceHistory.last().timestamp.toLocalDate()
        .toEpochDay() - balanceHistory.first().timestamp.toLocalDate().toEpochDay()

    val intervalDays = maxOf(1, (timeSpan / maxPoints).toInt())

    val groupedData = balanceHistory.groupBy { point ->
        val dayIndex = (point.timestamp.toLocalDate().toEpochDay() -
            balanceHistory.first().timestamp.toLocalDate().toEpochDay()) / intervalDays
        dayIndex
    }

    return groupedData.map { (_, group) ->
        val avgBalance = group.map { it.balance }.reduce { acc, balance -> acc + balance } /
            BigDecimal(group.size.toLong())
        val middleIndex = group.size / 2
        val representativePoint = group[middleIndex]

        BalancePoint(
            timestamp = representativePoint.timestamp,
            balance = avgBalance,
            currency = representativePoint.currency
        )
    }.sortedBy { it.timestamp }
}
