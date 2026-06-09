package com.pennywiseai.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope

@Composable
fun SharedTransitionScope.sharedElementIcon(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val state = rememberSharedContentState(key = key)
    return Modifier.sharedElement(state, animatedVisibilityScope)
}
