package com.pennywiseai.tracker.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.navigation.Appearance
import com.pennywiseai.tracker.navigation.Categories
import com.pennywiseai.tracker.navigation.Faq
import com.pennywiseai.tracker.navigation.ImportStatement
import com.pennywiseai.tracker.navigation.ManageAccounts
import com.pennywiseai.tracker.navigation.Rules
import com.pennywiseai.tracker.navigation.SettingsPage
import com.pennywiseai.tracker.navigation.SettingsSection
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.animations.pwFadeIn
import com.pennywiseai.tracker.ui.animations.pwFadeOut
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.ProfileAvatar
import com.pennywiseai.tracker.ui.components.SupportDevelopmentDialog
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * The Settings hub, opened from the avatar, so it doubles as the profile page: who
 * the user is, shortcuts to what they open most, then one row per page so everything
 * is two taps away (#1056).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    hubViewModel: SettingsHubViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    val hub by hubViewModel.state.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    // F-Droid builds have no Play billing, so they show a "Support development"
    // tip jar instead of the (un-buyable) Pro upsell. Play builds keep Pro.
    val isFdroidBuild = com.pennywiseai.tracker.BuildConfig.IS_FDROID_BUILD

    val scrollState = rememberScrollState()
    // The bar names the page only once the profile block that heads it has scrolled away.
    var profileHeight by remember { mutableIntStateOf(0) }
    val showBarTitle by remember { derivedStateOf { profileHeight > 0 && scrollState.value >= profileHeight } }

    PennyWiseScaffold(
        modifier = modifier,
        customTopBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(visible = showBarTitle, enter = pwFadeIn(), exit = pwFadeOut()) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
                navigationIcon = { SettingsNavigationContent(onNavigateBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .overScrollVertical()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(horizontal = Dimensions.Padding.content)
                .padding(bottom = Spacing.Layout.scrollBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SettingsProfileHeader(
                state = hub,
                modifier = Modifier.onSizeChanged { profileHeight = it.height },
            )

            // What people came to Settings for most, one tap away instead of a page deep.
            SettingsShortcutTiles(state = hub, onNavigate = onNavigate)

            // Its own block, high on the page: the highest-discoverability slot.
            SettingsGroup {
                if (isFdroidBuild) {
                    SettingsNavItem(
                        icon = Icons.Default.Favorite,
                        title = stringResource(R.string.support_title),
                        accent = true,
                        subtitle = stringResource(R.string.support_subtitle),
                        onClick = { showSupportDialog = true },
                        position = ListItemPosition.Single,
                    )
                } else {
                    // Paid users see "Active" so the row reads as status; free users
                    // see "Upgrade" so it reads as a call to action.
                    SettingsNavItem(
                        icon = Icons.Default.WorkspacePremium,
                        title = if (isProEntitled) stringResource(R.string.settings_pro_title_active) else stringResource(R.string.settings_pro_title_upgrade),
                        accent = true,
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

            SettingsGroupHeader(stringResource(R.string.settings_hub_money))
            SettingsGroup {
                HubRow(
                    icon = Icons.Default.Sms,
                    title = stringResource(R.string.settings_tracking_title),
                    subtitle = stringResource(R.string.settings_tracking_summary),
                    onClick = { open(SettingsSection.TRACKING) },
                    position = ListItemPosition.Top,
                )
                HubRow(
                    icon = Icons.Default.AccountBalance,
                    title = stringResource(R.string.settings_accounts_categories_title),
                    subtitle = stringResource(R.string.settings_accounts_categories_summary),
                    onClick = { open(SettingsSection.ACCOUNTS) },
                    position = ListItemPosition.Middle,
                )
                HubRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = stringResource(R.string.settings_planning_title),
                    subtitle = stringResource(R.string.settings_planning_summary),
                    onClick = { open(SettingsSection.PLANNING) },
                    position = ListItemPosition.Middle,
                )
                HubRow(
                    icon = Icons.Default.CurrencyExchange,
                    title = stringResource(R.string.settings_currency_section),
                    subtitle = stringResource(R.string.settings_currency_summary),
                    onClick = { open(SettingsSection.CURRENCY) },
                    position = ListItemPosition.Bottom,
                    trailingText = hub.baseCurrency,
                )
            }

            SettingsGroupHeader(stringResource(R.string.settings_hub_app))
            SettingsGroup {
                HubRow(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.settings_appearance_title),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    onClick = { onNavigate(Appearance) },
                    position = ListItemPosition.Top,
                    trailingText = stringResource(
                        when (hub.isDarkTheme) {
                            null -> R.string.appearance_mode_system
                            true -> R.string.appearance_mode_dark
                            false -> R.string.appearance_mode_light
                        }
                    ),
                )
                HubRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_formats_title),
                    subtitle = stringResource(R.string.settings_formats_summary),
                    onClick = { open(SettingsSection.FORMATS) },
                    position = ListItemPosition.Middle,
                )
                HubRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.settings_notifications_section),
                    subtitle = stringResource(R.string.settings_notifications_summary),
                    onClick = { open(SettingsSection.NOTIFICATIONS) },
                    position = ListItemPosition.Bottom,
                )
            }

            SettingsGroupHeader(stringResource(R.string.settings_hub_privacy_data))
            SettingsGroup {
                // App lock lives on Security, next to its timeout. A second switch here
                // only repeated that page.
                HubRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_security_section),
                    subtitle = stringResource(R.string.settings_security_summary),
                    onClick = { open(SettingsSection.SECURITY) },
                    position = ListItemPosition.Top,
                )
                HubRow(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.settings_data_section),
                    subtitle = stringResource(R.string.settings_data_summary),
                    onClick = { open(SettingsSection.DATA) },
                    position = ListItemPosition.Bottom,
                )
            }

            SettingsGroupHeader(stringResource(R.string.settings_hub_advanced))
            SettingsGroup {
                HubRow(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.settings_ai_section),
                    subtitle = stringResource(R.string.settings_ai_summary),
                    onClick = { open(SettingsSection.AI) },
                    position = ListItemPosition.Top,
                )
                HubRow(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.settings_developer_section),
                    subtitle = stringResource(R.string.settings_developer_summary),
                    onClick = { open(SettingsSection.DEVELOPER) },
                    position = ListItemPosition.Bottom,
                )
            }

            SettingsGroupHeader(stringResource(R.string.settings_hub_help))
            SettingsGroup {
                HubRow(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = stringResource(R.string.settings_help_faq_title),
                    subtitle = stringResource(R.string.settings_help_faq_subtitle),
                    onClick = { onNavigate(Faq) },
                    position = ListItemPosition.Top,
                )
                HubRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.settings_user_guide_title),
                    subtitle = stringResource(R.string.settings_user_guide_subtitle),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.Links.USER_GUIDE_URL)))
                    },
                    position = ListItemPosition.Middle,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                )
                HubRow(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.settings_report_issue_title),
                    subtitle = stringResource(R.string.settings_report_issue_subtitle),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarim2000/pennywiseai-tracker/issues/new/choose"))
                        context.startActivity(intent)
                    },
                    position = ListItemPosition.Bottom,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                )
            }

            // App Version
            Text(
                text = stringResource(R.string.settings_app_version, com.pennywiseai.tracker.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showUpgradeSheet) {
        UpgradeSheet(onDismiss = { showUpgradeSheet = false })
    }

    if (showSupportDialog) {
        SupportDevelopmentDialog(onDismiss = { showSupportDialog = false })
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ListItemPosition,
    trailingText: String? = null,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
) {
    SettingsNavItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        position = position,
        trailingText = trailingText,
        trailingIcon = trailingIcon,
    )
}

