package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.isDark
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * The PennyWise mascot: a [shape] with eyes that blink and look around, showing a [mood].
 * It is decoration and stays silent to accessibility — give the slot around it its name.
 *
 * Up to 32dp it draws as a two-tone glyph: a [tint] silhouette with the eyes cut out, so
 * whatever is behind it (a nav pill, a bar) shows through. It blinks on a delay-driven
 * clock and draws no frames in between. Larger, it draws the detailed owl from theme
 * roles on a frame clock, with a wandering gaze. Either clock runs only while the mascot
 * is composed, its lifecycle is STARTED and animations are on; otherwise it holds a
 * static pose. Time is read in the draw phase only, so it never recomposes per frame.
 */
@Composable
fun Mascot(
    size: Dp,
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Calm,
    shape: MascotShape = MascotShape.Owl,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val detailed = size > Dimensions.Icon.large
    val motion = when {
        !rememberAnimationsEnabled() -> MascotMotion.Still
        detailed -> MascotMotion.Full
        else -> MascotMotion.Quiet
    }
    val state = remember { MascotState(mood) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(motion, lifecycle) { state.runClock(motion, lifecycle) }
    val blendSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    // A mood change plays out with the motion in effect when it arrives, not the one at the last change.
    val currentMotion by rememberUpdatedState(motion)
    LaunchedEffect(mood) { state.changeMood(mood, currentMotion, blendSpec, lifecycle) }

    val colors = if (detailed) owlColors(tint) else null
    // The artwork is symmetric and never mirrored; only where it looks and leans is.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics {}
            // The two-tone glyph punches its eyes out, which needs a layer of its own.
            .then(if (detailed) Modifier else Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen })
            .drawWithCache {
                val geometry = shape.geometry()
                val scratch = Path()
                val k = this.size.minDimension / BOX
                onDrawBehind {
                    val pose = state.pose(motion).let { it.copy(gazeX = it.gazeX * direction, roll = it.roll * direction) }
                    scale(k, k, pivot = Offset.Zero) {
                        if (colors != null) drawShadow(pose, colors.shadow)
                        translate(top = pose.lift) {
                            rotate(pose.roll, pivot = geometry.pivot) {
                                if (colors != null) drawDetailedOwl(geometry, pose, colors, scratch)
                                else drawTwoTone(geometry, pose, tint, scratch)
                            }
                        }
                    }
                }
            }
    )
}

private const val BOX = 100f

/** Pill eyes are drawn this much larger in the two-tone glyph, so they still read at 24dp. */
private const val TWO_TONE_EYE_SCALE = 1.5f

/** Below this contrast against the body, the face mask falls back to a neutral container. */
private const val MASK_MIN_CONTRAST = 1.6f

@Stable
private class MascotState(initialMood: MascotMood) {
    var mood by mutableStateOf(initialMood)
        private set
    private var moodStart = Long.MIN_VALUE / 4
    private var from: MascotPose? = null
    private val blend = Animatable(1f)
    private val time = mutableLongStateOf(0L)
    private val quietBlink = mutableFloatStateOf(0f)
    private val seed = Random.nextFloat() * 10f

    /** The pose last drawn: where a mood change blends from. */
    private var shown = MascotPose()

    /** Called from the draw phase, so the state reads here invalidate the drawing only. */
    fun pose(motion: MascotMotion): MascotPose {
        val t = time.longValue
        var pose = moodPose(mood, t, t - moodStart, seed, motion)
        if (motion == MascotMotion.Quiet) pose = pose.copy(blink = max(pose.blink, quietBlink.floatValue))
        val w = blend.value
        from?.let { if (w < 1f) pose = blendPose(it, pose, w) }
        shown = pose
        return pose
    }

