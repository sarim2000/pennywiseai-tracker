package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

@Composable
fun BalanceSparkline(
    data: List<BigDecimal>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val max = data.maxOf { it }.toFloat()
    val min = data.minOf { it }.toFloat()
    val range = (max - min).takeIf { it > 0f } ?: 1f

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val stepX = width / (data.size - 1)

        val linePath = Path()

        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value.toFloat() - min) / range * height * 0.85f + height * 0.075f)

            if (index == 0) {
                linePath.moveTo(x, y)
            } else {
                // Smooth cubic bezier between points
                val prevX = (index - 1) * stepX
                val prevValue = data[index - 1]
                val prevY = height - ((prevValue.toFloat() - min) / range * height * 0.85f + height * 0.075f)
                val midX = (prevX + x) / 2f
                linePath.cubicTo(midX, prevY, midX, y, x, y)
            }
        }

        // Draw the line
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Gradient fill under the line
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.25f),
                    lineColor.copy(alpha = 0.05f),
                    Color.Transparent
                )
            )
        )
    }
}
