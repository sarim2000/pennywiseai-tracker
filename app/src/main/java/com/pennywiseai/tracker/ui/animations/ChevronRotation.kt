package com.pennywiseai.tracker.ui.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

@Composable
fun chevronRotation(isExpanded: Boolean): Float {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chevronRotation"
    )
    return rotation
}
