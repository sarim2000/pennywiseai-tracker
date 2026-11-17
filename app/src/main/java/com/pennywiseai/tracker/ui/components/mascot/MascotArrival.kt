package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/*
 * What the mascot does once a morph lands, or in place when it is asked to: one short
 * move that says what happened (a [MascotCue]). A shape plays its own cue when it lands
 * unless the caller names another; it never plays when a screen first shows it, and never
 * with animations off.
 *
 * - Shine (coin, income): a shine sweeps across it and a few sparkles twinkle round it.
 * - Stamp (receipt, expense): a check is stamped in its corner, and stays while it's a receipt.
 * - Glance (wallet, transfer): it looks, leaning, from line start to line end, as the money moved.
 * - CoinDrop (jar, investment or a goal saved into): a coin drops in through the lid, and the jar settles.
 * - Ring (bell): it rings, as it does when poked.
 * - The moments' own (MascotCues*.kt): a goal reached, a debt paid off, a message learned,
 *   rules applied, a statement read in, a backup kept safe, a bill paid, amounts hidden or
 *   shown again.
 *
 * A cue that ends in a check keeps it for as long as the shape stays, and a morph away
 * fades it with the old face.
 */

/** A one-shot move the mascot plays once its shape lands, or in place. */
enum class MascotCue {
    Shine,
    Stamp,
    Glance,
    CoinDrop,
    Ring,

    /** A goal reached: the jar fills to the brim and glints. */
    Brim,

    /** A debt paid off: the wallet closes on a check. */
    PaidOff,

    /** An unrecognised message taught: the bubble reads it, then checks it. */
    Learned,

    /** Rules applied to past rows: the receipt's lines fall into place, then a check. */
    Sorted,

    /** A statement imported: the receipt is read top to bottom, then checked. */
    Read,

    /** A backup kept safe or restored: the shield takes a check. */
    Secured,

    /** A bill marked paid: the bell gives a short ring and takes a check. */
    Paid,

    /** Amounts hidden: the owl covers its eyes. */
    CoverEyes,

    /** Amounts shown again: the owl peeks out. */
    Peek,
}

/**
 * Numbers each new cue a mascot is asked for: a change of cue to another non-null one, or
 * back to one after none. Dropping a cue (to null) keeps the number, so it plays out.
 */
internal class CueRequests(private var last: MascotCue?) {
    private var id = 0

    fun idFor(cue: MascotCue?): Int {
        if (cue != null && cue != last) id++
        last = cue
        return id
    }
}

/** The cue [shape] plays when it lands and no other is asked for, or null for none. */
internal fun defaultCue(shape: MascotShape): MascotCue? = when (shape) {
    MascotShape.Coin -> MascotCue.Shine
    MascotShape.Receipt -> MascotCue.Stamp
    MascotShape.Wallet -> MascotCue.Glance
    MascotShape.Jar -> MascotCue.CoinDrop
    MascotShape.Bell -> MascotCue.Ring
    MascotShape.Shield -> MascotCue.Secured
    else -> null
}

/** How long a cue plays at most; the bell's ring is the longest part. */
internal const val ARRIVAL_MS = RING_MS

