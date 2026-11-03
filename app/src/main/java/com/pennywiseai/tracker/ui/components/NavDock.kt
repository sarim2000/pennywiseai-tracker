package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pennywiseai.tracker.ui.theme.Dimensions

/**
 * The one primary action a tab docks beside the floating nav bar (Home's Sync,
 * Transactions' Add), so it sits in line with the tabs instead of floating over
 * the content.
 */
class NavDockAction(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val onLongClickLabel: String? = null,
    val loading: Boolean = false,
    /** Reports the button's window bounds, for the spotlight tutorial. */
    val onPositioned: ((Rect) -> Unit)? = null,
)

/** Holds the action of whichever tab is on screen; the floating nav bar draws it. */
@Stable
class NavDockState {
    private var owner: Any? = null

    var action: NavDockAction? by mutableStateOf(null)
        private set

    internal fun set(owner: Any, action: NavDockAction) {
        this.owner = owner
        this.action = action
    }

    internal fun clear(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            action = null
        }
    }
}

/**
 * Non-null only while the floating nav bar is in use. A tab that finds it docks
 * its action via [DockNavAction] and drops its own FAB; null means the screen
 * keeps drawing its FABs (the Normal nav style, or no nav bar at all).
 */
val LocalNavDock = staticCompositionLocalOf<NavDockState?> { null }

/** Docks [action] beside the nav bar while this composable is on screen. */
@Composable
fun DockNavAction(action: NavDockAction) {
    val dock = LocalNavDock.current ?: return
    val owner = remember { Any() }
    SideEffect { dock.set(owner, action) }
    DisposableEffect(dock, owner) {
        onDispose { dock.clear(owner) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DockedActionButton(
    action: NavDockAction,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
) {
    // M3 Expressive press morph: the squircle squares off while held.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (pressed) Dimensions.Component.dockedActionPressedCornerRadius else Dimensions.Component.dockedActionCornerRadius,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dockedActionCorner"
    )
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .then(action.onPositioned?.let { Modifier.spotlightTarget(it) } ?: Modifier)
            .size(Dimensions.Component.floatingNavHeight)
            .shadow(
                elevation = if (elevated) Dimensions.Elevation.bottomBar else Dimensions.Elevation.none,
                shape = shape
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onLongClickLabel = action.onLongClickLabel,
                onLongClick = action.onLongClick,
                onClick = action.onClick
            )
            .semantics { contentDescription = action.contentDescription },
        contentAlignment = Alignment.Center
    ) {
        // M3 Expressive: the shape-morphing loading indicator takes the
        // icon's place while the action runs (Home's SMS scan).
        if (action.loading) {
            LoadingIndicator(color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}
