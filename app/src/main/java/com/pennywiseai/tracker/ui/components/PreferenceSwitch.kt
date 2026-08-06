package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * A switch row for a grouped preference list.
 *
 * This file used to carry its own `ListItemPosition` enum, its own `toShape()`,
 * its own padding constant and its own `GroupedListItem` — a second,
 * slightly-different implementation of the pattern in
 * [GroupedRow][com.pennywiseai.tracker.ui.components.cards.GroupedRow]. The two
 * disagreed on corner radius (`largeIncreased` vs `large`), row gutter (1.5dp
 * vs 2dp) and supporting-text size, which is why the Appearance screen never
 * quite matched Settings. It now delegates to the shared primitives.
 *
 * Position is passed as a [ListItemPosition] rather than the old trio of
 * `isFirst` / `isLast` / `isSingle` booleans, which could contradict each other
 * (all three true was representable, and rendered as "middle").
 */
@Composable
fun PreferenceSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: ListItemPosition,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    subtitle: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Spacing.md,
        vertical = Dimensions.Padding.listRowVertical
    )
) {
    BlurredAnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        GroupedRow(
            position = position,
            modifier = modifier,
            onClick = { onCheckedChange(!checked) },
            contentPadding = contentPadding
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.secondary
                ) { leadingIcon() }
            }
            RowLabels(title = title, subtitle = subtitle.ifBlank { null })
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        if (checked) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}
