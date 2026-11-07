package com.pennywiseai.tracker.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.navigation.Appearance
import com.pennywiseai.tracker.navigation.Faq
import com.pennywiseai.tracker.navigation.SettingsPage
import com.pennywiseai.tracker.navigation.SettingsSection
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.blue_dark
import com.pennywiseai.tracker.ui.theme.blue_light
import com.pennywiseai.tracker.ui.theme.green_dark
import com.pennywiseai.tracker.ui.theme.green_light
import com.pennywiseai.tracker.ui.theme.grey_dark
import com.pennywiseai.tracker.ui.theme.grey_light
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.orange_dark
import com.pennywiseai.tracker.ui.theme.orange_light
import com.pennywiseai.tracker.ui.theme.pink_dark
import com.pennywiseai.tracker.ui.theme.pink_light
import com.pennywiseai.tracker.ui.theme.purple_dark
import com.pennywiseai.tracker.ui.theme.purple_light
import com.pennywiseai.tracker.ui.theme.red_dark
import com.pennywiseai.tracker.ui.theme.red_light
import com.pennywiseai.tracker.ui.theme.teal_dark
import com.pennywiseai.tracker.ui.theme.teal_light
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/** The Settings hub: one row per page, so everything is two taps away (#1056). */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    // F-Droid builds have no Play billing, so they show a "Support development"
    // tip jar instead of the (un-buyable) Pro upsell. Play builds keep Pro.
    val isFdroidBuild = com.pennywiseai.tracker.BuildConfig.IS_FDROID_BUILD

    SettingsPageScaffold(
        title = stringResource(R.string.settings_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        // Top of Settings on purpose: highest-discoverability slot.
        SettingsGroup {
            if (isFdroidBuild) {
                SettingsNavItem(
                    icon = Icons.Default.Favorite,
                    iconBgColor = yellow_light,
                    iconTint = yellow_dark,
                    title = stringResource(R.string.support_title),
                    subtitle = stringResource(R.string.support_subtitle),
                    onClick = { showSupportDialog = true },
                    position = ListItemPosition.Single,
                )
            } else {
                // Row content adapts to entitlement state — paid users see
                // "Active" so the row reads as status, free users see "Upgrade"
                // so it reads as a call-to-action.
                SettingsNavItem(
                    icon = Icons.Default.AutoAwesome,
                    iconBgColor = yellow_light,
                    iconTint = yellow_dark,
                    title = if (isProEntitled) stringResource(R.string.settings_pro_title_active) else stringResource(R.string.settings_pro_title_upgrade),
                    subtitle = if (isProEntitled) {
                        stringResource(R.string.settings_pro_subtitle_active)
                    } else {
                        stringResource(R.string.settings_pro_subtitle_upgrade)
                    },
                    onClick = { showUpgradeSheet = true },
                    position = ListItemPosition.Single,
                )
            }
        }

        fun open(section: SettingsSection) = onNavigate(SettingsPage(section))

        SectionHeaderV2(title = stringResource(R.string.settings_hub_money))
        SettingsGroup {
            SettingsNavItem(
                icon = Icons.Default.Sms,
                iconBgColor = orange_light,
                iconTint = orange_dark,
                title = stringResource(R.string.settings_tracking_title),
                subtitle = stringResource(R.string.settings_tracking_summary),
                onClick = { open(SettingsSection.TRACKING) },
                position = ListItemPosition.Top
            )
            SettingsNavItem(
                icon = Icons.Default.AccountBalance,
                iconBgColor = red_light,
                iconTint = red_dark,
                title = stringResource(R.string.settings_accounts_categories_title),
                subtitle = stringResource(R.string.settings_accounts_categories_summary),
                onClick = { open(SettingsSection.ACCOUNTS) },
                position = ListItemPosition.Middle
            )
            SettingsNavItem(
                icon = Icons.Default.AccountBalanceWallet,
                iconBgColor = green_light,
                iconTint = green_dark,
                title = stringResource(R.string.settings_planning_title),
                subtitle = stringResource(R.string.settings_planning_summary),
                onClick = { open(SettingsSection.PLANNING) },
                position = ListItemPosition.Middle
            )
            SettingsNavItem(
                icon = Icons.Default.CurrencyExchange,
                iconBgColor = teal_light,
                iconTint = teal_dark,
                title = stringResource(R.string.settings_currency_section),
                subtitle = stringResource(R.string.settings_currency_summary),
                onClick = { open(SettingsSection.CURRENCY) },
                position = ListItemPosition.Bottom
            )
        }

        SectionHeaderV2(title = stringResource(R.string.settings_personalization_section))
        SettingsGroup {
            SettingsNavItem(
                icon = Icons.Default.Palette,
                iconBgColor = orange_light,
                iconTint = orange_dark,
                title = stringResource(R.string.settings_appearance_title),
                subtitle = stringResource(R.string.settings_appearance_subtitle),
                onClick = { onNavigate(Appearance) },
                position = ListItemPosition.Top
            )
            SettingsNavItem(
                icon = Icons.Default.Language,
                iconBgColor = blue_light,
                iconTint = blue_dark,
                title = stringResource(R.string.settings_formats_title),
                subtitle = stringResource(R.string.settings_formats_summary),
                onClick = { open(SettingsSection.FORMATS) },
                position = ListItemPosition.Middle
            )
            SettingsNavItem(
                icon = Icons.Default.Notifications,
                iconBgColor = indigo_light,
                iconTint = indigo_dark,
                title = stringResource(R.string.settings_notifications_section),
                subtitle = stringResource(R.string.settings_notifications_summary),
                onClick = { open(SettingsSection.NOTIFICATIONS) },
                position = ListItemPosition.Bottom
            )
        }

        SectionHeaderV2(title = stringResource(R.string.settings_hub_privacy_data))
        SettingsGroup {
            SettingsNavItem(
                icon = Icons.Default.Lock,
                iconBgColor = red_light,
                iconTint = red_dark,
                title = stringResource(R.string.settings_security_section),
                subtitle = stringResource(R.string.settings_security_summary),
                onClick = { open(SettingsSection.SECURITY) },
                position = ListItemPosition.Top
            )
            SettingsNavItem(
                icon = Icons.Default.Backup,
                iconBgColor = purple_light,
                iconTint = purple_dark,
                title = stringResource(R.string.settings_data_section),
                subtitle = stringResource(R.string.settings_data_summary),
                onClick = { open(SettingsSection.DATA) },
                position = ListItemPosition.Bottom
            )
        }

        SectionHeaderV2(title = stringResource(R.string.settings_hub_advanced))
        SettingsGroup {
            SettingsNavItem(
                icon = Icons.Default.AutoAwesome,
                iconBgColor = yellow_light,
                iconTint = yellow_dark,
                title = stringResource(R.string.settings_ai_section),
                subtitle = stringResource(R.string.settings_ai_summary),
                onClick = { open(SettingsSection.AI) },
                position = ListItemPosition.Top
            )
            SettingsNavItem(
                icon = Icons.Default.Code,
                iconBgColor = grey_light,
                iconTint = grey_dark,
                title = stringResource(R.string.settings_developer_section),
                subtitle = stringResource(R.string.settings_developer_summary),
                onClick = { open(SettingsSection.DEVELOPER) },
                position = ListItemPosition.Bottom
            )
        }

        SectionHeaderV2(title = stringResource(R.string.settings_support_section))
        SettingsGroup {
            SettingsNavItem(
                icon = Icons.AutoMirrored.Filled.Help,
                iconBgColor = pink_light,
                iconTint = pink_dark,
                title = stringResource(R.string.settings_help_faq_title),
                subtitle = stringResource(R.string.settings_help_faq_subtitle),
                onClick = { onNavigate(Faq) },
                position = ListItemPosition.Top
            )
            SettingsNavItem(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                iconBgColor = teal_light,
                iconTint = teal_dark,
                title = stringResource(R.string.settings_user_guide_title),
                subtitle = stringResource(R.string.settings_user_guide_subtitle),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.Links.USER_GUIDE_URL)))
                },
                position = ListItemPosition.Middle,
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
            )
            SettingsNavItem(
                icon = Icons.Default.BugReport,
                iconBgColor = blue_light,
                iconTint = blue_dark,
                title = stringResource(R.string.settings_report_issue_title),
                subtitle = stringResource(R.string.settings_report_issue_subtitle),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarim2000/pennywiseai-tracker/issues/new/choose"))
                    context.startActivity(intent)
                },
                position = ListItemPosition.Bottom,
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
            )
        }

        // App Version
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.settings_app_version, com.pennywiseai.tracker.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.md))
    }

    if (showUpgradeSheet) {
        UpgradeSheet(onDismiss = { showUpgradeSheet = false })
    }

    if (showSupportDialog) {
        SupportDevelopmentDialog(onDismiss = { showSupportDialog = false })
    }
}

