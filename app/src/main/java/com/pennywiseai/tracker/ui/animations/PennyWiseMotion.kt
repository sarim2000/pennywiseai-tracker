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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
 */
@Composable
fun rememberAnimationsEnabled(): Boolean =
    // ponytail: read once per composition entry; a toggle mid-screen applies on next visit.
    remember { ValueAnimator.areAnimatorsEnabled() }

/**
 * One item of a first-entrance stagger. Shows immediately when [skip] (the screen already
 * ran its entrance) or animations are off; otherwise reveals with [pwEnter] after [delayMs].
 * Each item tracks its own `visible` so one composed just before [skip] flips still reveals.
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
    LaunchedEffect(Unit) {
        if (!visible) {
            delay(delayMs)
            visible = true
        }
    }
    AnimatedVisibility(visible = visible, modifier = modifier, enter = pwEnter(), content = content)
}
