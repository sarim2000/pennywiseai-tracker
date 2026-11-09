package com.pennywiseai.tracker.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.loan.UpiRequest
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.navigation.BudgetGroups
import com.pennywiseai.tracker.navigation.Categories
import com.pennywiseai.tracker.navigation.ExchangeRates
import com.pennywiseai.tracker.navigation.LearnedCategories
import com.pennywiseai.tracker.navigation.Loans
import com.pennywiseai.tracker.navigation.ManageAccounts
import com.pennywiseai.tracker.navigation.RecurringTransactions
import com.pennywiseai.tracker.navigation.Rules
import com.pennywiseai.tracker.navigation.Subscriptions
import com.pennywiseai.tracker.navigation.TransactionGroups
import com.pennywiseai.tracker.presentation.split.UpiIdDialog
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.amber_dark
import com.pennywiseai.tracker.ui.theme.amber_light
import com.pennywiseai.tracker.ui.theme.blue_dark
import com.pennywiseai.tracker.ui.theme.blue_light
import com.pennywiseai.tracker.ui.theme.green_dark
import com.pennywiseai.tracker.ui.theme.green_light
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
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.LocalDate

// ── Accounts & categories ──

@Composable
internal fun AccountsPage(
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
internal fun PlanningPage(
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
internal fun CurrencyPage(
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
