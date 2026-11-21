package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/*
 * The record-keeping cues: rules applied ([MascotCue.Sorted]), a statement imported
 * ([MascotCue.Read]), a backup kept safe ([MascotCue.Secured]) and a bill paid
 * ([MascotCue.Paid]). See MascotArrival.kt.
 */

/** Up from 0 to 1 and back between [from] and [to], 0 outside. */
private fun pulse(from: Float, to: Float, t: Float): Float =
    if (t <= from || t >= to) 0f else sin(PI * smoothstep(from, to, t)).toFloat()

/*
 * Rules applied: the receipt is squared up like a stack of slips. It jiggles side to side,
 * which shakes its lines loose, then taps down once and they fall back into line; then it
 * takes a check. The glyph has no lines, so there the jiggle and tap carry it.
 */
internal const val SORTED_END = 0.42f

/** Degrees of the jiggle at its widest, before it dies down. */
private const val SORTED_ROLL = 5f
private const val SORTED_JIGGLE_END = 0.2f

/** The tap that squares it, down in box units. */
private const val SORTED_TAP = 2.5f
private const val SORTED_TAP_FROM = 0.18f
private const val SORTED_TAP_TO = 0.3f

/** The jiggle's tilt at [t]: two swings each way, dying down to nothing. */
internal fun sortedRoll(t: Float): Float {
    val u = t / SORTED_JIGGLE_END
    if (u !in 0f..1f) return 0f
    return SORTED_ROLL * sin(2 * PI * 2 * u).toFloat() * (1 - u)
}

/** How far the tap has it down at [t]. */
internal fun sortedTap(t: Float): Float = SORTED_TAP * pulse(SORTED_TAP_FROM, SORTED_TAP_TO, t)

/** How loose the lines are at [t]: shaken out by the jiggle, back in line by the tap. */
internal fun sortedLoose(t: Float): Float = smoothstep(0f, 0.07f, t) * (1 - smoothstep(0.1f, 0.24f, t))

internal fun sortedPose(pose: MascotPose, t: Float): MascotPose {
    // It watches its lines settle, then looks up for the check.
    val looking = smoothstep(0f, 0.06f, t) * (1 - smoothstep(0.24f, 0.34f, t))
    return lookAt(pose, Offset(0f, 0.6f), looking)
        .let { it.copy(roll = it.roll + sortedRoll(t), lift = it.lift + sortedTap(t)) }
}

internal fun sortedCheck(t: Float): Float = smoothstep(0.26f, 0.4f, t)

/**
 * The receipt's two lines (as MascotShapes.kt draws them), knocked askew and back while
 * they are loose: the still ones are covered in the body's colour and drawn again moved.
 * Detailed, and on the receipt, only.
 */
internal fun DrawScope.drawSorted(shape: MascotShape, g: MascotGeometry, t: Float, ink: CueInk) {
    val loose = sortedLoose(t)
    if (ink.glyph || shape != MascotShape.Receipt || loose <= 0f) return
    for (line in RECEIPT_LINES) {
        val cover = line.rect.inflate(1f)
        drawRoundRect(ink.body, cover.topLeft, cover.size, CornerRadius(2.5f))
    }
    for (line in RECEIPT_LINES) {
        translate(line.shift.x * loose, line.shift.y * loose) {
            rotate(line.turn * loose, pivot = line.rect.center) {
                drawRoundRect(ink.mark, line.rect.topLeft, line.rect.size, CornerRadius(2f), alpha = g.detailAlpha)
            }
        }
    }
}

/** A line on the receipt, and where it is knocked to when loose: [shift] box units, [turn] degrees. */
private class ReceiptLine(val rect: Rect, val shift: Offset, val turn: Float)

private val RECEIPT_LINES = listOf(
    ReceiptLine(Rect(36f, 52f, 64f, 55.5f), Offset(5f, -2.5f), -8f),
    ReceiptLine(Rect(36f, 60f, 54f, 63.5f), Offset(-4f, 2f), 10f),
)

/*
 * A statement imported: the eyes read the receipt line by line, left to right with a quick
 * return, top to bottom, leaning a little the way they look, so the glyph reads too; then
 * it takes a check.
 */
internal const val READ_END = 0.62f

private const val READ_FROM = 0.04f
private const val READ_LINE = 0.14f
private const val READ_LINES = 3

/** The share of a line spent reading across it; the rest is the return. */
private const val READ_SWEEP = 0.75f
private const val READ_REACH = 0.55f
private const val READ_TOP = -0.45f
private const val READ_BOTTOM = 0.65f
private const val READ_LEAN = 3f

/** Where the eyes are on the page at [t]: across each line, then back and down to the next. */
internal fun readGaze(t: Float): Offset {
    val u = ((t - READ_FROM) / READ_LINE).coerceIn(0f, READ_LINES.toFloat())
    val line = min(u.toInt(), READ_LINES - 1)
    val within = u - line
    val across = smoothstep(0f, READ_SWEEP, within)
    // The last line has no next one to go back for.
    val back = if (line < READ_LINES - 1) smoothstep(READ_SWEEP, 1f, within) else 0f
    val x = -READ_REACH + 2 * READ_REACH * (across - back)
    val y = READ_TOP + (READ_BOTTOM - READ_TOP) * (line + back) / (READ_LINES - 1)
    return Offset(x, y)
}

