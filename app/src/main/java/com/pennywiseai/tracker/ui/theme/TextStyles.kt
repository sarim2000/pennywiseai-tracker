package com.pennywiseai.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
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

    private const val TABULAR = "tnum"

    /** The one big number on a screen — Home balance, account balance. */
    val heroAmount: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displaySmall.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR
        )

    /** A section total: monthly spend, budget cap, card balance. */
    val amountLarge: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = TABULAR
        )

    /** A summary figure inside a card or tile. */
    val amountMedium: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(
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
}
