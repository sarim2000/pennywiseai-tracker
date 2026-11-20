package com.pennywiseai.tracker.presentation.settings

import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.smsimport.LastScanStatus
import com.pennywiseai.tracker.domain.smsimport.SmsScanCounts
import com.pennywiseai.tracker.presentation.permission.PermissionViewModel
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.rememberSmsAccess
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.canReceiveSms
import com.pennywiseai.tracker.utils.trackingProblem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.NumberFormat

/** One place that answers "why didn't my transaction show up?". */
@Composable
fun SmsCheckupScreen(
    onNavigateBack: () -> Unit,
    onOpenBackgroundAccess: () -> Unit,
    onOpenUnrecognized: () -> Unit,
    viewModel: SmsCheckupViewModel = hiltViewModel(),
    backgroundAccessViewModel: BackgroundAccessViewModel = hiltViewModel(),
    permissionViewModel: PermissionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val background by backgroundAccessViewModel.state.collectAsStateWithLifecycle()
    val permissions by permissionViewModel.uiState.collectAsStateWithLifecycle()
    // Home's own catch-up scan won't fire: it's composed again with access already granted.
    val smsAccess = rememberSmsAccess(onGranted = viewModel::onSmsAccessGranted)
    // Every fix happens in system Settings, so every check reruns on return.
    var canReceiveSms by remember { mutableStateOf(context.canReceiveSms()) }
    LifecycleResumeEffect(Unit) {
        backgroundAccessViewModel.refresh()
        permissionViewModel.refreshNotificationAccess()
        canReceiveSms = context.canReceiveSms()
        onPauseOrDispose {}
    }
    // Relative times ("5 minutes ago") tick each minute while the screen is started.
    val now by remember { minuteTicks() }.collectAsStateWithLifecycle(System.currentTimeMillis())
    val notificationAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { permissionViewModel.refreshNotificationAccess() }

    PennyWiseScaffold(
        title = stringResource(R.string.sms_checkup_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
            }
        }
    ) { innerPadding ->
        val scan = state ?: return@PennyWiseScaffold
        val smsOk = smsAccess.granted && canReceiveSms
        val stepsLeft = background?.stepsLeft
        // Only what stops tracking counts, as on Home: battery optimisation is every app's
        // default, so the Background row lists it without raising the alarm.
        val issues = listOf(trackingProblem(smsOk, background) != null, scan.status.needsAttention).count { it }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = if (issues == 0) {
                    stringResource(R.string.sms_checkup_all_good)
                } else {
                    pluralStringResource(R.plurals.sms_checkup_needs_attention, issues, issues)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionHeaderV2(title = stringResource(R.string.sms_checkup_section_access))
            val accessSteps = listOf(
                ChecklistStep(
                    title = stringResource(R.string.sms_checkup_sms_title),
                    done = smsOk,
                    why = stringResource(R.string.sms_checkup_sms_why),
                    actionLabel = stringResource(
                        if (smsAccess.blocked) R.string.background_access_open_settings else R.string.background_access_allow
                    ),
                    onAction = smsAccess.request
                ),
                ChecklistStep(
                    title = stringResource(R.string.sms_checkup_notifications_title),
                    done = permissions.hasNotificationAccess,
                    why = stringResource(R.string.sms_checkup_notifications_why),
                    actionLabel = stringResource(R.string.sms_checkup_turn_on),
                    onAction = {
                        notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    optional = true
                ),
            )
            GroupedList {
                accessSteps.forEachIndexed { index, step ->
                    ChecklistRow(step, ListItemPosition.from(index, accessSteps.size))
                }
            }

            if (stepsLeft != null) {
                SectionHeaderV2(title = stringResource(R.string.sms_checkup_section_background))
                ChecklistRow(
                    ChecklistStep(
                        title = stringResource(R.string.background_access_title),
                        done = stepsLeft == 0,
                        why = when {
                            stepsLeft == 0 -> stringResource(R.string.sms_checkup_background_why)
                            // The one step that stops tracking: name it, as the Home card does.
                            background?.backgroundRestricted == true ->
                                stringResource(R.string.sms_checkup_background_restricted)
                            else -> pluralStringResource(R.plurals.sms_checkup_background_steps_left, stepsLeft, stepsLeft)
                        },
                        actionLabel = stringResource(R.string.sms_checkup_review),
                        onAction = onOpenBackgroundAccess,
                        // Matches the header count: only a background restriction stops tracking.
                        optional = background?.backgroundRestricted != true
                    ),
                    ListItemPosition.Single
                )
            }

            SectionHeaderV2(title = stringResource(R.string.sms_checkup_section_scan))
            LastScanCard(
                scan,
                now = now,
                canScan = smsAccess.granted,
                accessInSettings = smsAccess.blocked,
                onRescan = viewModel::rescan
            )

            SectionHeaderV2(title = stringResource(R.string.sms_checkup_section_not_imported))
            NotImportedCard(scan.notImported, onOpenUnrecognized)
        }
    }
}

