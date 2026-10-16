package com.pennywiseai.tracker.presentation.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.ColorSwatchRow
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Amount fields accept digits with one optional decimal point. */
internal val amountInput = Regex("^\\d*\\.?\\d{0,2}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onNavigateBack() }

    val form = state.form
    val errors = if (state.showErrors) form.errors(viewModel.today, state.original) else emptySet()
    val perMonth = form.perMonth(viewModel.today, state.original, state.contributions)
    var showDatePicker by remember { mutableStateOf(false) }

    PennyWiseScaffold(
        title = stringResource(if (state.isEditing) R.string.goal_edit_title_edit else R.string.goal_edit_title_new),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.goal_back))
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = Dimensions.Elevation.bottomBar) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm)
                ) {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isLoading && !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (state.isEditing) R.string.goal_edit_save else R.string.goal_edit_create))
                    }
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@PennyWiseScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.goal_edit_name)) },
                placeholder = { Text(stringResource(R.string.goal_edit_name_hint)) },
                singleLine = true,
                isError = GoalFieldError.NAME_BLANK in errors,
                supportingText = errorText(GoalFieldError.NAME_BLANK in errors, R.string.goal_edit_error_name),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CurrencyField(
                    currency = form.currency,
                    enabled = !state.currencyLocked,
                    onSelect = { c -> viewModel.update { it.copy(currency = c) } },
                    modifier = Modifier.weight(0.4f),
                )
                OutlinedTextField(
                    value = form.target,
                    onValueChange = { v -> if (v.matches(amountInput)) viewModel.update { it.copy(target = v) } },
                    label = { Text(stringResource(R.string.goal_edit_target)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = GoalFieldError.TARGET_INVALID in errors,
                    supportingText = errorText(GoalFieldError.TARGET_INVALID in errors, R.string.goal_edit_error_target),
                    modifier = Modifier.weight(0.6f),
                )
            }
            if (state.currencyLocked) {
                Text(
                    stringResource(R.string.goal_edit_currency_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = form.dueDate?.format(goalDateFormat).orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.goal_edit_due)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.goal_edit_due))
                    }
                },
                isError = GoalFieldError.DUE_NOT_AFTER_TODAY in errors,
                supportingText = errorText(GoalFieldError.DUE_NOT_AFTER_TODAY in errors, R.string.goal_edit_error_due),
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.isEditing) {
                OutlinedTextField(
                    value = form.alreadySaved,
                    onValueChange = { v -> if (v.matches(amountInput)) viewModel.update { it.copy(alreadySaved = v) } },
                    label = { Text(stringResource(R.string.goal_edit_already_saved)) },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(form.currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = GoalFieldError.ALREADY_SAVED_INVALID in errors,
                    supportingText = {
                        Text(
                            stringResource(
                                if (GoalFieldError.ALREADY_SAVED_INVALID in errors) R.string.goal_edit_error_already_saved
                                else R.string.goal_edit_already_saved_hint
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (perMonth != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Row(
                        Modifier.fillMaxWidth().padding(Dimensions.Padding.cardCompact),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            stringResource(R.string.goal_edit_helper, CurrencyFormatter.formatCurrency(perMonth, form.currency)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (state.saveFailed) {
                Text(
                    stringResource(R.string.goal_edit_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SectionHeaderV2(title = stringResource(R.string.goal_edit_color), topSpacing = Spacing.none)
            PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                ColorSwatchRow(selected = form.color, onSelect = { c -> viewModel.update { it.copy(color = c) } })
            }
        }
    }

    if (showDatePicker) {
        val today = viewModel.today
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.dueDate?.let { it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcDate().isAfter(today)
                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> viewModel.update { it.copy(dueDate = ms.toUtcDate()) } }
                    showDatePicker = false
                }) { Text(stringResource(R.string.goal_action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.goal_action_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

internal fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun errorText(show: Boolean, res: Int): (@Composable () -> Unit)? =
    if (show) ({ Text(stringResource(res)) }) else null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencyField(
    currency: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = currency,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.goal_edit_currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            CurrencyFormatter.getSupportedCurrencies().forEach { code ->
                DropdownMenuItem(
                    text = { Text("${CurrencyFormatter.getCurrencySymbol(code)} $code") },
                    onClick = { onSelect(code); expanded = false },
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                )
            }
        }
    }
}
