package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/*
 * What a shape does once a morph lands on it: one short move that says what it stands
 * for. It plays only when the mascot turns into the shape, never when a screen first
 * shows it, and never with animations off.
 *
 * - Coin (income): a shine sweeps across it and a few sparkles twinkle round it.
 * - Receipt (expense): a check is stamped in its corner, and stays while it's a receipt.
 * - Wallet (transfer): it looks, leaning, from line start to line end, as the money moved.
 * - Jar (investment): a coin drops in through the lid, and the jar settles under it.
 * - Bell: it rings, as it does when poked.
 */

/** How long a shape's arrival plays; the bell's ring is the longest part. */
internal const val ARRIVAL_MS = RING_MS

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val c = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

/**
 * Where [shape]'s arrival has nothing left to move, as a share of [ARRIVAL_MS]: its clock
 * stops there, so a settled shape draws no frames. 0 for a shape without an arrival.
 */
internal fun arrivalEnd(shape: MascotShape): Float = when (shape) {
    MascotShape.Coin -> COIN_SPARKLES.maxOf { it.delay } + SPARKLE_LIFE
    MascotShape.Receipt -> STAMP_IN
    MascotShape.Wallet -> GLANCE_DONE
    MascotShape.Jar -> JAR_DROP_END + JAR_SETTLE
    MascotShape.Bell -> 1f
    else -> 0f
}

/** Where the arrival is, 0..1, [sinceMs] after it began. */
internal fun arrivalAt(sinceMs: Long): Float = (sinceMs / ARRIVAL_MS.toFloat()).coerceIn(0f, 1f)

/** [pose] [sinceMs] after [shape] arrived: the moves that are the face's own. */
internal fun arrived(pose: MascotPose, shape: MascotShape, sinceMs: Long): MascotPose {
    if (sinceMs !in 0 until ARRIVAL_MS) return pose
    val t = arrivalAt(sinceMs)
    return when (shape) {
        MascotShape.Wallet -> {
            val glance = walletGlance(t)
            val attention = walletAttention(t)
            // It leans the way it looks, so the glance reads at 24dp too.
            lookAt(pose, glance, attention).let { it.copy(roll = it.roll + GLANCE_LEAN * glance.x * attention) }
        }
        MascotShape.Bell -> rung(pose, sinceMs)
        MascotShape.Jar -> pose.copy(lift = pose.lift + jarSettle(t))
        else -> pose
    }
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

private const val SPARKLE_LIFE = 0.45f

/** How much of the receipt's check shows at [t]; once stamped it stays. */
internal fun stampAlpha(t: Float): Float = smoothstep(0.05f, STAMP_IN, t)

private const val STAMP_IN = 0.22f