/** A small neutral heading over a group of hub rows. */
@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(start = Spacing.sm, top = Spacing.md)
            .semantics { heading() },
    )
}

/** The user, big and centred, with the app's one promise under their name. */
@Composable
private fun SettingsProfileHeader(state: SettingsHubState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ProfileAvatar(
            name = state.name,
            imageUri = state.imageUri,
            backgroundColor = state.backgroundColor,
            size = Dimensions.Icon.extraLarge,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            initialsStyle = MaterialTheme.typography.headlineLargeEmphasized,
        )
        if (state.name.isNotBlank()) {
            Text(
                text = state.name,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PrivacyPill(isSharing = state.isSharing)
    }
}

@Composable
private fun PrivacyPill(isSharing: Boolean) {
    val success = MaterialTheme.colorScheme.success
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = success.copy(alpha = Dimensions.Alpha.categoryTint),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = success,
                modifier = Modifier.size(Dimensions.Icon.small),
            )
            Text(
                text = stringResource(
                    if (isSharing) R.string.settings_profile_privacy_pill_sharing else R.string.settings_profile_privacy_pill,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** One shortcut tile: what it opens, a cheap count under it, and its corner of the 2×2 block. */
private class ShortcutTile(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val badge: Shape,
    val container: Color,
    val content: Color,
    val target: NavKey,
)

@Composable
private fun SettingsShortcutTiles(state: SettingsHubState, onNavigate: (NavKey) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tiles = listOf(
        ShortcutTile(
            title = stringResource(R.string.manage_accounts_title),
            subtitle = state.accountCount?.let { pluralStringResource(R.plurals.settings_tile_accounts_count, it, it) },
            icon = Icons.Default.AccountBalance,
            badge = MaterialShapes.Cookie9Sided.toShape(),
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            target = ManageAccounts,
        ),
        ShortcutTile(
            title = stringResource(R.string.rules_title),
            subtitle = state.activeRuleCount?.let { pluralStringResource(R.plurals.settings_tile_rules_count, it, it) },
            icon = Icons.Default.Rule,
            badge = MaterialShapes.Clover4Leaf.toShape(),
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            target = Rules,
        ),
        ShortcutTile(
            title = stringResource(R.string.categories_title),
            subtitle = state.categoryCount?.let { pluralStringResource(R.plurals.settings_tile_categories_count, it, it) },
            icon = Icons.Default.Category,
            badge = MaterialShapes.Sunny.toShape(),
            container = colors.secondaryContainer,
            content = colors.onSecondaryContainer,
            target = Categories,
        ),
        ShortcutTile(
            title = stringResource(R.string.import_statement_title),
            subtitle = stringResource(R.string.settings_tile_import_subtitle),
            icon = Icons.Default.UploadFile,
            badge = MaterialShapes.Flower.toShape(),
            container = colors.surfaceContainerHighest,
            content = colors.onSurface,
            target = ImportStatement,
        ),
    )
    // The block's outer corners are big and its inner ones small, like a grouped list.
    val big = Dimensions.CornerRadius.extraLarge
    val small = Dimensions.CornerRadius.medium
    val corners = listOf(
        RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = small, bottomStart = small),
        RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = small, bottomStart = small),
        RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = small, bottomStart = big),
        RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = big, bottomStart = small),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tiles.chunked(2).forEachIndexed { row, pair ->
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                pair.forEachIndexed { column, tile ->
                    SettingsShortcutTile(
                        tile = tile,
                        shape = corners[row * 2 + column],
                        onClick = { onNavigate(tile.target) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsShortcutTile(
    tile: ShortcutTile,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = tile.container,
        contentColor = tile.content,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatarLarge)
                    .clip(tile.badge)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                // The badge is a neutral surface, so its icon takes neutral ink, not the tile's.
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(Dimensions.Icon.medium),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tile.subtitle != null) {
                    Text(
                        text = tile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = Dimensions.Alpha.subtitle),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ListItemPosition,
    trailingText: String? = null,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    // Pro and support are the one accent on an otherwise neutral list.
    accent: Boolean = false,
) {
    val container = if (accent) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val tint = if (accent) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    GroupedRow(position = position, onClick = onClick) {
        IconTile(icon = icon, containerColor = container, contentColor = tint)
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
        IconTile(
            icon = icon,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                bottom = Spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
        ) {
            itemsIndexed(choices) { index, choice ->
                GroupedRow(
                    position = ListItemPosition.from(index, choices.size),
                    onClick = {
                        choice.onSelect()
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    },
                    containerColor = if (choice.selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ) {
                    RadioButton(selected = choice.selected, onClick = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                        if (choice.description != null) {
                            Text(
                                text = choice.description,
                                style = MaterialTheme.typography.bodyMedium,
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
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
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
