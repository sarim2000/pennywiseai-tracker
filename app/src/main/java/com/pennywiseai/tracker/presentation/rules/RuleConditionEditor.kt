package com.pennywiseai.tracker.presentation.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.FinancialAccountIdentity
import com.pennywiseai.tracker.ui.theme.Spacing

/** The operator a field should reset to when it becomes the condition's field. */
private fun TransactionField.defaultConditionOperator(): ConditionOperator = when (this) {
    TransactionField.AMOUNT -> ConditionOperator.LESS_THAN
    TransactionField.TRANSACTION_TIME -> ConditionOperator.LESS_THAN
    TransactionField.TRANSACTION_HOUR -> ConditionOperator.EQUALS
    TransactionField.TRANSACTION_DAY_OF_WEEK -> ConditionOperator.EQUALS
    TransactionField.TRANSACTION_DAY_OF_MONTH -> ConditionOperator.EQUALS
    TransactionField.TRANSACTION_DATE -> ConditionOperator.EQUALS
    TransactionField.ACCOUNT -> ConditionOperator.EQUALS
    TransactionField.TYPE -> ConditionOperator.EQUALS
    else -> ConditionOperator.CONTAINS
}

/**
 * Operators offered for a condition field, paired with their display label.
 *
 * The set and its order come from [supportedOperators] so the picker and the
 * rule importer can't drift apart; only the wording lives here, since the same
 * operator reads differently per field ("<" for an amount, "before" for a time).
 */
private fun conditionOperatorsForField(
    field: TransactionField
): List<Pair<ConditionOperator, Int>> =
    supportedOperators(field).map { operator -> operator to operator.labelFor(field) }

