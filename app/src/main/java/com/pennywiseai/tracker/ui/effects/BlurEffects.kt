package com.pennywiseai.tracker.ui.effects

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * CompositionLocal providing whether blur effects are enabled.
 * Defaults to true. Set to false on devices that don't support blur
 * or when the user disables blur effects in preferences.
 */
val LocalBlurEffects = staticCompositionLocalOf { true }

/**
 * Applies [hazeSource] only when blur effects are enabled via [LocalBlurEffects].
 * When blur is disabled, returns the modifier unchanged.
 */
fun Modifier.conditionalHazeSource(
    state: HazeState,
    enabled: Boolean,
): Modifier = if (enabled) {
    this.hazeSource(state = state)
} else {
    this
}

/**
 * Applies [hazeEffect] only when blur effects are enabled via [LocalBlurEffects].
 * When blur is disabled, returns the modifier unchanged.
 */
fun Modifier.conditionalHazeEffect(
    state: HazeState,
    enabled: Boolean,
    style: HazeStyle = HazeStyle.Unspecified,
    block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = if (enabled) {
    this.hazeEffect(state = state, style = style, block = block)
} else {
    this
}
