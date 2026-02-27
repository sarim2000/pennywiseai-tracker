package com.pennywiseai.tracker.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.SectionHeader
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.orange_light
import com.pennywiseai.tracker.ui.theme.orange_dark
import com.pennywiseai.tracker.ui.theme.green_light
import com.pennywiseai.tracker.ui.theme.green_dark
import com.pennywiseai.tracker.ui.theme.teal_light
import com.pennywiseai.tracker.ui.theme.teal_dark
import com.pennywiseai.tracker.ui.theme.blue_light
import com.pennywiseai.tracker.ui.theme.blue_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.red_light
import com.pennywiseai.tracker.ui.theme.red_dark
import com.pennywiseai.tracker.ui.theme.pink_light
import com.pennywiseai.tracker.ui.theme.pink_dark
import com.pennywiseai.tracker.ui.theme.purple_light
import com.pennywiseai.tracker.ui.theme.purple_dark
import com.pennywiseai.tracker.ui.theme.cyan_light
import com.pennywiseai.tracker.ui.theme.cyan_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.grey_light
import com.pennywiseai.tracker.ui.theme.grey_dark
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.utils.CurrencyFormatter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onNavigateToUnrecognizedSms: () -> Unit = {},
    onNavigateToManageAccounts: () -> Unit = {},
    onNavigateToFaq: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToExchangeRates: () -> Unit = {},
    onNavigateToAppearance: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    appLockViewModel: com.pennywiseai.tracker.ui.viewmodel.AppLockViewModel = hiltViewModel()
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by settingsViewModel.downloadState.collectAsStateWithLifecycle()
    val downloadProgress by settingsViewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedMB by settingsViewModel.downloadedMB.collectAsStateWithLifecycle()
    val totalMB by settingsViewModel.totalMB.collectAsStateWithLifecycle()
    val isDeveloperModeEnabled by settingsViewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val smsScanMonths by settingsViewModel.smsScanMonths.collectAsStateWithLifecycle(initialValue = 3)
    val smsScanAllTime by settingsViewModel.smsScanAllTime.collectAsStateWithLifecycle(initialValue = false)
    val baseCurrency by settingsViewModel.baseCurrency.collectAsStateWithLifecycle(initialValue = "INR")
    val importExportMessage by settingsViewModel.importExportMessage.collectAsStateWithLifecycle()
    val exportedBackupFile by settingsViewModel.exportedBackupFile.collectAsStateWithLifecycle()
    val unifiedCurrencyMode by settingsViewModel.unifiedCurrencyMode.collectAsStateWithLifecycle(initialValue = false)
    val displayCurrency by settingsViewModel.displayCurrency.collectAsStateWithLifecycle(initialValue = "INR")
    val availableCurrencies by settingsViewModel.availableCurrencies.collectAsStateWithLifecycle()
    var showSmsScanDialog by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showDisplayCurrencyDialog by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importBackup(it)
            }
        }
    )

    // File saver for export
    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.saveBackupToFile(it)
            }
        }
    )

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Settings",
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
            // ── Personalization ──
            SectionHeader(title = "Personalization")
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.Default.Palette,
                    iconBgColor = orange_light,
                    iconTint = orange_dark,
                    title = "Appearance",
                    subtitle = "Theme, colors, fonts & navigation",
                    onClick = onNavigateToAppearance,
                    position = ItemPosition.SINGLE
                )
            }

            // ── Currency ──
            SectionHeader(title = "Currency")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.CurrencyExchange,
                    iconBgColor = green_light,
                    iconTint = green_dark,
                    title = "Unified Currency Mode",
                    subtitle = "Convert all transactions to display currency",
                    checked = unifiedCurrencyMode,
                    onCheckedChange = { settingsViewModel.setUnifiedCurrencyMode(it) },
                    position = ItemPosition.TOP
                )
                AnimatedVisibility(visible = unifiedCurrencyMode) {
                    SettingsNavItem(
                        icon = Icons.Default.AttachMoney,
                        iconBgColor = teal_light,
                        iconTint = teal_dark,
                        title = "Display Currency",
                        subtitle = "All amounts shown in this currency",
                        onClick = { showDisplayCurrencyDialog = true },
                        position = ItemPosition.MIDDLE,
                        trailingText = "${CurrencyFormatter.getCurrencySymbol(displayCurrency)} $displayCurrency"
                    )
                }
                SettingsNavItem(
                    icon = Icons.Default.SwapHoriz,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = "Exchange Rates",
                    subtitle = "View and customize rates",
                    onClick = onNavigateToExchangeRates,
                    position = ItemPosition.MIDDLE
                )
                SettingsDropdownItem(
                    icon = Icons.Default.Flag,
                    iconBgColor = indigo_light,
                    iconTint = indigo_dark,
                    title = "Default Currency",
                    subtitle = "Currency used for conversions",
                    currentValue = "${CurrencyFormatter.getCurrencySymbol(baseCurrency)} $baseCurrency",
                    expanded = showCurrencyDropdown,
                    onExpandedChange = { showCurrencyDropdown = it },
                    position = ItemPosition.BOTTOM
                ) {
                    availableCurrencies.forEach { currency ->
                        DropdownMenuItem(
                            text = {
                                Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency")
                            },
                            onClick = {
                                settingsViewModel.updateBaseCurrency(currency)
                                showCurrencyDropdown = false
                            },
                            leadingIcon = if (currency == baseCurrency) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // ── Security ──
            SectionHeader(title = "Security")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.Lock,
                    iconBgColor = red_light,
                    iconTint = red_dark,
                    title = "App Lock",
                    subtitle = if (appLockUiState.canUseBiometric) {
                        "Protect your data with biometric authentication"
                    } else {
                        appLockUiState.biometricCapability.getErrorMessage()
                    },
                    checked = appLockUiState.isLockEnabled,
                    onCheckedChange = { appLockViewModel.setAppLockEnabled(it) },
                    enabled = appLockUiState.canUseBiometric,
                    position = if (appLockUiState.isLockEnabled) ItemPosition.TOP else ItemPosition.SINGLE
                )
                AnimatedVisibility(visible = appLockUiState.isLockEnabled) {
                    SettingsNavItem(
                        icon = Icons.Default.Timer,
                        iconBgColor = pink_light,
                        iconTint = pink_dark,
                        title = "Lock Timeout",
                        subtitle = when (appLockUiState.timeoutMinutes) {
                            0 -> "Lock immediately"
                            1 -> "After 1 minute"
                            else -> "After ${appLockUiState.timeoutMinutes} minutes"
                        },
                        onClick = { showTimeoutDialog = true },
                        position = ItemPosition.BOTTOM
                    )
                }
            }

            // ── Data Management ──
            SectionHeader(title = "Data Management")
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.Default.AccountBalance,
                    iconBgColor = red_light,
                    iconTint = red_dark,
                    title = "Manage Accounts",
                    subtitle = "View and manage your bank accounts",
                    onClick = onNavigateToManageAccounts,
                    position = ItemPosition.TOP
                )
                SettingsNavItem(
                    icon = Icons.Default.Category,
                    iconBgColor = purple_light,
                    iconTint = purple_dark,
                    title = "Categories",
                    subtitle = "Manage expense and income categories",
                    onClick = onNavigateToCategories,
                    position = ItemPosition.MIDDLE
                )
                SettingsNavItem(
                    icon = Icons.Default.AutoAwesome,
                    iconBgColor = orange_light,
                    iconTint = orange_dark,
                    title = "Smart Rules",
                    subtitle = "Automatic transaction categorization",
                    onClick = onNavigateToRules,
                    position = ItemPosition.MIDDLE
                )
                SettingsNavItem(
                    icon = Icons.Default.AccountBalanceWallet,
                    iconBgColor = green_light,
                    iconTint = green_dark,
                    title = "Budgets",
                    subtitle = "Track spending limits by category",
                    onClick = onNavigateToBudgets,
                    position = ItemPosition.MIDDLE
                )
                SettingsNavItem(
                    icon = Icons.Default.Upload,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = "Export Data",
                    subtitle = "Backup all data to a file",
                    onClick = { settingsViewModel.exportBackup() },
                    position = ItemPosition.MIDDLE
                )
                SettingsNavItem(
                    icon = Icons.Default.Download,
                    iconBgColor = cyan_light,
                    iconTint = cyan_dark,
                    title = "Import Data",
                    subtitle = "Restore data from backup",
                    onClick = { importLauncher.launch("*/*") },
                    position = ItemPosition.MIDDLE
                )
                SettingsNavItem(
                    icon = Icons.Default.CalendarMonth,
                    iconBgColor = teal_light,
                    iconTint = teal_dark,
                    title = "SMS Scan Period",
                    subtitle = if (smsScanAllTime) "Scan all SMS messages" else "Scan last $smsScanMonths months",
                    onClick = { showSmsScanDialog = true },
                    position = ItemPosition.BOTTOM,
                    trailingText = if (smsScanAllTime) "All Time" else "$smsScanMonths mo"
                )
            }

            // ── AI Features ──
            SectionHeader(title = "AI Features")
            SettingsGroup {
                AiChatSettingsItem(
                    downloadState = downloadState,
                    downloadProgress = downloadProgress,
                    downloadedMB = downloadedMB,
                    totalMB = totalMB,
                    onDownload = { settingsViewModel.startModelDownload() },
                    onCancel = { settingsViewModel.cancelDownload() },
                    onDelete = { settingsViewModel.deleteModel() }
                )
            }

            // ── Developer ──
            SectionHeader(title = "Developer")
            SettingsGroup {
                SettingsSwitchRow(
                    icon = Icons.Default.Code,
                    iconBgColor = grey_light,
                    iconTint = grey_dark,
                    title = "Developer Mode",
                    subtitle = "Show technical information in chat",
                    checked = isDeveloperModeEnabled,
                    onCheckedChange = { settingsViewModel.toggleDeveloperMode(it) },
                    position = ItemPosition.SINGLE
                )
            }

            // ── Support & Community ──
            SectionHeader(title = "Support & Community")
            SettingsGroup {
                SettingsNavItem(
                    icon = Icons.AutoMirrored.Filled.Help,
                    iconBgColor = pink_light,
                    iconTint = pink_dark,
                    title = "Help & FAQ",
                    subtitle = "Frequently asked questions and help",
                    onClick = onNavigateToFaq,
                    position = ItemPosition.TOP
                )
                SettingsNavItem(
                    icon = Icons.Default.BugReport,
                    iconBgColor = blue_light,
                    iconTint = blue_dark,
                    title = "Report an Issue",
                    subtitle = "Submit bug reports on GitHub",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sarim2000/pennywiseai-tracker/issues/new/choose"))
                        context.startActivity(intent)
                    },
                    position = ItemPosition.BOTTOM,
                    trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
                )
            }

            // App Version
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "PennyWise v${com.pennywiseai.tracker.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }

    // ── Dialogs ──

    // Display Currency Dialog
    if (showDisplayCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showDisplayCurrencyDialog = false },
            title = { Text("Display Currency") },
            text = {
                Column {
                    availableCurrencies.forEach { currency ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = currency == displayCurrency,
                                    onClick = {
                                        settingsViewModel.setDisplayCurrency(currency)
                                        showDisplayCurrencyDialog = false
                                    }
                                )
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currency == displayCurrency,
                                onClick = {
                                    settingsViewModel.setDisplayCurrency(currency)
                                    showDisplayCurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = "${CurrencyFormatter.getCurrencySymbol(currency)} $currency",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDisplayCurrencyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // SMS Scan Period Dialog
    if (showSmsScanDialog) {
        AlertDialog(
            onDismissRequest = { showSmsScanDialog = false },
            title = { Text("SMS Scan Period") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Choose how many months of SMS history to scan for transactions",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    val options = listOf(-1) + listOf(1, 2, 3, 6, 12, 24)
                    options.forEach { months ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (months == -1) {
                                        settingsViewModel.updateSmsScanAllTime(true)
                                    } else {
                                        settingsViewModel.updateSmsScanMonths(months)
                                        settingsViewModel.updateSmsScanAllTime(false)
                                    }
                                    showSmsScanDialog = false
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isSelected = if (months == -1) smsScanAllTime else smsScanMonths == months && !smsScanAllTime
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (months == -1) {
                                        settingsViewModel.updateSmsScanAllTime(true)
                                    } else {
                                        settingsViewModel.updateSmsScanMonths(months)
                                        settingsViewModel.updateSmsScanAllTime(false)
                                    }
                                    showSmsScanDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.md))
                            Text(
                                text = when (months) {
                                    -1 -> "All Time"
                                    1 -> "1 month"
                                    24 -> "2 years"
                                    else -> "$months months"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSmsScanDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Show import/export message
    importExportMessage?.let { message ->
        if (exportedBackupFile != null && message.contains("successfully! Choose")) {
            showExportOptionsDialog = true
        } else {
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(5000)
                settingsViewModel.clearImportExportMessage()
            }

            AlertDialog(
                onDismissRequest = { settingsViewModel.clearImportExportMessage() },
                title = { Text("Backup Status") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { settingsViewModel.clearImportExportMessage() }) {
                        Text("OK")
                    }
                }
            )
        }
    }

    // Export options dialog
    if (showExportOptionsDialog && exportedBackupFile != null) {
        val timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss")
        )
        val fileName = "PennyWise_Backup_$timestamp.pennywisebackup"

        AlertDialog(
            onDismissRequest = {
                showExportOptionsDialog = false
                settingsViewModel.clearImportExportMessage()
            },
            title = { Text("Save Backup") },
            text = {
                Column {
                    Text("Backup created successfully!")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Choose how you want to save it:", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            exportSaveLauncher.launch(fileName)
                            showExportOptionsDialog = false
                            settingsViewModel.clearImportExportMessage()
                        }
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Files")
                    }

                    TextButton(
                        onClick = {
                            settingsViewModel.shareBackup()
                            showExportOptionsDialog = false
                            settingsViewModel.clearImportExportMessage()
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportOptionsDialog = false
                        settingsViewModel.clearImportExportMessage()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Lock Timeout Dialog
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Lock Timeout") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Choose when to lock the app after it goes to background",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))

                    val timeoutOptions = listOf(
                        0 to "Immediately",
                        1 to "1 minute",
                        5 to "5 minutes",
                        15 to "15 minutes"
                    )

                    timeoutOptions.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appLockViewModel.setTimeoutMinutes(minutes)
                                    showTimeoutDialog = false
                                }
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLockUiState.timeoutMinutes == minutes,
                                onClick = {
                                    appLockViewModel.setTimeoutMinutes(minutes)
                                    showTimeoutDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

// ── Reusable Settings Components ──

private enum class ItemPosition { TOP, MIDDLE, BOTTOM, SINGLE }

private fun ItemPosition.toShape(): RoundedCornerShape = when (this) {
    ItemPosition.TOP -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    ItemPosition.MIDDLE -> RoundedCornerShape(4.dp)
    ItemPosition.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    ItemPosition.SINGLE -> RoundedCornerShape(16.dp)
}

@Composable
private fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
        content = content
    )
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: ItemPosition,
    trailingText: String? = null,
    trailingIcon: ImageVector = Icons.Default.ChevronRight
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = position.toShape()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: ItemPosition,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = position.toShape()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    currentValue: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    position: ItemPosition,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = position.toShape()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange
            ) {
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Currency") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) },
                    content = dropdownContent
                )
            }
        }
    }
}