internal fun smoothstep(from: Float, to: Float, x: Float): Float {
    val c = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

/**
 * Where [cue] has nothing left to move, as a share of [ARRIVAL_MS]: its clock stops there,
 * so a settled shape draws no frames. 0 for no cue.
 */
internal fun arrivalEnd(cue: MascotCue?): Float = when (cue) {
    null -> 0f
    MascotCue.Shine -> COIN_SPARKLES.maxOf { it.delay } + SPARKLE_LIFE
    MascotCue.Stamp -> STAMP_IN
    MascotCue.Glance -> GLANCE_DONE
    MascotCue.CoinDrop -> JAR_DROP_END + JAR_SETTLE
    MascotCue.Ring -> 1f
    MascotCue.Brim -> BRIM_END
    MascotCue.PaidOff -> PAID_OFF_END
    MascotCue.Learned -> LEARNED_END
    MascotCue.Sorted -> SORTED_END
    MascotCue.Read -> READ_END
    MascotCue.Secured -> SECURED_END
    MascotCue.Paid -> PAID_END
    MascotCue.CoverEyes -> COVER_END
    MascotCue.Peek -> PEEK_END
}

/** Where a cue is, 0..1, [sinceMs] after it began. */
internal fun arrivalAt(sinceMs: Long): Float = (sinceMs / ARRIVAL_MS.toFloat()).coerceIn(0f, 1f)

/** [pose] [sinceMs] into [cue]: the moves that are the face's own. */
internal fun arrived(pose: MascotPose, cue: MascotCue, sinceMs: Long): MascotPose {
    if (sinceMs !in 0 until ARRIVAL_MS) return pose
    val t = arrivalAt(sinceMs)
    return when (cue) {
        MascotCue.Glance -> {
            val glance = walletGlance(t)
            val attention = walletAttention(t)
            // It leans the way it looks, so the glance reads at 24dp too.
            lookAt(pose, glance, attention).let { it.copy(roll = it.roll + GLANCE_LEAN * glance.x * attention) }
        }
        MascotCue.Ring -> rung(pose, sinceMs)
        MascotCue.CoinDrop -> pose.copy(lift = pose.lift + jarSettle(t))
        MascotCue.Brim -> brimPose(pose, t)
        MascotCue.PaidOff -> paidOffPose(pose, t)
        MascotCue.Learned -> learnedPose(pose, t)
        MascotCue.Sorted -> sortedPose(pose, t)
        MascotCue.Read -> readPose(pose, t)
        MascotCue.Secured -> securedPose(pose, t)
        MascotCue.Paid -> paidPose(pose, t, sinceMs)
        MascotCue.CoverEyes -> coverEyesPose(pose, t)
        MascotCue.Peek -> peekPose(pose, t)
        MascotCue.Shine, MascotCue.Stamp -> pose
    }
}

/**
 * How much of [cue]'s check shows at [t]: none for a cue without one, and once a check is
 * in it stays.
 */
internal fun checkAlpha(cue: MascotCue?, t: Float): Float = when (cue) {
    MascotCue.Stamp -> stampAlpha(t)
    MascotCue.PaidOff -> paidOffCheck(t)
    MascotCue.Learned -> learnedCheck(t)
    MascotCue.Sorted -> sortedCheck(t)
    MascotCue.Read -> readCheck(t)
    MascotCue.Secured -> securedCheck(t)
    MascotCue.Paid -> paidCheck(t)
    else -> 0f
}

/** Where a check sits on [shape], in box units: clear of its eyes, inside its body. */
internal fun checkSpot(shape: MascotShape): Offset = when (shape) {
    MascotShape.Receipt -> Offset(64f, 65f)
    MascotShape.Wallet -> Offset(62f, 69f)
    MascotShape.Bubble -> Offset(67f, 57f)
    MascotShape.Bell -> Offset(62f, 66f)
    MascotShape.Shield -> Offset(50f, 66f)
    else -> Offset(64f, 66f)
}

/** A transfer's glance: from line start across to line end, a little low, at [t] 0..1. */
internal fun walletGlance(t: Float): Offset = Offset(-GLANCE_REACH + 2 * GLANCE_REACH * smoothstep(0.12f, 0.55f, t), 0.15f)

/** How much the glance holds the eyes: it takes them, then gives them back before the end. */
internal fun walletAttention(t: Float): Float = smoothstep(0f, 0.1f, t) * (1 - smoothstep(0.7f, GLANCE_DONE, t))

private const val GLANCE_DONE = 0.95f

private const val GLANCE_REACH = 0.8f

/** Degrees of lean for a full glance. */
private const val GLANCE_LEAN = 6f

/** Where the coin dropping into the jar is at [t], or null once it is in (or before it shows). */
internal fun jarCoin(t: Float): Offset? {
    if (t >= JAR_DROP_END) return null
    val fall = t / JAR_DROP_END
    // Falling: slow at the top, fastest as it reaches the lid.
    return Offset(50f, JAR_COIN_FROM + (JAR_COIN_TO - JAR_COIN_FROM) * fall * fall)
}

/** The coin fades in at the top of its fall. */
internal fun jarCoinAlpha(t: Float): Float = smoothstep(0f, 0.06f, t)

/** The jar dips as the coin lands, then stands again: translation only, in box units. */
internal fun jarSettle(t: Float): Float {
    val s = (t - JAR_DROP_END) / JAR_SETTLE
    return if (s in 0f..1f) JAR_DIP * sin(PI * s).toFloat() else 0f
}

internal const val JAR_COIN_R = 5f
private const val JAR_DROP_END = 0.28f
private const val JAR_SETTLE = 0.16f
private const val JAR_DIP = 4f
private const val JAR_COIN_FROM = 1f
private const val JAR_COIN_TO = 16f

/** Where a coin's shine band is at [t], from left of the coin to right of it, or null when it's gone. */
internal fun coinShineX(t: Float): Float? {
    if (t !in SHINE_START..SHINE_END) return null
    return -10f + 120f * smoothstep(SHINE_START, SHINE_END, t)
}

private const val SHINE_START = 0.04f
private const val SHINE_END = 0.4f

/** The sparkles round a coin: where each sits (outside the coin) and its size, in box units. */
internal val COIN_SPARKLES = listOf(
    Sparkle(Offset(15f, 18f), 8f, delay = 0.1f),
    Sparkle(Offset(87f, 24f), 6.5f, delay = 0.22f),
    Sparkle(Offset(88f, 84f), 7.5f, delay = 0.34f),
    Sparkle(Offset(12f, 80f), 6f, delay = 0.44f),
)

internal data class Sparkle(val at: Offset, val radius: Float, val delay: Float)

/** A sparkle twinkles on and off once, after its delay. */
internal fun sparkleAlpha(sparkle: Sparkle, t: Float): Float {
    val s = (t - sparkle.delay) / SPARKLE_LIFE
    return if (s in 0f..1f) sin(PI * s).toFloat() else 0f
}

internal const val SPARKLE_LIFE = 0.45f

/** How much of the receipt's check shows at [t]; once stamped it stays. */
internal fun stampAlpha(t: Float): Float = smoothstep(0.05f, STAMP_IN, t)

internal const val STAMP_IN = 0.22f
