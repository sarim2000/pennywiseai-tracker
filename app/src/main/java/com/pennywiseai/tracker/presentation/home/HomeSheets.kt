package com.pennywiseai.tracker.presentation.home

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.preferences.HomeSection
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
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
    onOpenReview: () -> Unit,
    onOpenShareCard: () -> Unit,
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

            // Reviews (Top) — week, cycle and year, whatever the Home banner is offering.
            MenuListItem(
                headline = stringResource(R.string.review_title),
                icon = { Icon(Icons.Default.Insights, contentDescription = null) },
                position = ListItemPosition.Top,
                onClick = {
                    onDismiss()
                    onOpenReview()
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

/** Edit home screen: reorder and hide Home sections (#770). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditHomeSectionsSheet(
    homeSectionLayout: List<Pair<HomeSection, Boolean>>,
    onDismiss: () -> Unit,
    onMoveSection: (HomeSection, Int) -> Unit,
    onSetSectionVisible: (HomeSection, Boolean) -> Unit
) {
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
            homeSectionLayout.forEachIndexed { i, (section, isVisible) ->
                GroupedRow(
                    position = ListItemPosition.from(i, homeSectionLayout.size),
                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    val sectionLabel = stringResource(section.titleRes())
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isVisible) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = i > 0,
                        onClick = { onMoveSection(section, -1) },
                        shapes = IconButtonDefaults.shapes()
                    ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.home_edit_move_up, sectionLabel)) }
                    IconButton(
                        enabled = i < homeSectionLayout.lastIndex,
                        onClick = { onMoveSection(section, +1) },
                        shapes = IconButtonDefaults.shapes()
                    ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.home_edit_move_down, sectionLabel)) }
                    Switch(
                        checked = isVisible,
                        onCheckedChange = { on -> onSetSectionVisible(section, on) }
                    )
                }
            }
        }
    }
}

@StringRes
private fun HomeSection.titleRes(): Int = when (this) {
    HomeSection.SAFE_TO_SPEND -> R.string.home_section_safe_to_spend
    HomeSection.BUDGETS -> R.string.home_section_budgets
    HomeSection.GOALS -> R.string.home_section_goals
    HomeSection.LOANS -> R.string.home_section_loans
    HomeSection.GROUPS -> R.string.home_section_groups
    HomeSection.RECENT_TRANSACTIONS -> R.string.home_section_recent_transactions
    HomeSection.ACCOUNTS -> R.string.home_section_accounts
    HomeSection.SUBSCRIPTIONS -> R.string.home_section_subscriptions
    HomeSection.MONEY_IN_MOTION -> R.string.home_section_money_in_motion
    HomeSection.ACTIVITY -> R.string.home_section_activity
}
