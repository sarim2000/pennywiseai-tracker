package com.pennywiseai.tracker.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.ImportStatement
import com.pennywiseai.tracker.presentation.applock.AppLockViewModel
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.ShakeToHideViewModel
import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.amber_dark
import com.pennywiseai.tracker.ui.theme.amber_light
import com.pennywiseai.tracker.ui.theme.blue_dark
import com.pennywiseai.tracker.ui.theme.blue_light
import com.pennywiseai.tracker.ui.theme.cyan_dark
import com.pennywiseai.tracker.ui.theme.cyan_light
import com.pennywiseai.tracker.ui.theme.green_dark
import com.pennywiseai.tracker.ui.theme.green_light
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.pink_dark
import com.pennywiseai.tracker.ui.theme.pink_light
import com.pennywiseai.tracker.ui.theme.purple_dark
import com.pennywiseai.tracker.ui.theme.purple_light
import com.pennywiseai.tracker.ui.theme.red_dark
import com.pennywiseai.tracker.ui.theme.red_light
import com.pennywiseai.tracker.ui.theme.teal_dark
import com.pennywiseai.tracker.ui.theme.teal_light
import com.pennywiseai.tracker.utils.AppDates

// ── Security ──

@Composable
internal fun SecurityPage(
    appLockViewModel: AppLockViewModel = hiltViewModel(),
    shakeToHideViewModel: ShakeToHideViewModel = hiltViewModel(),
) {
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val shakeToHideEnabled by shakeToHideViewModel.enabled.collectAsStateWithLifecycle()
    var showTimeoutSheet by remember { mutableStateOf(false) }

    SettingsGroup {
        SettingsSwitchRow(
            icon = Icons.Default.Lock,
            iconBgColor = red_light,
            iconTint = red_dark,
            title = stringResource(R.string.settings_app_lock_title),
            subtitle = if (appLockUiState.canUseBiometric) {
                stringResource(R.string.settings_app_lock_subtitle)
            } else {
                appLockUiState.biometricCapability.errorMessageRes?.let { stringResource(it) }.orEmpty()
            },
            checked = appLockUiState.isLockEnabled,
            onCheckedChange = { appLockViewModel.setAppLockEnabled(it) },
            // Turning it off always works, even after the screen lock was removed.
            enabled = appLockUiState.canUseBiometric || appLockUiState.isLockEnabled,
            position = ListItemPosition.Top
        )
        AnimatedVisibility(visible = appLockUiState.isLockEnabled, enter = pwExpand(), exit = pwCollapse()) {
            SettingsNavItem(
                icon = Icons.Default.Timer,
                iconBgColor = pink_light,
                iconTint = pink_dark,
                title = stringResource(R.string.settings_lock_timeout_title),
                subtitle = when (appLockUiState.timeoutMinutes) {
                    0 -> stringResource(R.string.settings_lock_timeout_immediately_subtitle)
                    else -> pluralStringResource(
                        R.plurals.settings_lock_timeout_after_minutes,
                        appLockUiState.timeoutMinutes,
                        appLockUiState.timeoutMinutes
                    )
                },
                onClick = { showTimeoutSheet = true },
                position = ListItemPosition.Middle
            )
        }
        SettingsSwitchRow(
            icon = Icons.Default.Vibration,
            iconBgColor = teal_light,
            iconTint = teal_dark,
            title = stringResource(R.string.settings_shake_to_hide_title),
            subtitle = stringResource(R.string.settings_shake_to_hide_subtitle),
            checked = shakeToHideEnabled,
            onCheckedChange = { shakeToHideViewModel.setEnabled(it) },
            position = ListItemPosition.Bottom
        )
    }

    if (showTimeoutSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_lock_timeout_title),
            body = stringResource(R.string.settings_lock_timeout_dialog_body),
            onDismiss = { showTimeoutSheet = false },
            choices = listOf(0, 1, 5, 15).map { minutes ->
                SettingsChoice(
                    label = if (minutes == 0) stringResource(R.string.settings_lock_timeout_option_immediately)
                        else pluralStringResource(R.plurals.settings_lock_timeout_option_minutes, minutes, minutes),
                    selected = appLockUiState.timeoutMinutes == minutes,
                ) { appLockViewModel.setTimeoutMinutes(minutes) }
            }
        )
    }
}

// ── Data Management ──

