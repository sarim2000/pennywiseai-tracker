package com.pennywiseai.tracker.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.ui.animations.emphasisStyle
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
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
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
    /**
     * Whether the bar is up and past its entrance beat, as it changes: the moment the owl
     * turns into [chatReaction], so a waiting reaction's hold starts then and not before.
     */
    onSettledChange: (Boolean) -> Unit = {},
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
    val settledChange by rememberUpdatedState(onSettledChange)
    LaunchedEffect(visible, animate) {
        arriving = animate
        if (!visible || animate) settledChange(false)
        if (!visible) return@LaunchedEffect
        if (animate) delay(CHAT_TAB_ENTRANCE_MS)
        arriving = false
        // One clock: the hold starts as the owl turns, however late the bar's first frame was.
        settledChange(true)
    }
    val shownReaction = if (animate && arriving) null else chatReaction

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
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectTab(item) },
                            interactionSource = interactionSource,
                            icon = {
                                // Without a pill or a label there is nothing
                                // else marking the selected item, so the glyph
                                // steps up to carry that weight.
                                val iconSize = if (hidePill && hideLabels) Dimensions.Icon.large
                                else Dimensions.Icon.medium
                                val selectedTint = if (hidePill) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onPrimaryContainer
                                if (item == BottomNavItem.Chat) {
                                    ChatTabMascot(
                                        label = stringResource(item.titleRes),
                                        selected = selected,
                                        selectedTint = selectedTint,
                                        mutedWhenUnselected = hidePill && hideLabels,
                                        size = iconSize,
                                        reaction = shownReaction,
                                        modifier = Modifier.anchorsChatSlot(normalChatAnchor)
                                    )
                                } else {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.titleRes),
                                        tint = if (selected) selectedTint else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        // Grade, not weight: the tab labels never shift.
                                        style = emphasisStyle(MaterialTheme.typography.labelMedium, selected, pressed)
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
                        .zIndex(1000f)
                        .selectableGroup(),
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
                                reaction = shownReaction,
                                elevated = !blurEffects,
                                onClick = { selectTab(item) },
                                modifier = Modifier.anchorsChatSlot(floatingChatAnchor)
                            )
                        }
                    }
                }
            }
        }

        // Last, so it draws over both bars. Outside them, so neither the floating pill's
        // clip nor the bars' blur layer cuts it off where it overhangs them.
        ChatReactionChip(
            chip = shownReaction?.chip,
            visible = visible && BottomNavItem.Chat in navigationItems,
            anchor = if (navBarStyle == NavBarStyle.FLOATING) floatingChatAnchor else normalChatAnchor,
            modifier = Modifier.matchParentSize()
        )
    }
}

/**
 * The Ask tab as the bar's big squircle: the owl, drawn in full in onPrimaryContainer,
 * on a primary container. M3 Expressive shape morphs carry its state: it rounds into a circle
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
    modifier: Modifier = Modifier,
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
            modifier = modifier
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
                value = reaction?.value ?: 0f,
                cue = reaction?.cue,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.primaryContainer
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
            .padding(horizontal = Spacing.sm),
    ) {
        val slotWidth = maxWidth / items.size
        val indicatorWidth = minOf(slotWidth, Dimensions.Component.floatingNavIndicatorWidth)

        // One indicator that slides between tabs, rather than one per tab. Null while no tab is open.
        val indicatorOffset: Dp? = if (selectedIndex >= 0) {
            animateDpAsState(
                targetValue = slotWidth * selectedIndex + (slotWidth - indicatorWidth) / 2,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "navIndicatorOffset"
            ).value
        } else {
            null
        }
        if (indicatorOffset != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = indicatorOffset)
                    .size(indicatorWidth, Dimensions.Component.floatingNavIndicatorHeight)
                    .clip(CircleShape)
                    // Solid primary: the tabs are icon-only, so this is what says which tab
                    // is open. A tint of primary sat at ~1.3:1 on the pill; primary reads at
                    // 4.5:1 on any surface, and the open tab's icon takes onPrimary on it.
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val label = stringResource(item.titleRes)
                // The tint follows the indicator, not the selection: an icon takes onPrimary as
                // far as the pill sits under it, so it never shows white on the bare bar (or
                // the pill passes under a muted icon) while the pill slides.
                val covered = indicatorOffset?.let {
                    indicatorCoverage(it + indicatorWidth / 2, slotWidth * index + slotWidth / 2, indicatorWidth)
                } ?: 0f
                val iconTint = lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.onPrimary,
                    covered
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
 * How much of a tab's icon, centred at [iconCenter], the indicator centred at
 * [indicatorCenter] covers: 1 under it, 0 clear of it, linear while an edge crosses the icon.
 */
