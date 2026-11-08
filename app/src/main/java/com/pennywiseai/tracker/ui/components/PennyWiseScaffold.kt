package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/**
 * The app's screen scaffold. Pass [title] (and optionally [subtitle]) for the
 * default top bar, or [customTopBar] — usually a [CustomTitleTopAppBar], which
 * takes its own `subtitle` — for a collapsing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennyWiseScaffold(
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    transparentTopBar: Boolean = false,
    customTopBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = customTopBar ?: {
            val barTitle: @Composable () -> Unit = { if (title.isNotEmpty()) Text(title) }
            val colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (transparentTopBar) Color.Transparent else MaterialTheme.colorScheme.surface
            )
            if (subtitle == null) {
                TopAppBar(
                    title = barTitle,
                    navigationIcon = navigationIcon ?: {},
                    actions = actions,
                    colors = colors
                )
            } else {
                TopAppBar(
                    title = barTitle,
                    subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = navigationIcon ?: {},
                    actions = actions,
                    colors = colors
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content
    )
}
