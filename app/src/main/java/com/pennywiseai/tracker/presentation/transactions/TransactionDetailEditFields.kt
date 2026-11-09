package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.presentation.categories.CategoryEditDialog
import com.pennywiseai.tracker.ui.components.CategoryMenuItems
import com.pennywiseai.tracker.ui.components.SelectedCategoryIcon
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.CurrencyUtils
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Dimensions
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.pennywiseai.tracker.ui.icons.categoryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isIncomeTransaction: Boolean,
    viewModel: TransactionDetailViewModel
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Find the selected category entity for displaying with color
    val selectedCategoryEntity = categories.find { it.name == selectedCategory }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = categoryLabel(selectedCategory),
            onValueChange = { },
            label = { Text(stringResource(R.string.txn_detail_label_category), fontWeight = FontWeight.SemiBold) },
            leadingIcon = { SelectedCategoryIcon(selectedCategoryEntity) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            shape = editFullShape,
            colors = editFilledColors()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Create a new category without leaving the edit flow (#584).
            CategoryMenuItems(
                categories = categories,
                isIncome = isIncomeTransaction,
                onSelected = {
                    onCategorySelected(it.name)
                    expanded = false
                },
                onAddCategory = {
                    expanded = false
                    showAddDialog = true
                }
            )
        }
    }

    if (showAddDialog) {
        CategoryEditDialog(
            defaultIsIncome = isIncomeTransaction,
            lockType = true,
            // Same parent picker as Settings → Categories (#876).
            parentOptions = categories,
            onDismiss = { showAddDialog = false },
            onSave = { name, color, _, icon, parentId ->
                // Dismiss only once the category is actually created/selected, so a
                // failure (e.g. same-name type conflict) keeps the dialog open with
                // the user's input intact.
                viewModel.createAndSelectCategory(name, color, icon, parentId) { success ->
                    if (success) showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeField(
    dateTime: LocalDateTime,
    onDateTimeChange: (LocalDateTime) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date button
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium)
                )
                .padding(Spacing.sm)
                .clickable(
                    onClick = { showDatePicker = true },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(Spacing.sm))
                Column {
                    Text(
                        text = dateTime.format(DateTimeFormatter.ofPattern("yyyy")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateTime.format(AppDates.pattern("dd MMMM")),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Time display
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                .clickable { showTimePicker = true },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val hour = if (dateTime.hour % 12 == 0) 12 else dateTime.hour % 12
                val minute = dateTime.minute
                val amPm = if (dateTime.hour < 12) "AM" else "PM"

                Box(
                    modifier = Modifier
                        .padding(Spacing.xs)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(0.2f),
                            shape = MaterialTheme.shapes.small
                        )
                ) {
                    Text(
                        text = String.format("%02d", hour),
                        color = MaterialTheme.colorScheme.primary,
                        style = PennyWiseText.amountMedium,
                        modifier = Modifier.padding(Spacing.xs)
                    )
                }
                Text(
                    text = ":",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = PennyWiseText.amountMedium
                )
                Box(
                    modifier = Modifier
                        .padding(Spacing.xs)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        )
                ) {
                    Text(
                        text = String.format("%02d", minute),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = PennyWiseText.amountMedium,
                        modifier = Modifier.padding(Spacing.xs)
                    )
                }
                Box(modifier = Modifier.padding(Spacing.xs)) {
                    Text(
                        text = amPm,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        onDateTimeChange(dateTime.withYear(newDate.year)
                            .withMonth(newDate.monthValue)
                            .withDayOfMonth(newDate.dayOfMonth))
                    }
                    showDatePicker = false
                }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.txn_detail_select_time)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    onDateTimeChange(dateTime.withHour(timePickerState.hour)
                        .withMinute(timePickerState.minute))
                    showTimePicker = false
                }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.txn_detail_action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyDropdown(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // The shared list, not a local copy: this dropdown used to hard-code 17
    // codes, so editing a transaction couldn't set currencies (BRL, COP, ARS…)
    // that adding one could.
    val currencies = remember { CurrencyUtils.getAllSupportedCurrencies() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = "${CurrencyFormatter.getCurrencySymbol(selectedCurrency)} $selectedCurrency",
            onValueChange = { },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true,
            shape = editFullShape,
            colors = editFilledColors()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                CurrencyFormatter.getCurrencySymbol(currency),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(currency)
                        }
                    },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountNumberField(
    accountNumber: String?,
    onAccountNumberChange: (String?) -> Unit,
    viewModel: TransactionDetailViewModel,
    label: String = stringResource(R.string.txn_detail_field_account),
    placeholder: String = stringResource(R.string.txn_detail_field_account_placeholder),
    excludeAccount: String? = null,
    // A real account picked from the dropdown, as (bankName, last4): accounts are
    // keyed by both (#566 / #570), and last4 may be blank for a Cash account (#913).
    onAccountSelected: (String, String) -> Unit,
    bankName: String? = null,
    excludeBankName: String? = null
) {
    val availableAccounts by viewModel.availableAccounts.collectAsStateWithLifecycle()
    val filteredAccounts = availableAccounts.filter {
        it.accountLast4 != excludeAccount || (excludeBankName != null && it.bankName != excludeBankName)
    }
    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember(accountNumber, bankName, availableAccounts) {
        mutableStateOf(availableAccounts.matchFor(accountNumber, bankName)?.displayName ?: accountNumber ?: "")
    }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        TextField(
            value = selectedAccount,
            onValueChange = { newValue ->
                selectedAccount = newValue
                // If manually typing, update the account number directly
                if (!availableAccounts.any { it.displayName == newValue }) {
                    onAccountNumberChange(newValue.ifEmpty { null })
                }
            },
            label = { Text(label, fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                Icon(
                    if (availableAccounts.any { it.displayName == selectedAccount && it.isCreditCard }) {
                        Icons.Default.CreditCard
                    } else {
                        Icons.Default.AccountBalance
                    },
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            },
            shape = editFullShape,
            colors = editFilledColors(),
            trailingIcon = {
                Row {
                    // Clear button if there's text
                    if (selectedAccount.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedAccount = ""
                                onAccountNumberChange(null)
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.txn_detail_clear),
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            singleLine = true,
            placeholder = { Text(placeholder) }
        )
        
        if (filteredAccounts.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredAccounts.forEach { account ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (account.isCreditCard) Icons.Default.CreditCard 
                                    else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.medium),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(account.displayName)
                            }
                        },
                        onClick = {
                            selectedAccount = account.displayName
                            onAccountSelected(account.bankName, account.accountLast4)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

/**
 * The account an edit field's [accountNumber] (and [bankName]) refers to (#876).
 * Same bank and number first; failing that, the one account with that number.
 * The number must match whole — a short or empty last-4 used to suffix-match
 * every account, so picking HDFC ••1155 could show some other account.
 */
internal fun List<TransactionDetailViewModel.AccountInfo>.matchFor(
    accountNumber: String?,
    bankName: String?
): TransactionDetailViewModel.AccountInfo? {
    // A blank number names an account only together with its bank: a Cash
    // account added without a number is ("Cash", "") (#913).
    if (accountNumber.isNullOrBlank()) {
        return if (accountNumber != null && bankName != null) find { it.accountLast4 == accountNumber && it.bankName == bankName } else null
    }
    val sameNumber = filter {
        it.accountLast4 == accountNumber ||
            (it.accountLast4.length >= 4 && accountNumber.endsWith(it.accountLast4))
    }
    return bankName?.let { bank -> sameNumber.find { it.bankName == bank } } ?: sameNumber.singleOrNull()
}