private fun indicatorCoverage(indicatorCenter: Dp, iconCenter: Dp, indicatorWidth: Dp): Float {
    val icon = Dimensions.Icon.medium
    val distance = (indicatorCenter - iconCenter).value.absoluteValue
    return (((indicatorWidth + icon) / 2).value - distance).div(icon.value).coerceIn(0f, 1f)
}

/**
 * The Chat tab's owl, or the shape of a [reaction] for a moment. Unselected it takes the
 * accent so the assistant stands out — unless [mutedWhenUnselected] (no pill and no label),
 * when colour is the only cue left and it mutes like its neighbours. Selected it takes
 * [selectedTint] like the other tabs, so it reads on the pill. It is decoration: the slot
 * keeps the tab's name and the click goes to the tab.
 */
@Composable
private fun ChatTabMascot(
    label: String,
    selected: Boolean,
    selectedTint: Color,
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
            tint = when {
                selected -> selectedTint
                mutedWhenUnselected -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

/** Where the Chat slot sits on screen, for the chip beside it. */
@Stable
private class ChatSlotAnchor {
    /**
     * The slot's bounds in root pixels, or null until it is placed: it goes when the slot
     * leaves with its bar, so a bar coming back never shows the chip where it last was.
     */
    var bounds by mutableStateOf<Rect?>(null)
}

private fun Modifier.anchorsChatSlot(anchor: ChatSlotAnchor) = this then ChatSlotAnchorElement(anchor)

private data class ChatSlotAnchorElement(val anchor: ChatSlotAnchor) : ModifierNodeElement<ChatSlotAnchorNode>() {
    override fun create() = ChatSlotAnchorNode(anchor)

    override fun update(node: ChatSlotAnchorNode) {
        node.anchor = anchor
    }
}

private class ChatSlotAnchorNode(var anchor: ChatSlotAnchor) : Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        anchor.bounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
    }

    override fun onDetach() {
        anchor.bounds = null
    }
}

/**
 * What a reaction says, in a small pill beside the Chat slot while it holds: the amount a
 * single save moved, or a word or a count for a moment. It rises in as the shape appears
 * and sinks out as the owl comes back. It echoes what the user just did, which the screen
 * they did it on already says, so TalkBack skips it, and it takes no touches.
 *
 * It sits on the slot's start side, level with the owl, so it lies over the bar (the
 * floating pill's end) and never over the content: above the bar it would land in a
 * list's amount column and read as another row's amount. It is placed from the slot's
 * measured bounds, so the docked action needs no special case, and kept a gap inside the
 * screen's edges.
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
    // Leaving the app clears the reaction, but the frame clock stops with the app, so the
    // chip's exit would only play on return: the old amount fading out over the bar. Each
    // stop starts the chip afresh instead, so it comes back as it is now, without a fade.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var stops by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stops++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Layout(
        content = {
            key(stops) {
                AnimatedVisibility(
                    visible = visible && chip != null,
                    enter = if (animate) {
                        // The chip carries an amount, so its short drift settles on effects: no overshoot.
                        fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.fastEffectsSpec()) { travel }
                    } else EnterTransition.None,
                    exit = if (animate) {
                        fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastEffectsSpec()) { travel }
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
            }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val gap = Spacing.sm.roundToPx()
        // Never wider than the screen less its edge gaps: a long figure shrinks to fit instead.
        val chip = measurables.firstOrNull()?.measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = (constraints.maxWidth - 2 * gap).coerceAtLeast(0))
        )
        val rtl = layoutDirection == LayoutDirection.Rtl
        layout(constraints.maxWidth, constraints.maxHeight) {
            val origin = coordinates?.positionInRoot()
            // Not until the slot is placed in the bar showing now.
            val slot = anchor.bounds
            if (chip == null || origin == null || slot == null) return@layout
            val beside = if (rtl) slot.right + gap else slot.left - gap - chip.width
            val maxX = constraints.maxWidth - gap - chip.width
            val x = if (maxX >= gap) (beside - origin.x).roundToInt().coerceIn(gap, maxX) else (constraints.maxWidth - chip.width) / 2
            val y = (slot.center.y - origin.y - chip.height / 2f).roundToInt()
            // Absolute: the slot is already in screen terms, mirrored or not.
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
    // Hidden, it's the bare mask: a sign in front would still say which way the money went.
    ChipPill(text = amount.money.format(signPrefix = sign), color = color)
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
