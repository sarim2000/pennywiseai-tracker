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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
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
 * The owl is line art in [tint]: eye rings, ear tufts and a beak, with pill eyes in the
 * rings. Up to 32dp the other shapes draw as a two-tone glyph: a [tint] silhouette with
 * the eyes cut out, so whatever is behind it (a nav pill, a bar) shows through. Larger,
 * they take the tertiary container roles. Pass the host's
 * [containerColor] with its matching [tint] to keep the silhouette legible through a morph.
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
    containerColor: Color = Color.Unspecified,
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
        state.setFigure(value, shape)
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
    LaunchedEffect(shape) { state.changeShape(shape, { currentMotion }, morphSpec) }

    val colors = if (detailed) mascotColors(tint, containerColor) else null
    // The artwork is symmetric and never mirrored; only where it looks and leans is.
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics {}
            .then(if (interactive) Modifier.mascotGestures(state, direction) else Modifier)
            // The two-tone glyph punches its eyes out, and the owl does at every size, which
            // needs a layer of its own.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val current = state.shape
                val geometry = current.geometry(state.value)
                val from = state.morphFrom
                // Only a running morph needs the outlines as profiles.
                val morph = from?.let {
                    MorphDrawing(
                        from = it,
                        fromGeometry = it.shape.geometry(it.value),
                        target = state.profileOf(current, state.value),
                        beak = owlBeak(),
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
 * The owl at rest in [mood], as [Mascot] draws it above [Dimensions.Icon.large] with
 * animations off, filling this scope's square: for where Compose can't run, such as a
 * widget's bitmap. Its eyes, rings and beak are cut out, so draw it on a layer of its own
 * (a fresh bitmap is one). No contact shadow: that's for a mascot standing on a screen.
 * [rtl] turns its look and lean toward the other side, as [Mascot] does.
 */
internal fun DrawScope.drawStaticOwl(mood: MascotMood, tint: Color, rtl: Boolean) {
    val direction = if (rtl) -1f else 1f
    val pose = moodPose(mood, 0L, MOOD_MOTION_MS, 0f, MascotMotion.Still)
        .let { it.copy(gazeX = it.gazeX * direction, roll = it.roll * direction) }
    // The owl reads only its body colour; the other roles are for objects.
    val colors = MascotColors(tint, tint, tint, tint, tint, tint)
    val decor = Decor(null, 1f, tint, colors, Path(), Paint())
    drawShape(MascotShape.Owl, MascotShape.Owl.geometry(), pose, size.minDimension / BOX, decor)
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

/** The owl's eyes grow less: any bigger and a full look would run them into the rings cut around them. */
private const val OWL_TWO_TONE_EYE_SCALE = 1.15f

/** A pie's profile follows its share; past this many cached, they're rebuilt as needed. */
private const val MAX_PROFILES = 32

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

    /** [shape]'s figure (see [Mascot]). */
    var value by mutableFloatStateOf(0f)
        private set

    /** The figure last passed in: [value], or the one that came with a shape still to morph in. */
    private var latestValue = 0f

    /**
     * Takes the figure the caller passed with [forShape]. One that comes with a new shape
     * waits for the morph to start, so the old shape keeps its own figure as it bends
     * away: a gauge's needle, a pie's slice, Bars' gaze.
     */
    fun setFigure(figure: Float, forShape: MascotShape) {
        latestValue = figure
        if (forShape == shape) value = figure
    }

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
        morphFrom?.let { from ->
            pose = withCutArrival(pose, from.moves, morph.value)
            // Bars' trend gaze eases out with the morph instead of snapping to the centre.
            from.gaze?.let { pose = lookAt(pose, it, from.gazeWeight * (1f - morph.value.coerceIn(0f, 1f))) }
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
     * [motion] is read again once the morph lands: "Remove animations" turned on during
     * it lets the morph finish but plays no arrival.
     */
    suspend fun changeShape(target: MascotShape, motion: () -> MascotMotion, spec: FiniteAnimationSpec<Float>) {
        if (target == shape) return
        if (motion() == MascotMotion.Still) {
            arrival.snapTo(1f)
            morphFrom = null
            arrivedShape = null
            shape = target
            value = latestValue
            morph.snapTo(1f)
            return
        }
        // Before the arrival stops: a check half stamped is caught half stamped.
        morphFrom = outlineOnScreen()
        // The old arrival now lives in morphFrom. Left set, a morph back to a receipt that
        // lands with "Remove animations" on would show the old check without its arrival.
        arrivedShape = null
        arrival.snapTo(1f)
        shape = target
        value = latestValue
        morph.snapTo(0f)
        morph.animateTo(1f, spec)
        morphFrom = null
        if (motion() == MascotMotion.Still) return
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
        // A jar mid-dip or a wallet mid-lean keeps that pose and lets it go through the morph.
        val moving = if (arrivedShape == shape && arrival.value < 1f) {
            CutArrival(shape, (arrival.value * ARRIVAL_MS).toLong(), 1f)
        } else {
            null
        }
        val gaze = if (shape == MascotShape.Bars && value != 0f) trendGaze(value) else null
        val from = morphFrom ?: return MorphFrom(here, g.eyes, g.pivot, shape, value, stamp = stamped, moves = moving, gaze = gaze)
        val p = morph.value
        val w = p.coerceIn(0f, 1f)
        val early = w < 0.5f
        return MorphFrom(
            profile = blendProfiles(from.profile, here, p),
            eyes = lerpEyes(from.eyes, g.eyes, p),
            pivot = lerp(from.pivot, g.pivot, p),
            // The face that shows most, and how much of it, as drawMorph draws them.
            shape = if (early) from.shape else shape,
            value = if (early) from.value else value,
            faceAlpha = if (early) fromFaceAlpha(from, w) else fadeAt(1f - FACE_FADE, 1f, w),
            owl = lerp(from.owl, owlness(shape), w),
            stamp = fromStampAlpha(from, w),
            squint = morphEyes(from, p),
            moves = from.moves?.let { it.copy(weight = cutWeight(it, p)) }?.takeIf { it.weight > 0f },
            // Into Bars, pose() already looks along the trend; out of it, what's left of the old look.
            gaze = gaze ?: from.gaze,
            gazeWeight = if (gaze != null) 1f else from.gazeWeight * (1f - w),
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
    /** A coin's shine and the sparkles and coin of an arrival. */
    val disc: Color,
    val iris: Color,
    val shadow: Color,
    /** The other shapes: a container body with its ink for eyes and markings. */
    val objectBody: Color,
    val objectInk: Color,
)

@Composable
private fun mascotColors(body: Color, containerColor: Color): MascotColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDark
    return MascotColors(
        body = body,
        disc = if (dark) scheme.onSurface else scheme.surface,
        iris = scheme.tertiary,
        shadow = scheme.onSurface,
        objectBody = if (containerColor.isSpecified) body else scheme.tertiaryContainer,
        objectInk = containerColor.takeOrElse { scheme.onTertiaryContainer },
    )
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
private class MorphDrawing(val from: MorphFrom, val fromGeometry: MascotGeometry, val target: MascotProfile, val beak: Path)

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
            c == null -> drawTwoTone(g, pose, d.tint, d.scratch, if (shape == MascotShape.Owl) OWL_TWO_TONE_EYE_SCALE else TWO_TONE_EYE_SCALE)
            shape == MascotShape.Owl -> {
                drawPath(g.body, c.body)
                drawPillEyes(g.eyes, 1f, pose, c.body, d.scratch, BlendMode.DstOut)
            }
            else -> {
                drawPath(g.body, c.objectBody)
                drawFace(g, c)
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
 * The owl's rings and beak open up as the morph becomes the owl and close as it leaves,
 * and its eyes turn into cut-outs, so the face never pops in once the morph lands.
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
    // Owl-ness on screen: from where the morph began to the new shape's.
    val owl = lerp(m.from.owl, owlness(shape), w)
    val owlCuts = fadeAt(OWL_CUTS_START, 1f, owl)
    posed(pose, lerp(m.from.pivot, g.pivot, progress), k) {
        val body = blendProfiles(m.from.profile, m.target, progress).toPath(outline)
        val c = d.colors
        if (c == null) {
            drawPath(body, d.tint)
            drawOwlCuts(eyes, owlCuts, m.beak, d.scratch)
            drawPillEyes(eyes, lerp(TWO_TONE_EYE_SCALE, OWL_TWO_TONE_EYE_SCALE, owl), squinting, d.tint, d.scratch, BlendMode.DstOut)
            drawStamp(fromStampAlpha(m.from, w), d.tint, TWO_TONE_STAMP, BlendMode.DstOut, d.scratch)
        } else {
            drawPath(body, lerpColor(c.objectBody, c.body, owl))
            val fromFace = fromFaceAlpha(m.from, w)
            clipPath(body) {
                withAlpha(fromFace, d.layer) { drawFace(m.fromGeometry, c) }
                withAlpha(fadeAt(1f - FACE_FADE, 1f, w), d.layer) { drawFace(g, c) }
            }
            drawOwlCuts(eyes, owlCuts, m.beak, d.scratch)
            // The owl's eyes are cut out, an object's are inked: cross from one to the other.
            if (owl > 0f) drawPillEyes(eyes, 1f, squinting, CUT.copy(alpha = owl), d.scratch, BlendMode.DstOut)
            if (owl < 1f) drawPillEyes(eyes, 1f, squinting, c.objectInk.copy(alpha = c.objectInk.alpha * (1f - owl)), d.scratch, DrawScope.DefaultBlendMode)
            clipPath(body) { drawStamp(fromStampAlpha(m.from, w), c.objectInk, 1f, DrawScope.DefaultBlendMode, d.scratch) }
        }
    }
}

/** DstOut reads only the paint's alpha: this cuts fully. */
private val CUT = Color.Black

/** How owl a morph must be before the rings and beak start to open (0..1). */
private const val OWL_CUTS_START = 0.35f

/** How big the rings are as they start to open, of their full size. */
private const val OWL_RINGS_FROM = 0.6f

/**
 * The owl's eye rings round [e]'s eyes and its [beak], cut out [strength] (0..1) of the
 * way: fainter and smaller below 1, exactly the owl's own cut-outs at 1.
 */
private fun DrawScope.drawOwlCuts(e: EyeSpec, strength: Float, beak: Path, scratch: Path) {
    if (strength <= 0f) return
    val cut = CUT.copy(alpha = strength)
    val r = OwlEye.RING_R * lerp(OWL_RINGS_FROM, 1f, strength)
    // Both rings in one path, so where they meet isn't cut twice.
    scratch.reset()
    for (side in SIDES) scratch.addOval(circle(e.x + side * e.spacing / 2, e.y, r))
    drawPath(scratch, cut, style = Stroke(OwlEye.RING_LINE), blendMode = BlendMode.DstOut)
    scale(strength, pivot = OWL_BEAK_CENTER) { drawPath(beak, cut, blendMode = BlendMode.DstOut) }
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
 * The eyes are [eyeScale] times their size. No markings: at this size they would only
 * blur the outline.
 */
private fun DrawScope.drawTwoTone(g: MascotGeometry, pose: MascotPose, tint: Color, scratch: Path, eyeScale: Float) {
    drawPath(g.body, tint)
    drawPillEyes(g.eyes, eyeScale, pose, tint, scratch, BlendMode.DstOut)
}

/** What sits on a body at the detailed size: an object's faint markings. Not its eyes. */
private fun DrawScope.drawFace(g: MascotGeometry, c: MascotColors) {
    g.details?.let { drawPath(it, c.objectInk, alpha = g.detailAlpha) }
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
