package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * A receipt/ticket-shaped outline with scalloped top/bottom edges
 * and circular notch cutouts on left/right sides.
 *
 * @param cutoutRadius radius of the side notch cutouts
 * @param cutoutTopOffset vertical position of the side cutouts from the top
 * @param scallopRadius radius of each scallop on the top/bottom edges
 */
class ReceiptShape(
    private val cutoutRadius: Float,
    private val cutoutTopOffset: Float,
    private val scallopRadius: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val scallopDiameter = scallopRadius * 2
            val scallopCount = (size.width / scallopDiameter).toInt().coerceAtLeast(1)
            val actualScallopWidth = size.width / scallopCount

            // Start from bottom-left
            moveTo(0f, size.height - scallopRadius)

            // Left edge going up, with side cutout
            lineTo(0f, cutoutTopOffset + cutoutRadius)
            arcTo(
                rect = Rect(
                    -cutoutRadius,
                    cutoutTopOffset - cutoutRadius,
                    cutoutRadius,
                    cutoutTopOffset + cutoutRadius
                ),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(0f, scallopRadius)

            // Top edge scallops (left to right)
            for (i in 0 until scallopCount) {
                val x = i * actualScallopWidth
                arcTo(
                    rect = Rect(x, 0f, x + actualScallopWidth, actualScallopWidth),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            }

            // Right edge going down, with side cutout
            lineTo(size.width, cutoutTopOffset - cutoutRadius)
            arcTo(
                rect = Rect(
                    size.width - cutoutRadius,
                    cutoutTopOffset - cutoutRadius,
                    size.width + cutoutRadius,
                    cutoutTopOffset + cutoutRadius
                ),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(size.width, size.height - scallopRadius)

            // Bottom edge scallops (right to left)
            for (i in 0 until scallopCount) {
                val x = size.width - (i * actualScallopWidth)
                arcTo(
                    rect = Rect(
                        x - actualScallopWidth,
                        size.height - actualScallopWidth,
                        x,
                        size.height
                    ),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
            }

            close()
        }
        return Outline.Generic(path)
    }
}
