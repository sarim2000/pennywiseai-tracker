package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * "Receipt settled": while [settled] is true, [content] (a merchant avatar)
 * gives way to a check whose container morphs from the avatar's cookie into a
 * circle. Only the caller decides when — pass true right after a user's own
 * save, never for imports or on load. A fresh composition with [settled]
 * already true still plays the morph in, since it starts from 0.
 */
@Composable
fun SettledCheck(
    settled: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val shapeProgress = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(settled) {
        val target = if (settled) 1f else 0f
        coroutineScope {
            launch { shapeProgress.animateTo(target, spatial) }
            launch { alpha.animateTo(target, effects) }
        }
    }

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { this.alpha = 1f - alpha.value }) { content() }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.alpha = alpha.value
                    // No scale-in (Material reads scale as elevation): the check fades
                    // while its container morphs. The spring overshoots; the morph
                    // itself stays within its two shapes.
                    shape = MorphShape(morph, shapeProgress.value.coerceIn(0f, 1f))
                    clip = true
                }
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/** A [Morph] between two unit-square `MaterialShapes`, scaled to the layout. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}
