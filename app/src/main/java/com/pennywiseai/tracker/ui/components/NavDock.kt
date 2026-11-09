package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.pennywiseai.tracker.R
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
    /**
     * Set briefly after the action's own work lands ("+N" new items): the button
     * shows it in the icon's place, then the owner clears it. Null shows the icon.
     */
    val resultCount: Int? = null,
    /** Reports the button's window bounds, for the spotlight tutorial. */
    val onPositioned: ((Rect) -> Unit)? = null,
)

/** Holds the action of whichever tab is on screen; the floating nav bar draws it. */
@Stable
class NavDockState {
    // ponytail: a stack, newest claim on top. During a tab fade-through both tabs are
    // composed; the outgoing one keeps its slot underneath until it leaves, so it can
    // neither overwrite the incoming action nor leave the dock empty on a fast return.
    private val claims = mutableStateListOf<Pair<Any, NavDockAction>>()

    val action: NavDockAction? get() = claims.lastOrNull()?.second

    /** The screen entering composition goes on top. */
    internal fun claim(owner: Any, action: NavDockAction) {
        claims.removeAll { it.first === owner }
        claims += owner to action
    }

    /** Refreshes [owner]'s action in place, without changing who is on top. */
    internal fun set(owner: Any, action: NavDockAction) {
        val i = claims.indexOfFirst { it.first === owner }
        if (i >= 0) claims[i] = owner to action
    }

    internal fun clear(owner: Any) {
        claims.removeAll { it.first === owner }
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
    val latest by rememberUpdatedState(action)
    DisposableEffect(dock, owner) {
        dock.claim(owner, latest)
        onDispose { dock.clear(owner) }
    }
    SideEffect { dock.set(owner, action) }
}

/**
 * True while a tab's own bottom toolbar takes the nav bar's place (Transactions'
 * bulk-select toolbar), so the two never stack. Provided in both nav styles.
 */
val LocalHideNavBar = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/** Hides the overlaid nav bar (and its docked action) while this composable is on screen. */
@Composable
fun HideNavBar() {
    // ponytail: one flag, not a counter — only one screen hides the bar at a time.
    val hidden = LocalHideNavBar.current ?: return
    DisposableEffect(hidden) {
        hidden.value = true
        onDispose { hidden.value = false }
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
        // icon's place while the action runs (Home's SMS scan), settles into
        // its result for a beat, then gives the icon back. A count is a value,
        // so the swap is an effects fade with no bounce.
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val face = when {
            action.loading -> DockFace.Loading
            action.resultCount != null -> DockFace.Result(action.resultCount)
            else -> DockFace.Idle
        }
        AnimatedContent(
            targetState = face,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) using null },
            contentAlignment = Alignment.Center,
            label = "dockedActionFace"
        ) { state ->
            when (state) {
                DockFace.Loading -> LoadingIndicator(color = MaterialTheme.colorScheme.onPrimary)
                is DockFace.Result -> {
                    val announce = pluralStringResource(R.plurals.dock_result_description, state.count, state.count)
                    Text(
                        text = if (state.count > DOCK_RESULT_MAX) stringResource(R.string.dock_result_many, DOCK_RESULT_MAX)
                        else stringResource(R.string.dock_result_new, state.count),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.semantics {
                            contentDescription = announce
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }
                DockFace.Idle -> Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        }
    }
}

/** What the docked button shows in its middle. */
private sealed interface DockFace {
    data object Loading : DockFace
    data class Result(val count: Int) : DockFace
    data object Idle : DockFace
}

/** Past this a result reads "99+", which still fits the button at 200% font scale. */
private const val DOCK_RESULT_MAX = 99
