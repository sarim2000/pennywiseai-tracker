package com.pennywiseai.tracker.ui.components

import com.pennywiseai.tracker.ui.theme.pennyWiseButtonShapes
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.presentation.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.effects.frostedBlur
import dev.chrisbanes.haze.HazeState

/** High enough that labels stay readable over content when the bar is translucent. */
private const val NAV_BLUR_ALPHA = 0.9f

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
    val measurer = rememberTextMeasurer()
    val itemLabels = navigationItems.map { stringResource(it.titleRes) }

    Box(modifier = modifier) {
        // NORMAL style NavigationBar
        BlurredAnimatedVisibility(
            visible = visible && navBarStyle == NavBarStyle.NORMAL,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            LedgerNavBar(
                items = navigationItems,
                selectedRoute = selectedRoute,
                onSelect = onTabSelected,
                hideLabels = hideLabels,
                hideTick = hidePill,
                labelStyle = fitLabelStyle(itemLabels, measurer, itemLabels.size + if (dock?.action != null) 1 else 0),
                action = dock?.action,
            )
        }

        // FLOATING style: one pill of icon-only tabs, with the current tab's
        // primary action (if it docked one) as a squircle beside it.
        BlurredAnimatedVisibility(
            visible = visible && navBarStyle == NavBarStyle.FLOATING,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
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
                        // Full opacity by the pill's upper edge, so half-letters can't show in
                        // the gap beside it or under the gesture bar.
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.22f to MaterialTheme.colorScheme.background,
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = Spacing.md)
                        .padding(horizontal = Dimensions.Padding.content)
                        .padding(bottom = Dimensions.Component.floatingNavBottomGap)
                        .zIndex(1000f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingNavPill(
                        items = navigationItems,
                        selectedIndex = navigationItems.indexOfFirst { it.route == selectedRoute },
                        onSelect = onTabSelected,
                        blurEffects = blurEffects,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = dockAction != null,
                        enter = fadeIn() + expandHorizontally(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            expandFrom = Alignment.Start
                        ),
                        exit = fadeOut() + shrinkHorizontally(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            shrinkTowards = Alignment.Start
                        )
                    ) {
                        shownAction?.let { action ->
                            DockedActionButton(
                                action = action,
                                elevated = !blurEffects,
                                modifier = Modifier.padding(start = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The default bar: flat on the page under one hairline, the selected tab marked by an accent
 * tick that travels along that line. The tab's main action sits in its own slot at the end,
 * in line with the tabs, so nothing floats over the figures.
 */
@Composable
private fun LedgerNavBar(
    items: List<BottomNavItem>,
    selectedRoute: String?,
    onSelect: (BottomNavItem) -> Unit,
    hideLabels: Boolean,
    hideTick: Boolean,
    labelStyle: TextStyle,
    action: NavDockAction?,
) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    val tick = MaterialTheme.colorScheme.primary
    // Rows dissolve into the bar instead of being sliced by its edge.
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = -Dimensions.Component.navFade)
            .height(Dimensions.Component.navFade)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background
                )
            )
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .height(Dimensions.Component.ledgerNavHeight)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(Modifier.weight(items.size.toFloat()).fillMaxHeight()) {
            val slot = maxWidth / items.size
            val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
            val tickX by animateDpAsState(
                targetValue = slot * selectedIndex.coerceAtLeast(0) + (slot - Dimensions.Component.navTickWidth) / 2,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "navTick"
            )
            Row(Modifier.fillMaxSize()) {
                items.forEach { item ->
                    val selected = item.route == selectedRoute
                    val tint by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "navTint"
                    )
                    val label = stringResource(item.titleRes)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                onClick = { onSelect(item) },
                                role = Role.Tab,
                                interactionSource = null,
                                indication = ripple(bounded = false, radius = Dimensions.Component.ledgerNavHeight / 2)
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        if (!hideLabels) {
                            Text(
                                text = label,
                                color = tint,
                                style = labelStyle,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clearAndSetSemantics {}
                            )
                        }
                    }
                }
            }
            if (selectedIndex >= 0 && !hideTick) {
                Box(
                    Modifier
                        .offset(x = tickX)
                        .size(Dimensions.Component.navTickWidth, Dimensions.Component.navTickHeight)
                        .background(tick)
                )
            }
        }
        if (action != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DockedActionButton(
                    action = action,
                    elevated = false,
                    size = Dimensions.Component.navActionSize
                )
            }
        }
    }
    // The rule is drawn last, over the bar's top edge, so the tick sits on it.
    Box(
        Modifier
            .fillMaxWidth()
            .height(Dimensions.Component.hairline)
            .background(rule)
    )
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
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    BoxWithConstraints(
        modifier = modifier
            .height(Dimensions.Component.floatingNavHeight)
            .shadow(
                elevation = if (blurEffects) Dimensions.Elevation.none else Dimensions.Elevation.bottomBar,
                shape = CircleShape
            )
            .clip(CircleShape)
            .then(if (blurEffects) Modifier.frostedBlur(hazeState) else Modifier)
            .background(containerColor.copy(alpha = if (blurEffects) NAV_BLUR_ALPHA else 1f))
            .border(Dimensions.Component.hairline, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = Spacing.sm)
            .selectableGroup()
    ) {
        val slotWidth = maxWidth / items.size
        val indicatorWidth = minOf(slotWidth, Dimensions.Component.floatingNavIndicatorWidth)
        val labels = items.map { stringResource(it.titleRes) }

        // One indicator that slides between tabs, rather than one per tab. It sits behind
        // the icon alone (the label sits below it), as in the M3 navigation bar.
        if (selectedIndex >= 0) {
            val indicatorOffset by animateDpAsState(
                targetValue = slotWidth * selectedIndex + (slotWidth - indicatorWidth) / 2,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "navIndicatorOffset"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = indicatorOffset, y = Dimensions.Component.floatingNavIndicatorTop)
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
                val label = labels[index]
                val iconTint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "navIconTint"
                )
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
                            // Labelled: a receipt, a chart and a speech bubble don't
                            // explain themselves. The label is decorative to TalkBack,
                            // which already reads the icon's description.
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = label,
                                    tint = iconTint,
                                    modifier = Modifier.size(Dimensions.Icon.inline)
                                )
                                Text(
                                    text = label,
                                    color = iconTint,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.clearAndSetSemantics {}
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** labelMedium, scaled down just enough for the longest of [labels] to fit a bar slot. */
@Composable
private fun fitLabelStyle(labels: List<String>, measurer: TextMeasurer, slots: Int): TextStyle {
    val base = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val available = with(density) { LocalWindowInfo.current.containerSize.width.toDp().coerceAtMost(Dimensions.Component.contentMaxWidth) }
    return remember(labels, base, available, slots) {
        val slot = with(density) { (available / slots.coerceAtLeast(1) - Spacing.xs).toPx() }
        val longest = labels.maxOfOrNull { measurer.measure(it, base, maxLines = 1).size.width } ?: 0
        if (longest <= slot) base else base.copy(fontSize = base.fontSize * (slot / longest), letterSpacing = 0.sp)
    }
}
