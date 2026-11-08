package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.utils.AppDates

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun SpendingBarChart(
    primaryCurrency: String,
    data: List<BalancePoint>,
    modifier: Modifier = Modifier,
    height: Int = 220
) {
    if (data.isEmpty()) return

    val frame = remember(data, primaryCurrency) { BarFrame.from(data, primaryCurrency) }
    val epochs = remember { BarEpochs() }
    val shown = remember(frame) { epochs.advance(frame) }

    // Same epoch = the bars retarget in place; a new epoch (other currency, bucket
    // size or a far-off scale) crossfades the whole chart instead of morphing
    // bars between figures that don't share an axis.
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = shown,
        modifier = modifier,
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith
                fadeOut(motion.defaultEffectsSpec()) using null
        },
        contentKey = { it.epoch },
        label = "bar_chart_scale"
    ) { BarChartCanvas(frame = it.frame, growIn = it.epoch == 0, height = height) }
}

@Composable
private fun BarChartCanvas(frame: BarFrame, growIn: Boolean, height: Int) {
    val primaryCurrency = frame.currency
    val chartData = frame.bars
    val maxValue = frame.maxValue
    val themeColors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    val barColor = themeColors.primary.copy(alpha = 0.8f)
    val gridColor = themeColors.onSurface.copy(alpha = 0.1f)
    val labelColor = themeColors.onSurface.copy(alpha = 0.7f)
    val valueLabelColor = themeColors.onSurface.copy(alpha = 0.7f)
    val indicatorColor = themeColors.onSurfaceVariant

    val chartLabel = PennyWiseText.chartLabel
    val labelStyle = chartLabel.copy(color = labelColor, textAlign = TextAlign.Center)
    val valueLabelStyle = chartLabel.copy(color = valueLabelColor, textAlign = TextAlign.Center)
    val indicatorStyle = chartLabel.copy(color = indicatorColor, textAlign = TextAlign.End)

    // One height per date bucket, so a data change retargets each bar from where
    // it stands. Heights are data: effects specs settle without overshooting.
    val motion = MaterialTheme.motionScheme
    val heights = remember { HashMap<LocalDateTime, Animatable<Float, AnimationVector1D>>() }
    val seeded = heights.isNotEmpty()
    val spec = if (seeded) motion.defaultEffectsSpec<Float>() else motion.slowEffectsSpec()
    chartData.forEach { bar ->
        val target = if (maxValue > 0.0) (bar.value / maxValue).toFloat() else 0f
        // First paint grows from zero once; a crossfaded-in chart starts settled.
        val anim = heights.getOrPut(bar.key) { Animatable(if (seeded || growIn) 0f else target) }
        key(bar.key) {
            LaunchedEffect(target) { anim.animateTo(target, spec) }
        }
    }
    SideEffect { heights.keys.retainAll(chartData.mapTo(HashSet()) { it.key }) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(vertical = Spacing.md)
    ) {
        if (chartData.isEmpty() || maxValue <= 0.0) return@Canvas

        val yAxisWidth = 48.dp.toPx()
        val xLabelHeight = 40.dp.toPx()
        val valueLabelReserve = 24.dp.toPx()
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

        // Bar geometry
        val barCount = chartData.size
        val totalSpacing = 8.dp.toPx() * (barCount - 1)
        val barThickness = ((chartWidth - totalSpacing) / barCount).coerceAtMost(12.dp.toPx())
        val actualTotalWidth = barThickness * barCount + totalSpacing
        val startOffset = chartLeft + (chartWidth - actualTotalWidth) / 2f

        // X-axis label thinning: measure a representative label to calculate step
        val sampleLabelMeasured = textMeasurer.measure(chartData.first().label, labelStyle)
        val typicalLabelWidth = sampleLabelMeasured.size.width.toFloat()
        val minGap = 8.dp.toPx()
        val maxLabels = (chartWidth / (typicalLabelWidth + minGap)).toInt().coerceAtLeast(2)
        val labelStep = if (barCount > maxLabels) ceil(barCount.toFloat() / maxLabels).toInt() else 1

        chartData.forEachIndexed { index, bar ->
            val target = (bar.value / maxValue).toFloat()
            val fraction = heights[bar.key]?.value ?: target
            val barHeight = fraction * chartHeight
            val x = startOffset + index * (barThickness + 8.dp.toPx())
            val barTop = chartBottom - barHeight

            // Draw bar
            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(mx(x, barThickness), barTop),
                    size = Size(barThickness, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Draw value label above bar (clamped to avoid Y-axis and right-edge overlap)
            if (bar.value > 0 && barHeight > 4.dp.toPx() && fraction >= target / 2f) {
                val valueText = formatCompactValue(bar.value, primaryCurrency)
                val valueMeasured = textMeasurer.measure(valueText, valueLabelStyle)
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

            // Draw x-axis label — only every Nth label to avoid overlap
            if (index % labelStep == 0 || index == barCount - 1) {
                val labelMeasured = textMeasurer.measure(bar.label, labelStyle)
                val labelX = x + barThickness / 2f - labelMeasured.size.width / 2f
                val labelY = chartBottom + 8.dp.toPx()
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

internal enum class BarGranularity { DAY, MONTH, YEAR }

internal data class Bar(val key: LocalDateTime, val label: String, val value: Double)

internal data class BarFrame(
    val currency: String,
    val granularity: BarGranularity,
    val bars: List<Bar>
) {
    val maxValue: Double get() = (bars.maxOfOrNull { it.value } ?: 0.0) * 1.2

    /**
     * Whether [next] can be shown by retargeting these bars: same currency and
     * bucket size, at least one bucket in common, and an axis within 2x. Anything
     * else crossfades — bars never morph between two currencies' figures.
     */
    fun morphsInto(next: BarFrame): Boolean {
        if (currency != next.currency || granularity != next.granularity) return false
        val keys = bars.mapTo(HashSet()) { it.key }
        if (next.bars.none { it.key in keys }) return false
        val (a, b) = maxValue to next.maxValue
        if (a <= 0.0 || b <= 0.0) return true
        return b / a in 0.5..2.0
    }

    companion object {
        fun from(data: List<BalancePoint>, currency: String): BarFrame {
            val sorted = data.sortedBy { it.timestamp }
            val isYearly = sorted.size > 1 && sorted.all { it.timestamp.dayOfYear == 1 }
            val isMonthly = !isYearly && sorted.all { it.timestamp.dayOfMonth == 1 }
            val spansMultipleYears = sorted.isNotEmpty() &&
                sorted.first().timestamp.year != sorted.last().timestamp.year
            val bars = sorted.map { point ->
                val label = when {
                    isYearly -> point.timestamp.format(DateTimeFormatter.ofPattern("yyyy"))
                    isMonthly && spansMultipleYears -> point.timestamp.format(AppDates.pattern("MMM yy"))
                    isMonthly -> point.timestamp.format(AppDates.pattern("MMM"))
                    else -> point.timestamp.format(AppDates.pattern("dd MMM"))
                }
                Bar(key = point.timestamp, label = label, value = point.balance.toDouble())
            }
            val granularity = when {
                isYearly -> BarGranularity.YEAR
                isMonthly -> BarGranularity.MONTH
                else -> BarGranularity.DAY
            }
            return BarFrame(currency, granularity, bars)
        }
    }
}

internal data class EpochFrame(val epoch: Int, val frame: BarFrame)

/** Bumps the epoch whenever a frame can't morph from the previous one. */
internal class BarEpochs {
    private var last: EpochFrame? = null

    fun advance(frame: BarFrame): EpochFrame {
        val prev = last
        val next = when {
            prev == null -> EpochFrame(0, frame)
            prev.frame.morphsInto(frame) -> EpochFrame(prev.epoch, frame)
            else -> EpochFrame(prev.epoch + 1, frame)
        }
        last = next
        return next
    }
}

private fun formatCompactValue(value: Double, currencyCode: String): String {
    if (com.pennywiseai.tracker.utils.AmountPrivacy.hidden) return com.pennywiseai.tracker.utils.AmountPrivacy.MASK
    val absValue = abs(value)
    val useIndianNotation = currencyCode in setOf("INR", "NPR", "PKR")
    return when {
        useIndianNotation && absValue >= 1_00_00_000 ->
            "${String.format(java.util.Locale.US, "%.1f", absValue / 1_00_00_000)}Cr"
        useIndianNotation && absValue >= 1_00_000 ->
            "${String.format(java.util.Locale.US, "%.1f", absValue / 1_00_000)}L"
        absValue >= 10_000_000 ->
            "${String.format(java.util.Locale.US, "%.1f", absValue / 1_000_000)}M"
        absValue >= 1_000 ->
            "${String.format(java.util.Locale.US, "%.1f", absValue / 1_000)}K"
        absValue > 0 -> absValue.toInt().toString()
        else -> "0"
    }
}