@StringRes
private fun ConditionOperator.labelFor(field: TransactionField): Int = when (field) {
    TransactionField.AMOUNT -> when (this) {
        ConditionOperator.LESS_THAN -> R.string.rules_op_less_than_symbol
        ConditionOperator.GREATER_THAN -> R.string.rules_op_greater_than_symbol
        else -> R.string.rules_op_equals_symbol
    }
    TransactionField.TRANSACTION_TIME -> when (this) {
        ConditionOperator.LESS_THAN -> R.string.rules_op_before
        ConditionOperator.GREATER_THAN -> R.string.rules_op_after
        ConditionOperator.GREATER_THAN_OR_EQUAL -> R.string.rules_op_at_or_after
        ConditionOperator.LESS_THAN_OR_EQUAL -> R.string.rules_op_at_or_before
        else -> R.string.rules_op_exactly_at
    }
    TransactionField.TRANSACTION_HOUR,
    TransactionField.TRANSACTION_DAY_OF_MONTH,
    TransactionField.TRANSACTION_DATE -> when (this) {
        ConditionOperator.LESS_THAN -> R.string.rules_op_before
        ConditionOperator.GREATER_THAN -> R.string.rules_op_after
        ConditionOperator.IN -> R.string.rules_op_is_any_of
        else -> R.string.rules_op_is
    }
    TransactionField.TYPE,
    TransactionField.TRANSACTION_DAY_OF_WEEK,
    TransactionField.ACCOUNT -> when (this) {
        ConditionOperator.NOT_EQUALS -> R.string.rules_op_is_not
        ConditionOperator.IN -> R.string.rules_op_is_any_of
        else -> R.string.rules_op_is
    }
    else -> when (this) {
        ConditionOperator.EQUALS -> R.string.rules_op_equals
        ConditionOperator.STARTS_WITH -> R.string.rules_op_starts_with
        else -> R.string.rules_op_contains
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConditionFieldSelector(
    condition: RuleCondition,
    onConditionChange: (RuleCondition) -> Unit,
    allAccounts: List<RulesViewModel.AccountInfo> = emptyList()
) {
    var fieldDropdownExpanded by remember { mutableStateOf(false) }

    // Field selector
    ExposedDropdownMenuBox(
        expanded = fieldDropdownExpanded,
        onExpandedChange = { fieldDropdownExpanded = !fieldDropdownExpanded }
    ) {
        val fieldOptions = listOf(
            TransactionField.AMOUNT to R.string.rules_field_amount,
            TransactionField.TYPE to R.string.rules_field_transaction_type,
            TransactionField.CATEGORY to R.string.rules_field_category,
            TransactionField.MERCHANT to R.string.rules_field_merchant,
            TransactionField.SMS_TEXT to R.string.rules_field_sms_text,
            TransactionField.BANK_NAME to R.string.rules_field_bank_name,
            TransactionField.TRANSACTION_TIME to R.string.rules_field_time_of_day,
            TransactionField.TRANSACTION_HOUR to R.string.rules_field_hour,
            TransactionField.TRANSACTION_DAY_OF_WEEK to R.string.rules_field_day_of_week,
            TransactionField.TRANSACTION_DAY_OF_MONTH to R.string.rules_field_day_of_month,
            TransactionField.TRANSACTION_DATE to R.string.rules_field_date,
            TransactionField.ACCOUNT to R.string.rules_field_account
        )
        TextField(
            value = stringResource(fieldOptions.firstOrNull { it.first == condition.field }?.second ?: R.string.rules_field_amount),
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.rules_field_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = fieldDropdownExpanded,
            onDismissRequest = { fieldDropdownExpanded = false }
        ) {
            fieldOptions.forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        // Reset value AND operator: the previous operator may be invalid for the
                        // new field (e.g. keeping "<" from Amount when switching to Transaction
                        // Type, which only supports is / is not).
                        onConditionChange(
                            condition.copy(
                                field = field,
                                value = "",
                                operator = field.defaultConditionOperator()
                            )
                        )
                        fieldDropdownExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Operator selector
    val operators = conditionOperatorsForField(condition.field)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        operators.forEach { (op, label) ->
            FilterChip(
                selected = condition.operator == op,
                shapes = FilterChipDefaults.shapes(),
                colors = FilterChipDefaults.filterChipColors(),
                onClick = { onConditionChange(condition.copy(operator = op)) },
                label = { Text(stringResource(label)) }
            )
        }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Value input
    when (condition.field) {
        TransactionField.TYPE -> {
            Text(
                text = stringResource(R.string.rules_select_transaction_type),
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                RULE_TRANSACTION_TYPE_OPTIONS.forEach { (type, displayLabel) ->
                    FilterChip(
                        selected = condition.value.equals(type, ignoreCase = true),
                        shapes = FilterChipDefaults.shapes(),
                        colors = FilterChipDefaults.filterChipColors(),
                        onClick = { onConditionChange(condition.copy(value = type)) },
                        label = {
                            Text(stringResource(displayLabel), style = MaterialTheme.typography.bodySmall)
                        }
                    )
                }
            }
        }

        TransactionField.TRANSACTION_DAY_OF_WEEK -> {
            val days = RULE_DAYS_OF_WEEK
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (condition.operator == ConditionOperator.IN ||
                    condition.operator == ConditionOperator.NOT_IN
                ) {
                    val selectedDays = condition.value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    days.forEach { (value, label) ->
                        FilterChip(
                            selected = value in selectedDays,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = {
                                val newSet = if (value in selectedDays) selectedDays - value else selectedDays + value
                                onConditionChange(condition.copy(value = newSet.sorted().joinToString(",")))
                            },
                            label = { Text(stringResource(label), style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                } else {
                    days.forEach { (value, label) ->
                        FilterChip(
                            selected = condition.value == value,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = { onConditionChange(condition.copy(value = value)) },
                            label = { Text(stringResource(label), style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }
        }

        TransactionField.TRANSACTION_DAY_OF_MONTH -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rules_day_of_month_label)) },
                placeholder = { Text(stringResource(R.string.rules_day_of_month_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        TransactionField.TRANSACTION_TIME -> {
            var showTimePicker by remember { mutableStateOf(false) }
            val initialHour = condition.value.split(":").getOrNull(0)?.toIntOrNull() ?: 9
            val initialMinute = condition.value.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            val timePickerState = rememberTimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute
            )

            TextField(
                value = condition.value,
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.rules_time_label)) },
                placeholder = { Text(stringResource(R.string.rules_time_placeholder)) },
                trailingIcon = {
                    IconButton(onClick = { showTimePicker = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.AccessTime, contentDescription = stringResource(R.string.rules_pick_time))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val formatted = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                            onConditionChange(condition.copy(value = formatted))
                            showTimePicker = false
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_cancel)) }
                    },
                    text = { TimePicker(state = timePickerState) }
                )
            }
        }

        TransactionField.TRANSACTION_HOUR -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rules_hour_label)) },
                placeholder = { Text(stringResource(R.string.rules_hour_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        TransactionField.TRANSACTION_DATE -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rules_date_label)) },
                placeholder = { Text(stringResource(R.string.rules_date_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        TransactionField.ACCOUNT -> RuleAccountPicker(
            value = condition.value,
            allAccounts = allAccounts,
            label = stringResource(R.string.rules_field_account),
            onSelect = { onConditionChange(condition.copy(value = it)) }
        )

        else -> {
            TextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text(stringResource(R.string.rules_value_label)) },
                placeholder = {
                    Text(
                        stringResource(
                            when (condition.field) {
                                TransactionField.AMOUNT -> R.string.rules_value_placeholder_amount
                                TransactionField.MERCHANT -> R.string.rules_value_placeholder_merchant
                                TransactionField.SMS_TEXT -> R.string.rules_value_placeholder_sms_text
                                TransactionField.CATEGORY -> R.string.rules_value_placeholder_category
                                TransactionField.BANK_NAME -> R.string.rules_value_placeholder_bank
                                else -> R.string.rules_value_placeholder_generic
                            }
                        )
                    )
                },
                keyboardOptions = if (condition.field == TransactionField.AMOUNT) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

/** Dropdown of the user's accounts; [value] and the selection are "BankName||Last4" keys. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleAccountPicker(
    value: String,
    allAccounts: List<RulesViewModel.AccountInfo>,
    label: String,
    onSelect: (String) -> Unit
) {
    // Account dropdown picker
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    val selectedAccount = allAccounts.firstOrNull {
        "${it.bankName}||${it.accountLast4}" == value
    }

    val displayText = selectedAccount?.let {
        it.displayName
    } ?: if (value.isNotBlank()) {
        // Show raw value if account no longer exists
        value
    } else {
        stringResource(R.string.rules_select_account)
    }

    Column {
        ExposedDropdownMenuBox(
            expanded = accountDropdownExpanded,
            onExpandedChange = { accountDropdownExpanded = !accountDropdownExpanded }
        ) {
            TextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = accountDropdownExpanded,
                onDismissRequest = { accountDropdownExpanded = false }
            ) {
                allAccounts.forEach { account ->
                    val key = "${account.bankName}||${account.accountLast4}"
                    val accountTypeLabel = when {
                        account.isCreditCard -> stringResource(R.string.rules_account_type_credit)
                        account.accountType != null -> account.accountType
                        else -> ""
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                FinancialAccountIdentity(
                                    bankName = account.bankName,
                                    accountLast4 = account.accountLast4
                                )
                                if (accountTypeLabel.isNotBlank()) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(accountTypeLabel, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(key)
                            accountDropdownExpanded = false
                        }
                    )
                }
            }
        }

        if (allAccounts.isEmpty()) {
            Text(
                text = stringResource(R.string.rules_no_accounts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}
