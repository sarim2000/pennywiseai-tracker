package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private const val TABULAR = "tnum"

/**
 * Tabular figures on any role, for a number that counts in place on a style
 * that isn't one of the money styles below (they already carry it) — e.g. a
 * goal ring's animated percent. Proportional digits would make it jitter
 * sideways on every frame.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

/**
 * Any role drawn as money — square body-family figures with `tnum`, at the
 * role's own size and weight. For an amount sized like a role none of the
 * named [PennyWiseText] money styles match (e.g. `titleLarge`); prefer those
 * when one fits. Never put an amount in a bare heading role: with Google Sans
 * Flex those are rounded and their digits proportional.
 */
@Composable @ReadOnlyComposable
fun TextStyle.money(): TextStyle =
    copy(fontFamily = MaterialTheme.typography.bodyLarge.fontFamily, fontFeatureSettings = TABULAR)

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
 *
 * Money styles also take the *body* family even when sized from a heading
 * role: with Google Sans Flex that keeps figures square (ROND 0) while the
 * words around them are rounded. For SYSTEM and SN Pro the two are the same.
 */
object PennyWiseText {

    private val moneyFamily: FontFamily?
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.fontFamily

    /** The one big number on a screen — Home's month spent, transaction detail,
     *  a Period Review card. */
    val heroAmount: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displaySmallEmphasized.copy(
            fontFamily = moneyFamily,
            fontFeatureSettings = TABULAR
        )

    /** A screen's headline total — the Analytics period total. One line: pair
     *  with `TextAutoSize` rather than letting a long amount wrap or clip. */
    val amountHeadline: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = moneyFamily,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR
        )

    /** A section total: monthly spend, budget cap, card balance. */
    val amountLarge: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = moneyFamily,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR
        )

    /** A summary figure inside a card or tile. */
    val amountMedium: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
            fontFamily = moneyFamily,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR
        )

    /** The trailing amount on a transaction / list row. */
    val amountRow: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            fontFeatureSettings = TABULAR
        )

    /** A small secondary figure — the native-currency amount under a
     *  converted one, a per-day average. */
    val amountSmall: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall.copy(
            fontFeatureSettings = TABULAR
        )

    /** Heading above a group of rows or cards. Pair with `onSurface`. */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall

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
        get() = MaterialTheme.typography.bodySmall

    /** Upper-case, widely tracked label above a summary figure ("TOTAL", "AVERAGE"). */
    val overline: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

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
     * Smallest size a one-line label may shrink to with `TextAutoSize` before
     * it would rather ellipsize — e.g. the totals-card tiles at a large font
     * scale. Shared so shrinking labels don't each pick their own floor.
     */
    val autoSizeFloor: TextUnit = 8.sp
}
