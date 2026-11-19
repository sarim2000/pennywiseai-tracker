package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * A grouped block that shows its first [visibleCount] rows and a "View all"
 * button for the rest. Each row gets its [ListItemPosition] for the rows on
 * screen, so the block's bottom corners follow the last visible row.
 */
@Composable
internal fun <T> ExpandableGroupedList(
    items: List<T>,
    modifier: Modifier = Modifier,
    visibleCount: Int = 6,
    row: @Composable (item: T, position: ListItemPosition) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) items else items.take(visibleCount)
    Column(modifier = modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
        GroupedList {
            shown.forEachIndexed { index, item -> row(item, ListItemPosition.from(index, shown.size)) }
        }
        if (items.size > visibleCount) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.expandable_list_view_less)
                    } else {
                        stringResource(R.string.expandable_list_view_all_more, items.size - visibleCount)
                    }
                )
            }
        }
    }
}

/**
 * One row of a share breakdown: leading glyph, name, share and amount, with a
 * thin bar in the group's colour underneath (its length relative to the
 * largest row). Tappable rows press-morph like every segmented list item.
 */
@Composable
internal fun ShareRow(
    position: ListItemPosition,
    title: String,
    amount: String,
    percent: Int?,
    fraction: Float,
    barColor: Color,
    onClick: (() -> Unit)?,
    leading: (@Composable () -> Unit)? = null
) {
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.smd),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
            ) {
                leading?.invoke()
                Text(
                    text = title,
                    style = PennyWiseText.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (percent != null) {
                    Text(
                        text = stringResource(R.string.analytics_percent, percent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = amount,
                    style = PennyWiseText.amountRow,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            ShareBar(fraction = fraction, color = barColor)
        }
    }
    if (onClick == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = container,
            shape = position.toShape(),
            content = content
        )
    } else {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.shapes(shape = position.toShape()),
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.segmentedColors(
                containerColor = container,
                contentColor = contentColorFor(container)
            ),
            contentPadding = PaddingValues(),
            content = content
        )
    }
}

/** A flat data meter: fills to [fraction] on an effects spec, once on first paint. */
@Composable
private fun ShareBar(fraction: Float, color: Color) {
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val shown by animateFloatAsState(
        targetValue = if (grown) fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "share_bar"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.progressBarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(shown)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** A category's glyph on a circle of its own colour. */
@Composable
internal fun CategoryBadge(name: String, colorHex: String?) {
    val color = CategoryMapping.colorFor(name, colorHex)
    Box(
        modifier = Modifier
            .size(Dimensions.Icon.avatar)
            .clip(CircleShape)
            .background(color.copy(alpha = Dimensions.Alpha.ghost)),
        contentAlignment = Alignment.Center
    ) {
        CategoryIcon(category = name, size = Dimensions.Icon.inline, tint = color)
    }
}

/** A plain glyph on a tinted circle, for rows without a category (tags, accounts). */
@Composable
internal fun TintBadge(icon: ImageVector, color: Color) {
    IconTile(
        icon = icon,
        containerColor = color.copy(alpha = Dimensions.Alpha.ghost),
        contentColor = color,
        size = Dimensions.Icon.avatar,
        glyphSize = Dimensions.Icon.inline
    )
}
