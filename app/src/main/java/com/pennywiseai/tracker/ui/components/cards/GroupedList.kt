package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The app's one grouped-list pattern: sibling rows share a tonal surface and
 * are separated by a 2dp gutter, with the outer corners of the block rounded
 * and the interior corners nearly square. The shape does the grouping, so no
 * dividers are needed.
 *
 * [GroupedList] handles the gutter; each child passes its
 * [ListItemPosition] (use `ListItemPosition.from(index, size)`) so the corners
 * come out right without any per-screen shape maths.
 *
 * ```
 * GroupedList {
 *     items.forEachIndexed { i, item ->
 *         GroupedRow(position = ListItemPosition.from(i, items.size), onClick = …) { … }
 *     }
 * }
 * ```
 */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
        content = content
    )
}

/**
 * One row of a [GroupedList].
 *
 * Enforces the two things screens kept getting wrong by hand: a minimum height
 * that keeps the tap target above the accessibility floor even for a
 * single-line row, and consistent inner padding.
 */
@Composable
fun GroupedRow(
    position: ListItemPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Spacing.md,
        vertical = Dimensions.Padding.listRowVertical
    ),
    minHeight: Dp = Dimensions.Component.minTouchTarget,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.md),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = position.toShape()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = minHeight)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )
    }
}

/**
 * A column variant of [GroupedRow] for rows whose content stacks (a row plus
 * an expanded dropdown or progress bar underneath).
 */
@Composable
fun GroupedColumn(
    position: ListItemPosition,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Spacing.md,
        vertical = Dimensions.Padding.listRowVertical
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.smd),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = position.toShape()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}

/**
 * The tinted circle that leads a settings-style row. Kept here so every screen
 * uses the same circle size and the same glyph size inside it — a glyph that
 * is 24dp in one row and 20dp in the next is one of the tells that makes a
 * list look hand-assembled.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.Icon.avatarLarge,
    glyphSize: Dp = Dimensions.Icon.medium,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(glyphSize)
        )
    }
}

/**
 * Title + supporting text for a list row, weighted to fill the space between
 * the leading and trailing slots.
 *
 * Supporting text is `bodyMedium`/`onSurfaceVariant` rather than `bodySmall`:
 * at 12sp the second line of a two-line row is genuinely hard to read, and
 * the colour role already carries the "this is secondary" signal without
 * shrinking it.
 */
@Composable
fun RowScope.RowLabels(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleMaxLines: Int = 1,
    subtitleMaxLines: Int = 2
) {
    Column(
        modifier = modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = title,
            style = PennyWiseText.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = PennyWiseText.rowSubtitle,
                color = subtitleColor,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
