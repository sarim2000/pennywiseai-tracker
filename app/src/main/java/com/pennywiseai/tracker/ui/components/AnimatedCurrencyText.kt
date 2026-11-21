package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexAxes
import com.pennywiseai.tracker.ui.theme.LocalGoogleSansFlexActive
import com.pennywiseai.tracker.ui.theme.withFlexAxes
import com.pennywiseai.tracker.utils.AmountPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * An amount that rolls like an odometer when it changes: only the digit slots
 * whose digit changed roll (up when the amount grew, down when it shrank), while
 * symbols and separators stay put, and with Google Sans Flex the figure pulses
 * a notch of grade and settles.
 *
 * It is still one [Text] — one semantics node, its own `maxLines` and `autoSize`
 * — and the roll is only drawn over it, from that text's own layout. A roll is
 * skipped (the value just swaps) on the first show, while amounts are hidden,
 * with animations off, and whenever the text doesn't sit on one unclipped line
 * or the auto-sized font size changes with the value.
 */
@Composable
fun AnimatedCurrencyText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    autoSize: TextAutoSize? = null,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    // Hiding/showing amounts is a privacy toggle, not a value change: the mask
    // never rolls, and the amount it reveals is a first show, so it doesn't either.
    if (AmountPrivacy.MASK in text || !animationsEnabled) {
        Text(
            text = text,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = maxLines,
            autoSize = autoSize,
            modifier = modifier,
        )
        return
    }

    val motion = MaterialTheme.motionScheme
    val pulses = LocalGoogleSansFlexActive.current
    val scope = rememberCoroutineScope()
    val odometer = remember { Odometer(scope) }
    // Effects specs, not spatial ones: money settles without overshooting its slot.
    val rollSpec = motion.slowEffectsSpec<Float>()
    val pulseSpec = motion.slowEffectsSpec<Float>()
    SideEffect {
        odometer.rollSpec = rollSpec
        odometer.pulseSpec = pulseSpec
        odometer.pulses = pulses
    }
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    Text(
        text = text,
        // Grade, not weight: it never changes an advance width, so the slots the
        // roll is drawn in stay where the text lays them out.
        style = if (pulses) style.withFlexAxes(gradeBoost = odometer.gradeBoost) else style,
        color = textColor,
        fontWeight = fontWeight,
        maxLines = maxLines,
        autoSize = autoSize,
        onTextLayout = odometer::onLayout,
        modifier = modifier.drawWithContent { with(odometer) { draw(textColor) } },
    )
}

/** The roll in flight: the old text's layout and where each glyph goes. */
private class Roll(val old: TextLayoutResult, val newWidth: Int, val pieces: List<Piece>, val rollsUp: Boolean)

/**
 * One clipped region of the roll, in the new text's coordinates. A region starts
 * [shift] away from where the new layout puts it (its old place) and slides home.
 */
private sealed interface Piece {
    val left: Float
    val right: Float
    val shift: Float

    /** Glyphs that stay the same; adjacent ones with the same shift share a piece. */
    class Slide(override val left: Float, override val right: Float, override val shift: Float) : Piece

    /** A digit slot rolling from the old layout's glyph at [oldLeft] to the new one. */
    class Turn(
        override val left: Float,
        override val right: Float,
        override val shift: Float,
        val oldLeft: Float,
        val delay: Float,
    ) : Piece

    /** A new glyph rolling in from blank. */
    class Enter(override val left: Float, override val right: Float, val delay: Float) : Piece {
        override val shift = 0f
    }

    /** An old glyph rolling out; [left]/[right] are its old box, [shift] the anchor offset. */
    class Exit(override val left: Float, override val right: Float, override val shift: Float, val delay: Float) :
        Piece
}

private class Odometer(private val scope: CoroutineScope) {
    var rollSpec: FiniteAnimationSpec<Float> = snap()
    var pulseSpec: FiniteAnimationSpec<Float> = snap()
    var pulses = false

    private val pulse = Animatable(0f)

    /** The pulse's grade, on the grid, so composition only reruns when it steps. */
    val gradeBoost: Float by derivedStateOf {
        val step = GoogleSansFlexAxes.GRADE_STEP
        (pulse.value * GoogleSansFlexAxes.EMPHASIS_GRADE / step).roundToInt() * step.toFloat()
    }

    private var layout: TextLayoutResult? = null
    private var roll by mutableStateOf<Roll?>(null)
    private var progress by mutableFloatStateOf(1f)
    private var rollJob: Job? = null
    private var pulseJob: Job? = null

    fun onLayout(new: TextLayoutResult) {
        val old = layout
        layout = new
        if (old == null) return // first show: nothing to roll from
        if (old.layoutInput.text == new.layoutInput.text) {
            // Laid out again in place (the pulse's grade, the same constraints): a
            // roll stays valid only while the glyphs it was planned on haven't moved.
            if (roll != null && !sameGeometry(old, new)) stopRoll()
            return
        }
        startRoll(old, new)
        if (pulses) {
            pulseJob?.cancel()
            pulseJob = scope.launch {
                pulse.snapTo(1f)
                pulse.animateTo(0f, pulseSpec)
            }
        }
    }

