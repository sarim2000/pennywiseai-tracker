package com.pennywiseai.tracker.ui.animations

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.hypot
import kotlin.math.max

/**
 * A new look spreads as a circle from where the user last touched (the Appearance
 * control they tapped), over a still of the old one, instead of every colour
 * snapping at once. [content] is always composed with the theme on screen, which
 * lags [target] by the one frame it takes to take that still.
 *
 * Only the app's own window is caught: a dialog or sheet open over it just swaps.
 * With "Remove animations", or if the still can't be taken, the theme swaps at once.
 *
 * While [enabled] is false the theme also swaps at once, and a reveal under way
 * drops its still in the same frame. App Lock's cover passes false: the still is
 * drawn above [content], so it would otherwise show the unlocked screen over it.
 */
@Composable
fun <T> ThemeReveal(
    target: T,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (shown: T) -> Unit,
) {
    var shown by remember { mutableStateOf(target) }
    var still by remember { mutableStateOf<ImageBitmap?>(null) }
    val radius = remember { Animatable(0f) }
    val layer = rememberGraphicsLayer()
    // Read in draw, never as state: a touch doesn't recompose anything.
    val origin = remember { Holder(Offset.Unspecified) }
    val touchedAt = remember { Holder(0L) }
    val size = remember { Holder(Size.Zero) }
    // Fixed for one reveal, so a tap mid-reveal doesn't move the circle.
    val center = remember { Holder(Offset.Zero) }
    val animationsEnabled = rememberAnimationsEnabled()
    // A spatial spring: an overshoot past the far corner is invisible, so it's free.
    // Above the app's theme, so the scheme is named rather than read from MaterialTheme.
    val spec = remember { MotionScheme.expressive().slowSpatialSpec<Float>() }
    // Read in draw, so the still is gone from the very frame that turns this off.
    val drawStill by rememberUpdatedState(enabled)

    LaunchedEffect(target, enabled) {
        if (target == shown) return@LaunchedEffect
        if (!enabled || !animationsEnabled || size.value == Size.Zero) {
            shown = target
            return@LaunchedEffect
        }
        // A change mid-reveal cancels this one: drop its still, or the next reveal
        // would flash it at radius 0 before taking its own.
        try {
            center.value = revealOrigin(origin.value, touchedAt.value, SystemClock.uptimeMillis(), size.value)
            radius.snapTo(0f)
            // The layer still holds the last frame drawn with the old theme.
            still = captureStill { layer.toImageBitmap() }
            shown = target
            if (still == null) return@LaunchedEffect
            // Re-theming the whole app is a heavy frame: start the clock after it, or the
            // spring is mostly spent before the first frame of the reveal is drawn.
            withFrameNanos { }
            withFrameNanos { }
            radius.animateTo(farthestCorner(center.value, size.value), spec)
        } finally {
            still = null
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Initial pass and nothing consumed: every tap still reaches its target.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) {
                            // A click lands on release, so that's the touch a change follows.
                            val change = event.changes.first()
                            origin.value = change.position
                            touchedAt.value = change.uptimeMillis
                        }
                    }
                }
            }
            .drawWithContent {
                size.value = this.size
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                val old = still?.takeIf { drawStill } ?: return@drawWithContent
                val hole = Path().apply { addOval(Rect(center.value, radius.value)) }
                clipPath(hole, ClipOp.Difference) { drawImage(old) }
            }
    ) {
        content(shown)
    }
}

/**
 * A full-window bitmap, or null when it can't be had (no memory for it on an old
 * phone, a layer that can't be read back). The theme then just swaps, which beats
 * a change that never applies.
 */
private suspend fun captureStill(capture: suspend () -> ImageBitmap): ImageBitmap? = try {
    capture()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.w(TAG, "Theme reveal skipped, no still: ${e.javaClass.simpleName}")
    null
} catch (_: OutOfMemoryError) {
    Log.w(TAG, "Theme reveal skipped, no memory for the still")
    null
}

private const val TAG = "ThemeReveal"

/** A plain box for values only draw and the reveal read; writing it never invalidates. */
private class Holder<V>(var value: V)

private val Size.center get() = Offset(width / 2f, height / 2f)

/** How long after a touch a theme change still counts as caused by it. */
internal const val TOUCH_CAUSE_MS = 1_000L

/**
 * Where the circle starts: the last touch if it was just now, so it caused this
 * change, else the centre. A change with no touch behind it (a backup restore, a
 * keyboard) shouldn't spread from some tap made minutes ago.
 */
internal fun revealOrigin(touch: Offset, touchedAt: Long, now: Long, size: Size): Offset =
    touch.takeIf { it.isSpecified && now - touchedAt <= TOUCH_CAUSE_MS } ?: size.center

/** The radius that covers [size] from [from]: the distance to its farthest corner. */
internal fun farthestCorner(from: Offset, size: Size): Float {
    val dx = max(from.x, size.width - from.x)
    val dy = max(from.y, size.height - from.y)
    return hypot(dx, dy)
}
