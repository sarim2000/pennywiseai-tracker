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
 *
 * [decimalSeparator] is the separator of the formatter that produced both
 * strings. Omit it only when that formatter isn't known; [decimalSeparatorOf]
 * then guesses, and it guesses wrong for a three-decimal amount beside a whole
 * one (`KWD 12.345` → `KWD 13`).
 */
internal fun odometerPlan(old: String, new: String, decimalSeparator: Char? = null): OdometerPlan? {
    val point = decimalSeparator ?: decimalSeparatorOf(old, new)
    val oldParts = AmountParts.of(old, point) ?: return null
    val newParts = AmountParts.of(new, point) ?: return null
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
 * Positive when [new] is larger. The formatter drops trailing zeros (`$448.6`,
 * `$461`), so each side's digits are scaled to the same number of fraction
 * digits before comparing.
 */
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
 * Guesses the decimal separator two amounts share, or null when neither shows
 * one. Pass the formatter's separator to [odometerPlan] instead when you have
 * it: this guess can't tell `12.345` (twelve dinars) from a thousands group
 * once the other amount is whole (`13`), because the formatter drops the
 * fraction and three digits is also how grouping looks.
 *
 * Both strings vote, strongest evidence first: two different separators (the
 * last is decimal, `1.234,5`); then a final separator followed by one or two
 * digits (no grouping is that short). Failing both, a lone `.`/`,` with three
 * digits after it is read as grouping in both strings — consistently, so
 * digits still pair up and compare correctly.
 */
internal fun decimalSeparatorOf(a: String, b: String): Char? {
    val cores = listOf(a, b).mapNotNull { text ->
        val first = text.indexOfFirst { it.isDigit() }
        if (first < 0) null else text.substring(first, text.indexOfLast { it.isDigit() } + 1)
    }
    cores.forEach { core ->
        val separators = core.filterNot { it.isDigit() }
        if (separators.toSet().size > 1) return separators.last()
    }
    cores.forEach { core ->
        val last = core.indexOfLast { !it.isDigit() }
        if (last >= 0 && core.length - 1 - last in 1..2) return core[last]
    }
    return null
}

/**
 * A formatted amount split around its numeric core, and the core split into its
 * [integer] part and its [fraction] (the decimal separator and the digits after
 * it; empty for a whole amount). [decimal] comes from [decimalSeparatorOf].
 */
private class AmountParts(
    val text: String,
    val prefix: IntRange,
    val core: IntRange,
    val suffix: IntRange,
    decimal: Char?,
) {

    private val point: Int? = decimal?.let { d -> core.lastOrNull { text[it] == d } }

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
        fun of(text: String, decimal: Char?): AmountParts? {
            val first = text.indexOfFirst { it.isDigit() }
            if (first < 0) return null
            val last = text.indexOfLast { it.isDigit() }
            return AmountParts(text, 0 until first, first..last, last + 1 until text.length, decimal)
        }
    }
}
