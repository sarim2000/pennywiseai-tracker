package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import java.math.BigDecimal

@Composable
fun BalanceSparkline(
    data: List<BigDecimal>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    currency: String,
    isBalanceHidden: Boolean = false,
    comparisonData: List<BigDecimal>? = null,
    comparisonLineColor: Color = Color.Gray
) {
    if (data.size < 2) return

    // Compute min/max from both datasets for shared Y scale
    val hasComparison = comparisonData != null && comparisonData.size >= 2
    val allValues = if (hasComparison) data + comparisonData!! else data
    val max = allValues.maxOf { it }.toFloat()
    val min = allValues.minOf { it }.toFloat()
    val range = (max - min).takeIf { it > 0f } ?: 1f

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = PennyWiseText.chartLabel.copy(color = labelColor)

    val startLabel = if (isBalanceHidden) "••••" else formatCompactBalance(data.first(), currency)
    val endLabel = if (isBalanceHidden) "••••" else formatCompactBalance(data.last(), currency)

    val startResult = textMeasurer.measure(startLabel, labelStyle)
    val endResult = textMeasurer.measure(endLabel, labelStyle)

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val labelHeight = 14.dp.toPx()
        val chartHeight = height - labelHeight
        val stepX = width / (data.size - 1)
        // RTL: time runs right to left, like the analytics charts.
        val rtl = layoutDirection == LayoutDirection.Rtl

        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            // Draw main line
            val linePath = Path()

            data.forEachIndexed { index, value ->
                val x = index * stepX
                val y = chartHeight - ((value.toFloat() - min) / range * chartHeight * 0.85f + chartHeight * 0.075f)

                if (index == 0) {
                    linePath.moveTo(x, y)
                } else {
                    val prevX = (index - 1) * stepX
                    val prevValue = data[index - 1]
                    val prevY = chartHeight - ((prevValue.toFloat() - min) / range * chartHeight * 0.85f + chartHeight * 0.075f)
                    val midX = (prevX + x) / 2f
                    linePath.cubicTo(midX, prevY, midX, y, x, y)
                }
            }

            // Comparison line under the main one. No area tint under the line: the lines sit on a
            // coloured card, and a tint of the line's own colour left it under 3:1 next to it.
            if (hasComparison) {
                val compData = comparisonData!!
                val compStepX = if (compData.size > 1) width / (compData.size - 1) else width
                val compPath = Path()

                compData.forEachIndexed { index, value ->
                    val x = index * compStepX
                    val y = chartHeight - ((value.toFloat() - min) / range * chartHeight * 0.85f + chartHeight * 0.075f)

                    if (index == 0) {
                        compPath.moveTo(x, y)
                    } else {
                        val prevX = (index - 1) * compStepX
                        val prevValue = compData[index - 1]
                        val prevY = chartHeight - ((prevValue.toFloat() - min) / range * chartHeight * 0.85f + chartHeight * 0.075f)
                        val midX = (prevX + x) / 2f
                        compPath.cubicTo(midX, prevY, midX, y, x, y)
                    }
                }

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f)
                drawPath(
                    path = compPath,
                    color = comparisonLineColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                )
            }

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
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
    }
}

private fun formatCompactBalance(value: BigDecimal, currency: String): String {
    // Symbol and K/M vs L/Cr follow the currency and the number-format setting.
    val prefix = if (value < BigDecimal.ZERO) "-" else ""
    return com.pennywiseai.tracker.utils.CurrencyFormatter.signed(
        prefix,
        com.pennywiseai.tracker.utils.CurrencyFormatter.formatAbbreviated(value.toDouble(), currency)
    )
}
