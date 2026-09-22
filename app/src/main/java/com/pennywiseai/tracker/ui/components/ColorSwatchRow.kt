package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/** Preset palette shared by categories and budgets. */
val PRESET_COLORS = listOf(
    "#E53935", "#D81B60", "#8E24AA", "#5E35B1",
    "#3949AB", "#1E88E5", "#039BE5", "#00ACC1",
    "#00897B", "#43A047", "#7CB342", "#C0CA33",
    "#FDD835", "#FFB300", "#FB8C00", "#F4511E",
    "#6D4C41", "#757575", "#546E7A", "#1565C0"
)

/** `"#RRGGBB"` → [Color], or [fallback] when the string is malformed. */
fun String.toColorOr(fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)

private fun isLightColor(color: Color): Boolean =
    (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue) > 0.5

/**
 * A wrapping row of [PRESET_COLORS] circles; the selected one carries a check.
 * A stored color outside the palette (e.g. smart-default budgets) is appended
 * so it still shows as selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        val colors = if (selected in PRESET_COLORS || selected.isBlank()) PRESET_COLORS else PRESET_COLORS + selected
        colors.forEach { colorHex ->
            val color = colorHex.toColorOr(MaterialTheme.colorScheme.primary)
            val isSelected = selected == colorHex
            // 48dp hit area around a 36dp visual circle.
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.minTouchTarget)
                    .clip(CircleShape)
                    .clickable { onSelect(colorHex) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.color_swatch_selected),
                            tint = if (isLightColor(color)) Color.Black.copy(alpha = 0.87f) else Color.White,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    }
                }
            }
        }
    }
}
