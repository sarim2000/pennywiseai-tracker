package com.pennywiseai.tracker.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.presentation.applock.AppLockViewModel
import com.pennywiseai.tracker.presentation.common.ExportTransactionsDialog
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.ShakeToHideViewModel
import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.text.rememberSyncedTextFieldState
import com.pennywiseai.tracker.ui.text.withCommittedText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val csvScope = rememberCoroutineScope()
    var csvExportRows by remember { mutableStateOf<List<TransactionEntity>?>(null) }
    csvExportRows?.let { ExportTransactionsDialog(transactions = it, onDismiss = { csvExportRows = null }) }
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    val importExportMessage by settingsViewModel.importExportMessage.collectAsStateWithLifecycle()
    val exportedBackupFile by settingsViewModel.exportedBackupFile.collectAsStateWithLifecycle()
    val exportSkippedReceipts by settingsViewModel.exportSkippedReceipts.collectAsStateWithLifecycle()
    val exportedBackupEncrypted by settingsViewModel.exportedBackupEncrypted.collectAsStateWithLifecycle()
    val isExportingBackup by settingsViewModel.isExportingBackup.collectAsStateWithLifecycle()
    val isRestoringBackup by settingsViewModel.isRestoringBackup.collectAsStateWithLifecycle()
    val deleteAllTransactionsCount by settingsViewModel.deleteAllTransactionsCount.collectAsStateWithLifecycle()
    val isDeletingAllTransactions by settingsViewModel.isDeletingAllTransactions.collectAsStateWithLifecycle()
    val deleteAllTransactionsResult by settingsViewModel.deleteAllTransactionsResult.collectAsStateWithLifecycle()
    val scheduledFolderBackupEnabled by settingsViewModel.scheduledFolderBackupEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scheduledFolderBackupLastTimestamp by settingsViewModel.scheduledFolderBackupLastTimestamp.collectAsStateWithLifecycle(initialValue = null)
    val scheduledFolderBackupLastSkippedReceipts by settingsViewModel.scheduledFolderBackupLastSkippedReceipts.collectAsStateWithLifecycle(initialValue = 0)
    val exportIncludeReceipts by settingsViewModel.exportIncludeReceipts.collectAsStateWithLifecycle(initialValue = true)
    val folderBackupIncludeReceipts by settingsViewModel.folderBackupIncludeReceipts.collectAsStateWithLifecycle(initialValue = false)
    val requestFolderPicker by settingsViewModel.requestFolderPicker.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var showEncryptedExportDialog by remember { mutableStateOf(false) }
    val backupPassphrasePrompt by settingsViewModel.backupPassphrasePrompt.collectAsStateWithLifecycle()

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

    // File saver for export. Saves the export whose dialog the user picked "Save" in.
    var saveExportPath by rememberSaveable { mutableStateOf<String?>(null) }
    val exportSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.saveBackupToFile(it, saveExportPath)
            }
            saveExportPath = null
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
            title = stringResource(R.string.settings_export_data_title),
            subtitle = if (isExportingBackup) {
                stringResource(R.string.settings_export_running_subtitle)
            } else {
                stringResource(R.string.settings_export_data_subtitle)
            },
            onClick = { settingsViewModel.exportBackup() },
            position = ListItemPosition.Top,
            // One export at a time, plain or encrypted: a second would replace the file the first offers.
            enabled = !isExportingBackup
        )
        SettingsNavItem(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.settings_export_encrypted_title),
            subtitle = if (isExportingBackup) {
                stringResource(R.string.settings_export_running_subtitle)
            } else {
                stringResource(R.string.settings_export_encrypted_subtitle)
            },
            onClick = { showEncryptedExportDialog = true },
            position = ListItemPosition.Middle,
            enabled = !isExportingBackup
        )
        SettingsSwitchRow(
            icon = Icons.Default.Receipt,
            title = stringResource(R.string.settings_export_include_receipts_title),
            subtitle = stringResource(R.string.settings_export_include_receipts_subtitle),
            checked = exportIncludeReceipts,
            onCheckedChange = { settingsViewModel.setExportIncludeReceipts(it) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.TableChart,
            title = stringResource(R.string.settings_export_csv_title),
            subtitle = stringResource(R.string.settings_export_csv_subtitle),
            onClick = { csvScope.launch { csvExportRows = settingsViewModel.loadAllTransactionsForCsv() } },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.Download,
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
                title = stringResource(R.string.settings_backup_now_title),
                subtitle = scheduledFolderBackupLastTimestamp?.let { timestamp ->
                    val formatted = java.time.Instant.ofEpochMilli(timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(AppDates.dateTimeFormatter())
                    if (scheduledFolderBackupLastSkippedReceipts > 0) {
                        pluralStringResource(
                            R.plurals.settings_backup_now_last_backup_skipped_receipts,
                            scheduledFolderBackupLastSkippedReceipts,
                            formatted,
                            scheduledFolderBackupLastSkippedReceipts
                        )
                    } else {
                        stringResource(R.string.settings_backup_now_last_backup, formatted)
                    }
                } ?: stringResource(R.string.settings_backup_now_subtitle),
                onClick = { settingsViewModel.backupToFolderNow() },
                position = ListItemPosition.Middle
            )
            SettingsSwitchRow(
                icon = Icons.Default.Receipt,
                title = stringResource(R.string.settings_folder_backup_include_receipts_title),
                subtitle = stringResource(R.string.settings_folder_backup_include_receipts_subtitle),
                checked = folderBackupIncludeReceipts,
                onCheckedChange = { settingsViewModel.setFolderBackupIncludeReceipts(it) },
                position = ListItemPosition.Middle
            )
            SettingsNavItem(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.settings_change_backup_folder_title),
                subtitle = stringResource(R.string.settings_change_backup_folder_subtitle),
                onClick = { settingsViewModel.requestChangeBackupFolder() },
                position = ListItemPosition.Bottom
            )
        }
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_import))
    SettingsGroup {
        // Statement import is a hub tile. CSV stays here with the other file imports.
        SettingsNavItem(
            icon = Icons.Default.Download,
            title = stringResource(R.string.settings_import_csv_title),
            subtitle = stringResource(R.string.settings_import_csv_subtitle),
            onClick = { csvImportLauncher.launch("*/*") },
            position = ListItemPosition.Single
        )
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_danger))
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.DeleteForever,
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
                        state = rememberSyncedTextFieldState(confirmationText) { confirmationText = it },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        enabled = !isDeletingAllTransactions,
                        label = { Text(stringResource(R.string.settings_delete_all_dialog_type_to_confirm, confirmWord)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedField.shape
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = withCommittedText {
                        if (confirmationText.trim() == confirmWord) {
                            settingsViewModel.deleteAllTransactions(count)
                        }
                    },
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

    // A restore's progress stays up until it's done (decrypting can take a while), whatever
    // other status shows meanwhile. OK only hides it; the restore's result still shows.
    var restoreProgressHidden by remember(isRestoringBackup) { mutableStateOf(false) }
    if (isRestoringBackup && !restoreProgressHidden) {
        AlertDialog(
            onDismissRequest = { restoreProgressHidden = true },
            title = { Text(stringResource(R.string.settings_backup_status_title)) },
            text = { Text(stringResource(R.string.settings_importing_backup)) },
            confirmButton = {
                TextButton(onClick = { restoreProgressHidden = true }, shapes = ButtonDefaults.shapes()) {
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
            title = {
                Text(
                    if (exportedBackupEncrypted) {
                        stringResource(R.string.settings_save_backup_title_encrypted)
                    } else {
                        stringResource(R.string.settings_save_backup_title)
                    }
                )
            },
            text = {
                Column {
                    Text(
                        if (exportSkippedReceipts > 0) {
                            pluralStringResource(
                                R.plurals.settings_backup_created_skipped_receipts,
                                exportSkippedReceipts,
                                exportSkippedReceipts
                            )
                        } else {
                            stringResource(R.string.settings_save_backup_created)
                        }
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.settings_save_backup_choose), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            saveExportPath = exportedBackupFile?.path
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
                            settingsViewModel.shareBackup(exportedBackupFile?.path)
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

    if (showEncryptedExportDialog) {
        EncryptedExportDialog(
            onExport = { passphrase ->
                showEncryptedExportDialog = false
                settingsViewModel.exportBackup(passphrase)
            },
            onDismiss = { showEncryptedExportDialog = false }
        )
    }

    backupPassphrasePrompt?.let { prompt ->
        // A fresh dialog per attempt: a second wrong passphrase shows its error again.
        key(prompt.attempt) {
            BackupPassphraseDialog(
                wrongPassphrase = prompt.wrongPassphrase,
                onRestore = { passphrase -> settingsViewModel.importBackup(prompt.uri, passphrase) },
                onDismiss = { settingsViewModel.cancelBackupPassphrase() }
            )
        }
    }
}

/** Picks the passphrase for an encrypted backup export. Never stored. */
@Composable
private fun EncryptedExportDialog(onExport: (CharArray) -> Unit, onDismiss: () -> Unit) {
    // Plain remember, not saveable: the passphrase must not land in saved instance state.
    val passphraseState = remember { TextFieldState() }
    val confirmState = remember { TextFieldState() }
    ClearOnDispose(passphraseState, confirmState)
    // Compared and copied char by char: no String copies of the passphrase.
    val matches = confirmState.text.contentEquals(passphraseState.text)
    val mismatch = confirmState.text.isNotEmpty() && !matches
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_backup_passphrase_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.settings_backup_passphrase_export_body))
                PassphraseField(passphraseState, stringResource(R.string.settings_backup_passphrase_label))
                PassphraseField(
                    confirmState,
                    stringResource(R.string.settings_backup_passphrase_confirm_label),
                    error = if (mismatch) stringResource(R.string.settings_backup_passphrase_mismatch) else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val passphrase = passphraseState.toCharArray()
                    passphraseState.wipe()
                    confirmState.wipe()
                    onExport(passphrase)
                },
                enabled = passphraseState.text.isNotEmpty() && matches,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.settings_backup_passphrase_export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_action_cancel)) }
        }
    )
}

