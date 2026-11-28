package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The goal and debt cues: a goal reached ([MascotCue.Brim]) and a debt paid off
 * ([MascotCue.PaidOff]). See MascotArrival.kt.
 */

/*
 * A goal reached, on the full jar: a last coin drops in through the lid, the jar takes its
 * weight and then stands a little taller, a glint crosses it and sparkles twinkle round
 * its rim, one after another. At 96dp the contents ripple out under the neck as the coin
 * lands. Nothing stays: the full jar is the milestone.
 */

/** Where the last coin is at [t], falling onto the lid, or null once it is in. */
internal fun brimCoin(t: Float): Offset? {
    if (t >= BRIM_LAND) return null
    val fall = t / BRIM_LAND
    return Offset(50f, BRIM_COIN_FROM + (BRIM_COIN_TO - BRIM_COIN_FROM) * fall * fall)
}

/** The coin fades in at the top of its fall. */
internal fun brimCoinAlpha(t: Float): Float = smoothstep(0f, 0.05f, t)

/** The jar dips under the coin, then stands proud: translation only, in box units (negative is up). */
internal fun brimLift(t: Float): Float = BRIM_DIP * swell(BRIM_LAND, 0.34f, t) - BRIM_RISE * swell(0.32f, 0.56f, t)

internal fun brimPose(pose: MascotPose, t: Float): MascotPose = pose.copy(lift = pose.lift + brimLift(t))

/** How much the ripple under the neck shows at [t], and how far it has spread (0..1). */
internal fun brimRipple(t: Float): Float = swell(BRIM_LAND, 0.42f, t)

internal fun brimRippleSpread(t: Float): Float = smoothstep(BRIM_LAND, 0.42f, t)

/** Where the glint across the full jar is at [t], from left of it to right of it, or null when it's gone. */
internal fun brimGlintX(t: Float): Float? {
    if (t <= BRIM_GLINT_START || t >= BRIM_GLINT_END) return null
    return -10f + 120f * smoothstep(BRIM_GLINT_START, BRIM_GLINT_END, t)
}

/** The sparkles round the jar's rim, off its body (it spans x 20..80 below y 25, the lid above). */
internal val BRIM_SPARKLES = listOf(
    Sparkle(Offset(22f, 14f), 6.5f, delay = 0.34f),
    Sparkle(Offset(80f, 12f), 7.5f, delay = 0.4f),
    Sparkle(Offset(89f, 36f), 5f, delay = 0.46f),
    Sparkle(Offset(11f, 40f), 5.5f, delay = 0.5f),
)

/** A rim sparkle twinkles on and off once, quicker than a coin's. */
internal fun brimSparkleAlpha(sparkle: Sparkle, t: Float): Float {
    val s = (t - sparkle.delay) / BRIM_SPARKLE_LIFE
    return if (s in 0f..1f) sin(PI * s).toFloat() else 0f
}

/** Once the last rim sparkle is out. */
internal val BRIM_END = BRIM_SPARKLES.maxOf { it.delay } + BRIM_SPARKLE_LIFE

/** The coin, then over the body the ripple (detailed only) and the glint, then the sparkles off it. */
internal fun DrawScope.drawBrim(g: MascotGeometry, t: Float, ink: CueInk) {
    brimCoin(t)?.let { drawCircle(ink.accent, JAR_COIN_R, it, alpha = brimCoinAlpha(t)) }
    val ripple = brimRipple(t)
    // A thin line cut through a 24dp glyph would only read as a crack.
    if (ripple > 0f && !ink.glyph) {
        val half = 6f + 18f * brimRippleSpread(t)
        clipPath(g.body) {
            ink.scratch.reset()
            ink.scratch.moveTo(50f - half, BRIM_RIPPLE_Y)
            ink.scratch.quadraticTo(50f, BRIM_RIPPLE_Y - 4f, 50f + half, BRIM_RIPPLE_Y)
            drawPath(ink.scratch, ink.light, alpha = BRIM_RIPPLE_ALPHA * ripple, style = Stroke(2.4f, cap = StrokeCap.Round))
        }
    }
    brimGlintX(t)?.let { x ->
        clipPath(g.body) {
            rotate(BRIM_GLINT_TILT, pivot = Offset(x, 52f)) {
                drawRect(
                    color = ink.light,
                    topLeft = Offset(x - BRIM_GLINT_WIDTH / 2, -50f),
                    size = Size(BRIM_GLINT_WIDTH, 200f),
                    alpha = BRIM_GLINT_ALPHA,
                    blendMode = ink.markBlend,
                )
            }
        }
    }
    // Off the body: in the tint as a glyph, never cut.
    for (s in BRIM_SPARKLES) {
        val a = brimSparkleAlpha(s, t)
        if (a > 0f) drawSparkle(s.at, s.radius, ink.accent, a, ink.scratch)
    }
}

