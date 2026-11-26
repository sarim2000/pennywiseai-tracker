package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * The owl's own cues and the message one: amounts hidden ([MascotCue.CoverEyes]), shown
 * again ([MascotCue.Peek]), and an unrecognised message taught ([MascotCue.Learned]).
 * See MascotArrival.kt.
 */

/** Amounts hidden: the owl's wings come up over its shut eyes, hold, and go down. */
internal const val COVER_END = 0.8f

/** How far the wings are up at [t]: 0 down, 1 over the eyes. */
internal fun coverAmount(t: Float): Float = smoothstep(0f, 0.15f, t) * (1 - smoothstep(0.62f, COVER_END, t))

internal fun coverEyesPose(pose: MascotPose, t: Float): MascotPose {
    val up = coverAmount(t)
    return if (up <= 0f) pose else pose.copy(blink = max(pose.blink, up))
}

/** The two wings, rising from the belly over the eyes. */
internal fun DrawScope.drawCoverEyes(t: Float, ink: CueInk) {
    val up = coverAmount(t)
    if (up <= 0f) return
    for (side in floatArrayOf(-1f, 1f)) {
        val rest = Offset(50f + side * 24f, 80f)
        val over = Offset(50f + side * 14f, 53f)
        val c = lerp(rest, over, up)
        rotate(side * (40f - 25f * up), pivot = c) {
            val topLeft = Offset(c.x - WING_W / 2, c.y - WING_H / 2)
            drawOval(ink.body, topLeft, Size(WING_W, WING_H), alpha = up)
            drawOval(ink.mark, topLeft, Size(WING_W, WING_H), alpha = up, style = Stroke(WING_EDGE), blendMode = ink.markBlend)
        }
    }
}

private const val WING_W = 30f
private const val WING_H = 20f
private const val WING_EDGE = 2.4f

/** Amounts shown again: a quick double blink and a look round. */
internal const val PEEK_END = 0.6f

internal fun peekPose(pose: MascotPose, t: Float): MascotPose {
    val blink = max(bump(0f, 0.12f, t), bump(0.16f, 0.28f, t))
    val look = Offset(sin(2 * PI * smoothstep(0.3f, PEEK_END, t)).toFloat() * 0.6f, 0f)
    return lookAt(pose.copy(blink = max(pose.blink, blink)), look, 1f - smoothstep(0.5f, PEEK_END, t))
}

/** Up from 0 to 1 and back between [from] and [to], 0 outside. */
private fun bump(from: Float, to: Float, t: Float): Float =
    if (t <= from || t >= to) 0f else sin(PI * smoothstep(from, to, t)).toFloat()

/** A message taught: the bubble reads it, line by line, then checks it. */
internal const val LEARNED_END = 0.6f

internal fun learnedPose(pose: MascotPose, t: Float): MascotPose {
    val reading = smoothstep(0f, 0.06f, t) * (1 - smoothstep(0.36f, 0.44f, t))
    val x = sin(2 * PI * 2 * smoothstep(0f, 0.4f, t)).toFloat() * 0.7f
    return lookAt(pose, Offset(x, 0.2f), reading)
}

internal fun learnedCheck(t: Float): Float = smoothstep(0.42f, 0.58f, t)
