package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.utils.AmountPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * An amount that rolls like an odometer when it changes: only the digit slots
 * whose digit changed roll (up when the amount grew, down when it shrank), while
 * symbols and separators stay put. It works the same in every font: amounts
 * use tabular figures, so a digit slot keeps its width.
 *
 * It is still one [Text] — one semantics node, its own `maxLines` and `autoSize`
 * — and the roll is only drawn over it, from that text's own layout. The first
 * time it shows on a screen it rolls in from zero, once (saved across rotation
 * and tab returns). A roll is skipped (the value just swaps)
 * while amounts are hidden, with animations off, whenever the text doesn't sit
 * on one unclipped line or the auto-sized font size changes with the value,
 * and when a new amount arrives while a roll is still running.
 *
 * [decimalSeparator] is the separator of the formatter that produced [text].
 * Without it a three-decimal amount beside a whole one rolls the wrong way.
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
    decimalSeparator: Char? = null,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    // The first time the amount shows on this screen it rolls in from zero, once:
    // saved, so a rotation or coming back to the tab doesn't replay it. Above the
    // mask, so an amount first shown hidden rolls in when it's revealed.
    var rolledIn by rememberSaveable { mutableStateOf(false) }
    // Hiding/showing amounts is a privacy toggle, not a value change: the mask
    // never rolls.
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
    val scope = rememberCoroutineScope()
    // No cache: each roll measures its masks once and holds them itself.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val odometer = remember(measurer) { Odometer(scope, measurer) }
    // Effects specs, not spatial ones: money settles without overshooting its slot.
    val rollSpec = motion.slowEffectsSpec<Float>()
    SideEffect {
        odometer.rollSpec = rollSpec
        odometer.decimalSeparator = decimalSeparator
        odometer.rollIn = !rolledIn
        odometer.onRolledIn = { rolledIn = true }
    }
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    Text(
        text = text,
        style = style,
        color = textColor,
        fontWeight = fontWeight,
        maxLines = maxLines,
        autoSize = autoSize,
        onTextLayout = odometer::onLayout,
        modifier = modifier.drawWithContent { with(odometer) { draw(textColor) } },
    )
}

/**
 * The roll in flight. Every piece is drawn from a copy of a layout in which only
 * its own characters are visible ([masked]), never by clipping a glyph's box out
 * of the full text: a glyph's ink can overhang its advance (Google Sans Flex's
 * comma reaches under the digit before it), so a box clip would carry a sliver
 * of the comma along with a rolling digit and cut the rest of it short.
 */
private class Roll(
    val newWidth: Int,
    val oldWidth: Int,
    /** Old layout's x shift: 0 in LTR; in RTL, the amount the left edge moved. */
    val anchor: Float,
    val pieces: List<Piece>,
    val rollsUp: Boolean,
    /** Old-text masks by character range, built once. */
    val old: Map<IntRange, TextLayoutResult>,
    /** New-text masks by character range, built once. */
    val new: Map<IntRange, TextLayoutResult>,
)

/** One moving part of the roll; [shift] is how far its glyphs start from home. */
private sealed interface Piece {
    val newRange: IntRange? get() = null
    val oldRange: IntRange? get() = null

    /** Glyphs that stay the same; adjacent ones with the same shift share a piece. */
    class Slide(override val newRange: IntRange, val shift: Float) : Piece

    /** A digit slot rolling from old glyph [oldIndex] (at [oldLeft]) to new glyph [newIndex] (at [left]). */
    class Turn(
        newIndex: Int,
        oldIndex: Int,
        val left: Float,
        val oldLeft: Float,
        val shift: Float,
    ) : Piece {
        override val newRange = newIndex..newIndex
        override val oldRange = oldIndex..oldIndex
    }

    /** A new glyph rolling in from blank. */
    class Enter(newIndex: Int) : Piece {
        override val newRange = newIndex..newIndex
    }

    /** An old glyph rolling out; [shift] is the start-anchor offset. */
    class Exit(oldIndex: Int, val shift: Float) : Piece {
        override val oldRange = oldIndex..oldIndex
    }
}

private class Odometer(private val scope: CoroutineScope, private val measurer: TextMeasurer) {
    var rollSpec: FiniteAnimationSpec<Float> = snap()
    var decimalSeparator: Char? = null

