package com.pennywiseai.tracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Semantic dimensions layered on top of the raw [Spacing] scale.
 *
 * Rule of thumb: reach for a token here when the value has a *meaning*
 * ("standard icon", "card padding", "minimum touch target"). Reach for
 * [Spacing] when you just need a gap. Never hard-code a dp literal in a screen
 * — if nothing here fits, add a token so the next screen agrees with this one.
 */
object Dimensions {

    // ── Padding ───────────────────────────────────────────────────────────
    object Padding {
        /** Screen gutter — content inset from the window edge. */
        val content = Spacing.Layout.screenHorizontal

        /** Card internal padding. Matches [content] so a card's text lines up
         *  with unwrapped text on the same screen. */
        val card = 16.dp

        /** Denser card padding for list rows and compact tiles. */
        val cardCompact = 12.dp

        /** Vertical padding for a two-line list row (with 16dp horizontal). */
        val listRowVertical = 12.dp

        /** Dialog / bottom-sheet content padding (Material 3 spec). */
        val dialog = 24.dp

        /** Empty-state block padding. */
        val empty = 32.dp

        /** FAB inset from the screen edge. */
        val fab = 16.dp
    }

    // ── Elevation ─────────────────────────────────────────────────────────
    // The app is predominantly flat: containers are separated by tonal
    // surfaces (surfaceContainerLow/High) rather than shadows. Only genuinely
    // floating things get elevation.
    object Elevation {
        val none = 0.dp
        val card = 0.dp
        val raisedCard = 1.dp
        val bottomBar = 3.dp
        val fab = 6.dp
        val dialog = 8.dp
    }

    // ── Alpha ─────────────────────────────────────────────────────────────
    // Only ever apply these to a *strong* colour role (onSurface, onPrimary…).
    // Dimming an already-muted role such as onSurfaceVariant stacks two
    // reductions and drops below the WCAG AA contrast floor.
    object Alpha {
        const val high = 0.90f
        const val medium = 0.55f
        const val disabled = 0.38f
        const val divider = 0.12f
        const val surface = 0.65f

        /** Secondary text sitting on a coloured container. */
        const val subtitle = 0.75f

        /** Placeholder / skeleton content shown behind an empty state. */
        const val ghost = 0.12f
    }

    // ── Icon sizes ────────────────────────────────────────────────────────
    // Five steps, each with a clear job. Anything outside this set reads as a
    // mistake next to its neighbours.
    object Icon {
        /** Dots, legend swatches, trend arrows glued to text. */
        val tiny = 12.dp

        /** Inline with body text — trailing chevrons in buttons, chip icons. */
        val small = 16.dp

        /** Inline with a title, dense toolbars, list-row trailing affordances. */
        val inline = 20.dp

        /** The default: app-bar actions, list leading icons, standard buttons. */
        val medium = 24.dp

        /** Icon inside a tonal circle, or a prominent standalone glyph. */
        val large = 32.dp

        /** Leading avatar / brand-logo circle in a list row (M3 standard). */
        val list = 40.dp

        /** Alias of [list] for non-list avatars (settings rows, headers). */
        val avatar = 40.dp

        /** Larger avatar — profile headers, prominent settings rows. */
        val avatarLarge = 48.dp

        /** The glyph inside an empty-state circle. */
        val emptyStateGlyph = 32.dp

        /** The empty-state circle itself. */
        val emptyStateContainer = 64.dp

        /** Full-bleed illustrative icon for a whole-screen empty state. */
        val extraLarge = 96.dp
    }

    // ── Corner radius ─────────────────────────────────────────────────────
    // Mirrors MaterialTheme.shapes; use the theme shapes where a Shape is
    // accepted and these only where a raw Dp is needed.
    object CornerRadius {
        /** Interior corners of a row inside a connected/grouped list block. */
        val small = 4.dp
        val medium = 8.dp
        val card = 12.dp
        val large = 16.dp
        val extraLarge = 28.dp

        /** For circular elements. */
        val full = 50.dp
    }

    // ── Component metrics ─────────────────────────────────────────────────
    object Component {
        val bottomBarHeight = 80.dp
        val buttonHeight = 48.dp

        /** Accessibility floor for anything tappable. */
        val minTouchTarget = 48.dp

        /** Compact icon-button footprint — still tappable, visually lighter.
         *  Pair with a 48dp touch target via `minimumInteractiveComponentSize`
         *  or generous surrounding padding. */
        val iconButton = 40.dp

        /** Minimum height of a single-line list row. */
        val listItemMinHeight = 56.dp

        /** Minimum height of a two-line list row (M3 spec). */
        val listItemMinHeightTwoLine = 72.dp

        val dividerThickness = 1.dp

        /** Hairline used to outline cards in dark mode. */
        val hairline = 0.5.dp

        val progressIndicatorSize = 24.dp
        val chipHeight = 32.dp

        /**
         * Height of a determinate progress track (budget spend, loan repayment,
         * download). Budget bars were 10dp, loan bars 6dp and download bars the
         * Material default — three weights for the same idea.
         */
        val progressBarHeight = 8.dp

        /** The colour dot beside a chart legend entry. */
        val legendDot = 10.dp

        /** Standard FAB diameter. */
        val fab = 56.dp

        /** How far a FAB stack sits above the bottom edge, clearing the nav bar. */
        val fabBottomInset = 96.dp

        /**
         * Extra bottom padding a scrolling list needs so its last row can be
         * scrolled clear of a FAB stack. Add to [bottomBarHeight] on screens
         * that also have a bottom nav bar.
         */
        val fabScrollClearance = 112.dp
    }

    // ── Motion ────────────────────────────────────────────────────────────
    object Animation {
        const val short = 120
        const val medium = 250
        const val long = 400
    }
}