    suspend fun runClock(motion: MascotMotion, lifecycle: Lifecycle) {
        when (motion) {
            MascotMotion.Still -> quietBlink.floatValue = 0f
            MascotMotion.Quiet -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { blinkNowAndThen() }
            MascotMotion.Full -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) withFrameMillis { time.longValue = it }
            }
        }
    }

    /** Waits 4–8s, blinks for 190ms (sometimes twice), repeats. No frames while it waits. */
    private suspend fun blinkNowAndThen() {
        try {
            while (true) {
                delay(nextBlinkDelayMillis(Random.nextFloat()))
                val double = Random.nextFloat() < DOUBLE_BLINK_CHANCE
                val start = withFrameMillis { it }
                do {
                    val elapsed = withFrameMillis { it } - start
                    quietBlink.floatValue = blinkAt(elapsed, double)
                } while (elapsed < blinkLengthMs(double))
            }
        } finally {
            quietBlink.floatValue = 0f // stopped mid-blink: don't freeze with the lids half shut
        }
    }

    /** Blends from the pose on screen to [target]'s, on the theme's effects spec. */
    suspend fun changeMood(
        target: MascotMood,
        motion: MascotMotion,
        spec: FiniteAnimationSpec<Float>,
        lifecycle: Lifecycle,
    ) {
        if (target == mood) return
        val now = withFrameMillis { it }
        from = shown
        time.longValue = now
        moodStart = now
        mood = target
        if (motion == MascotMotion.Still) {
            blend.snapTo(1f)
            return
        }
        blend.snapTo(0f)
        coroutineScope {
            launch { blend.animateTo(1f, spec) }
            // A Quiet mascot has no frame clock: run one just long enough for the
            // mood's own one-shot motion (the hop, the shake), then stop again. Like
            // the other clocks it runs only while STARTED; stopped early, it lands.
            if (motion == MascotMotion.Quiet) {
                while (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    val t = withFrameMillis { it }
                    time.longValue = t
                    if (t - now >= MOOD_MOTION_MS) break
                }
                time.longValue = now + MOOD_MOTION_MS
            }
        }
    }
}

private class OwlColors(
    val body: Color,
    val mask: Color,
    val disc: Color,
    val iris: Color,
    val pupil: Color,
    val shadow: Color,
)

@Composable
private fun owlColors(body: Color): OwlColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    return OwlColors(
        body = body,
        // Some branded schemes put primaryContainer right next to primary.
        mask = if (contrastRatio(body, scheme.primaryContainer) < MASK_MIN_CONTRAST) scheme.surfaceContainerHigh
        else scheme.primaryContainer,
        // Light discs with dark pupils in both themes.
        disc = if (dark) scheme.onSurface else scheme.surface,
        iris = scheme.tertiary,
        pupil = if (dark) scheme.surface else scheme.onSurface,
        shadow = scheme.onSurface,
    )
}

/** WCAG contrast ratio between two opaque colours (1..21). */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

private val SIDES = floatArrayOf(-1f, 1f)

/** A contact shadow that shrinks and fades as the mascot hops. */
private fun DrawScope.drawShadow(pose: MascotPose, color: Color) {
    val rx = 26f * (1 + pose.lift / 20f)
    drawOval(
        color = color,
        topLeft = Offset(50f - rx, 92.4f),
        size = Size(rx * 2, 5.2f),
        alpha = 0.12f * (1 + pose.lift / 12f),
    )
}

/** A quadratic stroke from [x0], [y0] through control [cx], [cy] to [x1], [y1]. */
private fun DrawScope.drawCurve(
    path: Path,
    x0: Float,
    y0: Float,
    cx: Float,
    cy: Float,
    x1: Float,
    y1: Float,
    color: Color,
    width: Float,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    path.reset()
    path.moveTo(x0, y0)
    path.quadraticTo(cx, cy, x1, y1)
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round), blendMode = blendMode)
}

/**
 * The 24/32dp glyph: the silhouette in [tint] and two pill eyes cut out of it. DstOut
 * reads only the paint's alpha, so the cut-outs take the colour of whatever is behind.
 */
