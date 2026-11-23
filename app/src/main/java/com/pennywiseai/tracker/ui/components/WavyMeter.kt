package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.ui.animations.animateMeterFraction
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
 *
 * It fills from empty once, when it first shows ([fillIn]; off by default for dense
 * list rows so a long list doesn't sweep in all at once), then eases to new values.
 */
@Composable
fun WavyMeter(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    fillIn: Boolean = !dense,
) {
    // The drawn fraction is read only inside the bar's draw lambda, so easing doesn't recompose.
    val target = fraction.coerceIn(0f, 1f)
    val shown = animateMeterFraction(target, fillIn = fillIn)
    MeterBar(shown, target, color, trackColor, modifier, dense)
}

/**
 * [WavyMeter] whose [color] follows the value: it gets the fraction on screen, so a
 * status colour turns at 70% when the bar reaches 70%, not when the target does,
 * and crossfades on the fast effects spec. The first fill-in is the exception: it
 * sweeps in the colour of the value it's heading for.
 */
@Composable
fun WavyMeter(
    fraction: Float,
    color: @Composable (shown: Float) -> Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    fillIn: Boolean = !dense,
) {
    val target = fraction.coerceIn(0f, 1f)
    val shown = animateMeterFraction(target, fillIn = fillIn)
    // The first fill-in sweeps in its final colour: crossfading through the status colours
    // mid-sweep reads muddy, and primary barely shows on the hero card.
    val tint by animateColorAsState(
        color(if (shown.filling) target else shown.value),
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "meterColor"
    )
    MeterBar(shown, target, tint, trackColor, modifier, dense)
}

/**
 * Composes a data [meter] only while amounts show ([hidden] false): a known limit and its
 * fill give the spend away. The meter's saveable state outlives the gap, so revealing
 * amounts shows it settled instead of replaying its fill-in.
 */
@Composable
fun HideableMeter(hidden: Boolean, meter: @Composable () -> Unit) {
    val holder = rememberSaveableStateHolder()
    if (!hidden) holder.SaveableStateProvider("meter", meter)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MeterBar(
    shown: State<Float>,
    target: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier,
    dense: Boolean,
) {
    // A bar headed for an end (full or near empty) stays flat all the way: the indicator's own
    // amplitude easing, started mid-sweep, doesn't settle back to flat once the sweep ends.
    val amplitude = when {
        target <= FLAT_BELOW || target >= FLAT_FROM -> FlatAmplitude
        dense -> DenseAmplitude
        else -> WavyProgressIndicatorDefaults.indicatorAmplitude
    }
    LinearWavyProgressIndicator(
        progress = { shown.value },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = trackColor,
        stopSize = Dimensions.Component.wavyMeterStop,
        amplitude = amplitude,
        wavelength = if (dense) Dimensions.Component.wavyMeterDenseWavelength else WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed = Dimensions.Component.wavyMeterStop,
    )
}

/** About a third of the standard crest, flat at the very ends like the default. */
private val DenseAmplitude: (Float) -> Float = { p -> if (p <= FLAT_BELOW || p >= FLAT_FROM) 0f else 0.35f }

private val FlatAmplitude: (Float) -> Float = { 0f }

// Where the default amplitude goes flat, at either end of the bar.
private const val FLAT_BELOW = 0.1f
private const val FLAT_FROM = 0.95f
