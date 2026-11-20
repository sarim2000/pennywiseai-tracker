package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.GRAPHIC_CONTRAST
import com.pennywiseai.tracker.ui.theme.neutralSurfaces
import com.pennywiseai.tracker.ui.theme.readableOn

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
 *
 * [color] keeps its hue but is nudged to 3:1 on the track and the card it sits on, so a
 * pale budget or category colour (preset yellow) still shows. Pass [containerColor] when
 * that card isn't a neutral surface (a tinted or primaryContainer card). Don't animate
 * [color]: each in-between frame would be checked and cached.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyMeter(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    containerColor: Color? = null,
) {
    val cards = containerColor?.let { listOf(it) } ?: MaterialTheme.colorScheme.neutralSurfaces
    // A translucent track is seen over the card, so that's what the fill is checked against.
    val tracks = cards.map { trackColor.compositeOver(it) }
    val clamped = fraction.coerceIn(0f, 1f)
    LinearWavyProgressIndicator(
        progress = { clamped },
        modifier = modifier.fillMaxWidth(),
        color = color.readableOn(tracks + cards, GRAPHIC_CONTRAST),
        trackColor = trackColor,
        stopSize = Dimensions.Component.wavyMeterStop,
        amplitude = if (dense) DenseAmplitude else WavyProgressIndicatorDefaults.indicatorAmplitude,
        wavelength = if (dense) Dimensions.Component.wavyMeterDenseWavelength else WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = Dimensions.Component.wavyMeterStop,
    )
}

/** About a third of the standard crest, flat at the very ends like the default. */
private val DenseAmplitude: (Float) -> Float = { p -> if (p <= 0.1f || p >= 0.95f) 0f else 0.35f }
