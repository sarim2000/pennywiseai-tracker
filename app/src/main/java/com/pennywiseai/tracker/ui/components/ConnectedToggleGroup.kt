package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Single-choice Material 3 Expressive connected button group — the replacement
 * for `SingleChoiceSegmentedButtonRow`. Exactly one of [options] is [selected];
 * tapping any button calls [onSelect] with its option (including the already
 * selected one, matching `SegmentedButton.onClick`).
 *
 * Labels wrap to a second line rather than truncating at large font sizes.
 * With [fillMaxWidth] the row fills the available width and the buttons share
 * it equally; otherwise each button wraps its label. Use [wrap] when the labels
 * are too long or too many to share one line: each button keeps its natural
 * width and the group flows onto further lines.
 */
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    textStyle: TextStyle? = null,
    wrap: Boolean = false,
) {
    ConnectedChoiceRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        fillMaxWidth = fillMaxWidth,
        wrap = wrap,
    ) { option ->
        Text(
            text = label(option),
            // null keeps the button's own label style (labelLarge).
            style = textStyle ?: LocalTextStyle.current,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [ConnectedChoiceRow] with custom button content, for options that need more
 * than a single label. Text inside inherits the button's content colour, so it
 * follows the selected state without any per-screen colour logic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
    wrap: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    if (wrap) {
        FlowRow(
            modifier = modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { option ->
                ChoiceButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) },
                    // The buttons may land on different lines, so they can't connect.
                    shapes = trayShapes(),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                ) { content(option) }
            }
        }
    } else {
        Row(
            modifier = (if (fillMaxWidth) modifier.fillMaxWidth() else modifier)
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                .padding(Spacing.xxs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            options.forEach { option ->
                ChoiceButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) },
                    // Equal heights when one label wraps and its neighbour doesn't.
                    modifier = (if (fillMaxWidth) Modifier.weight(1f) else Modifier).fillMaxHeight(),
                    shapes = trayShapes(),
                    // Shared-width buttons give the room to the label, not the padding.
                    contentPadding = PaddingValues(
                        horizontal = if (fillMaxWidth) Spacing.sm else Spacing.md,
                        vertical = Spacing.sm,
                    ),
                ) { content(option) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun trayShapes(): ToggleButtonShapes = MaterialTheme.shapes.small.let {
    ToggleButtonShapes(shape = it, pressedShape = it, checkedShape = it)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ToggleButtonShapes,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .heightIn(min = Dimensions.Component.minTouchTarget)
            .semantics { role = Role.RadioButton },
        shapes = shapes,
        colors = ToggleButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            checkedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            checkedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        contentPadding = contentPadding,
    ) { content() }
}
