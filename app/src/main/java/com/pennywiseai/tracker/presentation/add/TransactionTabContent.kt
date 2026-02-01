package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.displayName
import com.pennywiseai.tracker.domain.model.getAccountType
import com.pennywiseai.tracker.presentation.accounts.AccountType
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.transactionUiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // Handle keyboard properly
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Currency Selector
        ExposedDropdownMenuBox(
            expanded = showCurrencyMenu,
            onExpandedChange = { showCurrencyMenu = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "${CurrencyFormatter.getCurrencySymbol(uiState.currency)} ${uiState.currency}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCurrencyMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = showCurrencyMenu,
                onDismissRequest = { showCurrencyMenu = false }
            ) {
                CurrencyFormatter.getSupportedCurrencies().forEach { currency ->
                    DropdownMenuItem(
                        text = { Text("${CurrencyFormatter.getCurrencySymbol(currency)} $currency") },
                        onClick = {
                            viewModel.updateTransactionCurrency(currency)
                            showCurrencyMenu = false
                        }
                    )
                }
            }
        }

        // Amount Input
        OutlinedTextField(
            value = uiState.amount,
            onValueChange = viewModel::updateTransactionAmount,
            label = { Text("Amount *") },
            prefix = { Text(CurrencyFormatter.getCurrencySymbol(uiState.currency)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            isError = uiState.amountError != null,
            supportingText = uiState.amountError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Transaction Type Selection
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Transaction Type *",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionType.values().forEach { type ->
                    FilterChip(
                        selected = uiState.transactionType == type,
                        onClick = { viewModel.updateTransactionType(type) },
                        label = { 
                            Text(type.name.lowercase(Locale.getDefault())
                                .replaceFirstChar { it.titlecase(Locale.getDefault()) }) 
                        },
                        leadingIcon = if (uiState.transactionType == type) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
        
        // Merchant Name Input
        OutlinedTextField(
            value = uiState.merchant,
            onValueChange = viewModel::updateTransactionMerchant,
            label = { Text("Merchant/Description *") },
            leadingIcon = { 
                Icon(
                    Icons.Default.Store, 
                    contentDescription = null
                ) 
            },
            isError = uiState.merchantError != null,
            supportingText = uiState.merchantError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Category Dropdown
        ExposedDropdownMenuBox(
            expanded = showCategoryMenu,
            onExpandedChange = { showCategoryMenu = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.category,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category *") },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Category, 
                        contentDescription = null
                    ) 
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                isError = uiState.categoryError != null,
                supportingText = uiState.categoryError?.let { { Text(it) } }
            )
            
            ExposedDropdownMenu(
                expanded = showCategoryMenu,
                onDismissRequest = { showCategoryMenu = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            viewModel.updateTransactionCategory(category.name)
                            showCategoryMenu = false
                        }
                    )
                }
            }
        }

        // Account Selection (Optional)
        ExposedDropdownMenuBox(
            expanded = showAccountMenu,
            onExpandedChange = { showAccountMenu = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.selectedAccount?.let { account ->
                    "${account.bankName} • ${account.getAccountType().displayName()} • ••${account.accountLast4}"
                } ?: "No account (Manual Entry)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Account (Optional)") },
                leadingIcon = {
                    Icon(
                        when (uiState.selectedAccount?.getAccountType()) {
                            AccountType.CASH -> Icons.Default.Money
                            AccountType.CREDIT -> Icons.Default.CreditCard
                            AccountType.SAVINGS, AccountType.CURRENT -> Icons.Default.AccountBalance
                            null -> Icons.Default.AccountBalance
                        },
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Row {
                        if (uiState.selectedAccount != null) {
                            IconButton(onClick = { viewModel.updateSelectedAccount(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear selection")
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAccountMenu)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                supportingText = {
                    Text("Select an account to update its balance automatically")
                }
            )

            ExposedDropdownMenu(
                expanded = showAccountMenu,
                onDismissRequest = { showAccountMenu = false }
            ) {
                // "No account" option
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("No account (Manual Entry)")
                            Text(
                                "Transaction won't affect any account balance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        viewModel.updateSelectedAccount(null)
                        showAccountMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Block, contentDescription = null)
                    }
                )

                HorizontalDivider()

                // Account list grouped by type
                val groupedAccounts = accounts.groupBy { it.getAccountType() }

                groupedAccounts.forEach { (accountType, accountList) ->
                    // Section header
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

                    // Accounts in this group
                    accountList.forEach { account ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("${account.bankName} ••${account.accountLast4}")
                                    Text(
                                        CurrencyFormatter.formatCurrency(account.balance, account.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            onClick = {
                                viewModel.updateSelectedAccount(account)
                                showAccountMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    when (accountType) {
                                        AccountType.CASH -> Icons.Default.Money
                                        AccountType.CREDIT -> Icons.Default.CreditCard
                                        else -> Icons.Default.AccountBalance
                                    },
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (uiState.selectedAccount?.id == account.id) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Date Selection
        OutlinedTextField(
            value = uiState.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            leadingIcon = { 
                Icon(
                    Icons.Default.CalendarToday, 
                    contentDescription = null
                ) 
            },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Change date")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Time Selection
        OutlinedTextField(
            value = uiState.date.format(DateTimeFormatter.ofPattern("hh:mm a")),
            onValueChange = {},
            readOnly = true,
            label = { Text("Time") },
            leadingIcon = { 
                Icon(
                    Icons.Default.Schedule, 
                    contentDescription = null
                ) 
            },
            trailingIcon = {
                IconButton(onClick = { showTimePicker = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Change time")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Notes/Description (Optional)
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::updateTransactionNotes,
            label = { Text("Notes (Optional)") },
            leadingIcon = { 
                Icon(
                    Icons.Default.Description, 
                    contentDescription = null
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        
        
        // Save Button
        Button(
            onClick = {
                viewModel.saveTransaction(onSuccess = onSave)
            },
            enabled = uiState.isValid && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Save Transaction")
            }
        }
        
        // Bottom padding for keyboard
        Spacer(modifier = Modifier.height(80.dp))
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.date
                .toLocalDate()
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
                            viewModel.updateTransactionDate(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.date.hour,
            initialMinute = uiState.date.minute
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTransactionTime(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}