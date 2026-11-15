package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
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
import androidx.compose.ui.input.pointer.pointerInput
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
 * roles on a frame clock, with a wandering gaze; other shapes take the tertiary
 * container roles. Either clock runs only while the mascot is composed, its lifecycle is
 * STARTED and animations are on; otherwise it holds a static pose. Time is read in the
 * draw phase only, so it never recomposes per frame.
 *
 * A new [shape] crossfades in on the theme's effects spec; a new [mood] blends from the
 * pose on screen. A small mascot that is [MascotMood.Thinking] runs a frame clock for as
 * long as it thinks: the owl's eyes drift and its head sways, a [MascotShape.Bubble]'s
 * eyes sweep along its lines.
 *
 * [look] points the open eyes somewhere (gaze -1..1, x toward line end, y down) until it
 * is null again. An [interactive] mascot answers a tap with a poke (a quick blink and a
 * wobble) and a drag by looking at the finger, back to centre once it lifts. Neither has
 * a haptic or a semantics node: the mascot never acts as a button of its own.
 */
@Composable
fun Mascot(
    size: Dp,
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Calm,
    shape: MascotShape = MascotShape.Owl,
    tint: Color = MaterialTheme.colorScheme.primary,
    look: Offset? = null,
    interactive: Boolean = false,
) {
    val detailed = size > Dimensions.Icon.large
    val motion = when {
        !rememberAnimationsEnabled() -> MascotMotion.Still
        detailed -> MascotMotion.Full
        else -> MascotMotion.Quiet
    }
    val state = remember { MascotState(mood, shape) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val thinking = mood == MascotMood.Thinking
    LaunchedEffect(motion, lifecycle, thinking) { state.runClock(motion, lifecycle, thinking) }
    SideEffect { state.look = look }
    val motionScheme = MaterialTheme.motionScheme
    val blendSpec = motionScheme.defaultEffectsSpec<Float>()
    val swapSpec = motionScheme.slowEffectsSpec<Float>()
    // A change plays out with the motion in effect when it arrives, not the one at the last change.
    val currentMotion by rememberUpdatedState(motion)
    LaunchedEffect(mood) { state.changeMood(mood, currentMotion, blendSpec, lifecycle) }
    LaunchedEffect(shape) { state.changeShape(shape, currentMotion, swapSpec) }

    val colors = if (detailed) mascotColors(tint) else null
    // The artwork is symmetric and never mirrored; only where it looks and leans is.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics {}
            .then(if (interactive) Modifier.mascotGestures(state, direction) else Modifier)
            // The two-tone glyph punches its eyes out, which needs a layer of its own.
            .then(if (detailed) Modifier else Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen })
            .drawWithCache {
                val current = state.shape
                val currentGeometry = current.geometry()
                val previous = state.previousShape
                val previousGeometry = previous?.geometry()
                val scratch = Path()
                val layer = Paint()
                val k = this.size.minDimension / BOX
                onDrawBehind {
                    val pose = state.pose(motion).let { it.copy(gazeX = it.gazeX * direction, roll = it.roll * direction) }
                    if (colors != null) scale(k, k, pivot = Offset.Zero) { drawShadow(pose, colors.shadow) }
                    // Each body fades in a layer of its own, so one's eye cut-outs never punch the other.
                    val fade = if (previousGeometry == null) 1f else state.swapProgress
                    if (previous != null && previousGeometry != null) {
                        withAlpha(1f - fade, layer) { drawShape(previous, previousGeometry, pose, k, colors, tint, scratch) }
                    }
                    withAlpha(fade, layer) { drawShape(current, currentGeometry, pose, k, colors, tint, scratch) }
                }
            }
    )
}

/** Tap to poke; drag to have the eyes follow the finger. The drag is consumed, so a list under it doesn't scroll. */
private fun Modifier.mascotGestures(state: MascotState, direction: Float): Modifier = this
    .pointerInput(state) { detectTapGestures(onTap = { state.poke() }) }
    .pointerInput(state, direction) {
        // From the middle of the face, in units of the mascot's size; x toward line end.
        fun gazeAt(p: Offset) = dragGaze(
            dx = (p.x / size.width - 0.5f) * direction,
            dy = p.y / size.height - OwlEye.Y / BOX,
        )
        detectDragGestures(
            onDragStart = { state.dragLook = gazeAt(it) },
            onDragEnd = { state.dragLook = null },
            onDragCancel = { state.dragLook = null },
            onDrag = { change, _ ->
                change.consume()
                state.dragLook = gazeAt(change.position)
            },
        )
    }

/** A thinking message bubble is reading: its eyes sweep left and right. */
private fun isSweeping(shape: MascotShape, mood: MascotMood) = shape == MascotShape.Bubble && mood == MascotMood.Thinking

