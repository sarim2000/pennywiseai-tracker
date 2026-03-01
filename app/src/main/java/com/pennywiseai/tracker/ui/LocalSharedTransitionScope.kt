package com.pennywiseai.tracker.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

/**
 * Provides [SharedTransitionScope] from the root [SharedTransitionLayout]
 * to deeply nested composables without threading through every function signature.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Provides the [AnimatedVisibilityScope] of the current navigation destination
 * so shared element modifiers can reference it from nested composables.
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
