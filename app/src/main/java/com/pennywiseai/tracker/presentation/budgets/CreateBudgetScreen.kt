package com.pennywiseai.tracker.presentation.budgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.budget_card_colors
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBudgetScreen(
    budgetId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expenseCategories by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val availableCurrencies by viewModel.availableCurrencies.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isEditMode = budgetId != null

    // Form state
    var currentStep by remember { mutableStateOf(if (isEditMode) 1 else 0) }
    var selectedPreset by remember { mutableStateOf<BudgetPreset?>(null) }
    var name by remember { mutableStateOf("") }
    var limitAmount by remember { mutableStateOf("") }
    var periodType by remember { mutableStateOf(BudgetPeriodType.MONTHLY) }
    var startDate by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var endDate by remember { mutableStateOf(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth())) }
    var currency by remember { mutableStateOf("INR") }
    var includeAllCategories by remember { mutableStateOf(false) }
    var selectedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedColor by remember { mutableStateOf("#1565C0") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Load budget for edit mode
    LaunchedEffect(budgetId) {
        if (budgetId != null) {
            viewModel.loadBudgetForEdit(budgetId)
        }
    }

    // Populate form when editing
    LaunchedEffect(uiState.editingBudget) {
        uiState.editingBudget?.let { budget ->
            name = budget.name
            limitAmount = budget.limitAmount.toPlainString()
            periodType = budget.periodType
            startDate = budget.startDate
            endDate = budget.endDate
            currency = budget.currency
            includeAllCategories = budget.includeAllCategories
            selectedColor = budget.color
        }
        if (uiState.editingBudgetCategories.isNotEmpty()) {
            selectedCategories = uiState.editingBudgetCategories.toSet()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearEditingBudget()
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearSnackbarMessage()
            }
        }
    }

    // Update dates when period type changes
    LaunchedEffect(periodType) {
        when (periodType) {
            BudgetPeriodType.WEEKLY -> {
                val today = LocalDate.now()
                startDate = today.with(java.time.DayOfWeek.MONDAY)
                endDate = startDate.plusDays(6)
            }
            BudgetPeriodType.MONTHLY -> {
                startDate = LocalDate.now().withDayOfMonth(1)
                endDate = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth())
            }
            BudgetPeriodType.CUSTOM -> {
                // Keep current dates for custom
            }
        }
    }

    val isFormValid = name.isNotBlank() &&
            limitAmount.isNotBlank() &&
            limitAmount.toBigDecimalOrNull() != null &&
            (limitAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO &&
            (includeAllCategories || selectedCategories.isNotEmpty())

    PennyWiseScaffold(
        title = if (isEditMode) "Edit Budget" else "Create Budget",
        navigationIcon = {
            IconButton(onClick = {
                if (currentStep > 0 && !isEditMode) {
                    currentStep--
                } else {
                    onNavigateBack()
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            if (isEditMode) {
                IconButton(onClick = {
                    budgetId?.let { viewModel.deleteBudget(it) }
                    onNavigateBack()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Budget",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentStep) {
                0 -> {
                    // Preset Selection Step
                    PresetSelectionStep(
                        onPresetSelected = { preset ->
                            selectedPreset = preset
                            name = preset.name
                            includeAllCategories = preset.includeAllCategories
                            selectedCategories = preset.categories.toSet()
                            selectedColor = preset.color
                            currentStep = 1
                        }
                    )
                }
                1 -> {
                    // Budget Form Step
                    BudgetFormStep(
                        name = name,
                        onNameChange = { name = it },
                        limitAmount = limitAmount,
                        onLimitAmountChange = { limitAmount = it },
                        periodType = periodType,
                        onPeriodTypeChange = { periodType = it },
                        startDate = startDate,
                        endDate = endDate,
                        onStartDateClick = { showStartDatePicker = true },
                        onEndDateClick = { showEndDatePicker = true },
                        currency = currency,
                        onCurrencyChange = { currency = it },
                        availableCurrencies = availableCurrencies,
                        includeAllCategories = includeAllCategories,
                        onIncludeAllCategoriesChange = { includeAllCategories = it },
                        selectedCategories = selectedCategories,
                        onCategoryToggle = { category ->
                            selectedCategories = if (category in selectedCategories) {
                                selectedCategories - category
                            } else {
                                selectedCategories + category
                            }
                        },
                        expenseCategories = expenseCategories.map { it.name },
                        selectedColor = selectedColor,
                        onColorChange = { selectedColor = it },
                        isFormValid = isFormValid,
                        isEditMode = isEditMode,
                        onSave = {
                            val amount = limitAmount.toBigDecimalOrNull() ?: return@BudgetFormStep

                            if (isEditMode && budgetId != null) {
                                viewModel.updateBudget(
                                    budgetId = budgetId,
                                    name = name,
                                    limitAmount = amount,
                                    periodType = periodType,
                                    startDate = startDate,
                                    endDate = endDate,
                                    currency = currency,
                                    includeAllCategories = includeAllCategories,
                                    categories = selectedCategories.toList(),
                                    color = selectedColor
                                )
                            } else {
                                viewModel.createBudget(
                                    name = name,
                                    limitAmount = amount,
                                    periodType = periodType,
                                    startDate = startDate,
                                    endDate = endDate,
                                    currency = currency,
                                    includeAllCategories = includeAllCategories,
                                    categories = selectedCategories.toList(),
                                    color = selectedColor
                                )
                            }
                            onNavigateBack()
                        }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Date Pickers
        if (showStartDatePicker) {
            DatePickerDialog(
                selectedDate = startDate,
                onDateSelected = {
                    startDate = it
                    if (it > endDate) endDate = it.plusMonths(1)
                },
                onDismiss = { showStartDatePicker = false }
            )
        }

        if (showEndDatePicker) {
            DatePickerDialog(
                selectedDate = endDate,
                onDateSelected = {
                    if (it >= startDate) endDate = it
                },
                onDismiss = { showEndDatePicker = false }
            )
        }
    }
}

@Composable
private fun PresetSelectionStep(
    onPresetSelected: (BudgetPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Text(
                text = "Choose a budget type",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
        }

        items(BudgetPresets.ALL_PRESETS) { preset ->
            PresetCard(
                preset = preset,
                onClick = { onPresetSelected(preset) }
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: BudgetPreset,
    onClick: () -> Unit
) {
    val presetColor = try {
        Color(android.graphics.Color.parseColor(preset.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    PennyWiseCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(presetColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetFormStep(
    name: String,
    onNameChange: (String) -> Unit,
    limitAmount: String,
    onLimitAmountChange: (String) -> Unit,
    periodType: BudgetPeriodType,
    onPeriodTypeChange: (BudgetPeriodType) -> Unit,
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    availableCurrencies: List<String>,
    includeAllCategories: Boolean,
    onIncludeAllCategoriesChange: (Boolean) -> Unit,
    selectedCategories: Set<String>,
    onCategoryToggle: (String) -> Unit,
    expenseCategories: List<String>,
    selectedColor: String,
    onColorChange: (String) -> Unit,
    isFormValid: Boolean,
    isEditMode: Boolean,
    onSave: () -> Unit
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Name
        item {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Budget Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Limit Amount
        item {
            OutlinedTextField(
                value = limitAmount,
                onValueChange = { value ->
                    if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                        onLimitAmountChange(value)
                    }
                },
                label = { Text("Budget Limit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("$currency ") }
            )
        }

        // Period Type
        item {
            Text(
                text = "Period",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                BudgetPeriodType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = periodType == type,
                        onClick = { onPeriodTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = BudgetPeriodType.entries.size
                        )
                    ) {
                        Text(type.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        // Custom Date Range (only for Custom period)
        item {
            AnimatedVisibility(
                visible = periodType == BudgetPeriodType.CUSTOM,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OutlinedTextField(
                        value = startDate.format(dateFormatter),
                        onValueChange = {},
                        label = { Text("Start Date") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = onStartDateClick) {
                                Icon(Icons.Default.CalendarToday, "Select start date")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = endDate.format(dateFormatter),
                        onValueChange = {},
                        label = { Text("End Date") },
                        modifier = Modifier.weight(1f),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = onEndDateClick) {
                                Icon(Icons.Default.CalendarToday, "Select end date")
                            }
                        }
                    )
                }
            }
        }

        // Currency
        item {
            var currencyExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = it }
            ) {
                OutlinedTextField(
                    value = currency,
                    onValueChange = {},
                    label = { Text("Currency") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false }
                ) {
                    availableCurrencies.forEach { curr ->
                        DropdownMenuItem(
                            text = { Text(curr) },
                            onClick = {
                                onCurrencyChange(curr)
                                currencyExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Categories
        item {
            Text(
                text = "Categories",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onIncludeAllCategoriesChange(!includeAllCategories) }
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("All Categories")
                Checkbox(
                    checked = includeAllCategories,
                    onCheckedChange = { onIncludeAllCategoriesChange(it) }
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = !includeAllCategories,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    expenseCategories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCategoryToggle(category) }
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Checkbox(
                                checked = category in selectedCategories,
                                onCheckedChange = { onCategoryToggle(category) }
                            )
                        }
                    }
                }
            }
        }

        // Color
        item {
            Text(
                text = "Color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(budget_card_colors.size) { index ->
                    val colorHex = String.format("#%06X", 0xFFFFFF and budget_card_colors[index].hashCode())
                    val isSelected = selectedColor == colorHex ||
                            (index == 0 && selectedColor == "#1565C0") ||
                            budget_card_colors[index] == try {
                                Color(android.graphics.Color.parseColor(selectedColor))
                            } catch (e: Exception) { null }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(budget_card_colors[index])
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                            .clickable {
                                onColorChange(String.format("#%06X", 0xFFFFFF and budget_card_colors[index].hashCode()))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Save Button
        item {
            Spacer(modifier = Modifier.height(Spacing.md))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid
            ) {
                Text(if (isEditMode) "Update Budget" else "Create Budget")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochDay() * 24 * 60 * 60 * 1000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onDateSelected(localDate)
                    }
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
