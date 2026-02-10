package com.pennywiseai.tracker.presentation.monthlybudget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.CategoryBudgetLimitEntity
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetSettingsScreen(
    viewModel: MonthlyBudgetViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()

    var budgetAmountText by remember(uiState.monthlyLimit) {
        mutableStateOf(uiState.monthlyLimit?.toPlainString() ?: "")
    }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showRemoveBudgetDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryBudgetLimitEntity?>(null) }

    val allocatedTotal = uiState.categoryLimits.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.limitAmount }
    val currentLimit = budgetAmountText.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val unallocated = currentLimit - allocatedTotal

    PennyWiseScaffold(
        title = "Budget Settings",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Monthly Budget Amount
            item {
                Text(
                    text = "Monthly Budget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                OutlinedTextField(
                    value = budgetAmountText,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                            budgetAmountText = value
                            val amount = value.toBigDecimalOrNull()
                            if (amount != null && amount > BigDecimal.ZERO) {
                                viewModel.setMonthlyLimit(amount)
                            }
                        }
                    },
                    label = { Text("Total Monthly Limit") },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(uiState.baseCurrency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Category Limits Section
            item {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Limits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                Text(
                    text = "Set spending limits for specific categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Category limit items
            items(
                items = uiState.categoryLimits,
                key = { it.categoryName }
            ) { categoryLimit ->
                CategoryLimitRow(
                    categoryLimit = categoryLimit,
                    currency = uiState.baseCurrency,
                    onEdit = { editingCategory = categoryLimit },
                    onRemove = { viewModel.removeCategoryLimit(categoryLimit.categoryName) }
                )
            }

            // Unallocated info
            if (uiState.categoryLimits.isNotEmpty() && currentLimit > BigDecimal.ZERO) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Dimensions.Padding.content),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Unallocated",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatCurrency(
                                    if (unallocated >= BigDecimal.ZERO) unallocated else BigDecimal.ZERO,
                                    uiState.baseCurrency
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (unallocated < BigDecimal.ZERO) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Remove Budget button
            if (uiState.monthlyLimit != null) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    OutlinedButton(
                        onClick = { showRemoveBudgetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Remove Budget")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Add Category Dialog
    if (showAddCategoryDialog) {
        val existingCategories = uiState.categoryLimits.map { it.categoryName }.toSet()
        val availableCategories = expenseCategories.filter { it.name !in existingCategories }

        AddCategoryLimitDialog(
            availableCategories = availableCategories.map { it.name },
            currency = uiState.baseCurrency,
            onConfirm = { categoryName, amount ->
                viewModel.setCategoryLimit(categoryName, amount)
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    // Edit Category Dialog
    editingCategory?.let { category ->
        EditCategoryLimitDialog(
            categoryName = category.categoryName,
            currentAmount = category.limitAmount,
            currency = uiState.baseCurrency,
            onConfirm = { amount ->
                viewModel.setCategoryLimit(category.categoryName, amount)
                editingCategory = null
            },
            onDismiss = { editingCategory = null }
        )
    }

    // Remove Budget Confirmation
    if (showRemoveBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveBudgetDialog = false },
            title = { Text("Remove Budget?") },
            text = { Text("This will remove your monthly budget. Category limits will be preserved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeBudget()
                        showRemoveBudgetDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryLimitRow(
    categoryLimit: CategoryBudgetLimitEntity,
    currency: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryLimit.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(categoryLimit.limitAmount, currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategoryLimitDialog(
    availableCategories: List<String>,
    currency: String = "INR",
    onConfirm: (String, BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Category Limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (availableCategories.isEmpty()) {
                    Text("All categories already have limits set.")
                } else {
                    // Category picker
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Amount input
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { value ->
                            if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                                amountText = value
                            }
                        },
                        label = { Text("Limit Amount") },
                        prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val category = selectedCategory
                    val amount = amountText.toBigDecimalOrNull()
                    if (category != null && amount != null && amount > BigDecimal.ZERO) {
                        onConfirm(category, amount)
                    }
                },
                enabled = selectedCategory != null &&
                    amountText.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditCategoryLimitDialog(
    categoryName: String,
    currentAmount: BigDecimal,
    currency: String = "INR",
    onConfirm: (BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(currentAmount.toPlainString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit $categoryName Limit") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                        amountText = value
                    }
                },
                label = { Text("Limit Amount") },
                prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toBigDecimalOrNull()
                    if (amount != null && amount > BigDecimal.ZERO) {
                        onConfirm(amount)
                    }
                },
                enabled = amountText.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
