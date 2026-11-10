package com.pennywiseai.tracker.ui.effects

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing whether blur effects are enabled.
 * Defaults to true. Set to false on devices that don't support blur
 * or when the user disables blur effects in preferences.
 */
val LocalBlurEffects = staticCompositionLocalOf { true }