    /** Whether the first layout rolls in from zero instead of just showing. */
    var rollIn = false
    var onRolledIn: () -> Unit = {}

    private var layout: TextLayoutResult? = null
    private var roll by mutableStateOf<Roll?>(null)
    private var progress by mutableFloatStateOf(1f)
    private var holding by mutableStateOf(false)
    private var rollJob: Job? = null

    fun onLayout(new: TextLayoutResult) {
        val old = layout
        layout = new
        if (old == null) {
            // First show: roll in from the same amount with every digit at 0, so each
            // slot turns up to its digit in the one beat a change uses.
            if (rollIn) {
                onRolledIn()
                startRoll(relaid(new, AnnotatedString(zeroed(new.layoutInput.text.text))), new, rollingIn = true)
            }
            return
        }
        if (old.layoutInput.text == new.layoutInput.text) {
            // Laid out again in place: a roll stays valid only while the glyphs
            // it was planned on haven't moved.
            if (roll != null && !sameGeometry(old, new)) stopRoll()
            return
        }
        if (roll != null) {
            // `old` is the target this roll has not reached. A replacement roll
            // would start from a figure that never settled, so show the latest
            // value. The next change rolls from that settled value.
            stopRoll()
            return
        }
        startRoll(old, new)
    }

    /** An ineligible amount just swaps. */
    private fun startRoll(old: TextLayoutResult, new: TextLayoutResult, rollingIn: Boolean = false) {
        val plan = odometerPlan(old.layoutInput.text.text, new.layoutInput.text.text, decimalSeparator)
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
                    if (last is Piece.Slide && last.shift == shift && last.newRange.last == move.newIndex - 1) {
                        pieces[pieces.lastIndex] = Piece.Slide(last.newRange.first..move.newIndex, shift)
                    } else {
                        pieces += Piece.Slide(move.newIndex..move.newIndex, shift)
                    }
                }
                GlyphChange.Roll -> pieces += Piece.Turn(
                    move.newIndex, move.oldIndex!!, box.left, oldLeft!!, shift,
                )
                GlyphChange.Enter -> pieces += Piece.Enter(move.newIndex)
            }
        }
        for (exit in plan.exits) {
            if (old.getBoundingBox(exit.oldIndex).width > 0f) pieces += Piece.Exit(exit.oldIndex, anchor)
        }
        roll = Roll(
            newWidth = new.size.width,
            oldWidth = old.size.width,
            anchor = anchor,
            pieces = pieces,
            rollsUp = plan.rollsUp,
            old = masks(old, pieces.mapNotNull { it.oldRange }),
            new = masks(new, pieces.mapNotNull { it.newRange }),
        )
        progress = 0f
        rollJob?.cancel()
        // A screen's first frames are its heaviest: a roll-in waits, blank, for frames
        // to come steadily, or it's spent before it's drawn and "0,000" sits there.
        holding = rollingIn
        rollJob = scope.launch {
            if (rollingIn) {
                awaitSteadyFrames()
                holding = false
            }
            animate(0f, 1f, animationSpec = rollSpec) { value, _ -> progress = value }
            roll = null
        }
    }

    private fun stopRoll() {
        rollJob?.cancel()
        roll = null
        holding = false
        progress = 1f
    }

    /**
     * [layout]'s text re-measured once per range with every other character
     * transparent: same input, so the same glyph positions, kerning and shaping.
     */
    private fun masks(layout: TextLayoutResult, ranges: List<IntRange>): Map<IntRange, TextLayoutResult> {
        val text = layout.layoutInput.text
        return ranges.associateWith { range ->
            relaid(
                layout,
                buildAnnotatedString {
                    append(text)
                    if (range.first > 0) addStyle(Hidden, 0, range.first)
                    if (range.last + 1 < text.length) addStyle(Hidden, range.last + 1, text.length)
                },
            )
        }
    }

    /** [text] measured exactly as [layout]'s own text was. */
    private fun relaid(layout: TextLayoutResult, text: AnnotatedString): TextLayoutResult {
        val input = layout.layoutInput
        return measurer.measure(
            text = text,
            style = input.style,
            overflow = input.overflow,
            softWrap = input.softWrap,
            maxLines = input.maxLines,
            placeholders = input.placeholders,
            constraints = input.constraints,
            layoutDirection = input.layoutDirection,
            density = input.density,
            fontFamilyResolver = input.fontFamilyResolver,
        )
    }

    fun ContentDrawScope.draw(color: Color) {
        val roll = roll
        val new = layout
        if (roll == null || new == null || new.size.width != roll.newWidth) {
            drawContent()
            return
        }
        if (holding) return
        // One beat for every slot (design rule 2): a staggered roll would show the
        // units' new digit beside the old hundreds, a number neither side has.
        val p = progress
        val top = new.getLineTop(0)
        val bottom = new.getLineBottom(0)
        // Up: the old glyph leaves through the top and the new one comes from below.
        val travel = (bottom - top) * if (roll.rollsUp) -1f else 1f
        // Cut rolling glyphs at the line box only. Horizontally the clip covers
        // both anchored layouts, so a much shorter amount doesn't slice off the
        // outgoing digits (twice the new width used to).
        val clip = rollClip(size.width, roll.oldWidth.toFloat(), roll.anchor, size.width)
        for (piece in roll.pieces) {
            val newMask = piece.newRange?.let { roll.new[it] }
            val oldMask = piece.oldRange?.let { roll.old[it] }
            when (piece) {
                // Unchanged glyphs don't roll: no need to cut them at all.
                is Piece.Slide -> translate(left = piece.shift * (1f - p)) { newMask?.let { drawText(it, color) } }
                is Piece.Turn -> {
                    val dx = piece.shift * (1f - p)
                    clipRect(clip.left, top, clip.right, bottom) {
                        translate(piece.left + dx - piece.oldLeft, travel * p) { oldMask?.let { drawText(it, color) } }
                        translate(dx, -travel * (1f - p)) { newMask?.let { drawText(it, color) } }
                    }
                }
                is Piece.Enter -> {
                    clipRect(clip.left, top, clip.right, bottom) {
                        translate(top = -travel * (1f - p)) { newMask?.let { drawText(it, color, alpha = p) } }
                    }
                }
                is Piece.Exit -> {
                    clipRect(clip.left, top, clip.right, bottom) {
                        translate(piece.shift, travel * p) { oldMask?.let { drawText(it, color, alpha = 1f - p) } }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal clip for a roll: both anchored layouts, plus [pad] so ink that
 * overhangs a layout isn't cut at the side. [anchor] is the old layout's x
 * shift (0 in LTR; negative in RTL when the old text is wider).
 */
internal fun rollClip(newWidth: Float, oldWidth: Float, anchor: Float, pad: Float) = RollClip(
    left = minOf(0f, anchor) - pad,
    right = maxOf(newWidth, oldWidth + anchor) + pad,
)

internal data class RollClip(val left: Float, val right: Float)

/**
 * Returns once frames arrive at a steady pace ([STEADY_FRAMES] in a row, each
 * within [STEADY_FRAME_NANOS] of the last), so what follows is actually drawn.
 * Gives up after [MAX_WAIT_FRAMES] on a device that never gets there.
 */
private suspend fun awaitSteadyFrames() {
    var last = withFrameNanos { it }
    var steady = 0
    var frames = 0
    while (steady < STEADY_FRAMES && frames < MAX_WAIT_FRAMES) {
        val now = withFrameNanos { it }
        steady = if (now - last <= STEADY_FRAME_NANOS) steady + 1 else 0
        last = now
        frames++
    }
}

private const val STEADY_FRAMES = 3
private const val STEADY_FRAME_NANOS = 50_000_000L
private const val MAX_WAIT_FRAMES = 12

/** [text] with every digit turned to 0: what a figure rolls in from. Same length, same slots. */
internal fun zeroed(text: String): String = text.map { if (it.isDigit()) '0' else it }.joinToString("")

/** Paints a character nothing, without changing how the text measures or shapes. */
private val Hidden = SpanStyle(color = Color.Transparent)

private fun fitsOneLine(layout: TextLayoutResult) = layout.lineCount == 1 && !layout.hasVisualOverflow

private fun sameGeometry(a: TextLayoutResult, b: TextLayoutResult) =
    a.size == b.size && a.layoutInput.style.fontSize == b.layoutInput.style.fontSize
