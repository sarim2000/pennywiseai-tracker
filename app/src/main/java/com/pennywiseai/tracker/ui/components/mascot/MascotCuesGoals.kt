package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin

/*
 * The goal and debt cues: a goal reached ([MascotCue.Brim]) and a debt paid off
 * ([MascotCue.PaidOff]). See MascotArrival.kt.
 */

/** A goal reached: a glint sweeps the full jar and sparkles twinkle round it, till the last one's out. */
internal val BRIM_END = COIN_SPARKLES.maxOf { it.delay } + SPARKLE_LIFE

internal fun brimPose(pose: MascotPose, @Suppress("UNUSED_PARAMETER") t: Float): MascotPose = pose

/** The glint across the full jar, over its body, then the sparkles round it. */
internal fun DrawScope.drawBrim(g: MascotGeometry, t: Float, ink: CueInk) {
    if (t >= 1f) return
    coinShineX(t)?.let { x ->
        clipPath(g.body) {
            rotate(BRIM_SHINE_TILT, pivot = Offset(x, 52f)) {
                drawRect(
                    color = ink.light,
                    topLeft = Offset(x - BRIM_SHINE_WIDTH / 2, -50f),
                    size = Size(BRIM_SHINE_WIDTH, 200f),
                    alpha = BRIM_SHINE_ALPHA,
                    blendMode = ink.markBlend,
                )
            }
        }
    }
    for (s in COIN_SPARKLES) {
        val a = sparkleAlpha(s, t)
        if (a > 0f) drawSparkle(s.at, s.radius, ink.accent, a, ink.scratch)
    }
}

private const val BRIM_SHINE_TILT = 20f
private const val BRIM_SHINE_WIDTH = 9f
private const val BRIM_SHINE_ALPHA = 0.55f

/** A debt paid off: the wallet gives a small nod as a check closes it. */
internal const val PAID_OFF_END = 0.4f

internal fun paidOffPose(pose: MascotPose, t: Float): MascotPose {
    val s = t / PAID_OFF_END
    val nod = if (s in 0f..1f) PAID_OFF_NOD * sin(PI * s).toFloat() else 0f
    return pose.copy(lift = pose.lift + nod)
}

internal fun paidOffCheck(t: Float): Float = smoothstep(0.08f, 0.28f, t)

private const val PAID_OFF_NOD = 3f
