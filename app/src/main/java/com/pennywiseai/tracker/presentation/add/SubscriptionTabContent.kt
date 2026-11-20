package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.ui.components.CategoryChip
import com.pennywiseai.tracker.ui.components.CategoryMenuItems
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.RoundedField
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.orSupporting
import com.pennywiseai.tracker.ui.components.rememberAmountEntry
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.text.rememberSyncedTextFieldState
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.utils.AppDates

private val BILLING_CYCLES = listOf("Monthly", "Quarterly", "Semi-Annual", "Annual", "Weekly")

/** A payment count, as the view model keeps it: digits only, at most three. */
private val PAYMENT_COUNT_INPUT = InputTransformation {
    val typed = asCharSequence().toString()
    val filtered = typed.filter { it.isDigit() }.take(3)
    if (filtered != typed) replace(0, length, filtered)
}

/**
 * The subscription form: direction → name → amount → cadence / next date / category /
 * account → notes and an instalment plan's end under "More details" → Save.
 */
@Composable
fun SubscriptionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.subscriptionUiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    // Closing the picker would hand focus back to a text field and pop its keyboard up again.
    val focusManager = LocalFocusManager.current
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showBillingCycleMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    // "Already paid" reaching the plan's end can't be saved: keep it in view.
    val moreExpanded = moreOpen || uiState.paymentsMadeError
    val isIncome = uiState.direction == SubscriptionDirection.INCOME

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.Padding.content, vertical = Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Income / Expense (#371). Income subscriptions get phantom-created on schedule
            // (for accounts that don't send SMS); expense subscriptions match bank-debit SMS.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ConnectedChoiceRow(
                    options = SubscriptionDirection.entries,
                    selected = uiState.direction,
                    onSelect = { viewModel.updateSubscriptionDirection(it) },
                    label = { direction ->
                        stringResource(
                            if (direction == SubscriptionDirection.INCOME) R.string.add_sub_direction_income else R.string.add_sub_direction_expense
                        )
                    }
                )
                Text(
                    text = stringResource(if (isIncome) R.string.add_sub_info_income else R.string.add_sub_info_expense),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextField(
                state = rememberSyncedTextFieldState(uiState.serviceName, viewModel::updateSubscriptionService),
                label = { Text(stringResource(R.string.add_sub_service_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Subscriptions, contentDescription = null) },
                isError = uiState.serviceError != null,
                supportingText = uiState.serviceError?.let { { Text(it.asString()) } },
                shape = RoundedField.shape,
                colors = RoundedField.colors()
            )

            // A sum too ("120+35"), with the operator row on the keyboard.
            val amount = rememberAmountEntry(uiState.amount, viewModel::updateSubscriptionAmount, uiState.currency)
            val amountSum = amount.sumHint
            TextField(
                state = amount.state,
                inputTransformation = amount.inputTransformation,
                label = { Text(stringResource(R.string.add_field_amount)) },
                textStyle = MaterialTheme.typography.headlineSmall.money(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountSum?.isError ?: (uiState.amountError != null),
                supportingText = amountSum.orSupporting(uiState.amountError?.let { { Text(it.asString()) } }),
                leadingIcon = { CurrencyButton(uiState.currency, viewModel::updateSubscriptionCurrency) },
                modifier = Modifier.fillMaxWidth().then(amount.modifier),
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = RoundedField.shape,
                colors = RoundedField.colors()
            )

            val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
            GroupedList {
                PickerRow(
                    position = ListItemPosition.Top,
                    label = stringResource(R.string.add_sub_billing_cycle),
                    value = billingCycleLabel(uiState.billingCycle),
                    onClick = { focusManager.clearFocus(); showBillingCycleMenu = true },
                    leading = { Icon(Icons.Default.EventRepeat, contentDescription = null, tint = iconTint) },
                    menu = {
                        DropdownMenu(
                            expanded = showBillingCycleMenu,
                            onDismissRequest = { showBillingCycleMenu = false },
                            shape = MaterialTheme.shapes.large
                        ) {
                            BILLING_CYCLES.forEach { cycle ->
                                DropdownMenuItem(
                                    text = { Text(billingCycleLabel(cycle)) },
                                    onClick = {
                                        viewModel.updateSubscriptionBillingCycle(cycle)
                                        showBillingCycleMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
                PickerRow(
                    position = ListItemPosition.Middle,
                    label = stringResource(R.string.add_sub_next_payment),
                    value = uiState.nextPaymentDate.format(AppDates.dateFormatter()),
                    onClick = { focusManager.clearFocus(); showDatePicker = true },
                    leading = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = iconTint) }
                )
                val selectedCategory = categories.find { it.name == uiState.category }
                PickerRow(
                    position = ListItemPosition.Middle,
                    label = stringResource(R.string.add_field_category),
                    value = categoryLabel(uiState.category),
                    onClick = { focusManager.clearFocus(); showCategoryMenu = true },
                    leading = {
                        if (selectedCategory != null) {
                            CategoryChip(category = selectedCategory, showText = false)
                        } else {
                            Icon(Icons.Default.Category, contentDescription = null, tint = iconTint)
                        }
                    },
                    supporting = uiState.categoryError?.asString(),
                    menu = {
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false },
                            shape = MaterialTheme.shapes.large
                        ) {
                            CategoryMenuItems(
                                categories = categories,
                                isIncome = isIncome,
                                onSelected = {
                                    viewModel.updateSubscriptionCategory(it.name)
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                )
                // Optional funding account (#570): marking paid (or a scheduled income) moves its
                // balance; none keeps the subscription unlinked.
                val noAccount = stringResource(R.string.add_sub_no_account)
                PickerRow(
                    position = ListItemPosition.Bottom,
                    label = stringResource(R.string.add_sub_paid_from),
                    value = uiState.selectedAccount?.displayLabel ?: noAccount,
                    valueColor = if (uiState.selectedAccount != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    onClick = { focusManager.clearFocus(); showAccountMenu = true },
                    leading = { AccountGlyph(uiState.selectedAccount) },
                    menu = {
                        AccountMenu(
                            expanded = showAccountMenu,
                            accounts = accounts,
                            selected = uiState.selectedAccount,
                            onPick = { viewModel.updateSubscriptionAccount(it); showAccountMenu = false },
                            onDismiss = { showAccountMenu = false },
                            noneLabel = noAccount,
                            noneHint = stringResource(R.string.add_sub_no_account_hint)
                        )
                    }
                )
            }

            val filled = listOfNotNull(
                stringResource(R.string.add_detail_notes).takeIf { uiState.notes.isNotBlank() },
                "${stringResource(R.string.add_sub_ends_after)}: ${uiState.endsAfter}".takeIf { uiState.endsAfter != null }
            )
            MoreDetailsRow(
                position = ListItemPosition.Single,
                expanded = moreExpanded,
                summary = filled.joinToString(" · ").ifEmpty { stringResource(R.string.add_sub_more_details_hint) },
                onToggle = { moreOpen = !moreExpanded }
            )
            if (moreExpanded) {
                TextField(
                    state = rememberSyncedTextFieldState(uiState.notes, viewModel::updateSubscriptionNotes),
                    label = { Text(stringResource(R.string.add_field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    shape = RoundedField.shape,
                    colors = RoundedField.colors()
                )
                // Ends after N payments (#1061): an EMI or instalment plan stops on its last one.
                if (!isIncome) {
                    TextField(
                        state = rememberSyncedTextFieldState(uiState.totalPayments, viewModel::updateSubscriptionTotalPayments),
                        inputTransformation = PAYMENT_COUNT_INPUT,
                        label = { Text(stringResource(R.string.add_sub_ends_after)) },
                        supportingText = { Text(stringResource(R.string.add_sub_ends_after_hint)) },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedField.shape,
                        colors = RoundedField.colors()
                    )
                    // A plan added after it started: "3 of 12 paid", not "0 of 12".
                    if (uiState.totalPayments.isNotEmpty()) {
                        TextField(
                            state = rememberSyncedTextFieldState(uiState.paymentsMade, viewModel::updateSubscriptionPaymentsMade),
                            inputTransformation = PAYMENT_COUNT_INPUT,
                            label = { Text(stringResource(R.string.add_sub_already_paid)) },
                            isError = uiState.paymentsMadeError,
                            supportingText = if (uiState.paymentsMadeError) {
                                { Text(stringResource(R.string.add_sub_already_paid_error)) }
                            } else null,
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedField.shape,
                            colors = RoundedField.colors()
                        )
                    }
                }
            }

            // Room for the Save bar laid over the end of the form.
            Spacer(modifier = Modifier.height(Dimensions.Component.fab + Spacing.xxl))
        }

        SaveBar(
            error = uiState.error?.asString(),
            enabled = uiState.isValid && !uiState.isLoading,
            loading = uiState.isLoading,
            onClick = { viewModel.saveSubscription(onSuccess = onSave) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showDatePicker) {
        AddDatePickerDialog(
            initial = uiState.nextPaymentDate,
            onPick = viewModel::updateSubscriptionNextPaymentDate,
            onDismiss = { showDatePicker = false }
        )
    }
}

/** Billing cycles are stored as English keys; only the label is translated. */
@Composable
private fun billingCycleLabel(cycle: String): String = when (cycle) {
    "Monthly" -> stringResource(R.string.add_sub_cycle_monthly)
    "Quarterly" -> stringResource(R.string.add_sub_cycle_quarterly)
    "Semi-Annual" -> stringResource(R.string.add_sub_cycle_semi_annual)
    "Annual" -> stringResource(R.string.add_sub_cycle_annual)
    "Weekly" -> stringResource(R.string.add_sub_cycle_weekly)
    else -> cycle
}