private fun DrawScope.drawTwoTone(g: MascotGeometry, pose: MascotPose, tint: Color, scratch: Path) {
    drawPath(g.body, tint)
    val e = g.eyes
    val sc = e.scale * TWO_TONE_EYE_SCALE
    val w = 4.6f * sc
    val h = 11f * sc
    val line = 2.6f * sc
    val cut = BlendMode.DstOut
    for (side in SIDES) {
        val cx = e.x + side * e.spacing / 2 + pose.gazeX * 6f
        val cy = e.y + pose.gazeY * 4f
        when (pose.style) {
            EyeStyle.Arc -> drawCurve(scratch, cx - w, cy + 2, cx, cy - 5 * sc, cx + w, cy + 2, tint, line, cut)
            EyeStyle.Closed -> drawCurve(scratch, cx - w, cy - 1, cx, cy + 4 * sc, cx + w, cy - 1, tint, line, cut)
            EyeStyle.Open -> {
                val open = max(h * max(0.09f, (1 - pose.blink) * (1 - pose.squint * 0.4f)), w * 0.55f)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(cx - w / 2, cy - open / 2 + (h - open) * 0.25f),
                    size = Size(w, open),
                    cornerRadius = CornerRadius(min(w, open) / 2),
                    blendMode = cut,
                )
                if (pose.brows > 0.01f) {
                    drawLine(
                        color = tint,
                        start = Offset(cx - side * 5 * sc, cy - h / 2 - 3 * sc),
                        end = Offset(cx + side * 4 * sc, cy - h / 2 - 6 * sc),
                        strokeWidth = line,
                        cap = StrokeCap.Round,
                        alpha = pose.brows,
                        blendMode = cut,
                    )
                }
            }
        }
    }
}

/** The 96dp owl: body, face mask, eye discs with iris and pupil, beak and the logo's coin. */
private fun DrawScope.drawDetailedOwl(g: MascotGeometry, pose: MascotPose, c: OwlColors, scratch: Path) {
    drawPath(g.body, c.body)
    g.mask?.let { drawPath(it, c.mask) }
    val ox = pose.gazeX * OwlEye.GAZE_TRAVEL
    val oy = pose.gazeY * OwlEye.GAZE_TRAVEL
    val r = OwlEye.DISC_R
    val cy = OwlEye.Y
    for (cx in floatArrayOf(OwlEye.LEFT_X, OwlEye.RIGHT_X)) {
        when (pose.style) {
            EyeStyle.Arc -> drawCurve(scratch, cx - 8, cy + 3, cx, cy - 7, cx + 8, cy + 3, c.pupil, 3f)
            EyeStyle.Closed -> drawCurve(scratch, cx - 8, cy - 1, cx, cy + 6, cx + 8, cy - 1, c.pupil, 3f)
            EyeStyle.Open -> {
                // The lid clips the whole eye, iris and all, so a blink reads as a lid.
                val half = r * (1 - pose.blink) * (1 - pose.squint * 0.45f)
                if (half < 1.6f) {
                    drawCurve(scratch, cx - 9, cy + 1, cx, cy + 4, cx + 9, cy + 1, c.pupil, 3f)
                } else {
                    val top = cy - half + r * pose.squint * 0.3f
                    clipRect(cx - r - 1, top, cx + r + 1, top + 2 * half) {
                        drawCircle(c.disc, r, Offset(cx, cy))
                        drawCircle(c.iris, OwlEye.IRIS_R, Offset(cx + ox, cy + oy))
                        drawCircle(c.pupil, OwlEye.PUPIL_R, Offset(cx + ox, cy + oy))
                        drawCircle(c.disc, 1.4f, Offset(cx + ox - 2f, cy + oy - 2.2f))
                    }
                }
                if (pose.brows > 0.01f) {
                    val side = if (cx < 50f) -1f else 1f
                    drawLine(
                        color = c.pupil,
                        start = Offset(cx + side * 10, cy - 15),
                        end = Offset(cx - side * 4, cy - 19),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                        alpha = pose.brows,
                    )
                }
            }
        }
    }
    g.beak?.let { drawPath(it, c.pupil) }
    drawCircle(c.iris, 7f, Offset(50f, 81f))
    drawCircle(c.pupil, 4.6f, Offset(50f, 81f), alpha = 0.35f, style = Stroke(1.4f))
}
