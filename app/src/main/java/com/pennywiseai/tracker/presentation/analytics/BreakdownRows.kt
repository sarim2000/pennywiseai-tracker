package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy

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

/** Each meter is scaled against the largest row in the group. */
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
    GroupedColumn(
        position = position,
        modifier = if (onClick == null) Modifier else Modifier
            .clip(position.toShape())
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            leading?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(text = title, style = PennyWiseText.rowTitle)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                ) {
                    Text(text = amount, style = PennyWiseText.amountRow)
                    if (percent != null && !AmountPrivacy.hidden) {
                        Text(
                            text = stringResource(R.string.analytics_percent, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (!AmountPrivacy.hidden) {
            // The spoken share is the percentage of the total, not the meter's relative width.
            WavyMeter(
                fraction = fraction,
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.clearAndSetSemantics {},
                dense = true
            )
        }
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
        CategoryIcon(
            category = name,
            size = Dimensions.Icon.inline,
            tint = color,
            modifier = Modifier.clearAndSetSemantics {}
        )
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
