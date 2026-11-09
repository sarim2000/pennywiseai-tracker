package com.pennywiseai.tracker.presentation.settings

import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.DAILY_SUMMARY_DEFAULT
import com.pennywiseai.tracker.data.preferences.DateFormatStyle
import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import com.pennywiseai.tracker.domain.loan.UpiRequest
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.navigation.AiAccess
import com.pennywiseai.tracker.navigation.BackgroundAccess
import com.pennywiseai.tracker.navigation.LearnedCategories
import com.pennywiseai.tracker.data.manager.ConscienceText
import com.pennywiseai.tracker.navigation.BudgetGroups
import com.pennywiseai.tracker.navigation.Categories
import com.pennywiseai.tracker.navigation.ExchangeRates
import com.pennywiseai.tracker.navigation.ImportStatement
import com.pennywiseai.tracker.navigation.Loans
import com.pennywiseai.tracker.navigation.ManageAccounts
import com.pennywiseai.tracker.navigation.RecurringTransactions
import com.pennywiseai.tracker.navigation.Rules
import com.pennywiseai.tracker.navigation.SettingsSection
import com.pennywiseai.tracker.navigation.Subscriptions
import com.pennywiseai.tracker.navigation.TransactionGroups
import com.pennywiseai.tracker.navigation.UnrecognizedSms
import com.pennywiseai.tracker.navigation.Webhooks
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.presentation.split.UpiIdDialog
import com.pennywiseai.tracker.ui.UiText
import com.pennywiseai.tracker.ui.ShakeToHideViewModel
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.amber_dark
import com.pennywiseai.tracker.ui.theme.amber_light
import com.pennywiseai.tracker.ui.theme.blue_dark
import com.pennywiseai.tracker.ui.theme.blue_light
import com.pennywiseai.tracker.ui.theme.cyan_dark
import com.pennywiseai.tracker.ui.theme.cyan_light
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
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import com.pennywiseai.tracker.presentation.applock.AppLockViewModel
import com.pennywiseai.tracker.presentation.permission.PermissionViewModel
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.LocalDate

/**
 * One page of Settings: a line on what the page is for, then its rows. Each page
 * owns its state, launchers and dialogs, so opening one doesn't start the others'.
 */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    onNavigateBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, intro) = when (section) {
        SettingsSection.TRACKING -> R.string.settings_tracking_title to R.string.settings_tracking_intro
        SettingsSection.ACCOUNTS -> R.string.settings_accounts_categories_title to R.string.settings_accounts_categories_intro
        SettingsSection.PLANNING -> R.string.settings_planning_title to R.string.settings_planning_intro
        SettingsSection.CURRENCY -> R.string.settings_currency_section to R.string.settings_currency_intro
        SettingsSection.FORMATS -> R.string.settings_formats_title to R.string.settings_formats_intro
        SettingsSection.NOTIFICATIONS -> R.string.settings_notifications_section to R.string.settings_notifications_intro
        SettingsSection.SECURITY -> R.string.settings_security_section to R.string.settings_security_intro
        SettingsSection.DATA -> R.string.settings_data_section to R.string.settings_data_intro
        SettingsSection.AI -> R.string.settings_ai_section to R.string.settings_ai_intro
        SettingsSection.DEVELOPER -> R.string.settings_developer_section to R.string.settings_developer_intro
    }
    SettingsPageScaffold(
        title = stringResource(title),
        intro = stringResource(intro),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        when (section) {
            SettingsSection.TRACKING -> TrackingPage(onNavigate)
            SettingsSection.ACCOUNTS -> AccountsPage(onNavigate)
            SettingsSection.PLANNING -> PlanningPage(onNavigate)
            SettingsSection.CURRENCY -> CurrencyPage(onNavigate)
            SettingsSection.FORMATS -> FormatsPage()
            SettingsSection.NOTIFICATIONS -> NotificationsPage()
            SettingsSection.SECURITY -> SecurityPage()
            SettingsSection.DATA -> DataPage(onNavigate)
            SettingsSection.AI -> AiPage(onNavigate)
            SettingsSection.DEVELOPER -> DeveloperPage(onNavigate)
        }
    }
}