private const val BOX = 100f

/** Pill eyes are drawn this much larger in the two-tone glyph, so they still read at 24dp. */
private const val TWO_TONE_EYE_SCALE = 1.5f

/** An object's markings sit this faintly over its body. */
private const val DETAIL_ALPHA = 0.32f

/** Below this contrast against the body, the face mask falls back to a neutral container. */
private const val MASK_MIN_CONTRAST = 1.6f

@Stable
private class MascotState(initialMood: MascotMood, initialShape: MascotShape) {
    var mood by mutableStateOf(initialMood)
        private set
    var shape by mutableStateOf(initialShape)
        private set

    /** The shape fading out while [shape] fades in, or null. */
    var previousShape by mutableStateOf<MascotShape?>(null)
        private set
    private val swap = Animatable(1f)

    /** How far [shape] has faded in over [previousShape], 0..1. Read it in the draw phase. */
    val swapProgress: Float get() = swap.value
    private var moodStart = Long.MIN_VALUE / 4
    private var from: MascotPose? = null
    private val blend = Animatable(1f)
    private val time = mutableLongStateOf(0L)
    private val quietBlink = mutableFloatStateOf(0f)
    private val seed = Random.nextFloat() * 10f

    /** The pose last drawn: where a mood change blends from. */
    private var shown = MascotPose()

    /** Where the caller points the eyes, and where a finger dragging on the mascot is. */
    var look by mutableStateOf<Offset?>(null)
    var dragLook by mutableStateOf<Offset?>(null)

    /** How far the eyes follow [dragLook] or [look] (0..1), and the point they've reached. */
    private var attention = 0f
    private var followed = Offset.Zero
    private var pokeStart = Long.MIN_VALUE / 4

    /** Called from the draw phase, so the state reads here invalidate the drawing only. */
    fun pose(motion: MascotMotion): MascotPose {
        val t = time.longValue
        var pose = moodPose(mood, t, t - moodStart, seed, motion, sweep = isSweeping(shape, mood))
        if (motion == MascotMotion.Quiet) pose = pose.copy(blink = max(pose.blink, quietBlink.floatValue))
        val w = blend.value
        from?.let { if (w < 1f) pose = blendPose(it, pose, w) }
        val target = dragLook ?: look
        if (motion == MascotMotion.Still) {
            // No clock to ease on: the eyes are simply where they're pointed.
            if (target != null) pose = lookAt(pose, target, 1f)
        } else {
            pose = poked(lookAt(pose, followed, attention), t - pokeStart)
        }
        shown = pose
        return pose
    }

    /** A tap: a quick blink and a damped wobble. Only a mascot on a frame clock shows it. */
    fun poke() {
        pokeStart = time.longValue
    }

