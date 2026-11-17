package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import androidx.compose.ui.graphics.lerp as lerpColor

/**
 * The PennyWise mascot: a [shape] with eyes that blink and look around, showing a [mood].
 * It is decoration and stays silent to accessibility — give the slot around it its name.
 *
 * Up to 32dp it draws as a two-tone glyph: a [tint] silhouette with the eyes cut out, so
 * whatever is behind it (a nav pill, a bar) shows through. Larger, it draws the detailed
 * owl from theme roles; other shapes take the tertiary container roles.
 *
 * How it moves doesn't depend on its size. By default it blinks on a delay-driven clock
 * and draws no frames in between: frames run only while something plays out (a mood or
 * shape change, a poke, a finger on it and the eyes settling after) and stop once the
 * pose has settled. [alive] keeps a frame clock running instead, with a wandering gaze
 * and breathing while asleep: for the one hero a screen is about (the empty chat), never
 * for an empty state or a grid. A mascot that is [MascotMood.Thinking] runs a frame clock
 * for as long as it thinks: the owl's eyes drift and its head sways, a
 * [MascotShape.Bubble]'s eyes sweep along its lines. Any clock runs only while the mascot
 * is composed, its lifecycle is STARTED and animations are on; otherwise it holds a
 * static pose. Time is read in the draw phase only, so it never recomposes per frame.
 *
 * A new [shape] is one body bending into the next on the theme's spatial spring, never two
 * bodies faded over each other, so the glyph keeps its [tint] whole the whole way (see
 * MascotMorph.kt); then it plays the shape's arrival (MascotArrival.kt). A new [mood]
 * blends from the pose on screen.
 *
 * [value] is the shape's figure: a [MascotShape.Pie]'s slice share, how full a
 * [MascotShape.Jar] is or how much of a [MascotShape.Gauge] is used (0..1), or which way a
 * [MascotShape.Bars] went (-1..1), which its eyes follow: up when it rose, down when it fell.
 *
 * [look] points the open eyes somewhere (gaze -1..1, x toward line end, y down) until it
 * is null again. An [interactive] mascot answers a tap with a poke (a quick blink and a
 * wobble; a [MascotShape.Bell] also swings) and a drag by looking at the finger, back to
 * centre once it lifts. The drag is never consumed, so a list under the mascot still
 * scrolls. Neither has a haptic or a semantics node: the mascot never acts as a button.
 */
@Composable
fun Mascot(
    size: Dp,
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Calm,
    shape: MascotShape = MascotShape.Owl,
    tint: Color = MaterialTheme.colorScheme.primary,
    value: Float = 0f,
    look: Offset? = null,
    interactive: Boolean = false,
    alive: Boolean = false,
) {
    val detailed = size > Dimensions.Icon.large
    val motion = when {
        !rememberAnimationsEnabled() -> MascotMotion.Still
        alive -> MascotMotion.Full
        else -> MascotMotion.Quiet
    }
    val state = remember { MascotState(mood, shape) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val thinking = mood == MascotMood.Thinking
    LaunchedEffect(motion, lifecycle, thinking) { state.runClock(motion, lifecycle, thinking) }
    SideEffect {
        state.look = look
        state.value = value
    }
    val motionScheme = MaterialTheme.motionScheme
    val blendSpec = motionScheme.defaultEffectsSpec<Float>()
    // A shape change is spatial, so a spring. The whole figure becomes another, so it takes
    // the panel's spring rather than a small part's (fast is over in a frame or two at 24dp),
    // and the hero the full-screen one, as it travels farther.
    val morphSpec = if (detailed) motionScheme.slowSpatialSpec<Float>() else motionScheme.defaultSpatialSpec()
    // A change plays out with the motion in effect when it arrives, not the one at the last change.
    val currentMotion by rememberUpdatedState(motion)
    LaunchedEffect(mood) { state.changeMood(mood, currentMotion, blendSpec, lifecycle) }
    LaunchedEffect(shape) { state.changeShape(shape, currentMotion, morphSpec) }

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
                val geometry = current.geometry(state.value)
                val from = state.morphFrom
                // Only a running morph needs the outlines as profiles.
                val morph = from?.let {
                    MorphDrawing(
                        from = it,
                        fromGeometry = it.shape.geometry(state.value),
                        target = state.profileOf(current, state.value),
                    )
                }
                val scratch = Path()
                val outline = Path()
                val layer = Paint()
                val k = this.size.minDimension / BOX
                onDrawBehind {
                    val pose = state.pose(motion).let { it.copy(gazeX = it.gazeX * direction, roll = it.roll * direction) }
                    if (colors != null) scale(k, k, pivot = Offset.Zero) { drawShadow(pose, colors.shadow) }
                    val decor = Decor(state.arrivedShape, state.arrivalProgress, tint, colors, scratch, layer)
                    if (morph == null) {
                        drawShape(current, geometry, pose, k, decor)
                    } else {
                        drawMorph(morph, current, geometry, state.morphProgress, pose, k, decor, outline)
                    }
                }
            }
    )
}

