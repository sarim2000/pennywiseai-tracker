package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.animations.emphasisStyle
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Single-choice Material 3 Expressive connected button group — the replacement
 * for `SingleChoiceSegmentedButtonRow`. Exactly one of [options] is [selected];
 * tapping any button calls [onSelect] with its option (including the already
 * selected one, matching `SegmentedButton.onClick`).
 *
 * Labels are single-line with ellipsis so long translations don't wrap (a tile's
 * supporting line gets two).
 * With [fillMaxWidth] the row fills the available width and the buttons share
 * it equally; otherwise each button wraps its label.
 *
 * With [supportingText] each button becomes a taller two-line tile (label over
 * a smaller line). [fontFamily] draws an option's text in its own font, e.g. a
 * font picker.
 *
 * The checked label gains grade rather than weight (see [emphasisStyle]), so
 * no label resizes as the selection moves. An option's own [fontFamily]
 * replaces the style's family, so a font preview draws as-is.
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
    supportingText: (@Composable (T) -> String)? = null,
    fontFamily: (T) -> FontFamily? = { null },
) {
    // Tiles share the tallest one's height, since each option's font has its own metrics.
    val rowModifier = if (supportingText != null) modifier.height(IntrinsicSize.Min) else modifier
    Row(
        modifier = (if (fillMaxWidth) rowModifier.fillMaxWidth() else rowModifier).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val checked = option == selected
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                interactionSource = interactionSource,
                modifier = (if (fillMaxWidth) Modifier.weight(1f) else Modifier)
                    .then(if (supportingText != null) Modifier.fillMaxHeight() else Modifier)
                    .semantics { role = Role.RadioButton },
                shapes = shapes,
                // A tile sits on the page, whose colour the default unselected fill matches.
                colors = if (supportingText == null) {
                    ToggleButtonDefaults.colors()
                } else {
                    ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                },
                contentPadding = if (supportingText == null) {
                    ButtonDefaults.ContentPadding
                } else {
                    PaddingValues(horizontal = Spacing.sm, vertical = Spacing.md)
                },
            ) {
                if (supportingText == null) {
                    Text(
                        text = label(option),
                        // null keeps the button's own label style (labelLarge).
                        style = emphasisStyle(textStyle ?: LocalTextStyle.current, checked, pressed),
                        fontFamily = fontFamily(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label(option),
                            style = emphasisStyle(textStyle ?: MaterialTheme.typography.titleMedium, checked, pressed),
                            fontFamily = fontFamily(option),
                            maxLines = 1,
                            // Shrinks a long name (e.g. "Google Sans Flex") to fit a narrow tile.
                            autoSize = TextAutoSize.StepBased(minFontSize = MaterialTheme.typography.labelSmall.fontSize, maxFontSize = (textStyle ?: MaterialTheme.typography.titleMedium).fontSize),
                        )
                        Text(
                            text = supportingText(option),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = fontFamily(option),
                            textAlign = TextAlign.Center,
                            // Two lines: a third of the width cut longer translations short.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
