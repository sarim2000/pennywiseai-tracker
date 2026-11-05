package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Where a row sits inside a connected group, which decides which of its
 * corners are rounded. See [GroupedList] for the container.
 */
enum class ListItemPosition {
    Top,
    Middle,
    Bottom,
    Single;

    /** Every row but the first of a group has a rule above it. */
    val hasRule: Boolean get() = this == Middle || this == Bottom

    companion object {
        fun from(index: Int, size: Int): ListItemPosition {
            return when {
                size <= 1 -> Single
                index == 0 -> Top
                index == size - 1 -> Bottom
                else -> Middle
            }
        }
    }
}

/**
 * The single source of truth for grouped-row corners. Derived from the theme
 * shape scale so a change to `shapes.large` carries through, rather than being
 * re-typed as `RoundedCornerShape(16.dp)` per screen.
 */
@Composable
fun ListItemPosition.toShape(): CornerBasedShape {
    val outer = MaterialTheme.shapes.large
    val inner = RoundedCornerShape(0.dp)

    return when (this) {
        ListItemPosition.Top -> outer.copy(
            bottomStart = inner.bottomStart,
            bottomEnd = inner.bottomEnd
        )
        ListItemPosition.Middle -> inner
        ListItemPosition.Bottom -> outer.copy(
            topStart = inner.topStart,
            topEnd = inner.topEnd
        )
        ListItemPosition.Single -> outer
    }
}

/**
 * A transaction-style row: leading avatar, title + one metadata line, trailing
 * amount.
 *
 * The subtitle uses `bodySmall` on purpose — it carries a joined metadata
 * string ("9 Jan · 3:42 PM · Food · Bal ₹1,234"), and a dense single line of
 * facts wants to stay visually subordinate to the merchant name. Rows whose
 * second line is real supporting *prose* should use
 * [RowLabels][com.pennywiseai.tracker.ui.components.cards.RowLabels] instead,
 * which sets it at `bodyMedium`.
 */
private const val STACK_AMOUNT_FONT_SCALE = 1.5f

@Composable
fun ListItemCardV2(
    title: String,
    subtitle: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    contentPadding: Dp = Dimensions.Padding.cardCompact,
    /** Overrides the row's fill when set (e.g. for selected state). */
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    /** Draws the hairline above this row. */
    ruled: Boolean = false,
) {
    // At a large font the figure takes the row's width and squeezes the merchant to a
    // sliver, so it moves onto its own line instead.
    val stackAmount = LocalDensity.current.fontScale >= STACK_AMOUNT_FONT_SCALE
    val amountSlot: @Composable () -> Unit = {
        if (trailingContent != null) {
            trailingContent()
        } else {
            Text(
                text = amount,
                style = PennyWiseText.amountRow,
                color = amountColor,
                maxLines = 1,
                autoSize = PennyWiseText.fit(PennyWiseText.amountRow)
            )
        }
    }
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Keeps a row tappable even when its content is a single short
                // line, without padding out rows that are already tall.
                .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(Spacing.smd))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = title,
                    style = PennyWiseText.rowTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = PennyWiseText.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Two lines so a large font keeps the category, not just the date.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (stackAmount) amountSlot()
            }

            if (!stackAmount) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                amountSlot()
            }
        }
    }
    // Text on the section heading's gutter; a filled row needs a little air inside its fill.
    val padding = PaddingValues(horizontal = if (containerColor != null) Spacing.sm else Spacing.none, vertical = contentPadding)
    Surface(
        modifier = modifier.fillMaxWidth().ledgerRule(ruled),
        color = containerColor ?: Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else Modifier
                )
                .padding(padding)
        ) { row() }
    }
}
