package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
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

/** A debt paid off: the wallet gives a small nod as a check closes it. */
internal const val PAID_OFF_END = 0.4f

internal fun paidOffPose(pose: MascotPose, t: Float): MascotPose {
    val s = t / PAID_OFF_END
    val nod = if (s in 0f..1f) PAID_OFF_NOD * sin(PI * s).toFloat() else 0f
    return pose.copy(lift = pose.lift + nod)
}

internal fun paidOffCheck(t: Float): Float = smoothstep(0.08f, 0.28f, t)

private const val PAID_OFF_NOD = 3f

/** Up from 0 to 1 and back between [from] and [to], 0 outside. */
private fun swell(from: Float, to: Float, t: Float): Float =
    if (t <= from || t >= to) 0f else sin(PI * smoothstep(from, to, t)).toFloat()
