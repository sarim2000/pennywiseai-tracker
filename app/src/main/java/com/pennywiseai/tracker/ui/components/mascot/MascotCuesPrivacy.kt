package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * The owl's own cues and the message one: amounts hidden ([MascotCue.CoverEyes]), shown
 * again ([MascotCue.Peek]), and an unrecognised message taught ([MascotCue.Learned]).
 * See MascotArrival.kt.
 *
 * The owl's wings are part of its body: in its colour, clipped to its outline, so the
 * glyph's silhouette never changes and only a thin cut-out edge tells a wing from the
 * belly. Up, a wing lies over its eye (the hole cut in the glyph, the disc at 96dp) and
 * leaves the beak clear.
 */

/** Amounts hidden: the wings come up over the eyes, which shut under them, hold, and go down. */
internal const val COVER_END = 0.7f

/** How far the wings are up at [t]: 0 down, 1 over the eyes. */
internal fun coverAmount(t: Float): Float = smoothstep(0f, COVER_UP, t) * (1 - smoothstep(COVER_DOWN, COVER_END, t))

/** The eyes shut as the wings come, and the owl ducks a little behind them. */
internal fun coverEyesPose(pose: MascotPose, t: Float): MascotPose {
    val up = coverAmount(t)
    if (up <= 0f) return pose
    return pose.copy(blink = max(pose.blink, up), lift = pose.lift + COVER_DUCK * up)
}

internal fun DrawScope.drawCoverEyes(g: MascotGeometry, t: Float, ink: CueInk) {
    val up = coverAmount(t)
    drawWing(g.body, up, -1f, ink)
    drawWing(g.body, up, 1f, ink)
}

private const val COVER_UP = 0.16f
private const val COVER_DOWN = 0.52f
private const val COVER_DUCK = 1.5f

/**
 * Amounts shown again: the wings come up, the right one drops halfway so that eye
 * peeks out, then both drop with a little hop and the owl looks round.
 */
internal const val PEEK_END = 0.72f

/** How far the wing on [side] (-1 left, 1 right) is up at [t]: the right one lowers first. */
internal fun peekWing(t: Float, side: Float): Float {
    val up = smoothstep(0f, PEEK_UP, t) * (1 - smoothstep(PEEK_DROP, PEEK_DROP + 0.1f, t))
    return if (side > 0f) up * (1 - PEEK_PART * smoothstep(PEEK_PEEP, PEEK_PEEP + 0.1f, t)) else up
}

/** Shut as the wings come, open again as the first one lowers. */
internal fun peekShut(t: Float): Float = smoothstep(0f, PEEK_UP * 0.8f, t) * (1 - smoothstep(PEEK_PEEP, PEEK_PEEP + 0.06f, t))

/** Once both wings are down, a look one way and the other, back to the middle by [PEEK_END]. */
internal fun peekLook(t: Float): Float = sin(2 * PI * smoothstep(PEEK_LOOK, PEEK_END - 0.02f, t)).toFloat() * PEEK_REACH

internal fun peekPose(pose: MascotPose, t: Float): MascotPose {
    val hop = -PEEK_HOP * bump(PEEK_DROP + 0.02f, PEEK_DROP + 0.18f, t)
    val shut = pose.copy(blink = max(pose.blink, peekShut(t)), lift = pose.lift + hop)
    // The look takes the eyes only while it runs, and gives them back by the end.
    val attention = smoothstep(PEEK_LOOK, PEEK_LOOK + 0.03f, t) * (1 - smoothstep(PEEK_END - 0.04f, PEEK_END, t))
    return lookAt(shut, Offset(peekLook(t), 0f), attention)
}

internal fun DrawScope.drawPeek(g: MascotGeometry, t: Float, ink: CueInk) {
    drawWing(g.body, peekWing(t, -1f), -1f, ink)
    drawWing(g.body, peekWing(t, 1f), 1f, ink)
}

private const val PEEK_UP = 0.1f
private const val PEEK_PEEP = 0.16f
private const val PEEK_PART = 0.62f
private const val PEEK_DROP = 0.38f
private const val PEEK_LOOK = 0.46f
private const val PEEK_REACH = 0.75f
private const val PEEK_HOP = 2.5f

/**
 * One wing on [side] (-1 left, 1 right), [up] of the way from below the belly to over its
 * eye: it swings in about its root as it rises. Clipped to the [body], so it slides in
 * from the bottom edge and never adds to the outline.
 */
