/*
 * Pose maths for the PennyWise mascot. The timing constants (blink slots, double-blink
 * odds, gaze wander slots, mood blending) are adapted from open-bot-avatar by Dominik
 * Scholz, MIT licence: https://github.com/DominikScholz/open-bot-avatar
 */
package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/** What the mascot feels; each mood is a target pose the face blends toward. */
enum class MascotMood { Calm, Thinking, Happy, Worried, Asleep }

/** How the eyes are drawn: open discs/pills, happy `^` arcs, or closed lids. */
internal enum class EyeStyle { Open, Arc, Closed }

/**
 * How much the mascot moves.
 * - [Still]: "Remove animations" — a deliberate static pose, no clock at all.
 * - [Quiet]: small sizes. The caller blinks it from a delay-driven clock; time only
 *   advances while a mood change plays out or the mascot is thinking, so an idle one
 *   runs nothing between blinks.
 * - [Full]: the hero, on a frame clock: scheduled blinks and a wandering gaze.
 */
internal enum class MascotMotion { Still, Quiet, Full }

/**
 * One frame of the face, in the 0..100 drawing box. Gaze is -1..1 on each axis
 * (x toward line end, y down) and is mirrored for RTL when drawn, as is [roll].
 */
internal data class MascotPose(
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    /** Head tilt in degrees, positive toward line end. */
    val roll: Float = 0f,
    /** Vertical offset in box units; negative is up. Translation only, never a squash. */
    val lift: Float = 0f,
    val squint: Float = 0f,
    /** 0 open .. 1 shut. */
    val blink: Float = 0f,
    val brows: Float = 0f,
    val style: EyeStyle = EyeStyle.Open,
)

internal const val BLINK_MS = 190L
internal const val BLINK_SLOT_MS = 4200L
internal const val DOUBLE_BLINK_CHANCE = 0.2f
internal const val DOUBLE_BLINK_GAP_MS = 340L
private const val BLINK_OFFSET_MIN_MS = 500L
private const val BLINK_OFFSET_SPREAD_MS = 2900f
private const val QUIET_BLINK_MIN_MS = 4000L
private const val QUIET_BLINK_SPREAD_MS = 4000f
private const val WANDER_SLOT_MS = 1900L
private const val WANDER_EASE_MS = 160f
private const val WANDER_CENTRED_CHANCE = 0.4f
private const val HOP_MS = 920L
private const val HOP_HEIGHT = 6f
private const val SHAKE_MS = 700L
private const val SWEEP_REACH = 0.8f

/** Radians per second: one look across and back in about two seconds. */
private const val SWEEP_RATE = 3f

/** Asleep, a detailed mascot breathes: a slow rise and fall, in box units. */
private const val BREATH_MS = 3400f
private const val BREATH_LIFT = 1.2f

/** A poke: a quick blink and a wobble of at most [POKE_ROLL] degrees that dies out in [POKE_MS]. */
internal const val POKE_MS = 900L
internal const val POKE_ROLL = 6f
private const val POKE_DECAY = 5f
private const val POKE_RATE = 22f

/** How quickly the eyes find a finger, and how quickly they let go of it once it lifts. */
internal const val FOLLOW_MS = 120f
internal const val ATTENTION_MS = 300f

/** Gain on a drag's saturated reach: a finger about two thirds of the mascot away gets a full look. */
private const val DRAG_GAIN = 1.6f

/** How far up or down a [MascotShape.Bars] looks for a full change. */
private const val TREND_GAZE = 0.8f

/** Characters after which a typed line has the eyes at its far end. */
private const val TYPING_LINE_CHARS = 32f

/** How long a mood change keeps a Quiet mascot's clock running (the hop, the shake). */
internal const val MOOD_MOTION_MS = 1000L