/**
 * Tap to poke; drag to have the eyes follow the finger. A drag only watches the pointer
 * and consumes nothing, so a list under the mascot scrolls as it would without it; a tap
 * is the mascot's own, so nothing behind it takes it too.
 */
private fun Modifier.mascotGestures(state: MascotState, direction: Float): Modifier = pointerInput(state, direction) {
    // From the middle of the face, in units of the mascot's size; x toward line end.
    fun gazeAt(p: Offset) = dragGaze(
        dx = (p.x / size.width - 0.5f) * direction,
        dy = p.y / size.height - OwlEye.Y / BOX,
    )
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Past the touch slop it's a drag, and stays one: only then do the eyes follow.
        var dragging = false
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!dragging) {
                        change.consume()
                        state.poke()
                    }
                    break
                }
                dragging = dragging || (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                if (dragging) state.dragLook = gazeAt(change.position)
            }
        } finally {
            state.dragLook = null
        }
    }
}

/** A thinking message bubble is reading: its eyes sweep left and right. */
private fun isSweeping(shape: MascotShape, mood: MascotMood) = shape == MascotShape.Bubble && mood == MascotMood.Thinking

private const val BOX = 100f

/** Pill eyes are drawn this much larger in the two-tone glyph, so they still read at 24dp. */
private const val TWO_TONE_EYE_SCALE = 1.5f

/** A pie's profile follows its share; past this many cached, they're rebuilt as needed. */
private const val MAX_PROFILES = 32

/** Below this contrast against the body, the face mask falls back to a neutral container. */
private const val MASK_MIN_CONTRAST = 1.6f

@Stable
private class MascotState(initialMood: MascotMood, initialShape: MascotShape) {
    var mood by mutableStateOf(initialMood)
        private set
    var shape by mutableStateOf(initialShape)
        private set

    /** Where the running morph into [shape] began, or null once it has landed. */
    var morphFrom by mutableStateOf<MorphFrom?>(null)
        private set
    private val morph = Animatable(1f)

    /** How far the outline has bent from [morphFrom] into [shape]; a spring passes 1 and comes back. Read it in the draw phase. */
    val morphProgress: Float get() = morph.value

    /** The shape whose arrival last played (see MascotArrival.kt), or null. */
    var arrivedShape by mutableStateOf<MascotShape?>(null)
        private set
    private val arrival = Animatable(1f)

    /** How far that arrival has played, 0..1. Read it in the draw phase. */
    val arrivalProgress: Float get() = arrival.value

    private val profiles = HashMap<Pair<MascotShape, Float>, MascotProfile>()

    /** [shape]'s outline as a profile, built once. Only a pie's outline depends on its figure. */
    fun profileOf(shape: MascotShape, value: Float): MascotProfile {
        // A dragged pie slider would otherwise keep every share it passed through.
        if (profiles.size > MAX_PROFILES) profiles.clear()
        return profiles.getOrPut(shape to if (shape == MascotShape.Pie) value else 0f) { shape.geometry(value).profile() }
    }
    private var moodStart = Long.MIN_VALUE / 4
    private var from: MascotPose? = null
    private val blend = Animatable(1f)
    private val time = mutableLongStateOf(0L)
    private val quietBlink = mutableFloatStateOf(0f)
    private val seed = Random.nextFloat() * 10f

    /** Asks a Quiet mascot's idle clock for frames: a poke, a drag, a new look. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The pose last drawn: where a mood change blends from. */
    private var shown = MascotPose()