@Composable
private fun AiChatSettingsItem(
    downloadState: DownloadState,
    downloadProgress: Int,
    downloadedMB: Long,
    totalMB: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(yellow_light),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = yellow_dark, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Chat Assistant",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when (downloadState) {
                            DownloadState.NOT_DOWNLOADED -> "Download Qwen 2.5 model (${Constants.ModelDownload.MODEL_SIZE_MB} MB)"
                            DownloadState.DOWNLOADING -> "Downloading Qwen model..."
                            DownloadState.PAUSED -> "Download interrupted"
                            DownloadState.COMPLETED -> "Qwen ready for chat"
                            DownloadState.FAILED -> "Download failed"
                            DownloadState.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (downloadState) {
                    DownloadState.NOT_DOWNLOADED -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Download")
                        }
                    }
                    DownloadState.DOWNLOADING -> {
                        Text(
                            text = "$downloadProgress%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadState.PAUSED -> {
                        Button(onClick = onDownload) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Retry")
                        }
                    }
                    DownloadState.COMPLETED -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            TextButton(onClick = onDelete) {
                                Text("Delete")
                            }
                        }
                    }
                    DownloadState.FAILED -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Retry")
                        }
                    }
                    DownloadState.ERROR_INSUFFICIENT_SPACE -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Progress details during download
            AnimatedVisibility(
                visible = downloadState == DownloadState.DOWNLOADING,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$downloadedMB MB / $totalMB MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Cancel Download")
                    }
                }
            }

            // Info about AI features
            if (downloadState == DownloadState.NOT_DOWNLOADED ||
                downloadState == DownloadState.ERROR_INSUFFICIENT_SPACE
            ) {
                HorizontalDivider()
                Text(
                    text = "Chat with Qwen AI about your expenses and get financial insights. " +
                            "All conversations stay private on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsNavigationContent(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .animateContentSize()
            .padding(start = 16.dp)
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
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
