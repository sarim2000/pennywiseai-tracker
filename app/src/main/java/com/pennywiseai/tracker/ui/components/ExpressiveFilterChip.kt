package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import com.pennywiseai.tracker.ui.theme.Spacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.theme.Dimensions

@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
        selectedLabelColor = MaterialTheme.colorScheme.inverseOnSurface,
        selectedLeadingIconColor = MaterialTheme.colorScheme.inverseOnSurface,
        selectedTrailingIconColor = MaterialTheme.colorScheme.inverseOnSurface
    ),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected)
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shapes = FilterChipDefaults.shapes(
            shape = MaterialTheme.shapes.medium,
            selectedShape = MaterialTheme.shapes.medium
        ),
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        },
        colors = colors,
        border = border,
        modifier = modifier
    )
}

/**
 * The filter chips of a list. One line that scrolls, so filters never eat a second row of
 * the screen; at a large font they wrap instead, since most would be off screen.
 */
@Composable
fun FilterChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val arrangement = Arrangement.spacedBy(Spacing.xs)
    if (LocalDensity.current.fontScale >= 1.3f) {
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = arrangement) { content() }
    } else {
        // Runs out to the screen edge, so a chip that scrolls off is cut by the phone
        // rather than by an invisible line inside the margin.
        Row(
            modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val bleed = Dimensions.Padding.content.roundToPx()
                    val width = constraints.maxWidth + bleed * 2
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                }
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content),
            horizontalArrangement = arrangement,
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}