/** Asks for an encrypted backup's passphrase before restoring it. Never stored. */
@Composable
private fun BackupPassphraseDialog(wrongPassphrase: Boolean, onRestore: (CharArray) -> Unit, onDismiss: () -> Unit) {
    // Plain remember, not saveable: the passphrase must not land in saved instance state.
    val passphraseState = remember { TextFieldState() }
    ClearOnDispose(passphraseState)
    // A retry opens a new dialog; the error goes once the user starts typing again.
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(passphraseState) {
        snapshotFlow { passphraseState.text }.drop(1).first()
        edited = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_backup_passphrase_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.settings_backup_passphrase_restore_body))
                PassphraseField(
                    passphraseState,
                    stringResource(R.string.settings_backup_passphrase_label),
                    error = if (wrongPassphrase && !edited) stringResource(R.string.settings_backup_passphrase_wrong) else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val passphrase = passphraseState.toCharArray()
                    passphraseState.wipe()
                    onRestore(passphrase)
                },
                enabled = passphraseState.text.isNotEmpty(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.settings_backup_passphrase_restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.settings_action_cancel)) }
        }
    )
}

private fun TextFieldState.toCharArray(): CharArray = text.let { text -> CharArray(text.length) { text[it] } }

/** Empties the fields when the dialog closes, however it closes. */
@Composable
private fun ClearOnDispose(vararg states: TextFieldState) {
    DisposableEffect(Unit) {
        onDispose { states.forEach { it.wipe() } }
    }
}

/** Empties the field and its undo history, which would otherwise still hold the old text. */
@OptIn(ExperimentalFoundationApi::class)
internal fun TextFieldState.wipe() {
    clearText()
    undoState.clearHistory()
}

@Composable
private fun PassphraseField(state: TextFieldState, label: String, error: String? = null) {
    OutlinedSecureTextField(
        state = state,
        textObfuscationMode = TextObfuscationMode.Hidden,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedField.shape
    )
}
