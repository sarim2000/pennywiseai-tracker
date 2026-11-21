package com.pennywiseai.tracker.presentation.onboarding

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexAxes
import com.pennywiseai.tracker.ui.theme.LocalGoogleSansFlexActive
import com.pennywiseai.tracker.ui.theme.withFlexAxes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The welcome title, with the "PennyWise" in it inked in once: each letter
 * starts thin and grows to the title's weight on a spring, a beat after the one
 * before, its grade swelling and settling on the way.
 *
 * It plays only in Google Sans Flex (the wordmark sits inside a translated
 * sentence, so it follows the app font rather than switching typeface
 * mid-line), with animations on, and when the translation keeps the brand in
 * Latin. Otherwise, and once [played], it is the plain title. Purely visual:
 * nothing waits for it, and it reads as one text node.
 */
@Composable
internal fun WelcomeTitle(
    text: String,
    style: TextStyle,
    played: Boolean,
    onPlayed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = stringResource(R.string.app_name)
    val brandStart = text.indexOf(brand)
    val enabled = LocalGoogleSansFlexActive.current && rememberAnimationsEnabled()
    // Decided on first composition: after a rotation, a language switch or a
    // return to this step, [played] is already set and the title stays still.
    var playing by remember { mutableStateOf(!played) }
    if (!playing || !enabled || brandStart < 0) {
        Text(text = text, modifier = modifier, style = style)
        return
    }

    val progress = remember(brand) { List(brand.length) { Animatable(0f) } }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val windowInfo = LocalWindowInfo.current
    val drawn = remember { CompletableDeferred<Unit>() }
    LaunchedEffect(Unit) {
        // On a cold start Compose runs steady frames long before anything is on
        // screen: the splash holds the first draw, and the starting window covers
        // the app until it has drawn. Wait for the title's own first draw and for
        // the window to take focus (the starting window never does), then for the
        // frames to settle, so the sweep plays where it can be seen.
        drawn.await()
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        awaitSteadyFrames()
        onPlayed()
        delay(LEAD_IN_MS)
        coroutineScope {
            progress.forEachIndexed { i, letter ->
                launch {
                    delay(i * LETTER_STAGGER_MS)
                    letter.animateTo(1f, spec)
                }
            }
        }
        playing = false
    }

    // End on exactly the weight the plain title resolves to, "bold text" included.
    val adjustment = fontWeightAdjustment(LocalConfiguration.current)
    val endWeight = (style.fontWeight ?: FontWeight.Normal).weight + adjustment.toFloat()
    val startWeight = START_WEIGHT + adjustment

    BoxWithConstraints(modifier) {
        // Thin letters are narrower, so the line could take an extra word and
        // then drop it again. Hold the breaks the finished title will have.
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val shown = remember(text, style, maxWidth) {
            pinLineBreaks(text, measurer.measure(text, style, constraints = Constraints(maxWidth = maxWidth)))
        }
        // Each letter's point on the axis grid, read through derivedStateOf: the
        // title recomposes when some letter steps to a new variant, not per frame.
        val steps by remember(progress, startWeight, endWeight) {
            derivedStateOf { progress.map { LetterStep.at(it.value, startWeight, endWeight) } }
        }
        val annotated = buildAnnotatedString {
            append(shown)
            steps.forEachIndexed { i, step ->
                // Every letter walks the same quantized path, so they share cached fonts.
                val letterStyle = style.withFlexAxes(weight = step.weight, gradeBoost = step.gradeBoost)
                addStyle(letterStyle.toSpanStyle(), brandStart + i, brandStart + i + 1)
            }
        }
        Text(
            text = annotated,
            style = style,
            modifier = Modifier
                .clearAndSetSemantics { this.text = AnnotatedString(text) }
                .drawWithContent {
                    drawContent()
                    drawn.complete(Unit)
                },
        )
    }
}

/** A letter's axes at one point of its ink-in, snapped to Google Sans Flex's grid. */
private data class LetterStep(val weight: Float, val gradeBoost: Float) {
    companion object {
        fun at(progress: Float, startWeight: Float, endWeight: Float): LetterStep {
            // Ink rises mid-stroke and is gone by the end, where the overshoot
            // is carried by weight alone.
            val ink = sin(PI * progress.coerceIn(0f, 1f)).toFloat()
            return LetterStep(
                weight = snap(lerp(startWeight, endWeight, progress), GoogleSansFlexAxes.WEIGHT_STEP),
                gradeBoost = snap(GoogleSansFlexAxes.EMPHASIS_GRADE * ink, GoogleSansFlexAxes.GRADE_STEP),
            )
        }

        private fun snap(value: Float, step: Int) = (value / step).roundToInt() * step.toFloat()
    }
}

/**
 * Returns once frames arrive at a steady pace ([STEADY_FRAMES] in a row, each
 * within [STEADY_FRAME_NANOS] of the last), so what follows is actually drawn.
 * Gives up after [MAX_WAIT_FRAMES] on a device that never gets there.
 */
private suspend fun awaitSteadyFrames() {
    var last = withFrameNanos { it }
    var steady = 0
    var frames = 0
    while (steady < STEADY_FRAMES && frames < MAX_WAIT_FRAMES) {
        val now = withFrameNanos { it }
        steady = if (now - last <= STEADY_FRAME_NANOS) steady + 1 else 0
        last = now
        frames++
    }
}

/** [text] with each soft break [layout] chose at a space made a hard one; same length. */
private fun pinLineBreaks(text: String, layout: TextLayoutResult): String {
    val chars = text.toCharArray()
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        if (end in 1..chars.size && chars[end - 1] == ' ') chars[end - 1] = '\n'
    }
    return String(chars)
}

/** The system "bold text" offset Compose adds to every resolved weight (Android 12+). */
private fun fontWeightAdjustment(configuration: Configuration): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        configuration.fontWeightAdjustment != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED
    ) {
        configuration.fontWeightAdjustment
    } else {
        0
    }

/** Letters start this thin: a sketch of the wordmark before it inks in. */
private const val START_WEIGHT = 200f

/** Between letters; with the spring, the word settles in under a second. */
private const val LETTER_STAGGER_MS = 50L

/**
 * Lets the screen land (and the splash finish fading on first launch) before
 * the word moves; counted once the window has focus and frames are steady.
 */
private const val LEAD_IN_MS = 250L

/** Frames in a row that must come on time before the lead-in starts. */
private const val STEADY_FRAMES = 3

/** "On time": within three 60 Hz vsyncs of the frame before. */
private const val STEADY_FRAME_NANOS = 50_000_000L

/** At most this many frames of waiting, so a device that never runs steady still plays it. */
private const val MAX_WAIT_FRAMES = 12
