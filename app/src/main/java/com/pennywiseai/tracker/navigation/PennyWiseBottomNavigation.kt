package com.pennywiseai.tracker.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.ui.components.DockedActionButton
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.NavDockState
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotReaction
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.frostedBlur
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun PennyWiseBottomNavigation(
    modifier: Modifier = Modifier,
    /** Route of the tab on screen ([BottomNavItem.route]), or null. */
    selectedRoute: String?,
    onTabSelected: (BottomNavItem) -> Unit,
    navBarStyle: NavBarStyle,
    hideLabels: Boolean = false,
    hidePill: Boolean = false,
    blurEffects: Boolean = true,
    visible: Boolean = true,
    hiddenTabs: Set<String> = emptySet(),
    /** What the Chat tab's owl has turned into for a moment, or null for the owl. */
    chatReaction: MascotReaction? = null,
    hazeState: HazeState = remember { HazeState() },
    dock: NavDockState? = null,
) {
    val navigationItems = BottomNavItem.visible(hiddenTabs)
    val containerColor = MaterialTheme.colorScheme.surface
    // A leaving bar still draws (and hit-tests) through its exit. Ignore its taps,
    // or a tab tap would strand a push and the docked Sync would scan behind it.
    val selectTab: (BottomNavItem) -> Unit = { if (visible) onTabSelected(it) }

    // TalkBack skips a leaving bar too, rather than announce tabs that do nothing.
    Box(modifier = if (visible) modifier else modifier.clearAndSetSemantics {}) {
        // NORMAL style NavigationBar
        BlurredAnimatedVisibility(
            visible = visible && navBarStyle == NavBarStyle.NORMAL,
            enter = navBarEnter(),
            exit = navBarExit(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    thickness = Dimensions.Component.dividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(
                        alpha = if (blurEffects) 0.5f else 1f
                    ),
                    tonalElevation = Dimensions.Elevation.raisedCard,
                    modifier = Modifier.then(
                        if (blurEffects) Modifier.frostedBlur(hazeState, tint = containerColor) else Modifier
                    )
                ) {
                    navigationItems.forEach { item ->
                        val selected = item.route == selectedRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectTab(item) },
                            icon = {
                                // Without a pill or a label there is nothing
                                // else marking the selected item, so the glyph
                                // steps up to carry that weight.
                                val iconSize = if (hidePill && hideLabels) Dimensions.Icon.large
                                else Dimensions.Icon.medium
                                if (item == BottomNavItem.Chat) {
                                    ChatTabMascot(
                                        label = stringResource(item.titleRes),
                                        selected = selected,
                                        mutedWhenUnselected = hidePill && hideLabels,
                                        size = iconSize,
                                        reaction = chatReaction
                                    )
                                } else {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.titleRes),
                                        tint = if (selected) {
                                            if (hidePill) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onPrimaryContainer
                                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            },
                            label = if (hideLabels) null else {
                                {
                                    Text(
                                        text = stringResource(item.titleRes),
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = if (hidePill) Color.Transparent
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }

        // FLOATING style: one pill of icon-only tabs, with the current tab's
        // primary action (if it docked one) as a squircle beside it.
        BlurredAnimatedVisibility(
            visible = visible && navBarStyle == NavBarStyle.FLOATING,
            enter = navBarEnter(),
            exit = navBarExit(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val dockAction = dock?.action
            // Keep drawing the last action while it animates out.
            val lastAction = remember { arrayOfNulls<NavDockAction>(1) }
            if (dockAction != null) lastAction[0] = dockAction
            val shownAction = dockAction ?: lastAction[0]

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                // Ask leaves the pill for the big squircle at the end, and the tab's
                // docked action stacks above it, smaller. With Ask hidden the action
                // takes that slot at full size instead.
                val chatItem = navigationItems.firstOrNull { it == BottomNavItem.Chat }
                val pillItems = navigationItems - BottomNavItem.Chat
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Dimensions.Padding.content)
                        .padding(bottom = Dimensions.Component.floatingNavBottomGap)
                        .zIndex(1000f),
                    verticalAlignment = Alignment.Bottom
                ) {
                    FloatingNavPill(
                        items = pillItems,
                        selectedIndex = pillItems.indexOfFirst { it.route == selectedRoute },
                        onSelect = selectTab,
                        blurEffects = blurEffects,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                    // The small action sits at the end edge and grows from that corner.
                    val growFrom = TransformOrigin(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 0f else 1f, 1f)
                    Column(
                        modifier = Modifier.padding(start = Spacing.sm),
                        horizontalAlignment = Alignment.End
                    ) {
                        AnimatedVisibility(
                            visible = dockAction != null,
                            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                transformOrigin = growFrom
                            ) + expandVertically(MaterialTheme.motionScheme.fastSpatialSpec(), expandFrom = Alignment.Bottom),
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(
                                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                                transformOrigin = growFrom
                            ) + shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Bottom)
                        ) {
                            shownAction?.let { action ->
                                DockedActionButton(
                                    action = action,
                                    elevated = !blurEffects,
                                    // Off once the bar or the action is leaving.
                                    enabled = visible && dockAction != null,
                                    small = chatItem != null,
                                    modifier = if (chatItem != null) Modifier.padding(bottom = Spacing.sm) else Modifier
                                )
                            }
                        }
                        chatItem?.let { item ->
                            ChatDockButton(
                                label = stringResource(item.titleRes),
                                selected = item.route == selectedRoute,
                                reaction = chatReaction,
                                elevated = !blurEffects,
                                onClick = { selectTab(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Ask tab as the bar's big squircle: the owl, drawn in full, on a primary
 * container. M3 Expressive shape morphs carry its state: it rounds into a circle
 * while Ask is open and squares off while held.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDockButton(
    label: String,
    selected: Boolean,
    reaction: MascotReaction?,
    elevated: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> Dimensions.Component.dockedActionPressedCornerRadius
            selected -> Dimensions.Component.floatingNavHeight / 2
            else -> Dimensions.Component.dockedActionCornerRadius
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chatDockCorner"
    )
    val shape = RoundedCornerShape(corner)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.Component.floatingNavHeight)
                .shadow(if (elevated) Dimensions.Elevation.bottomBar else Dimensions.Elevation.none, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = ripple()
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Mascot(
                size = Dimensions.Component.chatDockMascot,
                shape = reaction?.shape ?: MascotShape.Owl,
                mood = reaction?.mood ?: MascotMood.Calm,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingNavPill(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onSelect: (BottomNavItem) -> Unit,
    blurEffects: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    BoxWithConstraints(
        modifier = modifier
            .height(Dimensions.Component.floatingNavHeight)
            .shadow(
                elevation = if (blurEffects) Dimensions.Elevation.none else Dimensions.Elevation.bottomBar,
                shape = CircleShape
            )
            .clip(CircleShape)
            .then(
                if (blurEffects) Modifier.frostedBlur(hazeState) else Modifier
            )
            .background(containerColor.copy(alpha = if (blurEffects) 0.8f else 1f))
            .padding(horizontal = Spacing.sm)
            .selectableGroup()
    ) {
        val slotWidth = maxWidth / items.size
        val indicatorWidth = minOf(slotWidth, Dimensions.Component.floatingNavIndicatorWidth)

        // One indicator that slides between tabs, rather than one per tab.
        if (selectedIndex >= 0) {
            val indicatorOffset by animateDpAsState(
                targetValue = slotWidth * selectedIndex + (slotWidth - indicatorWidth) / 2,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "navIndicatorOffset"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorOffset)
                    .size(indicatorWidth, Dimensions.Component.floatingNavIndicatorHeight)
                    .clip(CircleShape)
                    // A tint of primary rather than secondaryContainer, which
                    // all but vanishes against the pill in light themes.
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = Dimensions.Alpha.selectedIndicator)
                    )
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val label = stringResource(item.titleRes)
                val iconTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "navIconTint"
                )
                // Icon-only to keep the pill calm; long-press names the tab.
                // TooltipBox doesn't forward Row weights, so a Box holds the slot.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = { PlainTooltip { Text(label) } },
                        state = rememberTooltipState(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .selectable(
                                    selected = selected,
                                    onClick = { onSelect(item) },
                                    role = Role.Tab,
                                    interactionSource = null,
                                    indication = ripple(
                                        bounded = false,
                                        radius = Dimensions.Component.floatingNavIndicatorHeight / 2
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                                tint = iconTint,
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Chat tab's owl, or the shape of a [reaction] for a moment. It takes the accent in
 * both states so the assistant stands out, and the pill or indicator marks selection —
 * unless [mutedWhenUnselected] (no pill and no label), when colour is the only cue left
 * and it mutes like its neighbours. It is decoration: the slot keeps the tab's name and
 * the click goes to the tab.
 */
@Composable
private fun ChatTabMascot(
    label: String,
    selected: Boolean,
    mutedWhenUnselected: Boolean,
    size: Dp,
    reaction: MascotReaction?,
) {
    Box(modifier = Modifier.semantics { contentDescription = label }) {
        Mascot(
            size = size,
            shape = reaction?.shape ?: MascotShape.Owl,
            mood = reaction?.mood ?: MascotMood.Calm,
            tint = if (selected || !mutedWhenUnselected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Nav show/hide is chrome: a quick fade with a short slide (a quarter of the bar), not a
// full-height bouncy move.
@Composable
private fun navBarEnter(): EnterTransition {
    val motion = MaterialTheme.motionScheme
    return fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { it / 4 }
}

@Composable
private fun navBarExit(): ExitTransition {
    val motion = MaterialTheme.motionScheme
    return fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 4 }
}
