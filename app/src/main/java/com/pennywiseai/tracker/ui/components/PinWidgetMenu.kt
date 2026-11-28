package com.pennywiseai.tracker.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R

/**
 * A top bar overflow with "Add to home screen" for one widget, shown only where
 * [isSupported] says the launcher can pin one. The caller passes both, so this
 * component doesn't reach into the widget package.
 */
@Composable
fun PinWidgetMenu(isSupported: (Context) -> Boolean, pin: (Context) -> Boolean) {
    val context = LocalContext.current
    if (!remember { isSupported(context) }) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.budgets_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.widget_add_to_home_screen)) },
                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                onClick = { open = false; pin(context) },
            )
        }
    }
}