/** How much the reading holds the eyes: it takes them, and gives them back once the last line is read. */
internal fun readAttention(t: Float): Float {
    val done = READ_FROM + READ_LINE * READ_LINES
    return smoothstep(0f, 0.05f, t) * (1 - smoothstep(done, done + 0.1f, t))
}

internal fun readPose(pose: MascotPose, t: Float): MascotPose {
    val gaze = readGaze(t)
    val attention = readAttention(t)
    return lookAt(pose, gaze, attention).let { it.copy(roll = it.roll + READ_LEAN * gaze.x * attention) }
}

internal fun readCheck(t: Float): Float = smoothstep(0.48f, 0.6f, t)

/*
 * A backup kept safe: the shield stands up a little as a check lands on it, settles, and
 * a glint runs once across it, catching its corner. No sparkles round it: that's a goal's.
 */
internal const val SECURED_END = 0.5f

private const val SECURED_RISE = 3f
private const val GLINT_FROM = 0.16f
private const val GLINT_TO = 0.4f
private const val GLINT_TILT = 25f
private const val GLINT_WIDTH = 7f
private const val GLINT_ALPHA = 0.6f

/** The one glint off the shield's top corner, as the light leaves it. */
internal val SECURED_TING = Sparkle(Offset(88f, 11f), 6f, delay = 0.34f)
private const val TING_LIFE = 0.16f

/** How high the shield stands at [t], in box units. */
internal fun securedRise(t: Float): Float = SECURED_RISE * smoothstep(0f, 0.14f, t) * (1 - smoothstep(0.2f, 0.36f, t))

internal fun securedPose(pose: MascotPose, t: Float): MascotPose = pose.copy(lift = pose.lift - securedRise(t))

internal fun securedCheck(t: Float): Float = smoothstep(0.06f, 0.2f, t)

/** Where the glint is across the shield at [t], or null when it's not on it. */
internal fun securedGlintX(t: Float): Float? {
    if (t !in GLINT_FROM..GLINT_TO) return null
    return 5f + 90f * smoothstep(GLINT_FROM, GLINT_TO, t)
}

internal fun securedTing(t: Float): Float = pulse(SECURED_TING.delay, SECURED_TING.delay + TING_LIFE, t)

/** The glint, over the body (cut through the glyph), then the corner's glint off it. */
internal fun DrawScope.drawSecured(g: MascotGeometry, t: Float, ink: CueInk) {
    securedGlintX(t)?.let { x ->
        clipPath(g.body) {
            rotate(GLINT_TILT, pivot = Offset(x, 50f)) {
                drawRect(
                    color = ink.light,
                    topLeft = Offset(x - GLINT_WIDTH / 2, -50f),
                    size = Size(GLINT_WIDTH, 200f),
                    alpha = GLINT_ALPHA,
                    blendMode = ink.markBlend,
                )
            }
        }
    }
    val a = securedTing(t)
    if (a > 0f) drawSparkle(SECURED_TING.at, SECURED_TING.radius, ink.accent, a, ink.scratch)
}

/*
 * A bill paid: the bell gives one short ring, two swings that die quickly, with its ring
 * going out from both sides, and a check lands as it comes to rest.
 */
internal const val PAID_END = 0.45f

private const val PAID_ROLL = 11f
private const val PAID_RATE = 20f
private const val PAID_DECAY = 4f
private const val PAID_QUIET = 0.3f

/** The swing at [t]: a quickly damped ring, brought fully to rest by the end. */
internal fun paidRoll(t: Float): Float {
    if (t !in 0f..PAID_END) return 0f
    val s = t * ARRIVAL_MS / 1000f
    return PAID_ROLL * exp(-s * PAID_DECAY) * sin(s * PAID_RATE) * (1 - smoothstep(PAID_QUIET, PAID_END, t))
}

internal fun paidPose(pose: MascotPose, t: Float, @Suppress("UNUSED_PARAMETER") sinceMs: Long): MascotPose =
    pose.copy(roll = pose.roll + paidRoll(t))

internal fun paidCheck(t: Float): Float = smoothstep(0.24f, 0.4f, t)

/** How much of the ring's [inner] or outer arcs shows at [t]. */
internal fun paidRingAlpha(t: Float, inner: Boolean): Float =
    if (inner) pulse(0.02f, 0.22f, t) else pulse(0.08f, 0.28f, t)

/** The ring going out: two arcs each side of the bell's crown, off the body. */
internal fun DrawScope.drawPaid(t: Float, ink: CueInk) {
    val width = if (ink.glyph) 4f else 2.6f
    for (inner in booleanArrayOf(true, false)) {
        val a = paidRingAlpha(t, inner)
        if (a <= 0f) continue
        val r = if (inner) 33f else 41f
        val box = Rect(PAID_RING_CENTER, r)
        for (start in floatArrayOf(-22f, 158f)) {
            drawArc(ink.accent, start, 44f, false, box.topLeft, box.size, alpha = a, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}

/** Where the ring's arcs are centred: the bell's crown. */
private val PAID_RING_CENTER = Offset(50f, 34f)
