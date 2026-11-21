package com.pennywiseai.tracker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The app's screen scaffold. Pass [title] for the default top bar, or
 * [customTopBar] — usually a [CustomTitleTopAppBar], which takes an optional
 * `subtitle` — for a collapsing one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PennyWiseScaffold(
    modifier: Modifier = Modifier,
    title: String = "",
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    transparentTopBar: Boolean = false,
    customTopBar: @Composable (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    // A transparent container (Plan's sections) matches no scheme role, and contentColorFor
    // then falls back to black; the page behind it is the background.
    contentColor: Color = if (containerColor.alpha == 0f) MaterialTheme.colorScheme.onBackground else contentColorFor(containerColor),
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = customTopBar ?: {
            TopAppBar(
                title = { if (title.isNotEmpty()) Text(title) },
                navigationIcon = navigationIcon ?: {},
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    // The page's own colour: surface is the card tone, a band over the page.
                    containerColor = if (transparentTopBar) Color.Transparent else containerColor
                )
            )
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