@Composable
private fun LastScanCard(
    scan: SmsCheckupState,
    now: Long,
    canScan: Boolean,
    accessInSettings: Boolean,
    onRescan: () -> Unit
) {
    val record = scan.record
    val ended = record?.finishedAt?.let { relativeTime(it, now) }.orEmpty()
    val (icon, color) = when {
        scan.status == LastScanStatus.SUCCEEDED -> Icons.Default.Check to MaterialTheme.colorScheme.success
        scan.status == LastScanStatus.RUNNING -> Icons.Default.Sync to MaterialTheme.colorScheme.primary
        scan.status.needsAttention -> Icons.Default.PriorityHigh to MaterialTheme.colorScheme.warning
        else -> Icons.Default.Sync to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when (scan.status) {
        LastScanStatus.NEVER -> stringResource(R.string.sms_checkup_scan_never)
        LastScanStatus.RUNNING -> stringResource(R.string.sms_checkup_scan_running)
        LastScanStatus.SUCCEEDED -> stringResource(R.string.sms_checkup_scan_succeeded, ended)
        LastScanStatus.SAVE_FAILED -> stringResource(R.string.sms_checkup_scan_save_failed, ended)
        LastScanStatus.FAILED -> stringResource(R.string.sms_checkup_scan_failed, ended)
        LastScanStatus.CANCELLED -> stringResource(R.string.sms_checkup_scan_cancelled, ended)
        LastScanStatus.INTERRUPTED -> stringResource(R.string.sms_checkup_scan_interrupted)
    }
    // A running scan's record is the RUNNING one, with nothing counted yet.
    val counts = record?.counts?.takeIf { it.examined > 0 && scan.status != LastScanStatus.RUNNING }
    val lines = buildList {
        when {
            scan.status == LastScanStatus.NEVER -> add(stringResource(R.string.sms_checkup_scan_never_body))
            scan.status.endedEarly -> add(stringResource(R.string.sms_checkup_scan_ended_early_body))
            scan.status == LastScanStatus.SAVE_FAILED -> record?.counts?.failed?.let { failed ->
                add(pluralStringResource(R.plurals.sms_checkup_scan_save_failed_body, failed, formatCount(failed)))
            }
        }
        if (scan.status.needsAttention || scan.status == LastScanStatus.RUNNING) {
            // From the record, not the incremental cursor: a scan-window change resets
            // that to 0, and a restore brings it back without this record.
            record?.lastSucceededAt?.let { add(stringResource(R.string.sms_checkup_last_success, relativeTime(it, now))) }
        }
        if (counts != null) add(scanSummary(counts, stoppedEarly = scan.status.endedEarly))
        scan.lastLiveSmsAt?.let { add(stringResource(R.string.sms_checkup_live_sms, relativeTime(it, now))) }
        // The way to fix it is further up; say why the button is off right next to it,
        // naming the button the SMS access row shows.
        if (!canScan) {
            add(
                if (accessInSettings) {
                    stringResource(
                        R.string.sms_checkup_scan_needs_access_settings,
                        stringResource(R.string.background_access_open_settings)
                    )
                } else {
                    stringResource(R.string.sms_checkup_scan_needs_access)
                }
            )
        }
    }
    var showDetails by rememberSaveable { mutableStateOf(false) }

    GroupedColumn(position = ListItemPosition.Single) {
        StatusHeader(icon, color, title)
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (counts != null && showDetails) CountDetails(counts)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRescan,
                enabled = canScan && scan.status != LastScanStatus.RUNNING,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(
                    stringResource(
                        if (scan.status == LastScanStatus.NEVER) R.string.sms_checkup_scan_now else R.string.sms_checkup_scan_again
                    )
                )
            }
            if (counts != null) {
                TextButton(onClick = { showDetails = !showDetails }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(if (showDetails) R.string.sms_checkup_hide_details else R.string.sms_checkup_show_details))
                }
            }
        }
    }
}

