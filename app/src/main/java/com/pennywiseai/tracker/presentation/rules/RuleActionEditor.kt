package com.pennywiseai.tracker.presentation.rules

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Spacing

@StringRes
private fun actionTypeLabel(type: ActionType): Int = when (type) {
    ActionType.BLOCK -> R.string.rules_action_type_block
    ActionType.SET -> R.string.rules_action_type_set
    ActionType.APPEND -> R.string.rules_action_type_append
    ActionType.PREPEND -> R.string.rules_action_type_prepend
    ActionType.CLEAR -> R.string.rules_action_type_clear
    ActionType.ADD_TAG -> R.string.rules_action_type_add_tag
    ActionType.REMOVE_TAG -> R.string.rules_action_type_remove_tag
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionEditor(
    action: RuleAction,
    onActionChange: (RuleAction) -> Unit
) {
    var actionTypeDropdownExpanded by remember { mutableStateOf(false) }
    var actionFieldDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Action type selector
        ExposedDropdownMenuBox(
            expanded = actionTypeDropdownExpanded,
            onExpandedChange = { actionTypeDropdownExpanded = !actionTypeDropdownExpanded }
        ) {
            TextField(
                value = stringResource(actionTypeLabel(action.actionType)),
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.rules_action_type_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionTypeDropdownExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = actionTypeDropdownExpanded,
                onDismissRequest = { actionTypeDropdownExpanded = false }
            ) {
                // Offer exactly what the engine can carry out on this field. The
                // list used to be hardcoded to Set/Clear, which hid the Append and
                // Prepend actions the engine has always supported on merchant and
                // description (#747).
                val fieldTypes = supportedActionTypes(action.field).map { it to actionTypeLabel(it) }
                (listOf(ActionType.BLOCK to actionTypeLabel(ActionType.BLOCK)) + fieldTypes).forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            onActionChange(
                                action.copy(
                                    actionType = type,
                                    value = if (type == ActionType.BLOCK) "" else action.value
                                )
                            )
                            actionTypeDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Show message for BLOCK action or field selector for others
        if (action.actionType == ActionType.BLOCK) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = stringResource(R.string.rules_block_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            // Action field selector for non-BLOCK actions
            ExposedDropdownMenuBox(
                expanded = actionFieldDropdownExpanded,
                onExpandedChange = { actionFieldDropdownExpanded = !actionFieldDropdownExpanded }
            ) {
                TextField(
                    value = stringResource(
                        when (action.field) {
                            TransactionField.CATEGORY -> R.string.rules_field_category
                            TransactionField.MERCHANT -> R.string.rules_field_merchant_name
                            TransactionField.TYPE -> R.string.rules_field_transaction_type
                            TransactionField.NARRATION -> R.string.rules_field_description
                            TransactionField.BANK_NAME -> R.string.rules_field_account
                            TransactionField.TAGS -> R.string.rules_field_tags
                            else -> R.string.rules_field_label
                        }
                    ),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text(stringResource(R.string.rules_action)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionFieldDropdownExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = actionFieldDropdownExpanded,
                    onDismissRequest = { actionFieldDropdownExpanded = false }
                ) {
                    listOf(
                        TransactionField.CATEGORY to R.string.rules_field_category,
                        TransactionField.MERCHANT to R.string.rules_field_merchant_name,
                        TransactionField.TYPE to R.string.rules_field_transaction_type,
                        TransactionField.NARRATION to R.string.rules_field_description,
                        TransactionField.BANK_NAME to R.string.rules_field_account,
                        TransactionField.TAGS to R.string.rules_field_tags
                    ).forEach { (field, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                // Keep the action type one the engine can carry out on the
                                // new field (SET on TAGS, or ADD_TAG on CATEGORY, would be dead).
                                val types = supportedActionTypes(field)
                                val actionType = if (action.actionType in types) action.actionType else types.first()
                                onActionChange(action.copy(field = field, actionType = actionType, value = ""))
                                actionFieldDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Dynamic value input based on selected action field
            when (action.field) {
                TransactionField.CATEGORY -> {
                    // Category chips and input
                    val commonCategories = listOf(
                        "Food & Dining", "Transportation", "Shopping",
                        "Bills & Utilities", "Entertainment", "Healthcare",
                        "Investments", "Others"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonCategories.forEach { category ->
                            FilterChip(
                                selected = action.value == category,
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                onClick = { onActionChange(action.copy(value = category)) },
                                label = { Text(categoryLabel(category), style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_category_name_label)) },
                        placeholder = { Text(stringResource(R.string.rules_category_name_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TransactionField.TYPE -> {
                    // Transaction type chips with user-friendly labels
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
                                selected = action.value.equals(type, ignoreCase = true),
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                onClick = { onActionChange(action.copy(value = type)) },
                                label = {
                                    Text(
                                        stringResource(displayLabel),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            )
                        }
                    }
                }

                TransactionField.MERCHANT -> {
                    // Merchant name input with common suggestions
                    val commonMerchants = listOf(
                        "Amazon", "Swiggy", "Zomato", "Uber",
                        "Netflix", "Google", "Flipkart"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonMerchants.forEach { merchant ->
                            ElevatedAssistChip(
                                onClick = { onActionChange(action.copy(value = merchant)) },
                                label = { Text(merchant, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }

                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_field_merchant_name)) },
                        placeholder = { Text(stringResource(R.string.rules_merchant_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TransactionField.NARRATION -> {
                    // Description/Narration input
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_field_description)) },
                        placeholder = { Text(stringResource(R.string.rules_description_value_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3
                    )
                }

                TransactionField.BANK_NAME -> {
                    // Account / bank the transaction belongs to.
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_account_bank_label)) },
                        placeholder = { Text(stringResource(R.string.rules_value_placeholder_bank)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TransactionField.TAGS -> {
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_tag_name_label)) },
                        placeholder = { Text(stringResource(R.string.rules_value_placeholder_merchant)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                else -> {
                    // Generic text input for other fields
                    TextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_value_label)) },
                        placeholder = { Text(stringResource(R.string.rules_value_placeholder_generic)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}
