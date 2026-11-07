package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * The rule printed above a ledger row. Rows sit straight on the page and this hairline
 * separates them; no boxes, so the figures are the loudest thing on screen.
 */
@Composable
fun Modifier.ledgerRule(show: Boolean = true): Modifier {
    if (!show) return this
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        drawLine(
            color = color,
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = Dimensions.Component.hairline.toPx().coerceAtLeast(1f)
        )
    }
}

/**
 * The app's one list pattern: a ledger. Rows sit on the page, separated by a hairline
 * rule, with their text on the same gutter as the section heading above them.
 *
 * Each child passes its [ListItemPosition] (use `ListItemPosition.from(index, size)`),
 * which decides whether it draws a rule, so no screen does that by hand.
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
    containerColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(vertical = Dimensions.Padding.listRowVertical),
    minHeight: Dp = Dimensions.Component.minTouchTarget,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(Spacing.md),
    content: @Composable RowScope.() -> Unit
) {
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            content = content
        )
    }
    val ruled = modifier.fillMaxWidth().ledgerRule(position.hasRule)
    if (onClick == null) {
        Surface(modifier = ruled, color = containerColor, content = row)
    } else {
        Surface(
            onClick = onClick,
            modifier = ruled,
            enabled = enabled,
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.small,
            content = row
        )
    }
}

/** From this font scale a figure takes its own line rather than squeezing its label. */
internal const val STACK_FONT_SCALE = 1.5f

/**
 * One line of a printed statement: what it is on the left, the figure on the right, and the
 * small print under the label. The rule above is what makes a stack of these read as a table.
 */
@Composable
fun StatementLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    /** Style of the figure; pass `amountLarge` for the line a screen is really about. */
    valueStyle: TextStyle = PennyWiseText.amountMedium,
    onClick: (() -> Unit)? = null,
    /** A small glyph beside the value. */
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                .padding(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            val valueText: @Composable (Modifier) -> Unit = { valueModifier ->
                Text(
                    text = value,
                    style = valueStyle,
                    color = valueColor,
                    maxLines = 1,
                    autoSize = PennyWiseText.fit(valueStyle),
                    modifier = valueModifier,
                )
            }
            // At a large font the figure takes the width and the label goes to a letter a line.
            val stacked = LocalDensity.current.fontScale >= STACK_FONT_SCALE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                ) {
                    Text(
                        text = label,
                        style = PennyWiseText.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    if (!stacked) {
                        if (leading != null) {
                            Box(Modifier.align(Alignment.CenterVertically)) { leading() }
                        }
                        valueText(Modifier.alignByBaseline())
                    }
                }
                trailing?.invoke()
            }
            if (stacked) valueText(Modifier)
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = PennyWiseText.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val ruled = modifier.fillMaxWidth().ledgerRule()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = ruled,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content
        )
    } else {
        Box(ruled) { content() }
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
    containerColor: Color = Color.Transparent,
    contentPadding: PaddingValues = PaddingValues(vertical = Dimensions.Padding.listRowVertical),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.smd),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().ledgerRule(position.hasRule),
        color = containerColor
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
 * The coin that leads a settings-style row. Kept here so every screen
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
    size: Dp = Dimensions.Icon.avatar,
    glyphSize: Dp = Dimensions.Icon.inline,
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
