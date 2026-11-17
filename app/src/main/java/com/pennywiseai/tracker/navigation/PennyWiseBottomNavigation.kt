package com.pennywiseai.tracker.navigation

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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.ui.animations.rememberAnimationsEnabled
import com.pennywiseai.tracker.ui.components.DockedActionButton
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.NavDockState
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotReaction
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.components.mascot.ReactionAmount
import com.pennywiseai.tracker.ui.components.mascot.ReactionChip
import com.pennywiseai.tracker.ui.components.mascot.ReactionCount
import com.pennywiseai.tracker.ui.components.mascot.ReactionLabel
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.effects.frostedBlur
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.income
import com.pennywiseai.tracker.ui.theme.spending
import com.pennywiseai.tracker.ui.theme.tabular
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
    // Each style places its own Chat slot; both draw while the style switches.
    val normalChatAnchor = remember { ChatSlotAnchor() }
    val floatingChatAnchor = remember { ChatSlotAnchor() }
    // The bar comes back from under a pushed screen (Add) already holding the save's
    // reaction. The owl shows a beat first, then turns, so the change is seen, not missed.
    val animate = rememberAnimationsEnabled()
    // Set while the bar is away, so its first frame back is already the owl.
    var arriving by remember { mutableStateOf(animate) }
    LaunchedEffect(visible, animate) {
        arriving = animate
        if (!visible) return@LaunchedEffect
        if (animate) delay(CHAT_TAB_ENTRANCE_MS)
        arriving = false
    }
    val shownReaction = if (arriving) null else chatReaction

    // TalkBack skips a leaving bar too, rather than announce tabs that do nothing.
    Box(modifier = if (visible) modifier else modifier.clearAndSetSemantics {}) {
        // NORMAL style NavigationBar
        BlurredAnimatedVisibility(
            visible = visible && navBarStyle == NavBarStyle.NORMAL,
            enter = navBarEnter(),
            exit = navBarExit(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .anchorsBarTop(normalChatAnchor)
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
                                        reaction = shownReaction,
                                        modifier = Modifier.anchorsChatSlot(normalChatAnchor)
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
                        chatReaction = shownReaction,
                        selectedIndex = navigationItems.indexOfFirst { it.route == selectedRoute },
                        onSelect = selectTab,
                        blurEffects = blurEffects,
                        hazeState = hazeState,
                        modifier = Modifier
                            .weight(1f)
                            .anchorsBarTop(floatingChatAnchor),
                        chatSlotModifier = Modifier.anchorsChatSlot(floatingChatAnchor)
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

        // Last, so it draws over both bars. Outside them, so neither the floating pill's
        // clip nor the bars' blur layer cuts it off above the bar.
        ChatReactionChip(
            chip = shownReaction?.chip,
            visible = visible && BottomNavItem.Chat in navigationItems,
            anchor = if (navBarStyle == NavBarStyle.FLOATING) floatingChatAnchor else normalChatAnchor,
            modifier = Modifier.matchParentSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingNavPill(
    items: List<BottomNavItem>,
    chatReaction: MascotReaction?,
    selectedIndex: Int,
    onSelect: (BottomNavItem) -> Unit,
    blurEffects: Boolean,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    chatSlotModifier: Modifier = Modifier,
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
                            if (item == BottomNavItem.Chat) {
                                // The sliding indicator always marks selection here.
                                ChatTabMascot(
                                    label = label,
                                    selected = selected,
                                    mutedWhenUnselected = false,
                                    size = Dimensions.Icon.medium,
                                    reaction = chatReaction,
                                    modifier = chatSlotModifier
                                )
                            } else {
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
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.semantics { contentDescription = label }) {
        Mascot(
            size = size,
            shape = reaction?.shape ?: MascotShape.Owl,
            mood = reaction?.mood ?: MascotMood.Calm,
            value = reaction?.value ?: 0f,
            cue = reaction?.cue,
            tint = if (selected || !mutedWhenUnselected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Where the Chat slot sits on screen, in root pixels, for the chip above it. */
@Stable
private class ChatSlotAnchor {
    /** The Chat slot's horizontal centre. */
    var centerX by mutableFloatStateOf(Float.NaN)

    /** The top edge of the bar the slot sits in. */
    var barTop by mutableFloatStateOf(Float.NaN)
}

private fun Modifier.anchorsChatSlot(anchor: ChatSlotAnchor) = onGloballyPositioned {
    anchor.centerX = it.positionInRoot().x + it.size.width / 2f
}

private fun Modifier.anchorsBarTop(anchor: ChatSlotAnchor) = onGloballyPositioned {
    anchor.barTop = it.positionInRoot().y
}

/**
 * What a reaction says, in a small pill just above the Chat slot while it holds: the
 * amount a single save moved, or a word or a count for a moment. It rises in as the shape
 * appears and sinks out as the owl comes back. It echoes what the user just did, which
 * the screen they did it on already says, so TalkBack skips it, and it takes no touches. Centred on the slot (the
 * slot's measured centre, so RTL and the docked action need no special case), but kept a
 * gap inside the screen's edges.
 */
@Composable
private fun ChatReactionChip(
    chip: ReactionChip?,
    visible: Boolean,
    anchor: ChatSlotAnchor,
    modifier: Modifier = Modifier,
) {
    // Keep drawing the last chip while it leaves.
    val lastChip = remember { arrayOfNulls<ReactionChip>(1) }
    if (chip != null) lastChip[0] = chip
    val shownChip = chip ?: lastChip[0]
    val motion = MaterialTheme.motionScheme
    val animate = rememberAnimationsEnabled()
    val travel = with(LocalDensity.current) { Spacing.sm.roundToPx() }

    Layout(
        content = {
            AnimatedVisibility(
                visible = visible && chip != null,
                enter = if (animate) {
                    fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { travel }
                } else EnterTransition.None,
                exit = if (animate) {
                    fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { travel }
                } else ExitTransition.None
            ) {
                when (shownChip) {
                    is ReactionAmount -> AmountPill(shownChip)
                    is ReactionLabel -> ChipPill(stringResource(shownChip.text), MaterialTheme.colorScheme.onSurface)
                    is ReactionCount -> ChipPill(
                        pluralStringResource(shownChip.text, shownChip.count, shownChip.count),
                        MaterialTheme.colorScheme.onSurface
                    )
                    null -> Unit
                }
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val gap = Spacing.sm.roundToPx()
        // Never wider than the screen less its edge gaps: a long figure shrinks to fit instead.
        val chip = measurables.firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (constraints.maxWidth - 2 * gap).coerceAtLeast(0))
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            val origin = coordinates?.positionInRoot()
            if (chip == null || origin == null || anchor.centerX.isNaN() || anchor.barTop.isNaN()) return@layout
            val centred = (anchor.centerX - origin.x - chip.width / 2f).roundToInt()
            val maxX = constraints.maxWidth - gap - chip.width
            val x = if (maxX >= gap) centred.coerceIn(gap, maxX) else (constraints.maxWidth - chip.width) / 2
            val y = (anchor.barTop - origin.y).roundToInt() - gap - chip.height
            // Absolute: the anchor is already in screen terms, mirrored or not.
            chip.place(x, y)
        }
    }
}

/**
 * [amount] signed and coloured as the transaction lists do: income "+" in [income],
 * spending "-" in [spending], and money only moved (a transfer or an investment) plain.
 * Masked while the user hides amounts.
 */
@Composable
private fun AmountPill(amount: ReactionAmount) {
    val colorScheme = MaterialTheme.colorScheme
    val (sign, color) = when (amount.type) {
        TransactionType.INCOME -> "+" to colorScheme.income
        TransactionType.EXPENSE, TransactionType.CREDIT -> "-" to colorScheme.spending
        TransactionType.TRANSFER, TransactionType.INVESTMENT -> "" to colorScheme.onSurface
    }
    ChipPill(
        text = CurrencyFormatter.signed(
            sign,
            CurrencyFormatter.formatCurrency(amount.money.amount, amount.money.currency)
        ),
        color = color,
    )
}

/** The chip's pill: one line of [text] in [color] that steps down to fit rather than cut off. */
@Composable
private fun ChipPill(text: String, color: Color) {
    val colorScheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.tabular(),
        color = color,
        maxLines = 1,
        // Never cut short: a long one steps down to the money-text floor.
        autoSize = TextAutoSize.StepBased(
            minFontSize = PennyWiseText.autoSizeFloor,
            maxFontSize = MaterialTheme.typography.labelLarge.fontSize
        ),
        modifier = Modifier
            .clearAndSetSemantics {}
            .shadow(elevation = Dimensions.Elevation.bottomBar, shape = CircleShape)
            .background(colorScheme.surfaceContainerHigh, CircleShape)
            .border(Dimensions.Component.hairline, colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = Spacing.smd, vertical = Spacing.xs)
    )
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

/** How long the owl shows as the bar comes back before it turns into a held reaction: past the bar's own entrance. */
private const val CHAT_TAB_ENTRANCE_MS = 350L
