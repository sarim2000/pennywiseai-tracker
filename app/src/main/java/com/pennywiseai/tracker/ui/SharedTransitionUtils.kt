package com.pennywiseai.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.material3.MaterialTheme

@Composable
fun SharedTransitionScope.sharedElementIcon(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val state = rememberSharedContentState(key = key)
    // Bounds move on the theme's spatial spring, like every other position/size change.
    val motion = MaterialTheme.motionScheme
    return Modifier.sharedElement(
        state,
        animatedVisibilityScope,
        boundsTransform = BoundsTransform { _, _ -> motion.defaultSpatialSpec() },
    )
}
