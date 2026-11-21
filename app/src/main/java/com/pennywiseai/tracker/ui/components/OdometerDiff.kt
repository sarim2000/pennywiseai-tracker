package com.pennywiseai.tracker.ui.components

import java.math.BigInteger

/** What one character does when an amount changes from one formatted string to the next. */
internal enum class GlyphChange {
    /** The same character in the matching slot: it stays (sliding over if the length changed). */
    Same,

    /** A digit replaced by another digit: the slot rolls. */
    Roll,

    /** A character with no counterpart in the old string, e.g. the `1,` of `₹999` → `₹1,000`. */
    Enter,
}

/**
 * Character [newIndex] of the new string and, when it has one, its counterpart
 * [oldIndex] in the old string. [rank] orders the moving slots from the least
 * significant digit (0) up, for a right-to-left stagger.
 */
internal data class GlyphMove(val newIndex: Int, val oldIndex: Int?, val change: GlyphChange, val rank: Int = 0)

/** Character [oldIndex] of the old string, which has no counterpart and rolls out. */
internal data class GlyphExit(val oldIndex: Int, val rank: Int)

/**
 * How [AnimatedCurrencyText] moves from one formatted amount to the next.
 * [moves] covers every character of the new string, in order; [exits] are the
 * old characters with no counterpart, which roll out. [rollsUp] is the roll
 * direction: up when the amount grew or the direction is unknown.
 */
internal data class OdometerPlan(val moves: List<GlyphMove>, val exits: List<GlyphExit>, val rollsUp: Boolean) {
    val changed: Boolean get() = exits.isNotEmpty() || moves.any { it.change != GlyphChange.Same }
}

/**
 * Pairs the characters of two formatted amounts, or null when either has no
 * digits (a word swapped for a number: nothing to roll).
 *
 * A formatted amount is a prefix (sign, currency symbol), a numeric core (first
 * digit to last digit) and a suffix (a trailing symbol or code). The core is
 * split on its decimal separator: the integer part is aligned from the right so
 * units stay over units when the number gains or loses digits, and the fraction
 * (separator included) from the left, so `$461` → `$448.6` keeps `461` over
 * `448` and brings `.6` in rather than pairing `61` with `.6`. The prefix is
 * aligned from the right too, so `₹` still pairs with `₹` when a minus sign
 * appears before it; the suffix is aligned from the left. Amounts use tabular
 * figures, so a digit paired with a digit occupies the same width and can roll
 * in place.
 */
internal fun odometerPlan(old: String, new: String): OdometerPlan? {
    val oldParts = AmountParts.of(old) ?: return null
    val newParts = AmountParts.of(new) ?: return null
    val pairs = ArrayList<Pair<Int?, Int?>>(maxOf(old.length, new.length))
    pairs += alignEnd(oldParts.prefix, newParts.prefix)
    pairs += alignEnd(oldParts.integer, newParts.integer)
    pairs += alignStart(oldParts.fraction, newParts.fraction)
    pairs += alignStart(oldParts.suffix, newParts.suffix)

    val moves = arrayOfNulls<GlyphMove>(new.length)
    val exits = ArrayList<GlyphExit>()
    val rankOf = { index: Int -> (newParts.core.last - index).coerceAtLeast(0) }
    val oldRankOf = { index: Int -> (oldParts.core.last - index).coerceAtLeast(0) }
    for ((o, n) in pairs) {
        when {
            n == null -> exits += GlyphExit(o!!, oldRankOf(o))
            o == null -> moves[n] = GlyphMove(n, null, GlyphChange.Enter, rankOf(n))
            old[o] == new[n] -> moves[n] = GlyphMove(n, o, GlyphChange.Same)
            old[o].isDigit() && new[n].isDigit() -> moves[n] = GlyphMove(n, o, GlyphChange.Roll, rankOf(n))
            else -> {
                moves[n] = GlyphMove(n, null, GlyphChange.Enter, rankOf(n))
                exits += GlyphExit(o, oldRankOf(o))
            }
        }
    }
    return OdometerPlan(
        moves = moves.map { checkNotNull(it) },
        exits = exits,
        rollsUp = compareValues(oldParts, newParts) >= 0,
    )
}

/**
 * Compares two formatted amounts by value: positive when [new] is larger, null
 * when either has no digits. The formatter drops trailing zeros (`$448.6`,
 * `$461`), so each side's digits are scaled to the same number of fraction
 * digits before comparing.
 */
internal fun compareAmounts(old: String, new: String): Int? {
    val a = AmountParts.of(old) ?: return null
    val b = AmountParts.of(new) ?: return null
    return compareValues(a, b)
}

private fun compareValues(old: AmountParts, new: AmountParts): Int {
    val (a, aScale) = old.value
    val (b, bScale) = new.value
    val scale = maxOf(aScale, bScale)
    return b.multiply(BigInteger.TEN.pow(scale - bScale)).compareTo(a.multiply(BigInteger.TEN.pow(scale - aScale)))
}

private fun alignEnd(old: IntRange, new: IntRange): List<Pair<Int?, Int?>> {
    val n = maxOf(old.count(), new.count())
    return (n downTo 1).map { k ->
        (old.last + 1 - k).takeIf { it >= old.first } to (new.last + 1 - k).takeIf { it >= new.first }
    }
}

private fun alignStart(old: IntRange, new: IntRange): List<Pair<Int?, Int?>> {
    val n = maxOf(old.count(), new.count())
    return (0 until n).map { k ->
        (old.first + k).takeIf { it <= old.last } to (new.first + k).takeIf { it <= new.last }
    }
}

/**
 * A formatted amount split around its numeric core, and the core split into its
 * [integer] part and its [fraction] (the decimal separator and the digits after
 * it; empty for a whole amount). The decimal separator is the last separator
 * when one or two digits follow it; three is a thousands group.
 */
private class AmountParts(val text: String, val prefix: IntRange, val core: IntRange, val suffix: IntRange) {

    private val point: Int? = core.lastOrNull { !text[it].isDigit() }?.takeIf { core.last - it in 1..2 }

    val integer: IntRange get() = core.first until (point ?: core.last + 1)

    val fraction: IntRange get() = (point ?: core.last + 1)..core.last

    /** Signed digits as one integer, and the fraction-digit count they're scaled by. */
    val value: Pair<BigInteger, Int>
        get() {
            var digits = BigInteger.ZERO
            for (i in core) {
                val d = text[i].digitToIntOrNull() ?: continue
                digits = digits.multiply(BigInteger.TEN).add(BigInteger.valueOf(d.toLong()))
            }
            val negative = text.substring(prefix).any { it == '-' || it == '−' || it == '(' }
            val scale = point?.let { core.last - it } ?: 0
            return (if (negative) digits.negate() else digits) to scale
        }

    companion object {
        fun of(text: String): AmountParts? {
            val first = text.indexOfFirst { it.isDigit() }
            if (first < 0) return null
            val last = text.indexOfLast { it.isDigit() }
            return AmountParts(text, 0 until first, first..last, last + 1 until text.length)
        }
    }
}
