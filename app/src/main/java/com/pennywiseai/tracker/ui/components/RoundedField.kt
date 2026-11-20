package com.pennywiseai.tracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * A filled `TextField` with all four corners rounded. The library default keeps
 * a square bottom edge under an indicator line, which is the last sharp corner
 * in an otherwise rounded app; pass [shape] and [colors] together, since a
 * rounded bottom with an underline looks broken.
 *
 * Outlined fields only need `shape = RoundedField.shape`.
 */
object RoundedField {
    val shape: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.large

    @Composable
    fun colors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow): TextFieldColors =
        TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            errorContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        )
}
