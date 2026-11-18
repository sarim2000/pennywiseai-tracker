package com.pennywiseai.tracker.presentation.home

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.preferences.HomeSection
import com.pennywiseai.tracker.data.preferences.HomeSectionLayout
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.theme.*

/**
 * The "More options" sheet behind Home's avatar / menu button. Every row
 * closes the sheet, then runs its action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeMenuSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenShareCard: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditHome: () -> Unit,
    onReadMessagesAgain: () -> Unit
) {
    // The screen's context, read outside the sheet's own window.
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
        ) {
            // Title
            Text(
                text = stringResource(R.string.home_menu_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = Spacing.sm)
                    .fillMaxWidth()
            )

            // Insights (Top) — what needs attention this cycle.
            MenuListItem(
                headline = stringResource(R.string.insights_title),
                icon = { Icon(Icons.Default.Insights, contentDescription = null) },
                position = ListItemPosition.Top,
                onClick = {
                    onDismiss()
                    onOpenInsights()
                }
            )

            // Reviews (Middle) — week, cycle and year, whatever the Home banner is offering.
            MenuListItem(
                headline = stringResource(R.string.review_title),
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    onOpenReview()
                }
            )

            // Achievements (Middle) — beside the share card, which they feed.
            MenuListItem(
                headline = stringResource(R.string.achievements_title),
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    onOpenAchievements()
                }
            )

            // Share card (Middle) — the growth loop lives here rather than buried
            // in the Subscriptions screen, where nobody would find it.
            MenuListItem(
                headline = stringResource(R.string.home_menu_share_card),
                icon = { Icon(Icons.Default.Share, contentDescription = null) },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    onOpenShareCard()
                }
            )

            // Settings (Middle)
            MenuListItem(
                headline = stringResource(R.string.home_menu_settings),
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    onOpenSettings()
                }
            )

            // Edit home screen (Middle) — reorder / hide Home sections (#770)
            MenuListItem(
                headline = stringResource(R.string.home_menu_edit_home),
                icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    onEditHome()
                }
            )

            // Read messages again (Middle) — the full resync otherwise
            // hides behind a long-press on Sync.
            MenuListItem(
                headline = stringResource(R.string.home_resync_title),
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    onReadMessagesAgain()
                }
            )

            // Join Discord (Middle)
            MenuListItem(
                headline = stringResource(R.string.home_menu_discord),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_discord),
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                },
                position = ListItemPosition.Middle,
                onClick = {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.Links.DISCORD_URL))
                    context.startActivity(intent)
                }
            )

            // Rate on Play Store (Bottom)
            MenuListItem(
                headline = stringResource(R.string.home_menu_rate),
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                position = ListItemPosition.Bottom,
                onClick = {
                    onDismiss()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                        context.startActivity(intent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Version footer
            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) {
                    null
                }
            }
            versionName?.let {
                Text(
                    text = stringResource(R.string.home_version, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * A row in the "More options" bottom sheet. Uses the shared grouped-list
 * primitives so it matches the rows in Settings exactly — this sheet is the
 * shortcut *into* Settings, so the two looking different was jarring.
 */