    /** [thinking]: a Quiet mascot also needs frames, for as long as it thinks. */
    suspend fun runClock(motion: MascotMotion, lifecycle: Lifecycle, thinking: Boolean) {
        when (motion) {
            MascotMotion.Still -> quietBlink.floatValue = 0f
            MascotMotion.Quiet -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (thinking) launch { tick() }
                blinkNowAndThen()
            }
            MascotMotion.Full -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { tick() }
        }
    }

    private suspend fun tick() {
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last >= 0) follow(now - last)
                last = now
                time.longValue = now
            }
        }
    }

    /** Eases the eyes toward where they're pointed, and back to their own gaze once let go. */
    private fun follow(dtMs: Long) {
        val target = dragLook ?: look
        attention = ease(attention, if (target != null) 1f else 0f, dtMs, ATTENTION_MS)
        if (target != null) {
            followed = Offset(ease(followed.x, target.x, dtMs, FOLLOW_MS), ease(followed.y, target.y, dtMs, FOLLOW_MS))
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

    /** Crossfades from the shape on screen to [target], on the theme's effects spec. */
    suspend fun changeShape(target: MascotShape, motion: MascotMotion, spec: FiniteAnimationSpec<Float>) {
        if (target == shape) return
        previousShape = shape
        shape = target
        if (motion != MascotMotion.Still) {
            swap.snapTo(0f)
            swap.animateTo(1f, spec)
        } else {
            swap.snapTo(1f)
        }
        previousShape = null
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

private class MascotColors(
    val body: Color,
    val mask: Color,
    val disc: Color,
    val iris: Color,
    val pupil: Color,
    val shadow: Color,
    /** The other shapes: a container body with its ink for eyes and markings. */
    val objectBody: Color,
    val objectInk: Color,
)

@Composable
private fun mascotColors(body: Color): MascotColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    return MascotColors(
        body = body,
        // Some branded schemes put primaryContainer right next to primary.
        mask = if (contrastRatio(body, scheme.primaryContainer) < MASK_MIN_CONTRAST) scheme.surfaceContainerHigh
        else scheme.primaryContainer,
        // Light discs with dark pupils in both themes.
        disc = if (dark) scheme.onSurface else scheme.surface,
        iris = scheme.tertiary,
        pupil = if (dark) scheme.surface else scheme.onSurface,
        shadow = scheme.onSurface,
        objectBody = scheme.tertiaryContainer,
        objectInk = scheme.onTertiaryContainer,
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

/** Draws [content] at [alpha] in a layer of its own: nothing at 0, directly at 1. */
private inline fun DrawScope.withAlpha(alpha: Float, paint: Paint, content: DrawScope.() -> Unit) {
    when {
        alpha >= 1f -> content()
        alpha > 0f -> {
            paint.alpha = alpha
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
            content()
            drawContext.canvas.restore()
        }
    }
}

/** One shape in the pose, scaled from the 0..100 box: two-tone without [colors], detailed with. */
private fun DrawScope.drawShape(
    shape: MascotShape,
    g: MascotGeometry,
    pose: MascotPose,
    k: Float,
    colors: MascotColors?,
    tint: Color,
    scratch: Path,
) {
    scale(k, k, pivot = Offset.Zero) {
        translate(top = pose.lift) {
            rotate(pose.roll, pivot = g.pivot) {
                when {
                    colors == null -> drawTwoTone(g, pose, tint, scratch)
                    shape == MascotShape.Owl -> drawDetailedOwl(g, pose, colors, scratch)
                    else -> drawDetailedObject(g, pose, colors, scratch)
                }
            }
        }
    }
}

/**
 * The 24/32dp glyph: the silhouette in [tint] and two pill eyes cut out of it. DstOut
 * reads only the paint's alpha, so the cut-outs take the colour of whatever is behind.
 * No markings: at this size they would only blur the outline.
 */
private fun DrawScope.drawTwoTone(g: MascotGeometry, pose: MascotPose, tint: Color, scratch: Path) {
    drawPath(g.body, tint)
    drawPillEyes(g.eyes, TWO_TONE_EYE_SCALE, pose, tint, scratch, BlendMode.DstOut)
}

/** A shape other than the owl, at the detailed size: container body, faint markings, ink eyes. */
private fun DrawScope.drawDetailedObject(g: MascotGeometry, pose: MascotPose, c: MascotColors, scratch: Path) {
    drawPath(g.body, c.objectBody)
    g.details?.let { drawPath(it, c.objectInk, alpha = DETAIL_ALPHA) }
    drawPillEyes(g.eyes, 1f, pose, c.objectInk, scratch, DrawScope.DefaultBlendMode)
}

/** Two pill eyes at [e], [enlarge]d for small sizes, with `^` arcs, closed lids and brows. */
private fun DrawScope.drawPillEyes(
    e: EyeSpec,
    enlarge: Float,
    pose: MascotPose,
    color: Color,
    scratch: Path,
    blendMode: BlendMode,
) {
    val sc = e.scale * enlarge
    val w = 4.6f * sc
    val h = 11f * sc
    val line = 2.6f * sc
    for (side in SIDES) {
        val cx = e.x + side * e.spacing / 2 + pose.gazeX * 6f
        val cy = e.y + pose.gazeY * 4f
        when (pose.style) {
            EyeStyle.Arc -> drawCurve(scratch, cx - w, cy + 2, cx, cy - 5 * sc, cx + w, cy + 2, color, line, blendMode)
            EyeStyle.Closed -> drawCurve(scratch, cx - w, cy - 1, cx, cy + 4 * sc, cx + w, cy - 1, color, line, blendMode)
            EyeStyle.Open -> {
                val open = max(h * max(0.09f, (1 - pose.blink) * (1 - pose.squint * 0.4f)), w * 0.55f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - w / 2, cy - open / 2 + (h - open) * 0.25f),
                    size = Size(w, open),
                    cornerRadius = CornerRadius(min(w, open) / 2),
                    blendMode = blendMode,
                )
                if (pose.brows > 0.01f) {
                    drawLine(
                        color = color,
                        start = Offset(cx - side * 5 * sc, cy - h / 2 - 3 * sc),
                        end = Offset(cx + side * 4 * sc, cy - h / 2 - 6 * sc),
                        strokeWidth = line,
                        cap = StrokeCap.Round,
                        alpha = pose.brows,
                        blendMode = blendMode,
                    )
                }
            }
        }
    }
}

/** The 96dp owl: body, face mask, eye discs with iris and pupil, beak and the logo's coin. */
private fun DrawScope.drawDetailedOwl(g: MascotGeometry, pose: MascotPose, c: MascotColors, scratch: Path) {
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