    /** The shape's figure (see [Mascot]). */
    var value by mutableFloatStateOf(0f)

    private val lookState = mutableStateOf<Offset?>(null)
    private val dragLookState = mutableStateOf<Offset?>(null)

    /** Where the caller points the eyes. */
    var look: Offset?
        get() = lookState.value
        set(v) {
            if (v != lookState.value) {
                lookState.value = v
                wake.trySend(Unit)
            }
        }

    /** Where a finger dragging on the mascot is, or null once it lifts. */
    var dragLook: Offset?
        get() = dragLookState.value
        set(v) {
            if (v != dragLookState.value) {
                dragLookState.value = v
                wake.trySend(Unit)
            }
        }

    /** How far the eyes follow [dragLook] or [look], and the point they've reached. */
    private var follow = Follow()
    private var pokeStart = Long.MIN_VALUE / 4

    /** A tap waiting for the next frame, which starts its poke. */
    private var pokeRequested = false

    /** Called from the draw phase, so the state reads here invalidate the drawing only. */
    fun pose(motion: MascotMotion): MascotPose {
        val t = time.longValue
        var pose = moodPose(mood, t, t - moodStart, seed, motion, sweep = isSweeping(shape, mood))
        if (motion == MascotMotion.Quiet) pose = pose.copy(blink = max(pose.blink, quietBlink.floatValue))
        if (shape == MascotShape.Bars && value != 0f) pose = lookAt(pose, trendGaze(value), 1f)
        val arrivedAt = arrivedShape
        if (arrivedAt == shape && morphFrom == null && arrival.value < 1f) {
            pose = arrived(pose, shape, (arrival.value * ARRIVAL_MS).toLong())
        }
        val w = blend.value
        from?.let { if (w < 1f) pose = blendPose(it, pose, w) }
        val target = dragLook ?: look
        if (motion == MascotMotion.Still) {
            // No clock to ease on: the eyes are simply where they're pointed.
            if (target != null) pose = lookAt(pose, target, 1f)
        } else {
            pose = poked(lookAt(pose, follow.at, follow.attention), t - pokeStart)
            if (shape == MascotShape.Bell) pose = rung(pose, t - pokeStart)
        }
        shown = pose
        return pose
    }

    /** A tap: a quick blink and a damped wobble (a bell swings). Only a mascot with a clock shows it. */
    fun poke() {
        pokeRequested = true
        wake.trySend(Unit)
    }

