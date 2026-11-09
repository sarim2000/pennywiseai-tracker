package com.pennywiseai.tracker.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.navigation.BackgroundAccess
import com.pennywiseai.tracker.navigation.UnrecognizedSms
import com.pennywiseai.tracker.presentation.permission.PermissionViewModel
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.orange_dark
import com.pennywiseai.tracker.ui.theme.orange_light
import com.pennywiseai.tracker.ui.theme.teal_dark
import com.pennywiseai.tracker.ui.theme.teal_light
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AppDates
import java.time.LocalDate

// ── Automatic tracking ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackingPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    permissionViewModel: PermissionViewModel = hiltViewModel(),
    trackedAppsViewModel: TrackedAppsViewModel = hiltViewModel(),
    backgroundAccessViewModel: BackgroundAccessViewModel = hiltViewModel(),
) {
    val smsScanMonths by settingsViewModel.smsScanMonths.collectAsStateWithLifecycle(initialValue = 3)
    val smsScanAllTime by settingsViewModel.smsScanAllTime.collectAsStateWithLifecycle(initialValue = false)
    val smsScanUseCustomDate by settingsViewModel.smsScanUseCustomDate.collectAsStateWithLifecycle(initialValue = false)
    val smsScanCustomDate by settingsViewModel.smsScanCustomDate.collectAsStateWithLifecycle(initialValue = null)
    val trackedApps by trackedAppsViewModel.tracked.collectAsStateWithLifecycle()
    val permissionUiState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    val hasNotificationAccess = permissionUiState.hasNotificationAccess
    var showSmsScanSheet by remember { mutableStateOf(false) }
    var showSmsScanDatePicker by remember { mutableStateOf(false) }
    var customDatePicked by remember { mutableStateOf(false) }
    var showTrackedAppsDialog by remember { mutableStateOf(false) }
    val notificationAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        permissionViewModel.refreshNotificationAccess()
    }
    val backgroundAccess by backgroundAccessViewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        backgroundAccessViewModel.refresh()
        onPauseOrDispose {}
    }
    // Without SMS access there's no tracking to protect, so nothing to nag about.
    val backgroundStepsLeft = backgroundAccess?.stepsLeft?.takeIf { it > 0 && permissionUiState.hasPermission }
    val backgroundStepsColor = MaterialTheme.colorScheme.warning

    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.BatteryChargingFull,
            iconBgColor = if (backgroundStepsLeft != null) backgroundStepsColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
            iconTint = if (backgroundStepsLeft != null) backgroundStepsColor else MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(R.string.background_access_title),
            subtitle = stringResource(R.string.background_access_settings_subtitle),
            onClick = { onNavigate(BackgroundAccess) },
            position = ListItemPosition.Single,
            trailingText = backgroundStepsLeft?.let {
                pluralStringResource(R.plurals.background_access_settings_left, it, it)
            }
        )
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_sms))
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.CalendarMonth,
            iconBgColor = teal_light,
            iconTint = teal_dark,
            title = stringResource(R.string.settings_sms_scan_title),
            subtitle = when {
                smsScanAllTime -> stringResource(R.string.settings_sms_scan_subtitle_all_time)
                smsScanUseCustomDate -> {
                    val formattedDate = smsScanCustomDate?.let { formatSmsScanCustomDate(it) }
                    if (formattedDate != null) {
                        stringResource(R.string.settings_sms_scan_subtitle_from_date, formattedDate)
                    } else {
                        stringResource(R.string.settings_sms_scan_subtitle_custom)
                    }
                }
                else -> pluralStringResource(R.plurals.settings_sms_scan_subtitle_months, smsScanMonths, smsScanMonths)
            },
            onClick = { showSmsScanSheet = true },
            position = ListItemPosition.Top,
            trailingText = when {
                smsScanAllTime -> stringResource(R.string.settings_sms_scan_all_time)
                smsScanUseCustomDate -> smsScanCustomDate?.let { formatSmsScanCustomDateShort(it) }
                    ?: stringResource(R.string.settings_sms_scan_custom_short)
                else -> pluralStringResource(R.plurals.settings_sms_scan_months_short, smsScanMonths, smsScanMonths)
            }
        )
        SettingsNavItem(
            icon = Icons.Default.Sms,
            iconBgColor = orange_light,
            iconTint = orange_dark,
            title = stringResource(R.string.settings_unrecognized_sms_title),
            subtitle = stringResource(R.string.settings_unrecognized_sms_subtitle),
            onClick = { onNavigate(UnrecognizedSms) },
            position = ListItemPosition.Bottom
        )
    }

    SectionHeaderV2(title = stringResource(R.string.settings_group_app_notifications))
    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.Notifications,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_notification_access_title),
            subtitle = if (hasNotificationAccess) stringResource(R.string.settings_notification_access_enabled) else stringResource(R.string.settings_notification_access_disabled),
            onClick = { notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            position = ListItemPosition.Top,
            trailingText = if (hasNotificationAccess) stringResource(R.string.settings_notification_access_on) else stringResource(R.string.settings_notification_access_off)
        )
        SettingsNavItem(
            icon = Icons.Default.Apps,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_tracked_apps_title),
            subtitle = if (trackedApps.isEmpty()) stringResource(R.string.settings_tracked_apps_subtitle_none)
                else pluralStringResource(R.plurals.settings_tracked_apps_subtitle_count, trackedApps.size, trackedApps.size),
            onClick = { showTrackedAppsDialog = true },
            position = ListItemPosition.Bottom
        )
    }

    if (showTrackedAppsDialog) {
        TrackedAppsDialog(
            viewModel = trackedAppsViewModel,
            hasNotificationAccess = hasNotificationAccess,
            onEnableAccess = {
                notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onDismiss = { showTrackedAppsDialog = false }
        )
    }

    if (showSmsScanSheet) {
        val customDate = smsScanCustomDate?.let { formatSmsScanCustomDate(it) }
        val monthly = !smsScanAllTime && !smsScanUseCustomDate
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_sms_scan_title),
            body = stringResource(R.string.settings_sms_scan_dialog_body),
            onDismiss = {
                showSmsScanSheet = false
                // Opened only once the sheet is gone: cancelling the picker reopens the sheet,
                // and a hide still running would close it again.
                if (customDatePicked) {
                    customDatePicked = false
                    showSmsScanDatePicker = true
                }
            },
            choices = buildList {
                add(
                    SettingsChoice(stringResource(R.string.settings_sms_scan_all_time), smsScanAllTime) {
                        settingsViewModel.updateSmsScanAllTime(true)
                    }
                )
                add(
                    SettingsChoice(
                        label = if (customDate != null) stringResource(R.string.settings_sms_scan_option_custom_with_date, customDate)
                            else stringResource(R.string.settings_sms_scan_option_custom),
                        selected = smsScanUseCustomDate && !smsScanAllTime,
                    ) { customDatePicked = true }
                )
                listOf(1, 2, 3, 6, 12, 24).forEach { months ->
                    add(
                        SettingsChoice(
                            label = if (months == 24) pluralStringResource(R.plurals.settings_sms_scan_option_years, 2, 2)
                                else pluralStringResource(R.plurals.settings_sms_scan_option_months, months, months),
                            selected = monthly && smsScanMonths == months,
                        ) {
                            settingsViewModel.updateSmsScanMonths(months)
                            settingsViewModel.updateSmsScanAllTime(false)
                        }
                    )
                }
            }
        )
    }

    if (showSmsScanDatePicker) {
        val todayMillis = LocalDate.now()
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val initialSelectedDateMillis = smsScanCustomDate
            ?: LocalDate.now()
                .minusMonths(3)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= todayMillis
                }
            }
        )

        // Cancelling/dismissing the picker returns to the scan-period choices rather than
        // silently dropping the user back to the page (they came here to change the period).
        fun reopenScanSheet() {
            showSmsScanDatePicker = false
            showSmsScanSheet = true
        }

        DatePickerDialog(
            onDismissRequest = { reopenScanSheet() },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            settingsViewModel.updateSmsScanCustomDate(millis)
                        }
                        showSmsScanDatePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.settings_action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { reopenScanSheet() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.settings_action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatSmsScanCustomDate(dateMillis: Long): String {
    return java.time.Instant.ofEpochMilli(dateMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(AppDates.dateFormatter())
}

private fun formatSmsScanCustomDateShort(dateMillis: Long): String {
    return java.time.Instant.ofEpochMilli(dateMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .format(AppDates.dayMonthFormatter())
}
