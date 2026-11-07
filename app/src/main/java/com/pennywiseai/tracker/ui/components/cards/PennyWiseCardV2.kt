package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import com.pennywiseai.tracker.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The app's standard card container.
 *
 * A sheet laid on the page: `shapes.large` corners, `surfaceContainerLow` fill, a hairline
 * edge and no elevation. Use it only for things that aren't a ledger row, which is most
 * of what used to be a card.
 *
 * Pass [contentPadding] rather than padding the content yourself, so the
 * ripple on a clickable card covers the whole surface.
 */
@Composable
fun PennyWiseCardV2(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    /**
     * Default-only convenience for callers that just want to swap the
     * container colour (e.g. a selected-state tint) without constructing a
     * full [CardColors]. Wired into the default value of [colors]; if a
     * caller passes [colors] explicitly, that wins and this value is unused.
     */
    containerColor: androidx.compose.ui.graphics.Color? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow
    ),
    elevation: CardElevation = CardDefaults.cardElevation(
        defaultElevation = Dimensions.Elevation.card
    ),
    border: BorderStroke? = BorderStroke(
        Dimensions.Component.hairline,
        MaterialTheme.colorScheme.outlineVariant
    ),
    onClick: (() -> Unit)? = null,
    /**
     * Optional long-press handler. Routes the card through
     * [Modifier.combinedClickable] so tap and long-press resolve on the same
     * gesture surface — important when the card lives inside a scrolling
     * container or another drag-aware parent (e.g. SwipeToDismissBox) that
     * would otherwise race with a child pointerInput.
     *
     * **Requires [onClick]** to also be non-null. A long-press-only card
     * would still announce as a button to accessibility but no-op on tap;
     * we fail fast rather than ship that affordance.
     */
    onLongClick: (() -> Unit)? = null,
    contentPadding: Dp = Dimensions.Padding.card,
    /** No sheet at all, just the content on the page with the row gutter. The ledger default
     *  for summaries and single rows; the sheet is for what must lift off the page. */
    flat: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveBorder = if (flat) null else border
    val effectiveColors = if (flat) CardDefaults.cardColors(containerColor = Color.Transparent) else colors
    val innerPadding = if (flat) PaddingValues(vertical = Spacing.sm) else PaddingValues(contentPadding)

    when {
        onLongClick != null -> {
            // Combined click + long-click. Material's Card composable doesn't
            // accept onLongClick directly, so we wrap a non-clickable Card with
            // combinedClickable on the outer modifier. Require onClick here so
            // the card never advertises a button affordance whose tap is a
            // no-op (would mislead screen readers and touch users).
            val tap = requireNotNull(onClick) {
                "PennyWiseCardV2: onLongClick requires onClick to also be non-null."
            }
            Card(
                modifier = modifier.combinedClickable(
                    onClick = tap,
                    onLongClick = onLongClick
                ),
                colors = effectiveColors,
                shape = shape,
                elevation = elevation,
                border = effectiveBorder
            ) {
                Column(modifier = Modifier.padding(innerPadding)) { content() }
            }
        }
        onClick != null -> {
            Card(
                modifier = modifier,
                onClick = onClick,
                colors = effectiveColors,
                shape = shape,
                elevation = elevation,
                border = effectiveBorder
            ) {
                Column(modifier = Modifier.padding(innerPadding)) { content() }
            }
        }
        else -> {
            Card(
                modifier = modifier,
                colors = effectiveColors,
                shape = shape,
                elevation = elevation,
                border = effectiveBorder
            ) {
                Column(modifier = Modifier.padding(innerPadding)) { content() }
            }
        }
    }
}
