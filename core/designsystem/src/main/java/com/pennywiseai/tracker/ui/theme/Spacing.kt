package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The 4dp-base spacing scale. Every gap, margin and padding in the app should
 * come from here (or from a [Dimensions] semantic token that wraps one of
 * these) so the whole UI sits on the same rhythm.
 *
 * Prefer the *semantic* tokens in [Dimensions.Padding] / [Spacing.Layout] when
 * one describes the intent ("gap between sections") — a reader shouldn't have
 * to know that `lg` happens to be the section gap.
 */
object Spacing {
    val none = 0.dp

    /** Hairline separation — icon-to-label inside a chip, stacked meta lines. */
    val xxs = 2.dp

    /** Tight pairing — label above its value, chip internals. */
    val xs = 4.dp

    /** Related items inside one block. */
    val sm = 8.dp

    /** The workhorse: card padding, screen gutters, list row gaps. */
    val md = 16.dp

    /** Between distinct blocks inside a section. */
    val lg = 24.dp

    /** Between sections. */
    val xl = 32.dp

    val xxl = 48.dp
    val xxxl = 64.dp

    /** Odd-but-needed step between [sm] and [md] — 12dp. */
    val smd = 12.dp

    /**
     * Layout-level rhythm. These name the *role* rather than the size, so the
     * scale can be tuned in one place without a sweep through every screen.
     */
    object Layout {
        /** Horizontal gutter from the screen edge to content. */
        val screenHorizontal = 16.dp

        /** Vertical gap between two top-level sections (header + its content). */
        val sectionGap = 20.dp

        /** Gap between a section header and the content it labels. */
        val headerToContent = 8.dp

        /** Gap between sibling rows in a plain (non-grouped) list. */
        val listGap = 8.dp

        /**
         * Gap between rows of a *grouped* list — the connected
         * rounded-corner-block pattern where rows read as one unit.
         * Deliberately tiny; the shape does the separating.
         */
        val groupedListGap = 2.dp

        /** Inset for content nested inside an already-padded card. */
        val nestedContent = 12.dp

        /** Bottom breathing room so the last row clears the nav bar / FABs. */
        val scrollBottomPadding = 24.dp
    }
}