// ── Reusable Settings Components ──
//
// Row chrome — tonal surface, grouped-corner shape, padding, minimum height,
// the tinted icon circle, title/subtitle typography — lives in the shared
// `GroupedList` / `GroupedRow` / `IconTile` / `RowLabels` primitives, so a
// settings row and a grouped row on any other screen are literally the same
// object. These wrappers only add the settings-specific trailing affordance.

/** The frame shared by the hub and its pages; [intro] says what a page is for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    intro: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    PennyWiseScaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehavior,
                scrollBehaviorLarge = scrollBehavior,
                title = title,
                hasBackButton = true,
                navigationContent = { SettingsNavigationContent(onNavigateBack) },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (intro != null) {
                Row(
                    modifier = Modifier.padding(bottom = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.inline)
                    )
                    Text(
                        text = intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    GroupedList(content = content)
}

@Composable
internal fun SettingsNavItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ListItemPosition,
    trailingText: String? = null,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
) {
    GroupedRow(position = position, onClick = onClick) {
        IconTile(icon = icon, containerColor = iconBgColor, contentColor = iconTint)
        RowLabels(title = title, subtitle = subtitle)
        if (trailingText != null) {
            // Capped so a long account name can't squeeze the labels away; its last digits stay visible.
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.widthIn(max = Dimensions.Component.rowValueMaxWidth)
            )
        }
        // The chevron is a hint, not a control — at 20dp it stops competing
        // with the leading icon for attention the way a 24dp one did.
        Icon(
            trailingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.Icon.inline)
        )
    }
}

@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: ListItemPosition,
    enabled: Boolean = true
) {
    GroupedRow(
        position = position,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) }
    ) {
        IconTile(icon = icon, containerColor = iconBgColor, contentColor = iconTint)
        RowLabels(
            title = title,
            subtitle = subtitle,
            subtitleColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

internal class SettingsChoice(
    val label: String,
    val selected: Boolean,
    val description: String? = null,
    val onSelect: () -> Unit
)

/**
 * Pick one of [choices]: picking applies it and closes the sheet.
 * A choice that opens another surface should do so from [onDismiss], once the sheet is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsChoiceSheet(
    title: String,
    choices: List<SettingsChoice>,
    onDismiss: () -> Unit,
    body: String? = null
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    // Long lists (31 days, every currency) open on the current choice.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (choices.indexOfFirst { it.selected } - 2).coerceAtLeast(0)
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .selectableGroup()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = Spacing.md)
        ) {
            items(choices) { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = choice.selected,
                            role = Role.RadioButton,
                            onClick = {
                                choice.onSelect()
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                            }
                        )
                        .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                        .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = choice.selected, onClick = null)
                    Column {
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                        if (choice.description != null) {
                            Text(
                                text = choice.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationContent(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .animateContentSize()
            .padding(start = Spacing.md)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNavigateBack,
            ),
    ) {
        IconButton(
            onClick = onNavigateBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                modifier = Modifier.size(Dimensions.Icon.inline)
            )
        }
    }
}
