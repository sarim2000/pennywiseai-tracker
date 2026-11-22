package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.ui.theme.Dimensions

/**
 * The app's one determinate data meter (budget spent, goal saved, category
 * share, card utilisation): an M3 Expressive wavy bar that stands still.
 *
 * `waveSpeed = 0` and no stop dot keep it a shape rather than an animation, so a
 * budget never reads as "loading"; the moving, indeterminate wave stays reserved
 * for work in progress (scans, imports, downloads).
 *
 * [dense] calms the wave for rows in a list, where a full-height crest next to
 * the next row's crest turns the screen into noise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyMeter(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    LinearWavyProgressIndicator(
        progress = { clamped },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = trackColor,
        stopSize = Dimensions.Component.wavyMeterStop,
        amplitude = if (dense) DenseAmplitude else WavyProgressIndicatorDefaults.indicatorAmplitude,
        wavelength = if (dense) Dimensions.Component.wavyMeterDenseWavelength else WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = Dimensions.Component.wavyMeterStop,
    )
}

/** About a third of the standard crest, flat at the very ends like the default. */
private val DenseAmplitude: (Float) -> Float = { p -> if (p <= 0.1f || p >= 0.95f) 0f else 0.35f }
