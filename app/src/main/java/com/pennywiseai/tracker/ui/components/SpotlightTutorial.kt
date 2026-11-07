package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun SpotlightTutorial(
    isVisible: Boolean,
    targetPosition: Rect?,
    message: String,
    onDismiss: () -> Unit,
    onTargetClick: (() -> Unit)? = null
) {
    if (!isVisible || targetPosition == null) return
    
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "spotlight_alpha"
    )
    
    val density = LocalDensity.current
    val padding = with(density) { 12.dp.toPx() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Dark overlay with circular cutout for the target
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .pointerInput(targetPosition, padding) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val position = down.position
                        val center = targetPosition.center
                        val radius = (targetPosition.width / 2) + padding
                        val distance = sqrt(
                            (position.x - center.x).pow(2) + 
                            (position.y - center.y).pow(2)
                        )
                        
                        if (distance > radius) {
                            // Click outside spotlight - just dismiss
                            down.consume()
                            onDismiss()
                        } else {
                            // Click inside spotlight - trigger FAB action and dismiss
                            down.consume()
                            onTargetClick?.invoke()
                            onDismiss()
                        }
                    }
                }
        ) {
            val path = Path().apply {
                // First add the full screen rectangle
                addRect(Rect(0f, 0f, size.width, size.height))
                
                // Then add the circular cutout for the FAB
                val center = targetPosition.center
                val radius = (targetPosition.width / 2) + padding
                
                addOval(
                    Rect(
                        center.x - radius,
                        center.y - radius,
                        center.x + radius,
                        center.y + radius
                    )
                )
                
                // Set fill type to EvenOdd to create the hole effect
                fillType = PathFillType.EvenOdd
            }
            
            // Draw the path with a dark color
            drawPath(
                path = path,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }
        
        // Below the target (the header's scan button), ending at its right edge, with a tail
        // pointing at it. Beside it, the message covered the user's name.
        val radiusDp = with(density) { (targetPosition.width / 2 + padding).toDp() }
        val centerX = with(density) { targetPosition.center.x.toDp() }
        val centerY = with(density) { targetPosition.center.y.toDp() }
        val bubbleX = (centerX + radiusDp - SpotlightBubbleWidth).coerceAtLeast(Spacing.md)
        val tailColor = MaterialTheme.colorScheme.inverseSurface
        Column(
            modifier = Modifier
                .offset(x = bubbleX, y = centerY + radiusDp)
                .width(SpotlightBubbleWidth)
        ) {
            Canvas(
                modifier = Modifier
                    .padding(start = centerX - bubbleX - Spacing.sm)
                    .size(width = Spacing.md, height = Spacing.sm)
            ) {
                drawPath(
                    Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    },
                    color = tailColor
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.spotlight_tap_to_dismiss),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
            }
        }
    }
}

private val SpotlightBubbleWidth = 240.dp

/**
 * Modifier to capture the position of a composable for spotlight targeting
 */
fun Modifier.spotlightTarget(
    onPositioned: (Rect) -> Unit
) = this.onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInWindow()
    onPositioned(bounds)
}