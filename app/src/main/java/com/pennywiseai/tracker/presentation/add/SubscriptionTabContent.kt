package com.pennywiseai.tracker.presentation.add

import com.pennywiseai.tracker.utils.AppDates

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.domain.model.displayName
import com.pennywiseai.tracker.domain.model.icon
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.ui.components.CategoryMenuItems
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.SelectedCategoryIcon
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.icons.categoryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.subscriptionUiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showBillingCycleMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }

    val billingCycles = listOf("Monthly", "Quarterly", "Semi-Annual", "Annual", "Weekly")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Error Card
            uiState.error?.let { errorMessage ->
                PennyWiseCardV2(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    contentPadding = Dimensions.Padding.cardCompact
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                        Text(
                            text = errorMessage.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Income / Expense direction toggle (#371). Income subscriptions
            // get phantom-created on schedule (for accounts that don't send
            // SMS — wallets, allowances). Expense subscriptions match
            // incoming bank-debit SMS like today.
            val isIncome = uiState.direction ==
                com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.INCOME
            ConnectedChoiceRow(
                options = com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.entries,
                selected = uiState.direction,
                onSelect = { viewModel.updateSubscriptionDirection(it) },
                label = { direction ->
                    stringResource(
                        if (direction == com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.INCOME) {
                            R.string.add_sub_direction_income
                        } else {
                            R.string.add_sub_direction_expense
                        }
                    )
                }
            )

            // Info Card — copy adapts to the chosen direction.
            PennyWiseCardV2(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = Dimensions.Padding.cardCompact
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                    Text(
                        text = if (isIncome)
                            stringResource(R.string.add_sub_info_income)
                        else
                            stringResource(R.string.add_sub_info_expense),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Amount row: currency + amount
            AmountCurrencyField(
                amount = uiState.amount,
                onAmountChange = viewModel::updateSubscriptionAmount,
                errorText = uiState.amountError?.asString(),
                currency = uiState.currency,
                onCurrencySelected = viewModel::updateSubscriptionCurrency
            )

            // Billing cycle + Next payment date
            GroupedList {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AddPickerRow(
                        leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
                        label = stringResource(R.string.add_sub_billing_cycle),
                        value = billingCycleLabel(uiState.billingCycle),
                        shape = ListItemPosition.Top.toShape(),
                        onClick = { showBillingCycleMenu = true },
                        trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                    )

                    DropdownMenu(
                        expanded = showBillingCycleMenu,
                        onDismissRequest = { showBillingCycleMenu = false }
                    ) {
                        billingCycles.forEach { cycle ->
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

                AddPickerRow(
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    label = stringResource(R.string.add_sub_next_payment),
                    value = uiState.nextPaymentDate.format(AppDates.pattern("dd MMMM yyyy")),
                    shape = ListItemPosition.Bottom.toShape(),
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Service Name + Category + Notes (connected group)
            GroupedList {
                AddTextRow(
                    leadingIcon = { Icon(Icons.Default.Subscriptions, contentDescription = null) },
                    label = stringResource(R.string.add_sub_service_name),
                    value = uiState.serviceName,
                    onValueChange = viewModel::updateSubscriptionService,
                    placeholder = stringResource(R.string.add_sub_service_placeholder),
                    shape = ListItemPosition.Top.toShape(),
                    error = uiState.serviceError?.asString()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    AddPickerRow(
                        leadingIcon = { SelectedCategoryIcon(categories.find { it.name == uiState.category }) },
                        label = stringResource(R.string.add_field_category),
                        value = uiState.category.takeIf { it.isNotBlank() }?.let { categoryLabel(it) },
                        placeholder = stringResource(R.string.add_category_placeholder),
                        shape = ListItemPosition.Middle.toShape(),
                        onClick = { showCategoryMenu = true },
                        error = uiState.categoryError?.asString(),
                        trailing = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) }
                    )

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        CategoryMenuItems(
                            categories = categories,
                            isIncome = uiState.direction == com.pennywiseai.tracker.data.database.entity.SubscriptionDirection.INCOME,
                            onSelected = {
                                viewModel.updateSubscriptionCategory(it.name)
                                showCategoryMenu = false
                            }
                        )
                    }
                }

                AddTextRow(
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    label = stringResource(R.string.add_field_notes),
                    value = uiState.notes,
                    onValueChange = viewModel::updateSubscriptionNotes,
                    placeholder = stringResource(R.string.add_notes_placeholder),
                    shape = ListItemPosition.Bottom.toShape(),
                    singleLine = false
                )
            }

            // ── Funding account (optional) ──
            // When set, marking this subscription paid (or an auto-created
            // scheduled income) moves the chosen account's balance. Leaving it
            // unset keeps the subscription unlinked, exactly like before. (#570)
            Box(modifier = Modifier.fillMaxWidth()) {
                AccountPickerRow(
                    label = stringResource(R.string.add_sub_paid_from),
                    account = uiState.selectedAccount,
                    shape = ListItemPosition.Single.toShape(),
                    onClick = { showAccountMenu = true },
                    onClear = { viewModel.updateSubscriptionAccount(null) }
                )

                // Account selection dropdown menu
                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = { showAccountMenu = false }
                ) {
                    SelectableDropdownMenuItem(
                        selected = uiState.selectedAccount == null,
                        shapes = MenuDefaults.itemShape(0, 1),
                        colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                        text = {
                            Column {
                                Text(stringResource(R.string.add_sub_no_account))
                                Text(
                                    stringResource(R.string.add_sub_no_account_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            viewModel.updateSubscriptionAccount(null)
                            showAccountMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                    )
                    HorizontalDivider()
                    val groupedAccounts = accounts.groupBy { it.getAccountType() }
                    groupedAccounts.forEach { (accountType, accountList) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = accountType.displayName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                        accountList.forEachIndexed { index, account ->
                            SelectableDropdownMenuItem(
                                selected = uiState.selectedAccount?.id == account.id,
                                shapes = MenuDefaults.itemShape(index, accountList.size),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                text = {
                                    Column {
                                        Text(account.displayLabel)
                                        Text(
                                            CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.updateSubscriptionAccount(account)
                                    showAccountMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        accountType.icon,
                                        contentDescription = null
                                    )
                                },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }

        AddSaveBar(
            enabled = uiState.isValid && !uiState.isLoading,
            isLoading = uiState.isLoading,
            onClick = { viewModel.saveSubscription(onSuccess = onSave) }
        )
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.nextPaymentDate
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC)
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.updateSubscriptionNextPaymentDate(millis)
                        }
                        showDatePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(stringResource(R.string.add_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.add_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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
