package com.pennywiseai.tracker.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.manager.ConscienceText
import com.pennywiseai.tracker.data.preferences.DAILY_SUMMARY_DEFAULT
import com.pennywiseai.tracker.data.preferences.DateFormatStyle
import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.green_dark
import com.pennywiseai.tracker.ui.theme.green_light
import com.pennywiseai.tracker.ui.theme.indigo_dark
import com.pennywiseai.tracker.ui.theme.indigo_light
import com.pennywiseai.tracker.ui.theme.orange_dark
import com.pennywiseai.tracker.ui.theme.orange_light
import com.pennywiseai.tracker.ui.theme.pink_dark
import com.pennywiseai.tracker.ui.theme.pink_light
import com.pennywiseai.tracker.utils.AppDates
import java.time.LocalDate

// ── Language & formats ──

@Composable
internal fun FormatsPage(settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val numberFormatStyle by settingsViewModel.numberFormatStyle.collectAsStateWithLifecycle(initialValue = NumberFormatStyle.AUTO)
    val dateFormatStyle by settingsViewModel.dateFormatStyle.collectAsStateWithLifecycle(initialValue = DateFormatStyle.AUTO)
    var showNumberFormatSheet by remember { mutableStateOf(false) }
    var showDateFormatSheet by remember { mutableStateOf(false) }
    val hasPerAppLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.Language,
            iconBgColor = orange_light,
            iconTint = orange_dark,
            title = stringResource(R.string.settings_language_title),
            subtitle = stringResource(
                if (hasPerAppLanguage) R.string.settings_language_subtitle else R.string.settings_language_subtitle_device
            ),
            onClick = {
                // Per-app language is a system screen on Android 13+; older
                // versions follow the device language, so open that instead.
                context.startActivity(
                    if (hasPerAppLanguage) {
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    } else {
                        Intent(Settings.ACTION_LOCALE_SETTINGS)
                    }
                )
            },
            position = ListItemPosition.Top
        )
        SettingsNavItem(
            icon = Icons.Default.Numbers,
            iconBgColor = green_light,
            iconTint = green_dark,
            title = stringResource(R.string.settings_number_format_title),
            subtitle = stringResource(R.string.settings_number_format_subtitle),
            onClick = { showNumberFormatSheet = true },
            position = ListItemPosition.Middle,
            trailingText = numberFormatStyleLabel(numberFormatStyle)
        )
        SettingsNavItem(
            icon = Icons.Default.CalendarMonth,
            iconBgColor = green_light,
            iconTint = green_dark,
            title = stringResource(R.string.settings_date_format_title),
            subtitle = stringResource(R.string.settings_date_format_subtitle),
            onClick = { showDateFormatSheet = true },
            position = ListItemPosition.Bottom,
            trailingText = dateFormatStyleLabel(dateFormatStyle)
        )
    }

    if (showNumberFormatSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_number_format_title),
            onDismiss = { showNumberFormatSheet = false },
            choices = NumberFormatStyle.entries.map { style ->
                SettingsChoice(numberFormatStyleLabel(style), style == numberFormatStyle, numberFormatStyleExample(style)) {
                    settingsViewModel.updateNumberFormatStyle(style)
                }
            }
        )
    }
    if (showDateFormatSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_date_format_title),
            onDismiss = { showDateFormatSheet = false },
            choices = DateFormatStyle.entries.map { style ->
                SettingsChoice(dateFormatStyleLabel(style), style == dateFormatStyle, dateFormatStyleExample(style)) {
                    settingsViewModel.updateDateFormatStyle(style)
                }
            }
        )
    }
}

// Each option is named by what it looks like today; only Auto needs a word.
@Composable
private fun dateFormatStyleLabel(style: DateFormatStyle): String =
    if (style == DateFormatStyle.AUTO) stringResource(R.string.settings_number_format_auto)
    else AppDates.dateFormatter(style = style).format(LocalDate.now())

@Composable
private fun dateFormatStyleExample(style: DateFormatStyle): String {
    val today = LocalDate.now()
    val short = AppDates.dayMonthFormatter(style = style).format(today)
    return if (style == DateFormatStyle.AUTO) "${AppDates.dateFormatter(style = style).format(today)} · $short" else short
}

@Composable
private fun numberFormatStyleLabel(style: NumberFormatStyle): String = stringResource(
    when (style) {
        NumberFormatStyle.AUTO -> R.string.settings_number_format_auto
        NumberFormatStyle.INDIAN -> R.string.settings_number_format_indian
        NumberFormatStyle.INTERNATIONAL -> R.string.settings_number_format_international
    }
)

