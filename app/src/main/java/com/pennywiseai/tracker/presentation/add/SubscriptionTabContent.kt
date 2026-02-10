package com.pennywiseai.tracker.presentation.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionTabContent(
    viewModel: AddViewModel,
    onSave: () -> Unit
) {
    val uiState by viewModel.subscriptionUiState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showBillingCycleMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    
    val billingCycles = listOf("Monthly", "Quarterly", "Semi-Annual", "Annual", "Weekly")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // Handle keyboard properly
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Error Card
        uiState.error?.let { errorMessage ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Track recurring expenses. You'll need to add transactions manually each month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        // Service Name Input
        OutlinedTextField(
            value = uiState.serviceName,
            onValueChange = viewModel::updateSubscriptionService,
            label = { Text("Service Name *") },
            leadingIcon = { 
                Icon(
                    Icons.Default.Subscriptions, 
                    contentDescription = null
                ) 
            },
            placeholder = { Text("e.g., Netflix, Spotify") },
            isError = uiState.serviceError != null,
            supportingText = uiState.serviceError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
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
                            viewModel.updateSubscriptionCurrency(currency)
                            showCurrencyMenu = false
                        }
                    )
                }
            }
        }

        // Amount Input
        OutlinedTextField(
            value = uiState.amount,
            onValueChange = viewModel::updateSubscriptionAmount,
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
        
        // Billing Cycle Dropdown
        ExposedDropdownMenuBox(
            expanded = showBillingCycleMenu,
            onExpandedChange = { showBillingCycleMenu = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.billingCycle,
                onValueChange = {},
                readOnly = true,
                label = { Text("Billing Cycle *") },
                leadingIcon = { 
                    Icon(
                        Icons.Default.EventRepeat, 
                        contentDescription = null
                    ) 
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBillingCycleMenu) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                isError = uiState.billingCycleError != null,
                supportingText = uiState.billingCycleError?.let { { Text(it) } }
            )
            
            ExposedDropdownMenu(
                expanded = showBillingCycleMenu,
                onDismissRequest = { showBillingCycleMenu = false }
            ) {
                billingCycles.forEach { cycle ->
                    DropdownMenuItem(
                        text = { Text(cycle) },
                        onClick = {
                            viewModel.updateSubscriptionBillingCycle(cycle)
                            showBillingCycleMenu = false
                        }
                    )
                }
            }
        }
        
        // Next Payment Date
        OutlinedTextField(
            value = uiState.nextPaymentDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
            onValueChange = {},
            readOnly = true,
            label = { Text("Next Payment Date *") },
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
                            viewModel.updateSubscriptionCategory(category.name)
                            showCategoryMenu = false
                        }
                    )
                }
            }
        }
        
        // Notes/Description (Optional)
        OutlinedTextField(
            value = uiState.notes,
            onValueChange = viewModel::updateSubscriptionNotes,
            label = { Text("Notes (Optional)") },
            leadingIcon = { 
                Icon(
                    Icons.Default.Description, 
                    contentDescription = null
                ) 
            },
            placeholder = { Text("Add any additional information...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )
        
        // Save Button
        Button(
            onClick = {
                viewModel.saveSubscription(onSuccess = onSave)
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
                Text("Save Subscription")
            }
        }
        
        // Bottom padding for keyboard
        Spacer(modifier = Modifier.height(80.dp))
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
}