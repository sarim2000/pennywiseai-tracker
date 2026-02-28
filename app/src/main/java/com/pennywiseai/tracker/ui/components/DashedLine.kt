package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DashedLine(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
    dashWidth: Float = 10f,
    gapWidth: Float = 10f
) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x + dashWidth, 0f),
                strokeWidth = 2f
            )
            x += dashWidth + gapWidth
        }
    }
}