/**
 * "0 added" alone reads as a failure when the messages were already imported live, and a
 * stopped scan's count isn't the inbox, so say both.
 */
@Composable
private fun scanSummary(counts: SmsScanCounts, stoppedEarly: Boolean): String {
    val res = when {
        stoppedEarly && counts.duplicates > 0 -> R.plurals.sms_checkup_scan_summary_stopped_existing
        stoppedEarly -> R.plurals.sms_checkup_scan_summary_stopped
        counts.duplicates > 0 -> R.plurals.sms_checkup_scan_summary_existing
        else -> R.plurals.sms_checkup_scan_summary
    }
    return pluralStringResource(
        res,
        counts.examined,
        formatCount(counts.examined),
        formatCount(counts.saved),
        formatCount(counts.duplicates)
    )
}

@Composable
private fun CountDetails(counts: SmsScanCounts) {
    val rows = listOfNotNull(
        R.string.sms_checkup_count_saved to counts.saved,
        R.string.sms_checkup_count_duplicates to counts.duplicates,
        R.string.sms_checkup_count_excluded to counts.excluded,
        R.string.sms_checkup_count_unrecognised to counts.unrecognised,
        R.string.sms_checkup_count_special to counts.special,
        R.string.sms_checkup_count_discarded to counts.discarded,
        // Rare, so only shown when it happened.
        (R.string.sms_checkup_count_failed to counts.failed).takeIf { counts.failed > 0 },
        (R.string.sms_checkup_count_not_read to counts.notRead).takeIf { counts.notRead > 0 },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatCount(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NotImportedCard(notImported: NotImported, onReview: () -> Unit) {
    GroupedColumn(position = ListItemPosition.Single) {
        when (notImported) {
            is NotImported.Waiting -> {
                StatusHeader(
                    Icons.Default.Sms,
                    MaterialTheme.colorScheme.warning,
                    pluralStringResource(R.plurals.sms_checkup_not_imported_title, notImported.count, notImported.count)
                )
                Text(
                    stringResource(R.string.sms_checkup_not_imported_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onReview, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.sms_checkup_review))
                }
            }
            NotImported.NoneWaiting -> {
                StatusHeader(
                    Icons.Default.Check,
                    MaterialTheme.colorScheme.success,
                    stringResource(R.string.sms_checkup_not_imported_none)
                )
                Text(
                    stringResource(R.string.sms_checkup_not_imported_none_waiting_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            NotImported.AllRead -> StatusHeader(
                Icons.Default.Check,
                MaterialTheme.colorScheme.success,
                stringResource(R.string.sms_checkup_not_imported_none)
            )
        }
    }
}

@Composable
private fun StatusHeader(icon: ImageVector, color: Color, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon = icon, containerColor = color.copy(alpha = 0.15f), contentColor = color)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun minuteTicks(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(DateUtils.MINUTE_IN_MILLIS)
    }
}

@Composable
private fun relativeTime(millis: Long, now: Long): String {
    // DateUtils says "0 minutes ago" for the first minute.
    return if (now - millis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.sms_checkup_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)