    /** [thinking]: a Quiet mascot runs a frame clock for as long as it thinks. */
    suspend fun runClock(motion: MascotMotion, lifecycle: Lifecycle, thinking: Boolean) {
        when (motion) {
            MascotMotion.Still -> {
                quietBlink.floatValue = 0f
                pokeRequested = false
            }
            MascotMotion.Quiet -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { if (thinking) tick() else tickWhenNeeded() }
                blinkNowAndThen()
            }
            MascotMotion.Full -> lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { tick() }
        }
    }

    /** Frames for as long as the clock runs. */
    private suspend fun tick() {
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                frame(now, if (last >= 0) now - last else 0L)
                last = now
            }
        }
    }

    /**
     * A Quiet mascot's frames: a burst while something plays out ([needsFrames]), then
     * none until [wake]. The first burst also lands whatever a stop cut short.
     */
    private suspend fun tickWhenNeeded() {
        while (true) {
            var last = -1L
            do {
                val busy = withFrameMillis { now ->
                    frame(now, if (last >= 0) now - last else 0L)
                    last = now
                    val target = dragLook ?: look
                    needsFrames(dragLook != null, now - pokeStart, shape, follow, target).also { busy ->
                        if (!busy) follow = follow.settle(target)
                    }
                }
            } while (busy)
            wake.receive()
        }
    }

    /** One frame: starts a waiting poke and eases the eyes toward where they're pointed. */
    private fun frame(now: Long, dtMs: Long) {
        if (pokeRequested) {
            pokeRequested = false
            pokeStart = now
        }
        follow = follow.step(dragLook ?: look, dtMs)
        time.longValue = now
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

    /**
     * Bends the outline on screen into [target] on [spec], then plays [target]'s arrival.
     * A change that lands mid-morph starts from the outline as it is, so nothing jumps.
     */
    suspend fun changeShape(target: MascotShape, motion: MascotMotion, spec: FiniteAnimationSpec<Float>) {
        if (target == shape) return
        if (motion == MascotMotion.Still) {
            arrival.snapTo(1f)
            morphFrom = null
            arrivedShape = null
            shape = target
            morph.snapTo(1f)
            return
        }
        // Before the arrival stops: a check half stamped is caught half stamped.
        morphFrom = outlineOnScreen()
        arrival.snapTo(1f)
        shape = target
        morph.snapTo(0f)
        morph.animateTo(1f, spec)
        morphFrom = null
        arrivedShape = target
        // Only as long as the arrival moves: a settled shape asks for no more frames.
        val end = arrivalEnd(target)
        if (end > 0f) {
            arrival.snapTo(0f)
            arrival.animateTo(end, tween((end * ARRIVAL_MS).toInt(), easing = LinearEasing))
        }
    }

    /** The outline drawn right now: the shape, or the morph where it has got to. */
    private fun outlineOnScreen(): MorphFrom {
        val g = shape.geometry(value)
        val here = profileOf(shape, value)
        val stamped = if (arrivedShape == MascotShape.Receipt && shape == MascotShape.Receipt) stampAlpha(arrival.value) else 0f
        val from = morphFrom ?: return MorphFrom(here, g.eyes, g.pivot, shape, stamp = stamped)
        val p = morph.value
        val w = p.coerceIn(0f, 1f)
        val early = w < 0.5f
        return MorphFrom(
            profile = blendProfiles(from.profile, here, p),
            eyes = lerpEyes(from.eyes, g.eyes, p),
            pivot = lerp(from.pivot, g.pivot, p),
            // The face that shows most, and how much of it, as drawMorph draws them.
            shape = if (early) from.shape else shape,
            faceAlpha = if (early) fromFaceAlpha(from, w) else fadeAt(1f - FACE_FADE, 1f, w),
            owl = lerp(from.owl, owlness(shape), w),
            stamp = fromStampAlpha(from, w),
            squint = morphEyes(from, p),
        )
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

/** Past the 0..100 box on every side, so a layer in box units holds a tilted, hopping pose. */
private val LAYER_BOUNDS = Rect(-BOX, -BOX, 2 * BOX, 2 * BOX)

/** Draws [content] at [alpha] in a layer of its own, in box units: nothing at 0, directly at 1. */
private inline fun DrawScope.withAlpha(alpha: Float, paint: Paint, content: DrawScope.() -> Unit) {
    when {
        alpha >= 1f -> content()
        alpha > 0f -> {
            paint.alpha = alpha
            drawContext.canvas.saveLayer(LAYER_BOUNDS, paint)
            content()
            drawContext.canvas.restore()
        }
    }
}

/** What the drawing needs besides the shape and pose: the arrival under way, and how to paint. */
private class Decor(
    /** The shape whose arrival last played, and how far it has got (0..1). */
    val arrived: MascotShape?,
    val t: Float,
    val tint: Color,
    /** Null for the two-tone glyph. */
    val colors: MascotColors?,
    val scratch: Path,
    val layer: Paint,
)

/** A morph under way: where it began, that shape's geometry (for its fading face), and where it's headed. */
private class MorphDrawing(val from: MorphFrom, val fromGeometry: MascotGeometry, val target: MascotProfile)

/** Draws [content] in the 0..100 box, scaled by [k], hopped and tilted about [pivot] as [pose] says. */
private inline fun DrawScope.posed(pose: MascotPose, pivot: Offset, k: Float, crossinline content: DrawScope.() -> Unit) {
    scale(k, k, pivot = Offset.Zero) {
        translate(top = pose.lift) {
            rotate(pose.roll, pivot = pivot) { content() }
        }
    }
}

/** One shape at rest in the pose: two-tone without colours, detailed with, and its arrival over it. */
private fun DrawScope.drawShape(shape: MascotShape, g: MascotGeometry, pose: MascotPose, k: Float, d: Decor) {
    posed(pose, g.pivot, k) {
        val c = d.colors
        when {
            c == null -> drawTwoTone(g, pose, d.tint, d.scratch)
            shape == MascotShape.Owl -> {
                drawPath(g.body, c.body)
                drawFace(shape, g, pose, c, d.scratch)
            }
            else -> {
                drawPath(g.body, c.objectBody)
                drawFace(shape, g, pose, c, d.scratch)
                drawPillEyes(g.eyes, 1f, pose, c.objectInk, d.scratch, DrawScope.DefaultBlendMode)
            }
        }
        if (d.arrived == shape) drawArrival(shape, g, d)
    }
}

/**
 * The outline [progress] of the way from where the morph began to [shape]: one body, in
 * one colour, so the glyph never thins into two half-faded bodies. The eyes slide to
 * their new place, narrowing on the way. At the detailed size the old face fades out
 * early and the new one in late, inside the body, and the colour follows the body.
 */
private fun DrawScope.drawMorph(
    m: MorphDrawing,
    shape: MascotShape,
    g: MascotGeometry,
    progress: Float,
    pose: MascotPose,
    k: Float,
    d: Decor,
    outline: Path,
) {
    val w = progress.coerceIn(0f, 1f)
    val squinting = pose.copy(blink = max(pose.blink, morphEyes(m.from, progress)))
    val eyes = lerpEyes(m.from.eyes, g.eyes, progress)
    val from = m.from.shape
    posed(pose, lerp(m.from.pivot, g.pivot, progress), k) {
        val body = blendProfiles(m.from.profile, m.target, progress).toPath(outline)
        val c = d.colors
        if (c == null) {
            drawPath(body, d.tint)
            drawPillEyes(eyes, TWO_TONE_EYE_SCALE, squinting, d.tint, d.scratch, BlendMode.DstOut)
            drawStamp(fromStampAlpha(m.from, w), d.tint, TWO_TONE_STAMP, BlendMode.DstOut, d.scratch)
        } else {
            // Owl-ness on screen: from where the morph began to the new shape's.
            val owl = lerp(m.from.owl, owlness(shape), w)
            drawPath(body, lerpColor(c.objectBody, c.body, owl))
            val fromFace = fromFaceAlpha(m.from, w)
            clipPath(body) {
                withAlpha(fromFace, d.layer) { drawFace(from, m.fromGeometry, pose, c, d.scratch) }
                withAlpha(fadeAt(1f - FACE_FADE, 1f, w), d.layer) { drawFace(shape, g, pose, c, d.scratch) }
            }
            // The owl looks out of its disc eyes; while it's mid-morph, pill eyes stand in.
            val eyesIn = if (from == MascotShape.Owl) 1f - fromFace else 1f
            val eyesOut = if (shape == MascotShape.Owl) 1f - fadeAt(1f - FACE_FADE, 1f, w) else 1f
            withAlpha(min(eyesIn, eyesOut), d.layer) {
                drawPillEyes(eyes, 1f, squinting, lerpColor(c.objectInk, c.pupil, owl), d.scratch, DrawScope.DefaultBlendMode)
            }
            clipPath(body) { drawStamp(fromStampAlpha(m.from, w), c.objectInk, 1f, DrawScope.DefaultBlendMode, d.scratch) }
        }
    }
}

/** The part of a morph over which a face fades, at each end. */
private const val FACE_FADE = 0.3f

/** How much of the old face shows [w] into a morph: what showed when it began, fading early. */
private fun fromFaceAlpha(from: MorphFrom, w: Float) = from.faceAlpha * (1f - fadeAt(0f, FACE_FADE, w))

/** How shut the eyes are [progress] into a morph: narrowing through it, from however shut they were. */
private fun morphEyes(from: MorphFrom, progress: Float) =
    max(morphSquint(progress), from.squint * (1f - progress.coerceIn(0f, 1f)))

private fun fadeAt(from: Float, to: Float, x: Float): Float {
    val c = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

/** How much of the receipt's check shows [w] into a morph away from it: what showed, fading early. */
private fun fromStampAlpha(from: MorphFrom, w: Float) = from.stamp * (1f - fadeAt(0f, FACE_FADE, w))

/**
 * The 24/32dp glyph: the silhouette in [tint] and two pill eyes cut out of it. DstOut
 * reads only the paint's alpha, so the cut-outs take the colour of whatever is behind.
 * No markings: at this size they would only blur the outline.
 */
private fun DrawScope.drawTwoTone(g: MascotGeometry, pose: MascotPose, tint: Color, scratch: Path) {
    drawPath(g.body, tint)
    drawPillEyes(g.eyes, TWO_TONE_EYE_SCALE, pose, tint, scratch, BlendMode.DstOut)
}

/** What sits on a body at the detailed size: the owl's whole face, or an object's faint markings. Not an object's eyes. */
private fun DrawScope.drawFace(shape: MascotShape, g: MascotGeometry, pose: MascotPose, c: MascotColors, scratch: Path) {
    if (shape == MascotShape.Owl) drawOwlFace(g, pose, c, scratch)
    else g.details?.let { drawPath(it, c.objectInk, alpha = g.detailAlpha) }
}

/** A shape's arrival (MascotArrival.kt), over its body: in its ink when detailed, cut out or in the tint as a glyph. */
private fun DrawScope.drawArrival(shape: MascotShape, g: MascotGeometry, d: Decor) {
    val c = d.colors
    val cut = if (c == null) BlendMode.DstOut else DrawScope.DefaultBlendMode
    when (shape) {
        MascotShape.Receipt -> drawStamp(stampAlpha(d.t), c?.objectInk ?: d.tint, if (c == null) TWO_TONE_STAMP else 1f, cut, d.scratch)
        MascotShape.Coin -> if (d.t < 1f) {
            coinShineX(d.t)?.let { x ->
                clipPath(g.body) {
                    rotate(SHINE_TILT, pivot = Offset(x, 52f)) {
                        drawRect(
                            color = c?.disc ?: d.tint,
                            topLeft = Offset(x - SHINE_WIDTH / 2, -BOX / 2),
                            size = Size(SHINE_WIDTH, BOX * 2),
                            alpha = SHINE_ALPHA,
                            blendMode = cut,
                        )
                    }
                }
            }
            // Outside the coin: drawn in the tint, never cut.
            for (s in COIN_SPARKLES) {
                val a = sparkleAlpha(s, d.t)
                if (a > 0f) drawSparkle(s.at, s.radius, c?.iris ?: d.tint, a, d.scratch)
            }
        }
        MascotShape.Jar -> if (d.t < 1f) {
            jarCoin(d.t)?.let { drawCircle(c?.iris ?: d.tint, JAR_COIN_R, it, alpha = jarCoinAlpha(d.t)) }
        }
        else -> Unit
    }
}

/** The receipt's check: a ring with a tick in its lower corner, [enlarge]d for the glyph. */
private fun DrawScope.drawStamp(alpha: Float, color: Color, enlarge: Float, blendMode: BlendMode, scratch: Path) {
    if (alpha <= 0f) return
    val line = STAMP_LINE * enlarge
    val center = Offset(64f, 65f)
    val r = STAMP_R + (enlarge - 1f) * 1.5f
    drawCircle(color, r, center, alpha = alpha, style = Stroke(line), blendMode = blendMode)
    scratch.reset()
    scratch.moveTo(center.x - r * 0.5f, center.y + r * 0.02f)
    scratch.lineTo(center.x - r * 0.12f, center.y + r * 0.38f)
    scratch.lineTo(center.x + r * 0.5f, center.y - r * 0.38f)
    drawPath(scratch, color, alpha = alpha, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blendMode)
}

private const val STAMP_R = 7.5f
private const val STAMP_LINE = 2.2f
private const val TWO_TONE_STAMP = 1.5f
private const val SHINE_TILT = 20f
private const val SHINE_WIDTH = 9f
private const val SHINE_ALPHA = 0.55f

/** A four-pointed sparkle at [at], [r] to each point. */
private fun DrawScope.drawSparkle(at: Offset, r: Float, color: Color, alpha: Float, scratch: Path) {
    scratch.reset()
    scratch.moveTo(at.x, at.y - r)
    scratch.quadraticTo(at.x, at.y, at.x + r, at.y)
    scratch.quadraticTo(at.x, at.y, at.x, at.y + r)
    scratch.quadraticTo(at.x, at.y, at.x - r, at.y)
    scratch.quadraticTo(at.x, at.y, at.x, at.y - r)
    scratch.close()
    drawPath(scratch, color, alpha = alpha)
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

/** The 96dp owl's face: mask, eye discs with iris and pupil, beak and the logo's coin. */
private fun DrawScope.drawOwlFace(g: MascotGeometry, pose: MascotPose, c: MascotColors, scratch: Path) {
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