// ── Automatic tracking ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingPage(
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

// ── Accounts & categories ──

@Composable
private fun AccountsPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val countCreditCardAsExpense by settingsViewModel.countCreditCardAsExpense.collectAsStateWithLifecycle(initialValue = false)
    val useContactsForVpa by settingsViewModel.useContactsForVpa.collectAsStateWithLifecycle(initialValue = false)
    val upiId by settingsViewModel.upiId.collectAsStateWithLifecycle(initialValue = null)
    var showUpiIdDialog by remember { mutableStateOf(false) }
    // Launches the runtime permission request. If granted, we flip the
    // preference on; if denied, leave the switch off so the user can try
    // again without us silently turning the feature on later.
    val readContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) settingsViewModel.setUseContactsForVpa(true)
    }

    SettingsGroup {
        SettingsNavItem(
            icon = Icons.Default.AccountBalance,
            iconBgColor = red_light,
            iconTint = red_dark,
            title = stringResource(R.string.settings_manage_accounts_title),
            subtitle = stringResource(R.string.settings_manage_accounts_subtitle),
            onClick = { onNavigate(ManageAccounts) },
            position = ListItemPosition.Top
        )
        SettingsNavItem(
            icon = Icons.Default.Category,
            iconBgColor = purple_light,
            iconTint = purple_dark,
            title = stringResource(R.string.settings_categories_title),
            subtitle = stringResource(R.string.settings_categories_subtitle),
            onClick = { onNavigate(Categories) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.AutoAwesome,
            iconBgColor = orange_light,
            iconTint = orange_dark,
            title = stringResource(R.string.settings_smart_rules_title),
            subtitle = stringResource(R.string.settings_smart_rules_subtitle),
            onClick = { onNavigate(Rules) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.Lightbulb,
            iconBgColor = orange_light,
            iconTint = orange_dark,
            title = stringResource(R.string.learned_categories_title),
            subtitle = stringResource(R.string.learned_categories_subtitle),
            onClick = { onNavigate(LearnedCategories) },
            position = ListItemPosition.Middle
        )
        SettingsSwitchRow(
            icon = Icons.Default.CreditCard,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_card_spend_as_expense_title),
            subtitle = stringResource(R.string.settings_card_spend_as_expense_subtitle),
            checked = countCreditCardAsExpense,
            onCheckedChange = { settingsViewModel.setCountCreditCardAsExpense(it) },
            position = ListItemPosition.Bottom
        )
    }

    SectionHeaderV2(title = stringResource(R.string.settings_contacts_section))
    SettingsGroup {
        SettingsSwitchRow(
            icon = Icons.Default.Contacts,
            iconBgColor = teal_light,
            iconTint = teal_dark,
            title = stringResource(R.string.settings_contacts_vpa_title),
            subtitle = stringResource(R.string.settings_contacts_vpa_subtitle),
            checked = useContactsForVpa,
            onCheckedChange = { wantsOn ->
                if (wantsOn) {
                    val alreadyGranted = androidx.core.content.ContextCompat
                        .checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_CONTACTS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (alreadyGranted) {
                        settingsViewModel.setUseContactsForVpa(true)
                    } else {
                        readContactsLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                } else {
                    settingsViewModel.setUseContactsForVpa(false)
                }
            },
            position = ListItemPosition.Top
        )
        SettingsNavItem(
            icon = Icons.Default.QrCode2,
            iconBgColor = teal_light,
            iconTint = teal_dark,
            title = stringResource(R.string.settings_upi_title),
            subtitle = upiId ?: stringResource(R.string.settings_upi_subtitle),
            onClick = { showUpiIdDialog = true },
            position = ListItemPosition.Bottom,
            trailingText = if (upiId == null) stringResource(R.string.settings_upi_not_set) else null
        )
    }

    if (showUpiIdDialog) {
        UpiIdDialog(
            initial = upiId.orEmpty(),
            allowClear = true,
            onDismiss = { showUpiIdDialog = false },
            onSave = { id ->
                val ok = id.isBlank() || UpiRequest.isValidUpiId(id)
                if (ok) {
                    settingsViewModel.setUpiId(id)
                    showUpiIdDialog = false
                }
                ok
            }
        )
    }
}

// ── Planning ──

@Composable
private fun PlanningPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val budgetCycleStartDay by settingsViewModel.budgetCycleStartDay.collectAsStateWithLifecycle(initialValue = 1)
    var showBudgetCycleSheet by remember { mutableStateOf(false) }

    SettingsGroup {
        // The start day changes how Home / Analytics bucket transactions too, so
        // the row and every choice say exactly which dates that means.
        SettingsNavItem(
            icon = Icons.Default.DateRange,
            iconBgColor = teal_light,
            iconTint = teal_dark,
            title = stringResource(R.string.settings_budget_cycle_title),
            subtitle = stringResource(R.string.settings_budget_cycle_current, budgetCycleRange(budgetCycleStartDay)),
            onClick = { showBudgetCycleSheet = true },
            position = ListItemPosition.Top,
            trailingText = if (BudgetCycle.isLastWorkingDay(budgetCycleStartDay)) {
                stringResource(R.string.settings_budget_cycle_last_working_day_short)
            } else {
                ordinalSuffix(budgetCycleStartDay)
            }
        )
        SettingsNavItem(
            icon = Icons.Default.AccountBalanceWallet,
            iconBgColor = green_light,
            iconTint = green_dark,
            title = stringResource(R.string.settings_budgets_title),
            subtitle = stringResource(R.string.settings_budgets_subtitle),
            onClick = { onNavigate(BudgetGroups()) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.Subscriptions,
            iconBgColor = pink_light,
            iconTint = pink_dark,
            title = stringResource(R.string.subscriptions_title),
            subtitle = stringResource(R.string.settings_subscriptions_subtitle),
            onClick = { onNavigate(Subscriptions) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.EventRepeat,
            iconBgColor = green_light,
            iconTint = green_dark,
            title = stringResource(R.string.settings_recurring_title),
            subtitle = stringResource(R.string.settings_recurring_subtitle),
            onClick = { onNavigate(RecurringTransactions) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.SwapHoriz,
            iconBgColor = amber_light,
            iconTint = amber_dark,
            title = stringResource(R.string.settings_loans_title),
            subtitle = stringResource(R.string.settings_loans_subtitle),
            onClick = { onNavigate(Loans) },
            position = ListItemPosition.Middle
        )
        SettingsNavItem(
            icon = Icons.Default.Folder,
            iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            title = stringResource(R.string.settings_transaction_groups_title),
            subtitle = stringResource(R.string.settings_transaction_groups_subtitle),
            onClick = { onNavigate(TransactionGroups) },
            position = ListItemPosition.Bottom
        )
    }

    if (showBudgetCycleSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_budget_cycle_title),
            body = stringResource(R.string.settings_budget_cycle_dialog_body),
            onDismiss = { showBudgetCycleSheet = false },
            choices = listOf(
                SettingsChoice(
                    label = stringResource(R.string.settings_budget_cycle_last_working_day),
                    selected = BudgetCycle.isLastWorkingDay(budgetCycleStartDay),
                    description = budgetCycleRange(BudgetCycle.LAST_WORKING_DAY),
                ) { settingsViewModel.updateBudgetCycleStartDay(BudgetCycle.LAST_WORKING_DAY) }
            ) + (BudgetCycle.MIN_START_DAY..BudgetCycle.MAX_START_DAY).map { day ->
                SettingsChoice(
                    label = ordinalSuffix(day),
                    selected = day == budgetCycleStartDay,
                    description = budgetCycleRange(day),
                ) { settingsViewModel.updateBudgetCycleStartDay(day) }
            }
        )
    }
}

/** The dates of the current cycle if it starts on [startDay], e.g. "Nov 15 – Dec 14". */
private fun budgetCycleRange(startDay: Int): String {
    val (start, end) = BudgetCycle.currentCycle(LocalDate.now(), startDay)
    val formatter = AppDates.dayMonthFormatter()
    return "${formatter.format(start)} – ${formatter.format(end)}"
}

// ── Currency ──

@Composable
private fun CurrencyPage(
    onNavigate: (NavKey) -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val baseCurrency by settingsViewModel.baseCurrency.collectAsStateWithLifecycle(initialValue = "")
    val budgetConversionPrompt by settingsViewModel.budgetConversionPrompt.collectAsStateWithLifecycle()
    val unifiedCurrencyMode by settingsViewModel.unifiedCurrencyMode.collectAsStateWithLifecycle(initialValue = false)
    val displayCurrency by settingsViewModel.displayCurrency.collectAsStateWithLifecycle(initialValue = "")
    val availableCurrencies by settingsViewModel.availableCurrencies.collectAsStateWithLifecycle()
    val accounts by settingsViewModel.accounts.collectAsStateWithLifecycle()
    val mainAccountKey by settingsViewModel.mainAccountKey.collectAsStateWithLifecycle()
    var showMainAccountSheet by remember { mutableStateOf(false) }
    var showDefaultCurrencySheet by remember { mutableStateOf(false) }
    var showDisplayCurrencySheet by remember { mutableStateOf(false) }

    fun accountKey(account: AccountBalanceEntity) = "${account.bankName}_${account.accountLast4}"
    fun accountLabel(account: AccountBalanceEntity) = AccountBalanceEntity.accountLabel(
        account.alias?.takeIf { it.isNotBlank() } ?: account.bankName,
        account.accountLast4
    )
    fun currencyLabel(currency: String) = "${CurrencyFormatter.getCurrencySymbol(currency)} $currency"

    SettingsGroup {
        // Main account → sets the default currency (unless explicitly chosen below).
        if (accounts.isNotEmpty()) {
            SettingsNavItem(
                icon = Icons.Default.AccountBalanceWallet,
                iconBgColor = purple_light,
                iconTint = purple_dark,
                title = stringResource(R.string.settings_main_account_title),
                subtitle = stringResource(R.string.settings_main_account_subtitle),
                onClick = { showMainAccountSheet = true },
                position = ListItemPosition.Top,
                trailingText = accounts.firstOrNull { accountKey(it) == mainAccountKey }?.let { accountLabel(it) }
                    ?: stringResource(R.string.settings_main_account_not_set)
            )
        }
        SettingsNavItem(
            icon = Icons.Default.Flag,
            iconBgColor = indigo_light,
            iconTint = indigo_dark,
            title = stringResource(R.string.settings_default_currency_title),
            subtitle = stringResource(R.string.settings_default_currency_subtitle),
            onClick = { showDefaultCurrencySheet = true },
            position = if (accounts.isNotEmpty()) ListItemPosition.Middle else ListItemPosition.Top,
            trailingText = currencyLabel(baseCurrency)
        )
        SettingsSwitchRow(
            icon = Icons.Default.CurrencyExchange,
            iconBgColor = green_light,
            iconTint = green_dark,
            title = stringResource(R.string.settings_unified_currency_title),
            subtitle = stringResource(R.string.settings_unified_currency_subtitle),
            checked = unifiedCurrencyMode,
            onCheckedChange = { settingsViewModel.setUnifiedCurrencyMode(it) },
            position = ListItemPosition.Middle
        )
        AnimatedVisibility(visible = unifiedCurrencyMode, enter = pwExpand(), exit = pwCollapse()) {
            SettingsNavItem(
                icon = Icons.Default.AttachMoney,
                iconBgColor = teal_light,
                iconTint = teal_dark,
                title = stringResource(R.string.settings_display_currency_title),
                subtitle = stringResource(R.string.settings_display_currency_subtitle),
                onClick = { showDisplayCurrencySheet = true },
                position = ListItemPosition.Middle,
                trailingText = currencyLabel(displayCurrency)
            )
        }
        SettingsNavItem(
            icon = Icons.Default.SwapHoriz,
            iconBgColor = blue_light,
            iconTint = blue_dark,
            title = stringResource(R.string.settings_exchange_rates_title),
            subtitle = stringResource(R.string.settings_exchange_rates_subtitle),
            onClick = { onNavigate(ExchangeRates) },
            position = ListItemPosition.Bottom
        )
    }

    if (showMainAccountSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_main_account_title),
            onDismiss = { showMainAccountSheet = false },
            choices = accounts.map { account ->
                SettingsChoice(accountLabel(account), accountKey(account) == mainAccountKey) {
                    settingsViewModel.setMainAccount(account)
                }
            }
        )
    }
    if (showDefaultCurrencySheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_default_currency_title),
            onDismiss = { showDefaultCurrencySheet = false },
            choices = availableCurrencies.map { currency ->
                SettingsChoice(currencyLabel(currency), currency == baseCurrency) {
                    settingsViewModel.updateBaseCurrency(currency)
                }
            }
        )
    }
    if (showDisplayCurrencySheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_display_currency_title),
            onDismiss = { showDisplayCurrencySheet = false },
            choices = availableCurrencies.map { currency ->
                SettingsChoice(currencyLabel(currency), currency == displayCurrency) {
                    settingsViewModel.setDisplayCurrency(currency)
                }
            }
        )
    }

    // Budget conversion question: only asked while the user is still here. Back
    // from Budgets it would convert limits they've since entered in the new
    // currency. A rotation isn't leaving, the ViewModel carries it across.
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(settingsViewModel) {
        onDispose {
            if (activity?.isChangingConfigurations != true) settingsViewModel.dropBudgetConversion()
        }
    }
    budgetConversionPrompt?.let { prompt ->
        // Isolated: a Latin "1 INR = 84 JOD" reads in order inside Arabic text.
        val rate = "⁦1 ${prompt.from} = ${prompt.rate.round(java.math.MathContext(4)).toPlainString()} ${prompt.to}⁩"
        AlertDialog(
            onDismissRequest = { settingsViewModel.resolveBudgetConversion(convert = false) },
            title = { Text(stringResource(R.string.settings_budget_convert_title)) },
            text = { Text(stringResource(R.string.settings_budget_convert_message, prompt.from, prompt.to, rate)) },
            confirmButton = {
                TextButton(onClick = { settingsViewModel.resolveBudgetConversion(convert = true) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.settings_budget_convert_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsViewModel.resolveBudgetConversion(convert = false) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.settings_budget_convert_keep))
                }
            }
        )
    }
}

// ── Language & formats ──

@Composable
private fun FormatsPage(settingsViewModel: SettingsViewModel = hiltViewModel()) {
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
private fun NotificationsPage(settingsViewModel: SettingsViewModel = hiltViewModel()) {
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

// ── Security ──

@Composable
private fun SecurityPage(
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
private fun DataPage(
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

// ── AI Features ──

@Composable
private fun AiPage(
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
private fun DeveloperPage(
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

/**
 * English ordinal suffix for the budget cycle start day — "1st", "2nd", "3rd",
 * "4th"… "11th", "12th", "13th" follow the standard rule that the last two
 * digits decide the suffix (the 11/12/13 teens are always "th").
 */
private fun ordinalSuffix(day: Int): String {
    val safe = day.coerceIn(1, 31)
    // ponytail: English-only suffixes; other languages get the bare day number.
    if (java.util.Locale.getDefault().language != "en") return "$safe"
    val suffix = when {
        safe in 11..13 -> "th"
        safe % 10 == 1 -> "st"
        safe % 10 == 2 -> "nd"
        safe % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$safe$suffix"
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
