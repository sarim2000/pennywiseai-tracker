package com.pennywiseai.tracker.ui.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/**
 * Named styles for the handful of jobs the plain Material roles don't cover on
 * their own — chiefly money.
 *
 * Amounts get `tnum` (tabular figures): every digit takes the same advance
 * width, so a column of amounts in a list lines up on the decimal instead of
 * ragging left and right as digits change. Without it, `₹1,111` and `₹8,888`
 * render at visibly different widths and a scrolling list shimmers.
 *
 * These are derived from the theme typography rather than declared from
 * scratch, so they follow the user's font choice and any future scale change.
 */
object PennyWiseText {

    private const val TABULAR = "lnum, tnum"
    private const val LINING = "lnum"

    /** The one big number on a screen — Home spend, account balance. Proportional: at
     *  this size tabular digits look gapped, and nothing lines up under it. */
    val heroAmount: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displayMedium.copy(
            fontFeatureSettings = LINING,
            // The serif's thin strokes wash out when light on dark.
            fontWeight = if (MaterialTheme.colorScheme.isDark) FontWeight.Medium else MaterialTheme.typography.displayMedium.fontWeight,
        )

    /** A section total: monthly spend, budget left, card balance. */
    val amountLarge: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR)

    /** A summary figure inside a statement line or tile. */
    val amountMedium: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(
            fontSize = 19.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
            fontFeatureSettings = TABULAR
        )

    /** The trailing amount on a transaction / list row. Plain tabular sans, so a column of
     *  them compares at a glance; the serif is for totals and headings. */
    val amountRow: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = TABULAR
        )

    /** A small secondary figure — the native-currency amount under a
     *  converted one, a per-day average. */
    val amountSmall: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontFeatureSettings = TABULAR
        )

    /** Heading above a group of rows: the serif voice, so a section reads like a
     *  heading in a printed statement. Pair with `onSurface`. */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.sp)

    /** The title of a screen, in the top bar. */
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall

    /** The primary line of a list row — merchant, account, setting name. */
    val rowTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)

    /** The supporting line of a list row. Pair with `onSurfaceVariant`. */
    val rowSubtitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium

    /** Timestamps, counts, footnotes. Pair with `onSurfaceVariant`. */
    val metadata: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

    /** Upper-case, widely tracked label above a summary figure ("TOTAL", "AVERAGE"). */
    val overline: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)

    /** All-caps-ish label above a value in a detail sheet. */
    val fieldLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium

    /**
     * Axis ticks, value labels and legends drawn *inside* a chart.
     *
     * Charts render text through `TextMeasurer` on a Canvas, which takes a
     * `TextStyle` rather than a Material role — so each chart had been
     * declaring its own literal, and the app ended up with 9sp, 10sp and 11sp
     * labels doing the same job on adjacent screens. Charts pass this and add
     * only `color` / `textAlign`.
     */
    val chartLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR)

    /**
     * A big figure with its currency set smaller, muted and a little apart, so the digits lead.
     * Only the ends change: digits, separators and the sign keep the figure's own style, and
     * a masked "••••" has no currency to soften.
     */
    fun heroFigure(text: String, muted: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        val first = text.indexOfFirst { it.isDigit() }
        val last = text.indexOfLast { it.isDigit() }
        if (first < 0) return@buildAnnotatedString
        val style = SpanStyle(fontSize = HERO_CURRENCY_SCALE.em, color = muted, letterSpacing = HERO_CURRENCY_TRACKING.em)
        // Amounts carry directional isolates in some locales: those and the sign are left alone.
        val leading = (0 until first).firstOrNull { !isSignOrControl(text[it]) }
        if (leading != null) addStyle(style, leading, first)
        val trailing = (text.lastIndex downTo last + 1).firstOrNull { !isSignOrControl(text[it]) }
        if (trailing != null) addStyle(style, last + 1, trailing + 1)
    }

    private fun isSignOrControl(c: Char) =
        c == '+' || c == '-' || c == '\u2212' || Character.getType(c) == Character.FORMAT.toInt()
    private const val HERO_CURRENCY_SCALE = 0.7f
    private const val HERO_CURRENCY_TRACKING = 0.08f

    /**
     * Smallest size a one-line label may shrink to with `TextAutoSize` before
     * it would rather ellipsize — e.g. the totals-card tiles at a large font
     * scale. Shared so shrinking labels don't each pick their own floor.
     */
    val autoSizeFloor: TextUnit = 8.sp

    /**
     * For a money figure, with `maxLines = 1`: it shrinks to fit, so digits are never clipped
     * or broken across lines. Long totals are ordinary in currencies like IDR and VND.
     */
    fun fit(style: TextStyle): TextAutoSize = TextAutoSize.StepBased(
        minFontSize = autoSizeFloor,
        maxFontSize = style.fontSize
    )
}