    private fun startRoll(old: TextLayoutResult, new: TextLayoutResult) {
        val plan = odometerPlan(old.layoutInput.text.text, new.layoutInput.text.text)
        if (plan == null || !plan.changed || !fitsOneLine(old) || !fitsOneLine(new) ||
            old.layoutInput.style.fontSize != new.layoutInput.style.fontSize
        ) {
            stopRoll()
            return
        }
        // Start-anchored: in RTL a width change moves the left edge, so old x
        // positions shift by the difference.
        val anchor = if (new.layoutInput.layoutDirection == LayoutDirection.Rtl) {
            (new.size.width - old.size.width).toFloat()
        } else {
            0f
        }
        val pieces = ArrayList<Piece>()
        for (move in plan.moves) {
            val box = new.getBoundingBox(move.newIndex)
            if (box.width <= 0f) continue
            val oldLeft = move.oldIndex?.let { old.getBoundingBox(it).left }
            val shift = if (oldLeft != null) oldLeft + anchor - box.left else 0f
            when (move.change) {
                GlyphChange.Same -> {
                    val last = pieces.lastOrNull()
                    if (last is Piece.Slide && last.shift == shift && last.right == box.left) {
                        pieces[pieces.lastIndex] = Piece.Slide(last.left, box.right, shift)
                    } else {
                        pieces += Piece.Slide(box.left, box.right, shift)
                    }
                }
                GlyphChange.Roll -> pieces += Piece.Turn(box.left, box.right, shift, oldLeft!!, delayOf(move.rank))
                GlyphChange.Enter -> pieces += Piece.Enter(box.left, box.right, delayOf(move.rank))
            }
        }
        for (exit in plan.exits) {
            val box = old.getBoundingBox(exit.oldIndex)
            if (box.width > 0f) pieces += Piece.Exit(box.left, box.right, anchor, delayOf(exit.rank))
        }
        roll = Roll(old, new.size.width, pieces, plan.rollsUp)
        progress = 0f
        rollJob?.cancel()
        rollJob = scope.launch {
            animate(0f, 1f, animationSpec = rollSpec) { value, _ -> progress = value }
            roll = null
        }
    }

    private fun stopRoll() {
        rollJob?.cancel()
        roll = null
        progress = 1f
    }

    fun ContentDrawScope.draw(color: Color) {
        val roll = roll
        val new = layout
        if (roll == null || new == null || new.size.width != roll.newWidth) {
            drawContent()
            return
        }
        val p = progress
        val top = new.getLineTop(0)
        val bottom = new.getLineBottom(0)
        // Up: the old glyph leaves through the top and the new one comes from below.
        val travel = (bottom - top) * if (roll.rollsUp) -1f else 1f
        for (piece in roll.pieces) {
            val dx = piece.shift * (1f - p)
            when (piece) {
                // Unchanged glyphs don't roll: no need to cut them at the line box.
                is Piece.Slide -> clipRect(piece.left + dx, -size.height, piece.right + dx, 2 * size.height) {
                    translate(left = dx) { drawText(new, color) }
                }
                is Piece.Turn -> {
                    val t = stagger(p, piece.delay)
                    clipRect(piece.left + dx, top, piece.right + dx, bottom) {
                        translate(piece.left + dx - piece.oldLeft, travel * t) { drawText(roll.old, color) }
                        translate(dx, -travel * (1f - t)) { drawText(new, color) }
                    }
                }
                is Piece.Enter -> {
                    val t = stagger(p, piece.delay)
                    clipRect(piece.left, top, piece.right, bottom) {
                        translate(top = -travel * (1f - t)) { drawText(new, color, alpha = t) }
                    }
                }
                is Piece.Exit -> {
                    val t = stagger(p, piece.delay)
                    clipRect(piece.left + piece.shift, top, piece.right + piece.shift, bottom) {
                        translate(piece.shift, travel * t) { drawText(roll.old, color, alpha = 1f - t) }
                    }
                }
            }
        }
    }
}

/** Each step of rank waits a little longer, so the roll ripples from the units up. */
private fun delayOf(rank: Int): Float = STAGGER * rank.coerceAtMost(MAX_STAGGER_STEPS)

private fun stagger(progress: Float, delay: Float): Float = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)

private const val STAGGER = 0.05f
private const val MAX_STAGGER_STEPS = 6

private fun fitsOneLine(layout: TextLayoutResult) = layout.lineCount == 1 && !layout.hasVisualOverflow

private fun sameGeometry(a: TextLayoutResult, b: TextLayoutResult) =
    a.size == b.size && a.layoutInput.style.fontSize == b.layoutInput.style.fontSize
