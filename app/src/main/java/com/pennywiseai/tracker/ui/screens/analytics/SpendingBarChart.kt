package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.components.chartAxisLabels
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun SpendingBarChart(
    primaryCurrency: String,
    data: List<BalancePoint>,
    modifier: Modifier = Modifier,
    height: Int = 220
) {
    if (data.isEmpty()) return

    val sortedData = remember(data) { data.sortedBy { it.timestamp } }
    val themeColors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    val barColor = themeColors.primary
    val gridColor = themeColors.onSurface.copy(alpha = 0.1f)
    val labelColor = themeColors.onSurfaceVariant

    val chartLabel = PennyWiseText.chartLabel
    val labelStyle = chartLabel.copy(color = labelColor, textAlign = TextAlign.Center)
    val indicatorStyle = chartLabel.copy(color = labelColor, textAlign = TextAlign.End)

    val chartData = remember(sortedData) {
        val isYearly = sortedData.size > 1 && sortedData.all { it.timestamp.dayOfYear == 1 }
        val isMonthly = !isYearly && sortedData.all { it.timestamp.dayOfMonth == 1 }
        val spansMultipleYears = if (sortedData.isNotEmpty()) {
            sortedData.first().timestamp.year != sortedData.last().timestamp.year
        } else false

        sortedData.map { point ->
            val label = when {
                isYearly -> point.timestamp.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> point.timestamp.format(AppDates.pattern("MMM yy"))
                isMonthly -> point.timestamp.format(AppDates.pattern("MMM"))
                else -> point.timestamp.format(AppDates.pattern("dd MMM"))
            }
            BarData(label = label, value = point.balance.toDouble())
        }
    }

    val peakValue = remember(chartData) { chartData.maxOfOrNull { it.value } ?: 0.0 }
    val maxValue = niceAxisMax(peakValue)

    val animationProgress = remember { Animatable(0f) }
    // Bar heights are data: an effects spec settles without overshooting the value.
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(chartData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = motion.slowEffectsSpec())
    }
    val progress = animationProgress.value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
    ) {
        if (chartData.isEmpty() || maxValue <= 0.0) return@Canvas

        // The axis and label bands follow the text, so a large font can't clip or overlap them.
        val widestIndicator = textMeasurer.measure(
            CurrencyFormatter.formatAbbreviated(maxValue, primaryCurrency),
            indicatorStyle
        )
        val sampleLabelMeasured = textMeasurer.measure(chartData.first().label, labelStyle)
        val labelGap = 8.dp.toPx()
        val yAxisWidth = maxOf(48.dp.toPx(), widestIndicator.size.width + labelGap)
        val xLabelHeight = maxOf(40.dp.toPx(), sampleLabelMeasured.size.height + labelGap)
        val valueLabelReserve = maxOf(24.dp.toPx(), sampleLabelMeasured.size.height + 4.dp.toPx())
        val rightPadding = 24.dp.toPx()
        val chartLeft = yAxisWidth
        val chartRight = size.width - rightPadding
        val chartTop = valueLabelReserve
        val chartBottom = size.height - xLabelHeight
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        if (chartHeight <= 0f || chartWidth <= 0f) return@Canvas

        // RTL: mirror every x (axis on the right, time running right to left),
        // matching the line chart. [w] is the drawn item's width.
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun mx(x: Float, w: Float = 0f) = if (rtl) size.width - x - w else x

        // Draw horizontal grid lines and Y-axis indicators
        val gridLineCount = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
        for (i in 0..gridLineCount) {
            val fraction = i.toFloat() / gridLineCount
            val y = chartBottom - fraction * chartHeight
            drawLine(
                color = gridColor,
                start = Offset(mx(chartLeft), y),
                end = Offset(mx(chartRight), y),
                pathEffect = dashEffect,
                strokeWidth = 1f
            )
            val indicatorValue = maxValue * fraction
            val indicatorText = CurrencyFormatter.formatAbbreviated(abs(indicatorValue), primaryCurrency)
            val measured = textMeasurer.measure(indicatorText, indicatorStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = mx(yAxisWidth - measured.size.width - 4.dp.toPx(), measured.size.width.toFloat()),
                    y = y - measured.size.height / 2f
                )
            )
        }

        // Bar geometry: each bar is centred in an equal slot, so two points span the axis
        // like thirty do and their labels have room.
        val barCount = chartData.size
        val slotWidth = chartWidth / barCount
        val barThickness = (slotWidth * 0.8f).coerceAtMost(Dimensions.Component.chartBarMaxWidth.toPx())

        // Same rule as the line chart: evenly spaced labels, each at its own bar's position.
        val maxLabels = (chartWidth / (sampleLabelMeasured.size.width + 2.dp.toPx())).toInt()
            .coerceAtLeast(2)
        val labelledBars = chartAxisLabels(chartData.indices.map(Int::toString), maxLabels)
            .map(String::toInt)
            .toSet()

        // Dense bars have no room for a figure each: only the peak keeps one.
        val peakLabelWidth = textMeasurer.measure(CurrencyFormatter.formatAbbreviated(peakValue, primaryCurrency), labelStyle)
            .size.width
        // At a large font the labels collide with the axis text, and the axis already gives the scale.
        val showValueLabels = fontScale < LARGE_FONT_NO_BAR_LABELS
        val valueLabelsFit = showValueLabels && slotWidth >= peakLabelWidth + 4.dp.toPx()

        chartData.forEachIndexed { index, bar ->
            val barHeight = ((bar.value / maxValue) * chartHeight * progress).toFloat()
            val x = chartLeft + index * slotWidth + (slotWidth - barThickness) / 2f
            val barTop = chartBottom - barHeight

            // Draw bar
            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(mx(x, barThickness), barTop),
                    size = Size(barThickness, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Draw value label above bar (clamped to avoid Y-axis and right-edge overlap)
            if (
                (valueLabelsFit || (showValueLabels && bar.value == peakValue)) &&
                bar.value > 0 && barHeight > 4.dp.toPx() && progress > 0.5f
            ) {
                val valueText = CurrencyFormatter.formatAbbreviated(bar.value, primaryCurrency)
                val valueMeasured = textMeasurer.measure(valueText, labelStyle)
                val rawLabelX = x + barThickness / 2f - valueMeasured.size.width / 2f
                val minLabelX = yAxisWidth + 4.dp.toPx()
                val maxLabelX = size.width - 4.dp.toPx() - valueMeasured.size.width
                val labelX = rawLabelX.coerceIn(minLabelX, maxLabelX)
                val labelY = barTop - 4.dp.toPx() - valueMeasured.size.height
                if (labelY >= 0f) {
                    drawText(
                        textLayoutResult = valueMeasured,
                        topLeft = Offset(mx(labelX, valueMeasured.size.width.toFloat()), labelY)
                    )
                }
            }

            if (index in labelledBars) {
                val labelMeasured = textMeasurer.measure(bar.label, labelStyle)
                val labelX = (x + barThickness / 2f - labelMeasured.size.width / 2f)
                    .coerceIn(0f, (size.width - labelMeasured.size.width).coerceAtLeast(0f))
                val labelY = chartBottom + labelGap
                if (labelY + labelMeasured.size.height <= size.height) {
                    drawText(
                        textLayoutResult = labelMeasured,
                        topLeft = Offset(mx(labelX, labelMeasured.size.width.toFloat()), labelY)
                    )
                }
            }
        }
    }
}

private data class BarData(
    val label: String,
    val value: Double
)

/**
 * The top of a four-step axis, so ticks land on round numbers: 179 gives 0/50/100/150/200,
 * where 1.2x the peak gave 0/54/107/161/215.
 */
private const val LARGE_FONT_NO_BAR_LABELS = 1.3f

internal fun niceAxisMax(peak: Double): Double {
    if (peak <= 0.0) return 0.0
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(peak / 4)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it * 4 >= peak }
    return step * 4
}
