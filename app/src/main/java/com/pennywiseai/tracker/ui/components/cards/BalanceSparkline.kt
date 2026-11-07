package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import com.pennywiseai.tracker.ui.theme.PennyWiseText

@Composable
fun BalanceSparkline(
    data: List<BigDecimal>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    /** First point's date; the axis runs from it to today. */
    startDate: java.time.LocalDate? = null,
    /** The chart's top value, so the line has a scale to read against. */
    scaleLabel: String? = null,
    comparisonData: List<BigDecimal>? = null,
    comparisonLineColor: Color = Color.Gray,
    /** Names the dashed line, between the two date labels. */
    comparisonLabel: String? = null,
) {
    if (data.size < 2) return

    // Compute min/max from both datasets for shared Y scale
    val hasComparison = comparisonData != null && comparisonData.size >= 2
    val allValues = if (hasComparison) data + comparisonData!! else data
    // From zero: starting at the data's minimum made a JD10 rise fill the whole height.
    val max = allValues.maxOf { it }.toFloat()
    val min = 0f
    val range = (max - min).takeIf { it > 0f } ?: 1f

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = PennyWiseText.chartLabel.copy(color = labelColor)

    // Dates: the amount is already the hero above this chart.
    val dateFormat = com.pennywiseai.tracker.utils.AppDates.dayMonthFormatter()
    val startLabel = startDate?.format(dateFormat) ?: ""
    val endLabel = stringResource(R.string.sparkline_today)

    val startResult = textMeasurer.measure(startLabel, labelStyle)
    val endResult = textMeasurer.measure(endLabel, labelStyle)
    val comparisonResult = comparisonLabel?.takeIf { hasComparison }?.let { textMeasurer.measure("- - $it", labelStyle) }
    val scaleResult = scaleLabel?.let { textMeasurer.measure(it, labelStyle) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val labelHeight = 14.dp.toPx()
        val chartHeight = height - labelHeight
        val stepX = width / (data.size - 1)
        // RTL: time runs right to left, like the analytics charts.
        val rtl = layoutDirection == LayoutDirection.Rtl

        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            // Draw comparison line FIRST (behind main line)
            if (hasComparison) {
                val compData = comparisonData!!
                val compStepX = if (compData.size > 1) width / (compData.size - 1) else width
                val compPath = Path()

                compData.forEachIndexed { index, value ->
                    val x = index * compStepX
                    val y = chartHeight - ((value.toFloat() - min) / range * chartHeight * 0.7f + chartHeight * 0.075f)

                    if (index == 0) {
                        compPath.moveTo(x, y)
                    } else {
                        val prevValue = compData[index - 1]
                        val prevY = chartHeight - ((prevValue.toFloat() - min) / range * chartHeight * 0.7f + chartHeight * 0.075f)
                        compPath.lineTo(x, prevY)
                        compPath.lineTo(x, y)
                    }
                }

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                drawPath(
                    path = compPath,
                    color = comparisonLineColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                )
            }

            // Draw main line
            val linePath = Path()

            data.forEachIndexed { index, value ->
                val x = index * stepX
                val y = chartHeight - ((value.toFloat() - min) / range * chartHeight * 0.7f + chartHeight * 0.075f)

                if (index == 0) {
                    linePath.moveTo(x, y)
                } else {
                    val prevValue = data[index - 1]
                    val prevY = chartHeight - ((prevValue.toFloat() - min) / range * chartHeight * 0.7f + chartHeight * 0.075f)
                    // Spending is cumulative and jumps per purchase: a step, not a ramp.
                    linePath.lineTo(x, prevY)
                    linePath.lineTo(x, y)
                }
            }

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
            // Today, where the line stops: the one place on it you act from.
            val lastY = chartHeight - ((data.last().toFloat() - min) / range * chartHeight * 0.7f + chartHeight * 0.075f)
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset((data.size - 1) * stepX, lastY)
            )

            // Gradient fill under the main line
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(width, chartHeight)
                lineTo(0f, chartHeight)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.12f),
                        lineColor.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                )
            )
        }

        scaleResult?.let {
            drawText(
                textLayoutResult = it,
                topLeft = androidx.compose.ui.geometry.Offset(if (rtl) width - it.size.width else 0f, 0f)
            )
        }

        // Draw endpoint labels (start sits on the reading-start side)
        val labelY = height - 2.dp.toPx()
        drawText(
            textLayoutResult = startResult,
            topLeft = androidx.compose.ui.geometry.Offset(
                if (rtl) width - startResult.size.width else 0f,
                labelY - startResult.size.height
            )
        )
        drawText(
            textLayoutResult = endResult,
            topLeft = androidx.compose.ui.geometry.Offset(
                if (rtl) 0f else width - endResult.size.width,
                labelY - endResult.size.height
            )
        )
        // Only where it fits between the dates: a large font drops it before the dates.
        comparisonResult?.let {
            if (startResult.size.width + endResult.size.width + it.size.width + 16.dp.toPx() < width) {
                drawText(
                    textLayoutResult = it,
                    topLeft = androidx.compose.ui.geometry.Offset((width - it.size.width) / 2, labelY - it.size.height)
                )
            }
        }
    }
}

