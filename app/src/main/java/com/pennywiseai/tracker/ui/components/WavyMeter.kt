package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.neutralSurfaces

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
 * [color] is drawn as given and the track moves until it reads there at 3:1, so a pale
 * budget, category or goal colour (preset yellow) the user picked still shows on its track.
 * Pass [containerColor] when the card behind isn't a neutral surface (a tinted or
 * primaryContainer card). [keepColor] false is for a theme colour that may be the card's own
 * (Home's hero): there it is nudged to 3:1 on the card too. See [readableMeterColors].
 * Don't animate [color]: each in-between frame would be checked and cached.
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
    keepColor: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    // Remembered: an animating fraction recomposes this every frame.
    val meter = remember(color, trackColor, containerColor, keepColor, scheme) {
        val cards = containerColor?.let { listOf(it) } ?: scheme.neutralSurfaces
        readableMeterColors(color, trackColor, cards, scheme.surfaceContainerHighest, keepColor)
    }
    val clamped = fraction.coerceIn(0f, 1f)
    LinearWavyProgressIndicator(
        progress = { clamped },
        modifier = modifier.fillMaxWidth(),
        color = meter.indicator,
        trackColor = meter.track,
        stopSize = Dimensions.Component.wavyMeterStop,
        amplitude = if (dense) DenseAmplitude else WavyProgressIndicatorDefaults.indicatorAmplitude,
        wavelength = if (dense) Dimensions.Component.wavyMeterDenseWavelength else WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = Dimensions.Component.wavyMeterStop,
    )
}

/** About a third of the standard crest, flat at the very ends like the default. */
private val DenseAmplitude: (Float) -> Float = { p -> if (p <= 0.1f || p >= 0.95f) 0f else 0.35f }
