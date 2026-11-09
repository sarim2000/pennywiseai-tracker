package com.pennywiseai.tracker.ui.animations

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.delay

// The app's motion language lives in docs/design.md ("Motion language"). These are the
// generic transitions; reach for them before hand-building fade/slide/expand combos.

/** Short entrance travel: a fraction of the screen, never a full-width/height slide. */
private val EnterTravel = Spacing.lg

/** Content appearing in place: effects fade plus a short upward spatial slide. */
@Composable
fun pwEnter(travel: Dp = EnterTravel): EnterTransition {
    val motion = MaterialTheme.motionScheme
    val px = with(LocalDensity.current) { travel.roundToPx() }
    return fadeIn(motion.defaultEffectsSpec()) +
        slideInVertically(motion.defaultSpatialSpec()) { px }
}

/** Content leaving: a quick effects fade, no travel. */
@Composable
fun pwExit(): ExitTransition = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())

/** Plain fade in — for icons and small parts (no scale-in: Material reads scale as elevation). */
@Composable
fun pwFadeIn(): EnterTransition = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec())

/** Plain fade out — pair of [pwFadeIn]. */
@Composable
fun pwFadeOut(): ExitTransition = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())

/** A section opening under its heading: spatial size + effects fade. */
@Composable
fun pwExpand(): EnterTransition {
    val motion = MaterialTheme.motionScheme
    return expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) +
        fadeIn(motion.defaultEffectsSpec())
}

/** A section closing back into its heading. */
@Composable
fun pwCollapse(): ExitTransition {
    val motion = MaterialTheme.motionScheme
    return shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Top) +
        fadeOut(motion.fastEffectsSpec())
}

/**
 * False when the user set "Remove animations" (animator duration scale 0). Compose already
 * zeroes animation durations then; this is for skipping `delay`-based choreography too.
 * Re-read on every resume, so toggling it in Settings applies on return. Key `delay`
 * effects on this value, so a pending delay is dropped when it turns off.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = ValueAnimator.areAnimatorsEnabled() }
    return enabled
}

/** First-entrance stagger: the step between items, and the most steps it takes. */
private const val STAGGER_STEP_MS = 50L
private const val STAGGER_MAX_STEPS = 6

/**
 * Delay for the [index]th staggered item (0-based; a header at delay 0 comes before it).
 * Items past [STAGGER_MAX_STEPS] share the last beat, so a long list doesn't trickle in.
 */
fun staggerDelayMs(index: Int): Long = STAGGER_STEP_MS * (index + 1).coerceAtMost(STAGGER_MAX_STEPS)

/**
 * A screen's saveable "entrance already ran" flag, for [StaggeredEntrance]'s `skip`. Flips
 * just after the last [staggerDelayMs] beat — at once with animations off — and stays
 * flipped across recomposition, scrolling and returning to the screen.
 */
@Composable
fun rememberEntranceDone(): Boolean {
    var done by rememberSaveable { mutableStateOf(false) }
    val animationsEnabled = rememberAnimationsEnabled()
    LaunchedEffect(animationsEnabled) {
        if (!done) {
            if (animationsEnabled) delay(STAGGER_STEP_MS * (STAGGER_MAX_STEPS + 1))
            done = true
        }
    }
    return done
}

/**
 * One item of a first-entrance stagger. Shows immediately when [skip] (the screen already
 * ran its entrance) or animations are off; otherwise reveals with [pwEnter] after [delayMs]
 * (use [staggerDelayMs]). Each item tracks its own `visible` so one composed just before
 * [skip] flips still reveals.
 */
@Composable
fun StaggeredEntrance(
    delayMs: Long,
    skip: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    var visible by remember { mutableStateOf(skip || !animationsEnabled) }
    LaunchedEffect(animationsEnabled) {
        if (!visible) {
            if (animationsEnabled) delay(delayMs)
            visible = true
        }
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = pwEnter(), content = content)
}
