package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GradientMeshCard(
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GradientMesh")

    val animatedPrimaryColor by infiniteTransition.animateColor(
        initialValue = accentColor.copy(alpha = 0.15f),
        targetValue = accentColor.copy(alpha = 0.05f),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PrimaryColor"
    )

    val animatedSecondaryColor by infiniteTransition.animateColor(
        initialValue = accentColor.copy(alpha = 0.05f),
        targetValue = accentColor.copy(alpha = 0.15f),
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SecondaryColor"
    )

    // Blob 1: Top-left to center-right
    val offsetX1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX1"
    )
    val offsetY1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY1"
    )

    // Blob 2: Bottom-right to center-left
    val offsetX2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX2"
    )
    val offsetY2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY2"
    )

    // Blob 3: Top-right pulsing scale
    val offsetX3 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX3"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale3"
    )

    val cardBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    val cardShape = RoundedCornerShape(24.dp)

    val cardModifier = modifier
        .fillMaxWidth()
        .clip(cardShape)

    val cardColors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    val cardBorder = BorderStroke(1.dp, accentColor.copy(alpha = 0.1f))
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            onClick = onClick,
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation
        ) {
            GradientMeshContent(
                animatedPrimaryColor = animatedPrimaryColor,
                animatedSecondaryColor = animatedSecondaryColor,
                offsetX1 = offsetX1, offsetY1 = offsetY1,
                offsetX2 = offsetX2, offsetY2 = offsetY2,
                offsetX3 = offsetX3, scale3 = scale3,
                content = content
            )
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation
        ) {
            GradientMeshContent(
                animatedPrimaryColor = animatedPrimaryColor,
                animatedSecondaryColor = animatedSecondaryColor,
                offsetX1 = offsetX1, offsetY1 = offsetY1,
                offsetX2 = offsetX2, offsetY2 = offsetY2,
                offsetX3 = offsetX3, scale3 = scale3,
                content = content
            )
        }
    }
}

@Composable
private fun GradientMeshContent(
    animatedPrimaryColor: Color,
    animatedSecondaryColor: Color,
    offsetX1: Float, offsetY1: Float,
    offsetX2: Float, offsetY2: Float,
    offsetX3: Float, scale3: Float,
    content: @Composable () -> Unit
) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .blur(60.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Blob 1
                drawCircle(
                    color = animatedPrimaryColor,
                    center = Offset(
                        x = canvasWidth * offsetX1,
                        y = canvasHeight * offsetY1
                    ),
                    radius = canvasWidth * 0.5f
                )

                // Blob 2
                drawCircle(
                    color = animatedSecondaryColor,
                    center = Offset(
                        x = canvasWidth * offsetX2,
                        y = canvasHeight * offsetY2
                    ),
                    radius = canvasWidth * 0.5f
                )

                // Blob 3
                drawCircle(
                    color = animatedPrimaryColor.copy(
                        alpha = animatedPrimaryColor.alpha * 0.8f
                    ),
                    center = Offset(
                        x = canvasWidth * offsetX3,
                        y = canvasHeight * 0.2f
                    ),
                    radius = canvasWidth * scale3
                )
            }

            content()
        }
}