/** The coin lands on the lid here, and the jar takes it. */
private const val BRIM_LAND = 0.2f
private const val BRIM_COIN_FROM = 1f
private const val BRIM_COIN_TO = 16f
private const val BRIM_DIP = 3f
private const val BRIM_RISE = 2.5f
private const val BRIM_RIPPLE_Y = 33f
private const val BRIM_RIPPLE_ALPHA = 0.8f
private const val BRIM_GLINT_START = 0.3f
private const val BRIM_GLINT_END = 0.58f
private const val BRIM_GLINT_TILT = 20f
private const val BRIM_GLINT_WIDTH = 10f
private const val BRIM_GLINT_ALPHA = 0.6f
private const val BRIM_SPARKLE_LIFE = 0.24f

/*
 * A debt paid off, on the wallet: it rises a little, lighter, leaning back with relief,
 * then gives one nod as a check lands on it and its clasp snaps shut. One way and down, so
 * it never reads as a transfer's look from side to side.
 */

/** Where the wallet stands at [t]: up, lighter, then a nod down; translation only, in box units. */
internal fun paidOffLift(t: Float): Float = -PAID_OFF_RISE * swell(0.02f, 0.3f, t) + PAID_OFF_NOD * swell(0.28f, 0.46f, t)

/** Its lean at [t], in degrees: back toward line start while it rises, upright for the nod. */
internal fun paidOffLean(t: Float): Float = -PAID_OFF_LEAN * swell(0.02f, 0.34f, t)

internal fun paidOffPose(pose: MascotPose, t: Float): MascotPose =
    pose.copy(lift = pose.lift + paidOffLift(t), roll = pose.roll + paidOffLean(t))

/** The check lands with the nod, and stays. */
internal fun paidOffCheck(t: Float): Float = smoothstep(0.26f, 0.38f, t)

/** How much the clasp's snap shows at [t]: a flick of marks as the check lands. */
internal fun paidOffSnap(t: Float): Float = swell(0.28f, 0.46f, t)

/** Once the nod is done and the snap gone. */
internal const val PAID_OFF_END = 0.48f

/** Three short marks flicking out from the clasp, off the body, as it snaps shut. */
internal fun DrawScope.drawPaidOff(t: Float, ink: CueInk) {
    val a = paidOffSnap(t)
    if (a <= 0f) return
    val out = smoothstep(0.28f, 0.46f, t)
    val from = PAID_OFF_SNAP_FROM + out
    val width = if (ink.glyph) 4f else 3f
    for (deg in PAID_OFF_SNAP_ANGLES) {
        val r = Math.toRadians(deg.toDouble())
        val dir = Offset(cos(r).toFloat(), sin(r).toFloat())
        drawLine(
            color = ink.accent,
            start = PAID_OFF_CLASP + dir * from,
            end = PAID_OFF_CLASP + dir * (from + PAID_OFF_SNAP_LENGTH),
            strokeWidth = width,
            cap = StrokeCap.Round,
            alpha = a,
        )
    }
}

private const val PAID_OFF_RISE = 3f
private const val PAID_OFF_NOD = 2.5f
private const val PAID_OFF_LEAN = 5f

/** The clasp's middle, and how far out from it its marks start: just clear of the tab's edge. */
private val PAID_OFF_CLASP = Offset(75f, 55f)
private const val PAID_OFF_SNAP_FROM = 18f
private const val PAID_OFF_SNAP_LENGTH = 4.5f
private val PAID_OFF_SNAP_ANGLES = floatArrayOf(-40f, 0f, 40f)

/** Up from 0 to 1 and back between [from] and [to], 0 outside. */
private fun swell(from: Float, to: Float, t: Float): Float =
    if (t <= from || t >= to) 0f else sin(PI * smoothstep(from, to, t)).toFloat()
