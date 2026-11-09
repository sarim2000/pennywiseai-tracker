package com.pennywiseai.tracker.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.navigation.AiAccess
import com.pennywiseai.tracker.navigation.Webhooks
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.grey_dark
import com.pennywiseai.tracker.ui.theme.grey_light
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.teal_dark
import com.pennywiseai.tracker.ui.theme.teal_light
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light

// ── AI Features ──

@Composable
internal fun AiPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val downloadState by settingsViewModel.downloadState.collectAsStateWithLifecycle()
    val downloadProgress by settingsViewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadedMB by settingsViewModel.downloadedMB.collectAsStateWithLifecycle()
    val totalMB by settingsViewModel.totalMB.collectAsStateWithLifecycle()
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(settingsViewModel) { settingsViewModel.watchModelStatus() }

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
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.AutoAwesome,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.ai_access_settings_title),
            subtitle = stringResource(if (isProEntitled) R.string.ai_access_settings_subtitle else R.string.ai_access_settings_subtitle_pro),
            onClick = { if (isProEntitled) onNavigate(AiAccess) else showUpgradeSheet = true },
            position = ListItemPosition.Single
        )
    }

    if (showUpgradeSheet) {
        UpgradeSheet(onDismiss = { showUpgradeSheet = false })
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
    GroupedColumn(
        position = ListItemPosition.Single,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = Icons.Default.AutoAwesome,
                containerColor = yellow_light,
                contentColor = yellow_dark
            )
            RowLabels(
                title = stringResource(R.string.settings_ai_chat_title),
                subtitle = when (downloadState) {
                    DownloadState.NOT_DOWNLOADED -> stringResource(R.string.settings_ai_status_not_downloaded, Constants.ModelDownload.MODEL_SIZE_MB)
                    DownloadState.DOWNLOADING -> stringResource(R.string.settings_ai_status_downloading)
                    DownloadState.PAUSED -> stringResource(R.string.settings_ai_status_paused)
                    DownloadState.COMPLETED -> stringResource(R.string.settings_ai_status_completed)
                    DownloadState.FAILED -> stringResource(R.string.settings_ai_status_failed)
                    DownloadState.ERROR_INSUFFICIENT_SPACE -> stringResource(R.string.settings_ai_status_insufficient_space)
                }
            )

            when (downloadState) {
                DownloadState.NOT_DOWNLOADED -> {
                    Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_ai_download))
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
                    Button(onClick = onDownload, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_ai_retry))
                    }
                }
                DownloadState.COMPLETED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.settings_ai_downloaded),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.settings_ai_delete))
                        }
                    }
                }
                DownloadState.FAILED -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.settings_ai_retry))
                    }
                }
                DownloadState.ERROR_INSUFFICIENT_SPACE -> {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = stringResource(R.string.settings_ai_error),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                }
            }
        }

        // Progress details during download
        AnimatedVisibility(
            visible = downloadState == DownloadState.DOWNLOADING,
            enter = pwExpand(),
            exit = pwCollapse()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                LinearWavyProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.settings_ai_download_progress, downloadedMB, totalMB),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.settings_ai_cancel_download))
                }
            }
        }

        // Info about AI features
        if (downloadState == DownloadState.NOT_DOWNLOADED ||
            downloadState == DownloadState.ERROR_INSUFFICIENT_SPACE
        ) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_ai_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Developer ──

@Composable
internal fun DeveloperPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val isProEntitled by settingsViewModel.isProEntitled.collectAsStateWithLifecycle()
    val isDeveloperModeEnabled by settingsViewModel.isDeveloperModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    var showUpgradeSheet by remember { mutableStateOf(false) }

    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.CloudUpload,
            iconBgColor = teal_light,
            iconTint = teal_dark,
            title = stringResource(R.string.settings_webhooks_title),
            subtitle = stringResource(if (isProEntitled) R.string.settings_webhooks_subtitle else R.string.settings_webhooks_subtitle_pro),
            onClick = { if (isProEntitled) onNavigate(Webhooks) else showUpgradeSheet = true },
            position = ListItemPosition.Top
        )
        SettingsSwitchRow(
            icon = Icons.Default.Code,
            iconBgColor = grey_light,
            iconTint = grey_dark,
            title = stringResource(R.string.settings_developer_mode_title),
            subtitle = stringResource(R.string.settings_developer_mode_subtitle),
            checked = isDeveloperModeEnabled,
            onCheckedChange = { settingsViewModel.toggleDeveloperMode(it) },
            position = ListItemPosition.Bottom
        )
    }

    if (showUpgradeSheet) {
        UpgradeSheet(onDismiss = { showUpgradeSheet = false })
    }
}
