package com.pennywiseai.tracker.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.presentation.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.components.DockedActionButton
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.NavDockState
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.effects.frostedBlur
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
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.titleRes),
                                    tint = if (selected) {
                                        if (hidePill) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                                    // Without a pill or a label there is
                                    // nothing else marking the selected item, so
                                    // the glyph steps up to carry that weight.
                                    modifier = Modifier.size(
                                        if (hidePill && hideLabels) Dimensions.Icon.large
                                        else Dimensions.Icon.medium
                                    )
                                )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Dimensions.Padding.content)
                        .padding(bottom = Dimensions.Component.floatingNavBottomGap)
                        .zIndex(1000f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingNavPill(
                        items = navigationItems,
                        selectedIndex = navigationItems.indexOfFirst { it.route == selectedRoute },
                        onSelect = selectTab,
                        blurEffects = blurEffects,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = dockAction != null,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + expandHorizontally(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            expandFrom = Alignment.Start
                        ),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            shrinkTowards = Alignment.Start
                        )
                    ) {
                        shownAction?.let { action ->
                            DockedActionButton(
                                action = action,
                                elevated = !blurEffects,
                                // Off once the bar or the action is leaving.
                                enabled = visible && dockAction != null,
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                }
            }
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
