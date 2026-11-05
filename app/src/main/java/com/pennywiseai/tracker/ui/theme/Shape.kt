package com.pennywiseai.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

/** Buttons are cut square-ish with a soft corner, like a stamped label, and firm up when pressed. */
@Composable
fun pennyWiseButtonShapes(): ButtonShapes =
    ButtonShapes(shape = MaterialTheme.shapes.medium, pressedShape = MaterialTheme.shapes.small)

/** Off is a quiet tray with only a hairline: a screen of off-switches shouldn't be its loudest thing. */
@Composable
fun pennyWiseSwitchColors(): SwitchColors = SwitchDefaults.colors(
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)
