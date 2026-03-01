package com.pennywiseai.tracker.ui.screens.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.ui.components.BalancePoint
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
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

    val barColor = themeColors.primary.copy(alpha = 0.8f)
    val gridColor = themeColors.onSurface.copy(alpha = 0.1f)
    val labelColor = themeColors.onSurface.copy(alpha = 0.6f)
    val valueLabelColor = themeColors.onSurface.copy(alpha = 0.7f)
    val indicatorColor = themeColors.onSurfaceVariant.copy(alpha = 0.6f)

    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor, textAlign = TextAlign.Center)
    val valueLabelStyle = TextStyle(fontSize = 10.sp, color = valueLabelColor, textAlign = TextAlign.Center)
    val indicatorStyle = TextStyle(fontSize = 10.sp, color = indicatorColor, textAlign = TextAlign.End)

    val chartData = remember(sortedData) {
        val isYearly = sortedData.size > 1 && sortedData.all { it.timestamp.dayOfYear == 1 }
        val isMonthly = !isYearly && sortedData.all { it.timestamp.dayOfMonth == 1 }
        val spansMultipleYears = if (sortedData.isNotEmpty()) {
            sortedData.first().timestamp.year != sortedData.last().timestamp.year
        } else false

        sortedData.map { point ->
            val label = when {
                isYearly -> point.timestamp.format(DateTimeFormatter.ofPattern("yyyy"))
                isMonthly && spansMultipleYears -> point.timestamp.format(DateTimeFormatter.ofPattern("MMM yy"))
                isMonthly -> point.timestamp.format(DateTimeFormatter.ofPattern("MMM"))
                else -> point.timestamp.format(DateTimeFormatter.ofPattern("dd MMM"))
            }
            BarData(label = label, value = point.balance.toDouble())
        }
    }

    val maxValue = remember(chartData) {
        (chartData.maxOfOrNull { it.value } ?: 0.0) * 1.2
    }

    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(chartData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(600))
    }
    val progress = animationProgress.value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(vertical = Spacing.md)
    ) {
        if (chartData.isEmpty() || maxValue <= 0.0) return@Canvas

        val yAxisWidth = 48.dp.toPx()
        val xLabelHeight = 40.dp.toPx()
        val valueLabelReserve = 16.dp.toPx()
        val chartLeft = yAxisWidth
        val chartRight = size.width
        val chartTop = valueLabelReserve
        val chartBottom = size.height - xLabelHeight
        val chartHeight = chartBottom - chartTop
        val chartWidth = chartRight - chartLeft

        if (chartHeight <= 0f || chartWidth <= 0f) return@Canvas

        // Draw horizontal grid lines and Y-axis indicators
        val gridLineCount = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
        for (i in 0..gridLineCount) {
            val fraction = i.toFloat() / gridLineCount
            val y = chartBottom - fraction * chartHeight
            drawLine(
                color = gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                pathEffect = dashEffect,
                strokeWidth = 1f
            )
            val indicatorValue = maxValue * fraction
            val indicatorText = CurrencyFormatter.formatAbbreviated(abs(indicatorValue), primaryCurrency)
            val measured = textMeasurer.measure(indicatorText, indicatorStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = yAxisWidth - measured.size.width - 4.dp.toPx(),
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

        chartData.forEachIndexed { index, bar ->
            val barHeight = ((bar.value / maxValue) * chartHeight * progress).toFloat()
            val x = startOffset + index * (barThickness + 8.dp.toPx())
            val barTop = chartBottom - barHeight

            // Draw bar
            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, barTop),
                    size = Size(barThickness, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Draw value label above bar
            if (bar.value > 0 && barHeight > 4.dp.toPx() && progress > 0.5f) {
                val valueText = formatCompactValue(bar.value, primaryCurrency)
                val valueMeasured = textMeasurer.measure(valueText, valueLabelStyle)
                val labelX = x + barThickness / 2f - valueMeasured.size.width / 2f
                val labelY = barTop - 4.dp.toPx() - valueMeasured.size.height
                if (labelY >= 0f) {
                    drawText(
                        textLayoutResult = valueMeasured,
                        topLeft = Offset(labelX, labelY)
                    )
                }
            }

            // Draw x-axis label (rotated text approximated with angled placement)
            val labelText = bar.label
            val labelMeasured = textMeasurer.measure(labelText, labelStyle)
            val labelX = x + barThickness / 2f - labelMeasured.size.width / 2f
            val labelY = chartBottom + 8.dp.toPx()
            if (labelY + labelMeasured.size.height <= size.height) {
                drawText(
                    textLayoutResult = labelMeasured,
                    topLeft = Offset(labelX, labelY)
                )
            }
        }
    }
}

private data class BarData(
    val label: String,
    val value: Double
)

private fun formatCompactValue(value: Double, currencyCode: String): String {
    val absValue = abs(value)
    val useIndianNotation = currencyCode in setOf("INR", "NPR", "PKR")
    return when {
        useIndianNotation && absValue >= 1_00_00_000 ->
            "${String.format("%.1f", absValue / 1_00_00_000)}Cr"
        useIndianNotation && absValue >= 1_00_000 ->
            "${String.format("%.1f", absValue / 1_00_000)}L"
        absValue >= 10_000_000 ->
            "${String.format("%.1f", absValue / 1_000_000)}M"
        absValue >= 1_000 ->
            "${String.format("%.1f", absValue / 1_000)}K"
        absValue > 0 -> absValue.toInt().toString()
        else -> "0"
    }
}
