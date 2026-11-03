package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Single-choice Material 3 Expressive connected button group — the replacement
 * for `SingleChoiceSegmentedButtonRow`. Exactly one of [options] is [selected];
 * tapping any button calls [onSelect] with its option (including the already
 * selected one, matching `SegmentedButton.onClick`).
 *
 * Labels are single-line with ellipsis so long translations don't wrap.
 * With [fillMaxWidth] the row fills the available width and the buttons share
 * it equally; otherwise each button wraps its label.
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
) {
    Row(
        modifier = (if (fillMaxWidth) modifier.fillMaxWidth() else modifier).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = (if (fillMaxWidth) Modifier.weight(1f) else Modifier)
                    .semantics { role = Role.RadioButton },
                shapes = shapes,
            ) {
                Text(
                    text = label(option),
                    // null keeps the button's own label style (labelLarge).
                    style = textStyle ?: LocalTextStyle.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