@Composable
private fun MenuListItem(
    headline: String,
    icon: @Composable () -> Unit,
    position: ListItemPosition,
    onClick: () -> Unit,
) {
    GroupedRow(
        position = position,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.secondary
        ) { icon() }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Edit home screen: reorder and hide Home sections (#770). Rows reorder by dragging
 * their handle; the order lives locally while the finger is down and is saved once
 * on release, so DataStore emissions can't yank the row mid-drag. TalkBack users get
 * "Move up / down" custom actions instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditHomeSectionsSheet(
    homeSectionLayout: List<Pair<HomeSection, Boolean>>,
    onDismiss: () -> Unit,
    onMoveSection: (HomeSection, Int) -> Unit,
    onReorderSections: (List<HomeSection>) -> Unit,
    onSetSectionVisible: (HomeSection, Boolean) -> Unit
) {
    // One state object for the sheet's lifetime: the handles' pointerInput lambdas
    // capture it once, so it's synced from the saved layout rather than re-keyed.
    var items by remember { mutableStateOf(homeSectionLayout) }
    var dragged by remember { mutableStateOf<HomeSection?>(null) }
    // Skipped mid-drag so a save landing late (or a Switch toggled by a second finger)
    // can't move rows out from under the finger; the next emission catches up.
    LaunchedEffect(homeSectionLayout) { if (dragged == null) items = homeSectionLayout }
    val savedLayout by rememberUpdatedState(homeSectionLayout)
    val reorderSections by rememberUpdatedState(onReorderSections)
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<HomeSection, Int>() }
    val gapPx = with(LocalDensity.current) { Spacing.Layout.groupedListGap.toPx() }
    val haptics = LocalHapticFeedback.current
    val liftedShape = ListItemPosition.Single.toShape()

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
        ) {
            Text(
                text = stringResource(R.string.home_edit_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.home_edit_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(bottom = Spacing.sm)
                    .fillMaxWidth()
            )
            items.forEachIndexed { i, (section, isVisible) ->
                // Keyed so a row keeps its composition (and its in-flight drag) as it moves.
                key(section) {
                    val isDragged = dragged == section
                    val sectionLabel = stringResource(section.titleRes())
                    val moveUp = stringResource(R.string.home_edit_move_up, sectionLabel)
                    val moveDown = stringResource(R.string.home_edit_move_down, sectionLabel)
                    GroupedRow(
                        position = if (isDragged) ListItemPosition.Single else ListItemPosition.from(i, items.size),
                        containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .padding(horizontal = Dimensions.Padding.content)
                            .onSizeChanged { rowHeights[section] = it.height }
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragged) dragOffset else 0f
                                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                                shape = liftedShape
                                clip = false
                            },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(Dimensions.Component.minTouchTarget)
                                .padding(Spacing.smd)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        // One row at a time: a second finger on another handle is ignored.
                                        onDragStart = start@{
                                            if (dragged != null) return@start
                                            dragged = section
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = end@{
                                            if (dragged != section) return@end
                                            // Saves the order on screen, not a delta, so it can't drift
                                            // from what the user sees. Always saved: the last emission
                                            // can be older than a write still in flight, so comparing
                                            // against it could drop this drop. DataStore skips a no-op
                                            // write. The on/off state skipped mid-drag is picked up here.
                                            val order = items.map { it.first }
                                            items = HomeSectionLayout.reorder(savedLayout, order)
                                            reorderSections(order)
                                            dragged = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = cancel@{
                                            if (dragged != section) return@cancel
                                            items = savedLayout
                                            dragged = null
                                            dragOffset = 0f
                                        }
                                    ) { change, amount ->
                                        if (dragged != section) return@detectDragGestures
                                        change.consume()
                                        dragOffset += amount.y
                                        // Swap with a neighbour once the row is past its midpoint; a
                                        // fast flick can cross several rows in one event, so loop.
                                        while (true) {
                                            val at = items.indexOfFirst { it.first == section }
                                            val neighbour = if (dragOffset > 0) at + 1 else at - 1
                                            val step = items.getOrNull(neighbour)
                                                ?.let { (rowHeights[it.first] ?: 0) + gapPx } ?: break
                                            if (step <= 0f || kotlin.math.abs(dragOffset) <= step / 2) break
                                            items = HomeSectionLayout.move(items, section, neighbour - at)
                                            dragOffset -= if (neighbour > at) step else -step
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                        )
                        Text(
                            text = sectionLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isVisible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            // On the label, the row's focusable node, since the Surface
                            // doesn't merge its children and the handle has no semantics.
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    customActions = listOfNotNull(
                                        CustomAccessibilityAction(moveUp) { onMoveSection(section, -1); true }.takeIf { i > 0 },
                                        CustomAccessibilityAction(moveDown) { onMoveSection(section, +1); true }.takeIf { i < items.lastIndex }
                                    )
                                }
                        )
                        Switch(
                            checked = isVisible,
                            onCheckedChange = { on -> onSetSectionVisible(section, on) }
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun HomeSection.titleRes(): Int = when (this) {
    HomeSection.SAFE_TO_SPEND -> R.string.home_section_safe_to_spend
    HomeSection.BUDGETS -> R.string.home_section_budgets
    HomeSection.INSIGHTS -> R.string.home_section_insights
    HomeSection.GOALS -> R.string.home_section_goals
    HomeSection.LOANS -> R.string.home_section_loans
    HomeSection.GROUPS -> R.string.home_section_groups
    HomeSection.RECENT_TRANSACTIONS -> R.string.home_section_recent_transactions
    HomeSection.ACCOUNTS -> R.string.home_section_accounts
    HomeSection.SUBSCRIPTIONS -> R.string.home_section_subscriptions
    HomeSection.MONEY_IN_MOTION -> R.string.home_section_money_in_motion
    HomeSection.ACTIVITY -> R.string.home_section_activity
}
