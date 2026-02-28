package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility

// --- Grouped list item shape helpers ---

enum class ListItemPosition {
    Top, Middle, Bottom, Single;

    companion object {
        fun from(index: Int, size: Int) =
            if (size == 1) Single
            else when (index) {
                0 -> Top
                size - 1 -> Bottom
                else -> Middle
            }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listTopItemShape: CornerBasedShape
    @Composable get() = MaterialTheme.shapes.largeIncreased.copy(
        bottomStart = MaterialTheme.shapes.small.bottomStart,
        bottomEnd = MaterialTheme.shapes.small.bottomEnd
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listMiddleItemShape: CornerBasedShape
    @Composable get() = MaterialTheme.shapes.small

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listBottomItemShape: CornerBasedShape
    @Composable get() = MaterialTheme.shapes.largeIncreased.copy(
        topStart = MaterialTheme.shapes.small.topStart,
        topEnd = MaterialTheme.shapes.small.topEnd
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val listSingleItemShape: CornerBasedShape
    @Composable get() = MaterialTheme.shapes.largeIncreased

@Composable
fun ListItemPosition.toShape(): CornerBasedShape = when (this) {
    ListItemPosition.Top -> listTopItemShape
    ListItemPosition.Middle -> listMiddleItemShape
    ListItemPosition.Bottom -> listBottomItemShape
    ListItemPosition.Single -> listSingleItemShape
}

val listItemPadding = PaddingValues(horizontal = 16.dp, vertical = 1.5.dp)

// --- Grouped ListItem composable ---

@Composable
fun GroupedListItem(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    shape: CornerBasedShape? = listSingleItemShape,
    padding: PaddingValues = listItemPadding,
    onClick: (() -> Unit)? = null,
    listColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedListColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(padding)
            .then(
                if (shape != null) {
                    Modifier
                        .clip(shape)
                        .background(
                            if (selected) selectedListColor else listColor
                        )
                } else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leading != null) {
                CompositionLocalProvider(
                    LocalContentColor provides
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.secondary
                ) {
                    Box(contentAlignment = Alignment.Center) { leading() }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.bodyLarge) { headline() }
                }

                if (supporting != null) {
                    CompositionLocalProvider(
                        LocalContentColor provides
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        ProvideTextStyle(value = MaterialTheme.typography.bodySmall) { supporting() }
                    }
                }
            }

            if (trailing != null) {
                CompositionLocalProvider(
                    LocalContentColor provides
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                ) {
                    Box(contentAlignment = Alignment.Center) { trailing() }
                }
            }
        }
    }
}

// --- PreferenceSwitch ---

@Composable
fun PreferenceSwitch(
    visible: Boolean = true,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: @Composable () -> Unit = {},
    padding: PaddingValues = listItemPadding,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isSingle: Boolean = false
) {
    BlurredAnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        GroupedListItem(
            headline = { Text(title) },
            supporting = { if (subtitle.isNotEmpty()) Text(subtitle) },
            leading = { leadingIcon() },
            trailing = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    thumbContent = {
                        Icon(
                            if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    },
                )
            },
            shape = if (isFirst) ListItemPosition.Top.toShape()
                    else if (isLast) ListItemPosition.Bottom.toShape()
                    else if (isSingle) ListItemPosition.Single.toShape()
                    else ListItemPosition.Middle.toShape(),
            padding = padding
        )
    }
}