private fun DrawScope.drawWing(body: Path, up: Float, side: Float, ink: CueInk) {
    if (up <= 0f) return
    val rootX = 50f + side * (50f - lerp(WING_DOWN_ROOT.x, WING_UP_ROOT.x, up))
    val rootY = lerp(WING_DOWN_ROOT.y, WING_UP_ROOT.y, up)
    val wing = ink.scratch
    wingPath(wing)
    clipPath(body) {
        translate(rootX, rootY) {
            rotate(-side * lerp(WING_DOWN_ANGLE, WING_UP_ANGLE, up), pivot = Offset.Zero) {
                drawPath(wing, ink.body)
                if (ink.glyph) {
                    drawPath(wing, ink.mark, style = Stroke(WING_EDGE_GLYPH), blendMode = ink.markBlend)
                } else {
                    drawPath(wing, ink.mark, alpha = WING_EDGE_ALPHA, style = Stroke(WING_EDGE, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** A wing as a teardrop from its root at the origin, its broad tip up: symmetric, so either side. */
private fun wingPath(path: Path) {
    val l = WING_LENGTH
    val w = WING_WIDTH / 2
    val a = WING_WIDTH * 0.12f
    path.reset()
    path.moveTo(0f, 0f)
    path.cubicTo(-a, 0f, -w, -l * 0.35f, -w, -l * 0.62f)
    path.cubicTo(-w, -l * 0.9f, -w * 0.5f, -l, 0f, -l)
    path.cubicTo(w * 0.5f, -l, w, -l * 0.9f, w, -l * 0.62f)
    path.cubicTo(w, -l * 0.35f, a, 0f, 0f, 0f)
    path.close()
}

/** The left wing's root, up (by the flank, so the broad end lies over the eye at 35, 50) and down (below the belly). */
private val WING_UP_ROOT = Offset(20f, 74f)
private val WING_DOWN_ROOT = Offset(24f, 108f)

/** The left wing's lean, clockwise from upright: up it leans in over its eye, down a little out. */
private const val WING_UP_ANGLE = 26f
private const val WING_DOWN_ANGLE = -5f
private const val WING_LENGTH = 44f
private const val WING_WIDTH = 28f

/** The glyph's wing edge: cut out, so it reads as a wing over the body at 24dp. */
private const val WING_EDGE_GLYPH = 2.6f
private const val WING_EDGE = 1.6f
private const val WING_EDGE_ALPHA = 0.4f

/** Up from 0 to 1 and back between [from] and [to], 0 outside. */
private fun bump(from: Float, to: Float, t: Float): Float =
    if (t <= from || t >= to) 0f else sin(PI * smoothstep(from, to, t)).toFloat()

/**
 * A message taught: two lines show in the bubble and its eyes read them, one then the
 * other; the lines go as a check lands with a small nod, and a spark says it's got it.
 */
internal const val LEARNED_END = 0.66f

/** Where the eyes are on the lines at [t]: along the first, back, along the second. */
internal fun learnedGaze(t: Float): Offset {
    val x = when {
        t < LEARNED_LINE_BACK -> -LEARNED_REACH + 2 * LEARNED_REACH * smoothstep(LEARNED_READ_START, LEARNED_LINE_BACK, t)
        t < LEARNED_LINE_TWO -> LEARNED_REACH - 2 * LEARNED_REACH * smoothstep(LEARNED_LINE_BACK, LEARNED_LINE_TWO, t)
        else -> -LEARNED_REACH + 2 * LEARNED_REACH * smoothstep(LEARNED_LINE_TWO, LEARNED_READ_END, t)
    }
    return Offset(x, lerp(LEARNED_LINE_ONE_GAZE, LEARNED_LINE_TWO_GAZE, smoothstep(LEARNED_LINE_BACK, LEARNED_LINE_TWO, t)))
}

/** How much the eyes are on the lines: none before they show, none once read. */
internal fun learnedReading(t: Float): Float = smoothstep(0f, LEARNED_READ_START, t) * (1 - smoothstep(LEARNED_READ_END, LEARNED_READ_END + 0.08f, t))

internal fun learnedPose(pose: MascotPose, t: Float): MascotPose {
    val nodded = pose.copy(lift = pose.lift + LEARNED_NOD * bump(LEARNED_CHECK_IN, LEARNED_END - 0.04f, t))
    return lookAt(nodded, learnedGaze(t), learnedReading(t))
}

internal fun learnedCheck(t: Float): Float = smoothstep(LEARNED_CHECK_IN, LEARNED_CHECK_IN + 0.14f, t)

/** How much the lines show: in as it starts, out as the check lands. */
internal fun learnedLines(t: Float): Float = smoothstep(0f, LEARNED_READ_START, t) * (1 - smoothstep(LEARNED_READ_END + 0.02f, LEARNED_READ_END + 0.12f, t))

/** The spark off the bubble's corner once the check is in. */
internal fun learnedSpark(t: Float): Float = bump(LEARNED_SPARK_FROM, LEARNED_END, t)

internal fun DrawScope.drawLearned(t: Float, ink: CueInk) {
    val lines = learnedLines(t)
    if (lines > 0f) {
        val width = if (ink.glyph) LEARNED_LINE_GLYPH else LEARNED_LINE
        val alpha = lines * if (ink.glyph) 1f else LEARNED_LINE_ALPHA
        for ((y, end) in LEARNED_LINES) {
            drawLine(ink.mark, Offset(LEARNED_LINE_START, y), Offset(end, y), width, StrokeCap.Round, alpha = alpha, blendMode = ink.markBlend)
        }
    }
    val spark = learnedSpark(t)
    if (spark > 0f) drawSparkle(LEARNED_SPARK_AT, LEARNED_SPARK_R, ink.accent, spark, ink.scratch)
}

private const val LEARNED_READ_START = 0.06f
private const val LEARNED_LINE_BACK = 0.22f
private const val LEARNED_LINE_TWO = 0.26f
private const val LEARNED_READ_END = 0.4f
private const val LEARNED_REACH = 0.7f
private const val LEARNED_LINE_ONE_GAZE = 0.45f
private const val LEARNED_LINE_TWO_GAZE = 0.75f
private const val LEARNED_CHECK_IN = 0.44f
private const val LEARNED_NOD = 1.5f
private const val LEARNED_SPARK_FROM = 0.5f

/** The lines' height and where each ends: under the eyes, clear of the check at 67, 57. */
private val LEARNED_LINES = listOf(59f to 54f, 66f to 44f)
private const val LEARNED_LINE_START = 24f
private const val LEARNED_LINE = 2.4f
private const val LEARNED_LINE_GLYPH = 3.3f
private const val LEARNED_LINE_ALPHA = 0.55f
private val LEARNED_SPARK_AT = Offset(90f, 11f)
private const val LEARNED_SPARK_R = 6f