/** A repeatable 0..1 noise value for slot [i]; [seed] keeps two mascots out of step. */
internal fun rand(seed: Float, i: Long): Float {
    val x = sin(seed * 127.1 + i * 311.7) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

private fun lerp(a: Float, b: Float, w: Float) = a + (b - a) * w

/** One lid close and reopen: 0 → 1 → 0 over [BLINK_MS]. */
private fun bump(elapsedMs: Long): Float =
    if (elapsedMs in 0..BLINK_MS) sin(PI * elapsedMs / BLINK_MS).toFloat() else 0f

/** The lid [elapsedMs] into a blink that started at 0; a [double] blink closes twice. */
internal fun blinkAt(elapsedMs: Long, double: Boolean): Float =
    max(bump(elapsedMs), if (double) bump(elapsedMs - DOUBLE_BLINK_GAP_MS) else 0f)

internal fun blinkLengthMs(double: Boolean): Long = if (double) DOUBLE_BLINK_GAP_MS + BLINK_MS else BLINK_MS

/** When the blink in slot [slot] starts, from the slot's start. */
internal fun blinkOffsetMs(seed: Float, slot: Long): Long =
    BLINK_OFFSET_MIN_MS + (rand(seed, slot) * BLINK_OFFSET_SPREAD_MS).toLong()

internal fun isDoubleBlink(seed: Float, slot: Long): Boolean = rand(seed, slot + 99) < DOUBLE_BLINK_CHANCE

/** The Full clock's lid: one blink per [BLINK_SLOT_MS] slot, at a random point, 20% doubled. */
internal fun scheduledBlink(seed: Float, tMillis: Long): Float {
    val slot = Math.floorDiv(tMillis, BLINK_SLOT_MS)
    return blinkAt(tMillis - slot * BLINK_SLOT_MS - blinkOffsetMs(seed, slot), isDoubleBlink(seed, slot))
}

/** The Quiet clock's wait before its next blink, for a uniform [random] in 0..1. */
internal fun nextBlinkDelayMillis(random: Float): Long = QUIET_BLINK_MIN_MS + (random * QUIET_BLINK_SPREAD_MS).toLong()

/** Idle gaze: a new target every [WANDER_SLOT_MS] (40% straight ahead), eased in quickly. */
internal fun wanderAt(seed: Float, tMillis: Long): Offset {
    fun target(i: Long): Offset =
        if (rand(seed, i * 3 + 1) < WANDER_CENTRED_CHANCE) Offset.Zero
        else Offset((rand(seed, i * 3 + 2) * 2 - 1) * 0.6f, (rand(seed, i * 3 + 3) * 2 - 1) * 0.35f)
    val slot = Math.floorDiv(tMillis, WANDER_SLOT_MS)
    val w = smoothstep((tMillis - slot * WANDER_SLOT_MS) / WANDER_EASE_MS)
    val a = target(slot - 1)
    val b = target(slot)
    return Offset(lerp(a.x, b.x, w), lerp(a.y, b.y, w))
}

/** Keeps a gaze inside the unit disc, so the iris never leaves the eye. */
internal fun clampGaze(x: Float, y: Float): Offset {
    val m = hypot(x, y)
    return if (m <= 1f) Offset(x, y) else Offset(x / m, y / m)
}

/**
 * The target pose for [mood] at [tMillis], [sinceMoodMillis] after the mood began.
 * [MascotMotion.Still] returns the same pose for any time. With [sweep], a Thinking face
 * reads instead: the eyes sweep left and right along a line, a little above centre.
 */
internal fun moodPose(
    mood: MascotMood,
    tMillis: Long,
    sinceMoodMillis: Long,
    seed: Float,
    motion: MascotMotion,
    sweep: Boolean = false,
): MascotPose {
    val moving = motion != MascotMotion.Still
    val full = motion == MascotMotion.Full
    val t = tMillis / 1000f
    val wander = if (full) wanderAt(seed, tMillis) else Offset.Zero
    val pose = when (mood) {
        MascotMood.Calm -> MascotPose(
            gazeX = wander.x,
            gazeY = wander.y,
            blink = if (full) scheduledBlink(seed, tMillis) else 0f,
        )
        // Eyes up and toward line start, a slight tilt, a slower blink.
        MascotMood.Thinking -> MascotPose(
            gazeX = when {
                sweep && moving -> SWEEP_REACH * sin(t * SWEEP_RATE)
                sweep -> 0f
                moving -> -0.55f - 0.25f * sin(t * 0.9f)
                else -> -0.55f
            },
            gazeY = if (sweep) -0.5f else -0.8f,
            squint = 0.3f,
            roll = -3f + if (moving) 1.2f * sin(t * 2 * PI.toFloat() / 4.1f) else 0f,
            blink = if (full) scheduledBlink(seed, (tMillis * 0.7f).toLong()) else 0f,
        )
        // `^` eyes and one short hop.
        MascotMood.Happy -> MascotPose(
            style = EyeStyle.Arc,
            lift = if (moving && sinceMoodMillis in 0 until HOP_MS) {
                -HOP_HEIGHT * sin(PI * sinceMoodMillis / HOP_MS).toFloat()
            } else 0f,
        )
        // Brows, eyes down, one small damped shake as the mood arrives.
        MascotMood.Worried -> MascotPose(
            gazeX = wander.x * 0.3f,
            gazeY = 0.5f,
            brows = 1f,
            blink = if (full) scheduledBlink(seed, tMillis) else 0f,
            roll = if (moving && sinceMoodMillis in 0 until SHAKE_MS) {
                3f * sin(sinceMoodMillis * 0.034f) * (1 - sinceMoodMillis / SHAKE_MS.toFloat())
            } else 0f,
        )
        MascotMood.Asleep -> MascotPose(
            style = EyeStyle.Closed,
            lift = if (full) BREATH_LIFT * sin(2 * PI * tMillis / BREATH_MS).toFloat() else 0f,
        )
    }
    val gaze = clampGaze(pose.gazeX, pose.gazeY)
    return pose.copy(gazeX = gaze.x, gazeY = gaze.y)
}

/**
 * Blends from [from] — the pose that was on screen when the mood changed — to [to]
 * at [weight] 0..1. When the eye style changes, the lids close through the middle and
 * the new style appears behind them.
 */
internal fun blendPose(from: MascotPose, to: MascotPose, weight: Float): MascotPose {
    val w = weight.coerceIn(0f, 1f)
    val blink = lerp(from.blink, to.blink, w)
    val styleChanges = from.style != to.style
    return MascotPose(
        gazeX = lerp(from.gazeX, to.gazeX, w),
        gazeY = lerp(from.gazeY, to.gazeY, w),
        roll = lerp(from.roll, to.roll, w),
        lift = lerp(from.lift, to.lift, w),
        squint = lerp(from.squint, to.squint, w),
        brows = lerp(from.brows, to.brows, w),
        blink = if (styleChanges) max(blink, sin(PI * w).toFloat()) else blink,
        style = if (styleChanges && w < 0.5f) from.style else to.style,
    )
}

/** Exponential ease of [current] toward [target] over [dtMs], with time constant [tauMs]. */
internal fun ease(current: Float, target: Float, dtMs: Long, tauMs: Float): Float =
    current + (target - current) * (1 - exp(-dtMs / tauMs))

/**
 * Where the eyes look for a finger at [dx], [dy] from them, in units of the mascot's size
 * (x toward line end, y down). The reach saturates (tanh), so a finger far across the
 * screen still lands inside the eye, and one beside it gets a proportional glance.
 */
internal fun dragGaze(dx: Float, dy: Float): Offset {
    val m = hypot(dx, dy)
    val k = if (m > 1e-4f) tanh(m) / m else 1f
    return clampGaze(dx * k * DRAG_GAIN, dy * k * DRAG_GAIN)
}

/**
 * Looking down at a text field below while [length] characters are typed into it: the
 * eyes start at the line's start and follow it toward its end as it fills.
 */
internal fun typingGaze(length: Int): Offset {
    val along = (length / TYPING_LINE_CHARS).coerceIn(0f, 1f)
    return clampGaze(-0.5f + along, 0.8f)
}

/**
 * Where a [MascotShape.Bars] looks for a [change] of -1..1: up when spending rose, down
 * when it fell, ahead when it held. Only the direction, never a verdict on it.
 */
internal fun trendGaze(change: Float): Offset = Offset(0f, -TREND_GAZE * change.coerceIn(-1f, 1f))

/**
 * Lays [look] over [pose] at [attention] 0..1. Only open eyes follow: `^` and closed eyes
 * have no pupil to point.
 */
internal fun lookAt(pose: MascotPose, look: Offset, attention: Float): MascotPose {
    if (pose.style != EyeStyle.Open || attention <= 0f) return pose
    val w = attention.coerceAtMost(1f)
    val gaze = clampGaze(lerp(pose.gazeX, look.x, w), lerp(pose.gazeY, look.y, w))
    return pose.copy(gazeX = gaze.x, gazeY = gaze.y)
}

/** [pose] [sinceMs] after a poke: a quick blink and a damped wobble of at most ±[POKE_ROLL]°. */
internal fun poked(pose: MascotPose, sinceMs: Long): MascotPose {
    if (sinceMs !in 0 until POKE_MS) return pose
    val s = sinceMs / 1000f
    return pose.copy(
        roll = pose.roll + POKE_ROLL * exp(-s * POKE_DECAY) * sin(s * POKE_RATE),
        blink = max(pose.blink, bump(sinceMs)),
    )
}