@Composable
private fun numberFormatStyleExample(style: NumberFormatStyle): String = stringResource(
    when (style) {
        NumberFormatStyle.AUTO -> R.string.settings_number_format_auto_example
        NumberFormatStyle.INDIAN -> R.string.settings_number_format_indian_example
        NumberFormatStyle.INTERNATIONAL -> R.string.settings_number_format_international_example
    }
)

// ── Notifications ──

@Composable
internal fun NotificationsPage(settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val dailySummaryEnabled by settingsViewModel.dailySummaryEnabled.collectAsStateWithLifecycle(initialValue = DAILY_SUMMARY_DEFAULT)
    val weeklySummaryEnabled by settingsViewModel.weeklySummaryEnabled.collectAsStateWithLifecycle(initialValue = false)
    val conscienceEnabled by settingsViewModel.conscienceEnabled.collectAsStateWithLifecycle(initialValue = false)
    // The recap is a notification: on Android 13+ turning it on asks for the
    // permission first, and a refusal leaves it off with a word on why.
    val dailySummaryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settingsViewModel.setDailySummaryEnabled(true)
        } else {
            Toast.makeText(context, R.string.settings_daily_summary_needs_permission, Toast.LENGTH_LONG).show()
        }
    }
    val weeklySummaryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settingsViewModel.setWeeklySummaryEnabled(true)
        } else {
            Toast.makeText(context, R.string.settings_weekly_summary_needs_permission, Toast.LENGTH_LONG).show()
        }
    }
    val budgetAlertsEnabled by settingsViewModel.budgetAlertsEnabled.collectAsStateWithLifecycle(initialValue = false)
    val budgetAlertsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settingsViewModel.setBudgetAlertsEnabled(true)
        } else {
            Toast.makeText(context, R.string.settings_budget_alerts_needs_permission, Toast.LENGTH_LONG).show()
        }
    }
    val conscienceAvailable = remember(context) { ConscienceText(context).speaksAppLanguage }
    val consciencePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settingsViewModel.setConscienceEnabled(true)
        } else {
            Toast.makeText(context, R.string.conscience_needs_permission, Toast.LENGTH_LONG).show()
        }
    }
    fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED

    SettingsGroup {
        SettingsSwitchRow(
            icon = Icons.Default.Schedule,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_daily_summary_title),
            subtitle = stringResource(R.string.settings_daily_summary_subtitle),
            checked = dailySummaryEnabled,
            onCheckedChange = { enabled ->
                if (enabled && needsNotificationPermission()) {
                    dailySummaryPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settingsViewModel.setDailySummaryEnabled(enabled)
                }
            },
            position = ListItemPosition.Top
        )
        SettingsSwitchRow(
            icon = Icons.Default.Insights,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_weekly_summary_title),
            subtitle = stringResource(R.string.settings_weekly_summary_subtitle),
            checked = weeklySummaryEnabled,
            onCheckedChange = { enabled ->
                if (enabled && needsNotificationPermission()) {
                    weeklySummaryPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settingsViewModel.setWeeklySummaryEnabled(enabled)
                }
            },
            position = ListItemPosition.Middle
        )
        SettingsSwitchRow(
            icon = Icons.Default.Notifications,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_budget_alerts_title),
            subtitle = stringResource(R.string.settings_budget_alerts_subtitle, 80),
            checked = budgetAlertsEnabled,
            onCheckedChange = { enabled ->
                if (enabled && needsNotificationPermission()) {
                    budgetAlertsPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    settingsViewModel.setBudgetAlertsEnabled(enabled)
                }
            },
            position = ListItemPosition.Middle
        )
        if (conscienceAvailable) {
            SettingsSwitchRow(
                icon = Icons.Default.Psychology,
                iconBgColor = pink_light,
                iconTint = pink_dark,
                title = stringResource(R.string.conscience_title),
                subtitle = stringResource(R.string.conscience_settings_subtitle),
                checked = conscienceEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && needsNotificationPermission()) {
                        consciencePermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        settingsViewModel.setConscienceEnabled(enabled)
                    }
                },
                position = ListItemPosition.Middle
            )
        }
        SettingsNavItem(
            icon = Icons.Default.Tune,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_notification_types_title),
            subtitle = stringResource(R.string.settings_notification_types_subtitle),
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            },
            position = ListItemPosition.Bottom,
            trailingIcon = Icons.AutoMirrored.Filled.OpenInNew
        )
    }
}
