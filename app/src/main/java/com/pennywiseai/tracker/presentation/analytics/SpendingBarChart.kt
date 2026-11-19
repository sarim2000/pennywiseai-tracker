package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
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

    // Tonal bars with the latest one solid, as in the Insights hero chart.
    val barColor = themeColors.primaryContainer
    val latestBarColor = themeColors.primary
    val gridColor = themeColors.onSurface.copy(alpha = 0.1f)
    val labelColor = themeColors.onSurface.copy(alpha = 0.7f)
    val valueLabelColor = themeColors.onSurface.copy(alpha = 0.7f)
    val indicatorColor = themeColors.onSurfaceVariant

    val chartLabel = PennyWiseText.chartLabel
    val labelStyle = chartLabel.copy(color = labelColor, textAlign = TextAlign.Center)
    val valueLabelStyle = chartLabel.copy(color = valueLabelColor, textAlign = TextAlign.Center)
    val indicatorStyle = chartLabel.copy(color = indicatorColor, textAlign = TextAlign.End)

    // One animated value per date bucket, so a data change retargets each bar from
    // where it stands, and one animated axis top they're all drawn against: bar
    // height = value / axis, so an unchanged bar never reads as another amount
    // while the scale moves. Effects specs: data settles without overshooting.
    val motion = MaterialTheme.motionScheme
    val heights = remember { HashMap<LocalDateTime, Animatable<Float, AnimationVector1D>>() }
    val seeded = heights.isNotEmpty()
    val spec = if (seeded) motion.defaultEffectsSpec<Float>() else motion.slowEffectsSpec()
    val axis = remember { Animatable(maxValue.toFloat()) }
    LaunchedEffect(maxValue) { axis.animateTo(maxValue.toFloat(), spec) }
    chartData.forEach { bar ->
        val target = bar.value.toFloat()
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
        val axisMax = axis.value.toDouble()
        if (chartData.isEmpty() || maxValue <= 0.0 || axisMax <= 0.0) return@Canvas

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
            val indicatorValue = axisMax * fraction
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
            val shownValue = heights[bar.key]?.value ?: bar.value.toFloat()
            val barHeight = (shownValue / axisMax).toFloat().coerceIn(0f, 1f) * chartHeight
            val x = startOffset + index * (barThickness + 8.dp.toPx())
            val barTop = chartBottom - barHeight

            // Draw bar: a pill, the latest bucket in the solid accent
            if (barHeight > 0f) {
                drawRoundRect(
                    color = if (index == chartData.lastIndex) latestBarColor else barColor,
                    topLeft = Offset(mx(x, barThickness), barTop),
                    size = Size(barThickness, barHeight),
                    cornerRadius = CornerRadius(barThickness / 2f, barThickness / 2f)
                )
            }

            // Draw value label above bar (clamped to avoid Y-axis and right-edge overlap)
            if (bar.value > 0 && barHeight > 4.dp.toPx() && shownValue >= bar.value / 2) {
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
            // The last date always shows; a stepped label too close to it gives way.
            val last = barCount - 1
            if (index == last || (index % labelStep == 0 && last - index >= labelStep)) {
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

/** One bar of [PeriodBarChart]: a whole period's total, already labelled. */
@Immutable
data class PeriodBar(
    val key: LocalDate,
    val label: String,
    val spokenLabel: String,
    val amount: BigDecimal,
    val hasData: Boolean
)

/**
 * The Insights hero chart: one rounded pill per period, tonal bars with the
 * selected (last) period solid, its compact amount above and its name below.
 * No axis: six bars read at a glance, and every bar carries its own figure.
 * A period with no data at all draws as a short empty stub.
 */
@Composable
fun PeriodBarChart(
    bars: List<PeriodBar>,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.amount.toDouble() }.takeIf { it > 0.0 } ?: 1.0
    // One chart per currency: another currency's amounts share no axis with these,
    // so a switch grows a fresh chart in instead of morphing the old bars.
    key(currency) {
        // Grow in once on first paint; later data changes retarget from where they stand.
        var grown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { grown = true }
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val scale = LocalDensity.current.fontScale
            val minBarWidth = Dimensions.Icon.avatar * scale
            val chartWidth = maxOf(maxWidth, minBarWidth * bars.size + Spacing.sm * bars.lastIndex)
            val scroll = rememberScrollState()
            LaunchedEffect(chartWidth) {
                if (chartWidth > maxWidth) scroll.scrollTo(scroll.maxValue)
            }
            Row(
                modifier = Modifier
                    .horizontalScroll(scroll)
                    .width(chartWidth)
                    .height(Dimensions.Component.insightsChartHeight),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEachIndexed { index, bar ->
                    key(bar.key) {
                        PeriodBarColumn(
                            bar = bar,
                            fraction = if (grown) (bar.amount.toDouble() / max).toFloat().coerceIn(0f, 1f) else 0f,
                            selected = index == bars.lastIndex,
                            currency = currency
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PeriodBarColumn(bar: PeriodBar, fraction: Float, selected: Boolean, currency: String) {
    val colors = MaterialTheme.colorScheme
    val shown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "period_bar"
    )
    val label = PennyWiseText.chartLabel
    val spokenValue = if (bar.hasData) CurrencyFormatter.formatCurrency(bar.amount, currency)
    else stringResource(R.string.insights_no_transactions)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clearAndSetSemantics { contentDescription = "${bar.spokenLabel}, $spokenValue" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // The selected period's value already appears as the hero total.
        // formatAbbreviated drops the sign; a negative period (net outflow) must still read negative.
        val value = if (bar.hasData && !selected) {
            val abbreviated = CurrencyFormatter.formatAbbreviated(bar.amount.toDouble(), currency)
            if (bar.amount.signum() < 0 && !com.pennywiseai.tracker.utils.AmountPrivacy.hidden) "-$abbreviated" else abbreviated
        } else ""
        Text(
            text = value,
            style = label,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = label.fontSize
            )
        )
        Spacer(Modifier.height(Spacing.xs))
        // The bar fills a fraction of what's left after the labels; a fraction of the whole
        // column would push the month label out on the tallest bars.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Box(
                modifier = Modifier
                    // widthIn before fillMaxWidth: the other way round the column's
                    // fixed width would win over the cap.
                    .widthIn(max = Dimensions.Component.insightsBarMaxWidth)
                    .fillMaxWidth()
                    .then(
                        if (bar.hasData && bar.amount.signum() > 0) {
                            Modifier
                                .heightIn(min = Dimensions.Component.insightsBarMinHeight)
                                .fillMaxHeight(shown)
                        } else {
                            Modifier.height(Dimensions.Component.progressBarHeight)
                        }
                    )
                    .clip(CircleShape)
                    .background(
                        when {
                            !bar.hasData || bar.amount.signum() <= 0 -> colors.surfaceContainerHighest
                            selected -> colors.primary
                            else -> colors.primaryContainer
                        }
                    )
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = bar.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        // Bars draw as value / axis: to or from an all-zero frame both run to 0
        // together and the bars would read full height, so crossfade instead.
        if (a <= 0.0 || b <= 0.0) return false
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
