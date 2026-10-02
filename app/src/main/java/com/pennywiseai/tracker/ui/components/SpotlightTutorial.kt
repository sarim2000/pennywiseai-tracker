package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
        animationSpec = tween(durationMillis = 300),
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
        
        // Tutorial message positioned to the left of FAB
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { (targetPosition.left - 220.dp.toPx()).toDp() },
                    y = with(density) { targetPosition.center.y.toDp() - 30.dp }
                )
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .width(200.dp)
                        .padding(16.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.spotlight_tap_to_dismiss),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Modifier to capture the position of a composable for spotlight targeting
 */
fun Modifier.spotlightTarget(
    onPositioned: (Rect) -> Unit
) = this.onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInWindow()
    onPositioned(bounds)
}