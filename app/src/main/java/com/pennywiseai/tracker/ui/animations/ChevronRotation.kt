package com.pennywiseai.tracker.ui.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun chevronRotation(isExpanded: Boolean, durationMs: Int = 300): Float {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMs),
        label = "chevronRotation"
    )
    return rotation
}