@Composable
internal fun DataPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    val importExportMessage by settingsViewModel.importExportMessage.collectAsStateWithLifecycle()
    val exportedBackupFile by settingsViewModel.exportedBackupFile.collectAsStateWithLifecycle()
    val deleteAllTransactionsCount by settingsViewModel.deleteAllTransactionsCount.collectAsStateWithLifecycle()
    val isDeletingAllTransactions by settingsViewModel.isDeletingAllTransactions.collectAsStateWithLifecycle()
    val deleteAllTransactionsResult by settingsViewModel.deleteAllTransactionsResult.collectAsStateWithLifecycle()
    val scheduledFolderBackupEnabled by settingsViewModel.scheduledFolderBackupEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scheduledFolderBackupLastTimestamp by settingsViewModel.scheduledFolderBackupLastTimestamp.collectAsStateWithLifecycle(initialValue = null)
    val exportIncludeReceipts by settingsViewModel.exportIncludeReceipts.collectAsStateWithLifecycle(initialValue = true)
    val folderBackupIncludeReceipts by settingsViewModel.folderBackupIncludeReceipts.collectAsStateWithLifecycle(initialValue = false)
    val requestFolderPicker by settingsViewModel.requestFolderPicker.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importBackup(it)
            }
        }
    )

    // File picker for CSV transaction import
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importCsv(it)
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

    val backupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let { settingsViewModel.onBackupFolderSelected(it) }
        }
    )

    LaunchedEffect(requestFolderPicker) {
        if (requestFolderPicker) {
            backupFolderLauncher.launch(null)
            settingsViewModel.onFolderPickerLaunched()
        }
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_backup))
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.Upload,
            iconBgColor = blue_light,
            iconTint = blue_dark,
            title = stringResource(R.string.settings_export_data_title),
            subtitle = stringResource(R.string.settings_export_data_subtitle),
            onClick = { settingsViewModel.exportBackup() },
            position = ListItemPosition.Top
        )
        SettingsSwitchRow(
            icon = Icons.Default.Receipt,
            iconBgColor = blue_light,
            iconTint = blue_dark,
            title = stringResource(R.string.settings_export_include_receipts_title),
            subtitle = stringResource(R.string.settings_export_include_receipts_subtitle),
            checked = exportIncludeReceipts,
            onCheckedChange = { settingsViewModel.setExportIncludeReceipts(it) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.Download,
            iconBgColor = cyan_light,
            iconTint = cyan_dark,
            title = stringResource(R.string.settings_import_data_title),
            subtitle = stringResource(R.string.settings_import_data_subtitle),
            onClick = { importLauncher.launch("*/*") },
            position = ListItemPosition.Bottom
        )
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_auto_backup))
    SettingsGroup {
        SettingsSwitchRow(
            icon = Icons.Default.Backup,
            iconBgColor = purple_light,
            iconTint = purple_dark,
            title = stringResource(R.string.settings_folder_backup_title),
            subtitle = if (scheduledFolderBackupEnabled) {
                stringResource(R.string.settings_folder_backup_subtitle_enabled)
            } else if (!isProEntitled) {
                stringResource(R.string.settings_folder_backup_subtitle_pro)
            } else {
                stringResource(R.string.settings_folder_backup_subtitle_disabled)
            },
            checked = scheduledFolderBackupEnabled,
            // Scheduling daily backups is a Pro feature. Turning it ON while
            // free routes to the paywall; turning it OFF is always allowed so
            // a lapsed/downgraded user can still stop scheduled backups.
            onCheckedChange = { enabled ->
                if (enabled && !isProEntitled) {
                    showUpgradeSheet = true
                } else {
                    settingsViewModel.setScheduledFolderBackupEnabled(enabled)
                }
            },
            position = if (scheduledFolderBackupEnabled) ListItemPosition.Top else ListItemPosition.Single
        )
        if (scheduledFolderBackupEnabled) {
            SettingsNavItem(
                icon = Icons.Default.SaveAlt,
                iconBgColor = green_light,
                iconTint = green_dark,
                title = stringResource(R.string.settings_backup_now_title),
                subtitle = scheduledFolderBackupLastTimestamp?.let { timestamp ->
                    val formatted = java.time.Instant.ofEpochMilli(timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(AppDates.dateTimeFormatter())
                    stringResource(R.string.settings_backup_now_last_backup, formatted)
                } ?: stringResource(R.string.settings_backup_now_subtitle),
                onClick = { settingsViewModel.backupToFolderNow() },
                position = ListItemPosition.Middle
            )
            SettingsSwitchRow(
                icon = Icons.Default.Receipt,
                iconBgColor = purple_light,
                iconTint = purple_dark,
                title = stringResource(R.string.settings_folder_backup_include_receipts_title),
                subtitle = stringResource(R.string.settings_folder_backup_include_receipts_subtitle),
                checked = folderBackupIncludeReceipts,
                onCheckedChange = { settingsViewModel.setFolderBackupIncludeReceipts(it) },
                position = ListItemPosition.Middle
            )
            SettingsNavItem(
                icon = Icons.Default.FolderOpen,
                iconBgColor = amber_light,
                iconTint = amber_dark,
                title = stringResource(R.string.settings_change_backup_folder_title),
                subtitle = stringResource(R.string.settings_change_backup_folder_subtitle),
                onClick = { settingsViewModel.requestChangeBackupFolder() },
                position = ListItemPosition.Bottom
            )
        }
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_import))
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.Description,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_import_statement_title),
            subtitle = stringResource(R.string.settings_import_statement_subtitle),
            onClick = { onNavigate(ImportStatement) },
            position = ListItemPosition.Top
        )
        SettingsNavItem(
            icon = Icons.Default.Download,
            iconBgColor = cyan_light,
            iconTint = cyan_dark,
            title = stringResource(R.string.settings_import_csv_title),
            subtitle = stringResource(R.string.settings_import_csv_subtitle),
            onClick = { csvImportLauncher.launch("*/*") },
            position = ListItemPosition.Bottom
        )
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_danger))
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.DeleteForever,
            iconBgColor = red_light,
            iconTint = red_dark,
            title = stringResource(R.string.settings_delete_all_title),
            subtitle = stringResource(R.string.settings_delete_all_subtitle),
            onClick = { settingsViewModel.requestDeleteAllTransactions() },
            position = ListItemPosition.Single
        )
    }

    // Delete-all-transactions confirmation. Irreversible and unbatched, so it
    // asks for the word DELETE rather than a single tap, names the exact number
    // of rows, and points at Export Data first.
    deleteAllTransactionsCount?.let { count ->
        var confirmationText by rememberSaveable(count) { mutableStateOf("") }
        // The confirmation word is deliberately not translated.
        val confirmWord = "DELETE"
        val confirmed = confirmationText.trim().equals(confirmWord, ignoreCase = false)

        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAllTransactions) settingsViewModel.cancelDeleteAllTransactions()
            },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.settings_delete_all_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        pluralStringResource(R.plurals.settings_delete_all_dialog_body, count, count)
                    )
                    Text(
                        stringResource(R.string.settings_delete_all_dialog_kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = confirmationText,
                        onValueChange = { confirmationText = it },
                        singleLine = true,
                        enabled = !isDeletingAllTransactions,
                        label = { Text(stringResource(R.string.settings_delete_all_dialog_type_to_confirm, confirmWord)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsViewModel.deleteAllTransactions(count) },
                    enabled = confirmed && !isDeletingAllTransactions,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(if (isDeletingAllTransactions) stringResource(R.string.settings_delete_all_dialog_deleting) else stringResource(R.string.settings_delete_all_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { settingsViewModel.cancelDeleteAllTransactions() },
                    enabled = !isDeletingAllTransactions,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.settings_action_cancel))
                }
            }
        )
    }

    deleteAllTransactionsResult?.let { sentences ->
        AlertDialog(
            onDismissRequest = { settingsViewModel.clearDeleteAllTransactionsResult() },
            title = { Text(stringResource(R.string.settings_delete_all_result_title)) },
            text = { Text(sentences.map { it.asString() }.joinToString(" ")) },
            confirmButton = {
                TextButton(onClick = { settingsViewModel.clearDeleteAllTransactionsResult() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.settings_action_ok))
                }
            }
        )
    }

    // Show import/export message
    importExportMessage?.let { message ->
        if (exportedBackupFile != null && message == UiText.Res(R.string.settings_backup_created_choose)) {
            showExportOptionsDialog = true
        } else {
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(5000)
                settingsViewModel.clearImportExportMessage()
            }

            AlertDialog(
                onDismissRequest = { settingsViewModel.clearImportExportMessage() },
                title = { Text(stringResource(R.string.settings_backup_status_title)) },
                text = { Text(message.asString()) },
                confirmButton = {
                    TextButton(onClick = { settingsViewModel.clearImportExportMessage() }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.settings_action_ok))
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
            title = { Text(stringResource(R.string.settings_save_backup_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_save_backup_created))
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.settings_save_backup_choose), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            exportSaveLauncher.launch(fileName)
                            showExportOptionsDialog = false
                            settingsViewModel.clearImportExportMessage()
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_save_backup_to_files))
                    }

                    TextButton(
                        onClick = {
                            settingsViewModel.shareBackup()
                            showExportOptionsDialog = false
                            settingsViewModel.clearImportExportMessage()
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_save_backup_share))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportOptionsDialog = false
                        settingsViewModel.clearImportExportMessage()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.settings_action_cancel))
                }
            }
        )
    }

    if (showUpgradeSheet) {
        UpgradeSheet(onDismiss = { showUpgradeSheet = false })
    }
}
