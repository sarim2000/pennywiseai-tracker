package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.animations.emphasisStyle
import com.pennywiseai.tracker.ui.theme.Dimensions

@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
    ),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(enabled, selected)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shapes = FilterChipDefaults.shapes(
            shape = MaterialTheme.shapes.extraLarge,
            selectedShape = MaterialTheme.shapes.medium
        ),
        label = {
            Text(
                text = text,
                // Grade, not weight, so the chip never resizes as it toggles.
                style = emphasisStyle(LocalTextStyle.current, selected, pressed),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.small)
            )
        },
        colors = colors,
        border = border,
        modifier = modifier
    )
}
