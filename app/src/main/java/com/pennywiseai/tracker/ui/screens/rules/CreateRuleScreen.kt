package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.layout.*
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
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRuleScreen(
    onNavigateBack: () -> Unit,
    onSaveRule: (TransactionRule) -> Unit,
    existingRule: TransactionRule? = null
) {
    var ruleName by remember(existingRule) { mutableStateOf(existingRule?.name ?: "") }
    var description by remember(existingRule) { mutableStateOf(existingRule?.description ?: "") }

    // Initialize conditions list from existing rule or use single default condition
    var conditions by remember(existingRule) {
        mutableStateOf(existingRule?.conditions?.toMutableList() ?: mutableListOf(
            RuleCondition(
                field = TransactionField.AMOUNT,
                operator = ConditionOperator.LESS_THAN,
                value = ""
            )
        ))
    }

    // Initialize action state from existing rule or use defaults
    var actionType by remember(existingRule) {
        mutableStateOf(existingRule?.actions?.firstOrNull()?.actionType ?: ActionType.SET)
    }
    var actionField by remember(existingRule) {
        mutableStateOf(existingRule?.actions?.firstOrNull()?.field ?: TransactionField.CATEGORY)
    }
    var actionFieldDropdownExpanded by remember { mutableStateOf(false) }
    var actionTypeDropdownExpanded by remember { mutableStateOf(false) }
    var actionValue by remember(existingRule) {
        mutableStateOf(existingRule?.actions?.firstOrNull()?.value ?: "")
    }

    // Common presets for quick setup
    val commonPresets = listOf(
        "Block OTPs" to {
            ruleName = "Block OTP Messages"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "OTP"
                )
            )
            actionType = ActionType.BLOCK
            actionField = TransactionField.CATEGORY
            actionValue = ""
        },
        "Block Small Amounts" to {
            ruleName = "Block Small Transactions"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "10"
                )
            )
            actionType = ActionType.BLOCK
            actionField = TransactionField.CATEGORY
            actionValue = ""
        },
        "Small amounts → Food" to {
            ruleName = "Small Food Payments"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "200"
                )
            )
            actionType = ActionType.SET
            actionField = TransactionField.CATEGORY
            actionValue = "Food & Dining"
        },
        "Standardize Merchant" to {
            ruleName = "Standardize Merchant Name"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.CONTAINS,
                    value = "AMZN"
                )
            )
            actionType = ActionType.SET
            actionField = TransactionField.MERCHANT
            actionValue = "Amazon"
        },
        "Mark as Income" to {
            ruleName = "Mark Credits as Income"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "credited"
                )
            )
            actionType = ActionType.SET
            actionField = TransactionField.TYPE
            actionValue = "income"
        }
    )

    PennyWiseScaffold(
        title = if (existingRule != null) "Edit Rule" else "Create Rule",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        },
        actions = {
            TextButton(
                onClick = {
                    // Validate: rule name + all conditions have values + action is valid
                    val areConditionsValid = conditions.isNotEmpty() &&
                        conditions.all { it.value.isNotBlank() }
                    val isActionValid = actionType == ActionType.BLOCK || actionValue.isNotBlank()
                    val isValid = ruleName.isNotBlank() && areConditionsValid && isActionValid

                    if (isValid) {
                        val rule = TransactionRule(
                            id = existingRule?.id ?: UUID.randomUUID().toString(),
                            name = ruleName,
                            description = description.takeIf { it.isNotBlank() },
                            priority = existingRule?.priority ?: 100,
                            conditions = conditions.toList(),
                            actions = listOf(
                                RuleAction(
                                    field = actionField,
                                    actionType = actionType,
                                    value = if (actionType == ActionType.BLOCK) "" else actionValue
                                )
                            ),
                            isActive = existingRule?.isActive ?: true,
                            isSystemTemplate = existingRule?.isSystemTemplate ?: false,
                            createdAt = existingRule?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onSaveRule(rule)
                        // Navigation is handled in PennyWiseNavHost after saving
                    }
                },
                enabled = ruleName.isNotBlank() &&
                         conditions.isNotEmpty() &&
                         conditions.all { it.value.isNotBlank() } &&
                         (actionType == ActionType.BLOCK || actionValue.isNotBlank())
            ) {
                Text("Save")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding() // Push content up when keyboard appears
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Quick presets
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Quick Templates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonPresets.forEach { (label, action) ->
                            AssistChip(
                                onClick = action,
                                label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }

            // Rule name and description
            OutlinedTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text("Rule Name") },
                placeholder = { Text("e.g., Food expenses under 200") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("What does this rule do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            // Conditions section (supports multiple)
            Card {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "When",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = {
                                conditions = (conditions + RuleCondition(
                                    field = TransactionField.AMOUNT,
                                    operator = ConditionOperator.LESS_THAN,
                                    value = ""
                                )).toMutableList()
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Add Condition")
                        }
                    }

                    // Display all conditions
                    conditions.forEachIndexed { index, condition ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                // Header with delete button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (index == 0) "Condition" else "AND Condition ${index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (conditions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                conditions = conditions.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove condition",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                // Field selector
                                ConditionFieldSelector(
                                    condition = condition,
                                    onConditionChange = { newCondition ->
                                        conditions = conditions.toMutableList().apply {
                                            set(index, newCondition)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Action section
            Card {
                Column(
                    modifier = Modifier.padding(Dimensions.Padding.content),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Then",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Action type selector
                    ExposedDropdownMenuBox(
                        expanded = actionTypeDropdownExpanded,
                        onExpandedChange = { actionTypeDropdownExpanded = !actionTypeDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = when(actionType) {
                                ActionType.BLOCK -> "Block Transaction"
                                ActionType.SET -> "Set Field"
                                ActionType.APPEND -> "Append to Field"
                                ActionType.PREPEND -> "Prepend to Field"
                                ActionType.CLEAR -> "Clear Field"
                                ActionType.ADD_TAG -> "Add Tag"
                                ActionType.REMOVE_TAG -> "Remove Tag"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Action Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionTypeDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = actionTypeDropdownExpanded,
                            onDismissRequest = { actionTypeDropdownExpanded = false }
                        ) {
                            listOf(
                                ActionType.BLOCK to "Block Transaction",
                                ActionType.SET to "Set Field",
                                ActionType.CLEAR to "Clear Field"
                            ).forEach { (type, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        actionType = type
                                        actionTypeDropdownExpanded = false
                                        if (type == ActionType.BLOCK) {
                                            actionValue = "" // Clear value for BLOCK action
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Show message for BLOCK action or field selector for others
                    if (actionType == ActionType.BLOCK) {
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
                                    text = "Transactions matching this rule will be blocked and not saved",
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
                        OutlinedTextField(
                            value = when(actionField) {
                                TransactionField.CATEGORY -> "Set Category"
                                TransactionField.MERCHANT -> "Set Merchant Name"
                                TransactionField.TYPE -> "Set Transaction Type"
                                TransactionField.NARRATION -> "Set Description"
                                else -> "Set Field"
                            },
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Action") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionFieldDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = actionFieldDropdownExpanded,
                            onDismissRequest = { actionFieldDropdownExpanded = false }
                        ) {
                            listOf(
                                TransactionField.CATEGORY to "Set Category",
                                TransactionField.MERCHANT to "Set Merchant Name",
                                TransactionField.TYPE to "Set Transaction Type",
                                TransactionField.NARRATION to "Set Description"
                            ).forEach { (field, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        actionField = field
                                        actionFieldDropdownExpanded = false
                                        actionValue = "" // Reset value when changing field
                                    }
                                )
                            }
                        }
                    }

                    // Dynamic value input based on selected action field
                    when (actionField) {
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
                                        selected = actionValue == category,
                                        onClick = { actionValue = category },
                                        label = { Text(category, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Category Name") },
                                placeholder = { Text("e.g., Rent") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        TransactionField.TYPE -> {
                            // Transaction type chips with user-friendly labels
                            Text(
                                text = "Select transaction type:",
                                style = MaterialTheme.typography.bodySmall
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(
                                    "INCOME" to "Incoming",
                                    "EXPENSE" to "Outgoing",
                                    "CREDIT" to "Credit Card",
                                    "TRANSFER" to "Transfer",
                                    "INVESTMENT" to "Investment"
                                ).forEach { (type, displayLabel) ->
                                    FilterChip(
                                        selected = actionValue == type,
                                        onClick = { actionValue = type },
                                        label = {
                                            Text(
                                                displayLabel,
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
                                    AssistChip(
                                        onClick = { actionValue = merchant },
                                        label = { Text(merchant, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Merchant Name") },
                                placeholder = { Text("e.g., Amazon") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        TransactionField.NARRATION -> {
                            // Description/Narration input
                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Description") },
                                placeholder = { Text("e.g., Monthly subscription payment") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 3
                            )
                        }

                        else -> {
                            // Generic text input for other fields
                            OutlinedTextField(
                                value = actionValue,
                                onValueChange = { actionValue = it },
                                label = { Text("Value") },
                                placeholder = { Text("Enter value") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    } // End of if-else for BLOCK action
                }
            }

            // Preview
            val showPreview = ruleName.isNotBlank() &&
                             conditions.isNotEmpty() &&
                             conditions.all { it.value.isNotBlank() } &&
                             (actionType == ActionType.BLOCK || actionValue.isNotBlank())
            if (showPreview) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = "Rule Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = buildString {
                                append("When ")
                                conditions.forEachIndexed { index, condition ->
                                    if (index > 0) append(" AND ")
                                    append(when(condition.field) {
                                        TransactionField.AMOUNT -> "amount"
                                        TransactionField.TYPE -> "type"
                                        TransactionField.CATEGORY -> "category"
                                        TransactionField.MERCHANT -> "merchant"
                                        TransactionField.SMS_TEXT -> "SMS text"
                                        TransactionField.BANK_NAME -> "bank"
                                        else -> "field"
                                    })
                                    append(" ")
                                    append(when(condition.operator) {
                                        ConditionOperator.LESS_THAN -> "is less than"
                                        ConditionOperator.GREATER_THAN -> "is greater than"
                                        ConditionOperator.EQUALS -> "equals"
                                        ConditionOperator.CONTAINS -> "contains"
                                        ConditionOperator.STARTS_WITH -> "starts with"
                                        else -> "matches"
                                    })
                                    append(" ")
                                    // Show user-friendly labels for transaction types
                                    if (condition.field == TransactionField.TYPE) {
                                        append(when(condition.value) {
                                            "INCOME" -> "Incoming"
                                            "EXPENSE" -> "Outgoing"
                                            "CREDIT" -> "Credit Card"
                                            "TRANSFER" -> "Transfer"
                                            "INVESTMENT" -> "Investment"
                                            else -> condition.value
                                        })
                                    } else {
                                        append(condition.value)
                                    }
                                }
                                append(", ")
                                if (actionType == ActionType.BLOCK) {
                                    append("block transaction")
                                } else {
                                    append(when(actionField) {
                                        TransactionField.CATEGORY -> "set category to "
                                        TransactionField.MERCHANT -> "set merchant to "
                                        TransactionField.TYPE -> "set type to "
                                        TransactionField.NARRATION -> "set description to "
                                        else -> "set field to "
                                    })
                                    // Show user-friendly labels for transaction types in actions too
                                    if (actionField == TransactionField.TYPE) {
                                        append(when(actionValue) {
                                            "INCOME" -> "Incoming"
                                            "EXPENSE" -> "Outgoing"
                                            "CREDIT" -> "Credit Card"
                                            "TRANSFER" -> "Transfer"
                                            "INVESTMENT" -> "Investment"
                                            else -> actionValue
                                        })
                                    } else {
                                        append(actionValue)
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionFieldSelector(
    condition: RuleCondition,
    onConditionChange: (RuleCondition) -> Unit
) {
    var fieldDropdownExpanded by remember { mutableStateOf(false) }

    // Field selector
    ExposedDropdownMenuBox(
        expanded = fieldDropdownExpanded,
        onExpandedChange = { fieldDropdownExpanded = !fieldDropdownExpanded }
    ) {
        OutlinedTextField(
            value = when(condition.field) {
                TransactionField.AMOUNT -> "Amount"
                TransactionField.MERCHANT -> "Merchant"
                TransactionField.CATEGORY -> "Category"
                TransactionField.SMS_TEXT -> "SMS Text"
                TransactionField.TYPE -> "Transaction Type"
                TransactionField.BANK_NAME -> "Bank Name"
                else -> "Amount"
            },
            onValueChange = { },
            readOnly = true,
            label = { Text("Field") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = fieldDropdownExpanded,
            onDismissRequest = { fieldDropdownExpanded = false }
        ) {
            listOf(
                TransactionField.AMOUNT to "Amount",
                TransactionField.TYPE to "Transaction Type",
                TransactionField.CATEGORY to "Category",
                TransactionField.MERCHANT to "Merchant",
                TransactionField.SMS_TEXT to "SMS Text",
                TransactionField.BANK_NAME to "Bank Name"
            ).forEach { (field, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onConditionChange(condition.copy(field = field, value = ""))
                        fieldDropdownExpanded = false
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Operator selector
    val operators = when(condition.field) {
        TransactionField.AMOUNT -> listOf(
            ConditionOperator.LESS_THAN to "<",
            ConditionOperator.GREATER_THAN to ">",
            ConditionOperator.EQUALS to "="
        )
        else -> listOf(
            ConditionOperator.CONTAINS to "contains",
            ConditionOperator.EQUALS to "equals",
            ConditionOperator.STARTS_WITH to "starts with"
        )
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        operators.forEach { (op, label) ->
            FilterChip(
                selected = condition.operator == op,
                onClick = { onConditionChange(condition.copy(operator = op)) },
                label = { Text(label) }
            )
        }
    }

    Spacer(modifier = Modifier.height(Spacing.sm))

    // Value input
    when (condition.field) {
        TransactionField.TYPE -> {
            // Transaction type chips with user-friendly labels
            Text(
                text = "Select transaction type:",
                style = MaterialTheme.typography.bodySmall
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "INCOME" to "Incoming",
                    "EXPENSE" to "Outgoing",
                    "CREDIT" to "Credit Card",
                    "TRANSFER" to "Transfer",
                    "INVESTMENT" to "Investment"
                ).forEach { (type, displayLabel) ->
                    FilterChip(
                        selected = condition.value == type,
                        onClick = { onConditionChange(condition.copy(value = type)) },
                        label = {
                            Text(
                                displayLabel,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }
        }
        else -> {
            // Regular text input for other fields
            OutlinedTextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                label = { Text("Value") },
                placeholder = {
                    Text(
                        when(condition.field) {
                            TransactionField.AMOUNT -> "e.g., 200"
                            TransactionField.MERCHANT -> "e.g., Swiggy"
                            TransactionField.SMS_TEXT -> "e.g., salary"
                            TransactionField.CATEGORY -> "e.g., Food & Dining"
                            TransactionField.BANK_NAME -> "e.g., HDFC Bank"
                            else -> "Enter value"
                        }
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