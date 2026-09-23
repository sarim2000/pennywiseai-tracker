package com.pennywiseai.tracker.ui.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.pennywiseai.tracker.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.FinancialAccountIdentity
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.viewmodel.RulesViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.ui.theme.Spacing
import java.util.UUID

/**
 * Transaction-type options for rule condition/action pickers: stored enum name → display label.
 * Labels mirror the [com.pennywiseai.tracker.data.database.entity.TransactionType] names (e.g.
 * EXPENSE → "Expense") so what the user picks matches what the rule stores and applies.
 * Only the label is translated; the stored value stays the enum name.
 */
internal val RULE_TRANSACTION_TYPE_OPTIONS: List<Pair<String, Int>> = listOf(
    "INCOME" to R.string.rules_type_income,
    "EXPENSE" to R.string.rules_type_expense,
    "CREDIT" to R.string.rules_type_credit,
    "TRANSFER" to R.string.rules_type_transfer,
    "INVESTMENT" to R.string.rules_type_investment
)

/** User-facing label for a stored transaction-type value, falling back to the raw value. */
@Composable
internal fun ruleTransactionTypeLabel(value: String): String =
    RULE_TRANSACTION_TYPE_OPTIONS.firstOrNull { it.first.equals(value, ignoreCase = true) }
        ?.let { stringResource(it.second) }
        ?: value

/** Day-of-week condition values ("1" = Monday) → short display label. */
private val RULE_DAYS_OF_WEEK: List<Pair<String, Int>> = listOf(
    "1" to R.string.rules_day_mon,
    "2" to R.string.rules_day_tue,
    "3" to R.string.rules_day_wed,
    "4" to R.string.rules_day_thu,
    "5" to R.string.rules_day_fri,
    "6" to R.string.rules_day_sat,
    "7" to R.string.rules_day_sun
)

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
fun CreateRuleScreen(
    onNavigateBack: () -> Unit,
    onSaveRule: (TransactionRule) -> Unit,
    existingRule: TransactionRule? = null,
    // True only when editing a saved rule. A duplicate passes a prefilled [existingRule]
    // (carrying a fresh id) but isEditing = false, so it saves as a brand-new rule.
    // Defaults to false: a non-null prefill must NOT imply edit, or a duplicate that
    // omits this flag would overwrite its source. Callers state edit intent explicitly.
    isEditing: Boolean = false,
    allAccounts: List<RulesViewModel.AccountInfo> = emptyList()
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

    // Initialize actions list from existing rule or use a single default action
    var actions by remember(existingRule) {
        mutableStateOf(
            existingRule?.actions?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    RuleAction(
                        field = TransactionField.CATEGORY,
                        actionType = ActionType.SET,
                        value = ""
                    )
                )
        )
    }

    // Holds a pending switch-to-BLOCK while we confirm discarding the other actions.
    var pendingBlockAction by remember { mutableStateOf<RuleAction?>(null) }

    // Common presets for quick setup
    val commonPresets = listOf(
        R.string.rules_preset_block_otps to {
            ruleName = "Block OTP Messages"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "OTP"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.BLOCK,
                    value = ""
                )
            )
        },
        R.string.rules_preset_block_small to {
            ruleName = "Block Small Transactions"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "10"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.BLOCK,
                    value = ""
                )
            )
        },
        R.string.rules_preset_small_food to {
            ruleName = "Small Food Payments"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "200"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Food & Dining"
                )
            )
        },
        R.string.rules_preset_standardize_merchant to {
            ruleName = "Standardize Merchant Name"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.CONTAINS,
                    value = "AMZN"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.MERCHANT,
                    actionType = ActionType.SET,
                    value = "Amazon"
                )
            )
        },
        R.string.rules_preset_mark_income to {
            ruleName = "Mark Credits as Income"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.SMS_TEXT,
                    operator = ConditionOperator.CONTAINS,
                    value = "credited"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.TYPE,
                    actionType = ActionType.SET,
                    value = "INCOME"
                )
            )
        },
        R.string.rules_preset_daily_investment to {
            ruleName = "Daily Investment"
            conditions = mutableListOf(
                RuleCondition(
                    field = TransactionField.TRANSACTION_TIME,
                    operator = ConditionOperator.GREATER_THAN_OR_EQUAL,
                    value = "09:00"
                ),
                RuleCondition(
                    field = TransactionField.TRANSACTION_TIME,
                    operator = ConditionOperator.LESS_THAN,
                    value = "09:30"
                )
            )
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Investments"
                )
            )
        }
    )

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(if (isEditing) R.string.rules_edit_title else R.string.rules_create_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.rules_close))
                    }
                },
                actionContent = {
                    TextButton(
                        onClick = {
                            // Validate: rule name + all conditions have values + all actions are valid
                            val areConditionsValid = conditions.isNotEmpty() &&
                                conditions.all { it.validate() }
                            val isActionValid = actions.isNotEmpty() && actions.all { it.validate() }
                            val isValid = ruleName.isNotBlank() && areConditionsValid && isActionValid

                            if (isValid) {
                                val rule = TransactionRule(
                                    id = existingRule?.id ?: UUID.randomUUID().toString(),
                                    name = ruleName,
                                    description = description.takeIf { it.isNotBlank() },
                                    priority = existingRule?.priority ?: 100,
                                    conditions = conditions.toList(),
                                    actions = actions,
                                    isActive = existingRule?.isActive ?: true,
                                    isSystemTemplate = existingRule?.isSystemTemplate ?: false,
                                    createdAt = existingRule?.createdAt ?: System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSaveRule(rule)
                            }
                        },
                        enabled = ruleName.isNotBlank() &&
                                 conditions.isNotEmpty() &&
                                 conditions.all { it.validate() } &&
                                 actions.isNotEmpty() &&
                                 actions.all { it.validate() }
                    ) {
                        Text(stringResource(R.string.rules_save))
                    }
                },
                hazeState = hazeState
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(Dimensions.Padding.content)
                .imePadding()
                .overScrollVertical()
                .verticalScroll(rememberScrollState()),
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
                        text = stringResource(R.string.rules_quick_templates),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commonPresets.forEach { (label, action) ->
                            ElevatedAssistChip(
                                onClick = action,
                                label = { Text(stringResource(label), style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }
            }

            // Rule name and description
            TextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text(stringResource(R.string.rules_name_label)) },
                placeholder = { Text(stringResource(R.string.rules_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            TextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.rules_description_label)) },
                placeholder = { Text(stringResource(R.string.rules_description_placeholder)) },
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
                                text = stringResource(R.string.rules_when),
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
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.rules_add_condition))
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
                                // Header with logical-operator toggle and delete button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (index == 0) {
                                        Text(
                                            text = stringResource(R.string.rules_condition),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        LogicalOperatorToggle(
                                            selected = condition.logicalOperator,
                                            onSelect = { newOp ->
                                                conditions = conditions.toMutableList().apply {
                                                    set(index, condition.copy(logicalOperator = newOp))
                                                }
                                            }
                                        )
                                    }
                                    if (conditions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                conditions = conditions.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(Dimensions.Component.minTouchTarget)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.rules_remove_condition),
                                                modifier = Modifier.size(Dimensions.Icon.small),
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
                                    },
                                    allAccounts = allAccounts
                                )
                            }
                        }
                    }
                }
            }

            // Action section (supports multiple)
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
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.rules_then),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // BLOCK drops the transaction, so it's terminal — no further
                        // actions can run alongside it. Hide "Add Action" while one exists.
                        if (actions.none { it.actionType == ActionType.BLOCK }) {
                            TextButton(
                                onClick = {
                                    actions = actions + RuleAction(
                                        field = TransactionField.CATEGORY,
                                        actionType = ActionType.SET,
                                        value = ""
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(stringResource(R.string.rules_add_action))
                            }
                        }
                    }

                    // Display all actions
                    actions.forEachIndexed { index, action ->
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
                                        text = if (actions.size > 1) stringResource(R.string.rules_action_numbered, index + 1) else stringResource(R.string.rules_action),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (actions.size > 1) {
                                        IconButton(
                                            onClick = {
                                                actions = actions.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(Dimensions.Component.minTouchTarget)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.rules_remove_action),
                                                modifier = Modifier.size(Dimensions.Icon.small),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                // Per-action editor
                                ActionEditor(
                                    action = action,
                                    onActionChange = { updated ->
                                        // BLOCK is terminal — it drops the transaction, so the other
                                        // actions can't run. If the user switches to BLOCK while other
                                        // actions exist, confirm before discarding them (no silent
                                        // data loss); otherwise apply the change directly.
                                        if (updated.actionType == ActionType.BLOCK && actions.size > 1) {
                                            pendingBlockAction = updated
                                        } else {
                                            actions = actions.toMutableList().apply { set(index, updated) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Preview
            val showPreview = ruleName.isNotBlank() &&
                             conditions.isNotEmpty() &&
                             conditions.all { it.validate() } &&
                             actions.isNotEmpty() &&
                             actions.all { it.validate() }
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
                            text = stringResource(R.string.rules_preview_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = run {
                                val conditionTexts = conditions.map { condition ->
                                    val fieldName = stringResource(
                                        when (condition.field) {
                                            TransactionField.AMOUNT -> R.string.rules_preview_field_amount
                                            TransactionField.TYPE -> R.string.rules_preview_field_type
                                            TransactionField.CATEGORY -> R.string.rules_preview_field_category
                                            TransactionField.MERCHANT -> R.string.rules_preview_field_merchant
                                            TransactionField.NARRATION -> R.string.rules_preview_field_description
                                            TransactionField.SMS_TEXT -> R.string.rules_preview_field_sms_text
                                            TransactionField.BANK_NAME -> R.string.rules_preview_field_bank
                                            TransactionField.TRANSACTION_TIME -> R.string.rules_preview_field_time
                                            TransactionField.TRANSACTION_HOUR -> R.string.rules_preview_field_hour
                                            TransactionField.TRANSACTION_DAY_OF_WEEK -> R.string.rules_preview_field_day_of_week
                                            TransactionField.TRANSACTION_DAY_OF_MONTH -> R.string.rules_preview_field_day_of_month
                                            TransactionField.TRANSACTION_DATE -> R.string.rules_preview_field_date
                                            TransactionField.ACCOUNT -> R.string.rules_preview_field_account
                                            TransactionField.TAGS -> R.string.rules_preview_field_tags
                                        }
                                    )
                                    val operatorText = stringResource(
                                        when (condition.operator) {
                                            ConditionOperator.LESS_THAN -> R.string.rules_preview_op_before
                                            ConditionOperator.GREATER_THAN -> R.string.rules_preview_op_after
                                            ConditionOperator.LESS_THAN_OR_EQUAL -> R.string.rules_preview_op_at_or_before
                                            ConditionOperator.GREATER_THAN_OR_EQUAL -> R.string.rules_preview_op_at_or_after
                                            ConditionOperator.EQUALS -> R.string.rules_preview_op_is
                                            ConditionOperator.CONTAINS -> R.string.rules_preview_op_contains
                                            ConditionOperator.STARTS_WITH -> R.string.rules_preview_op_starts_with
                                            ConditionOperator.IN -> R.string.rules_preview_op_is_any_of
                                            ConditionOperator.NOT_EQUALS -> R.string.rules_preview_op_is_not
                                            else -> R.string.rules_preview_op_matches
                                        }
                                    )
                                    val valueText = when (condition.field) {
                                        TransactionField.TYPE -> ruleTransactionTypeLabel(condition.value)
                                        TransactionField.TRANSACTION_DAY_OF_WEEK ->
                                            condition.value.split(",").map { day ->
                                                RULE_DAYS_OF_WEEK.firstOrNull { it.first == day.trim() }
                                                    ?.let { stringResource(it.second) } ?: day
                                            }.joinToString(", ")
                                        TransactionField.ACCOUNT -> {
                                            val parts = condition.value.split("||")
                                            if (parts.size == 2) {
                                                AccountBalanceEntity.accountLabel(parts[0], parts[1])
                                            } else {
                                                condition.value
                                            }
                                        }
                                        else -> condition.value
                                    }
                                    stringResource(R.string.rules_preview_condition, fieldName, operatorText, valueText)
                                }
                                val actionTexts = actions.map { action ->
                                    if (action.actionType == ActionType.BLOCK) {
                                        stringResource(R.string.rules_preview_action_block)
                                    } else if (action.field == TransactionField.TAGS) {
                                        stringResource(
                                            if (action.actionType == ActionType.ADD_TAG) R.string.rules_preview_action_add_tag
                                            else R.string.rules_preview_action_remove_tag,
                                            action.value
                                        )
                                    } else {
                                        val fieldName = stringResource(
                                            when (action.field) {
                                                TransactionField.CATEGORY -> R.string.rules_preview_field_category
                                                TransactionField.MERCHANT -> R.string.rules_preview_field_merchant
                                                TransactionField.TYPE -> R.string.rules_preview_field_type
                                                TransactionField.NARRATION -> R.string.rules_preview_field_description
                                                TransactionField.BANK_NAME -> R.string.rules_preview_field_account
                                                else -> R.string.rules_preview_field_generic
                                            }
                                        )
                                        // Show user-friendly labels for transaction types in actions too
                                        val displayValue = if (action.field == TransactionField.TYPE) {
                                            ruleTransactionTypeLabel(action.value)
                                        } else {
                                            action.value
                                        }
                                        when (action.actionType) {
                                            ActionType.APPEND -> stringResource(R.string.rules_preview_action_append, displayValue, fieldName)
                                            ActionType.PREPEND -> stringResource(R.string.rules_preview_action_prepend, displayValue, fieldName)
                                            ActionType.CLEAR -> stringResource(R.string.rules_preview_action_clear, fieldName)
                                            else -> stringResource(R.string.rules_preview_action_set, fieldName, displayValue)
                                        }
                                    }
                                }
                                stringResource(
                                    R.string.rules_preview_sentence,
                                    conditionTexts.reduce { acc, text -> stringResource(R.string.rules_preview_conditions_and, acc, text) },
                                    actionTexts.reduce { acc, text -> stringResource(R.string.rules_preview_actions_and, acc, text) }
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    // Confirm before BLOCK discards the other (possibly filled-in) actions.
    if (pendingBlockAction != null) {
        AlertDialog(
            onDismissRequest = { pendingBlockAction = null },
            title = { Text(stringResource(R.string.rules_block_confirm_title)) },
            text = {
                Text(stringResource(R.string.rules_block_confirm_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    actions = listOf(pendingBlockAction!!)
                    pendingBlockAction = null
                }) { Text(stringResource(R.string.rules_block_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockAction = null }) { Text(stringResource(R.string.rules_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionFieldSelector(
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
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                    IconButton(onClick = { showTimePicker = true }) {
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
                        }) { Text(stringResource(R.string.rules_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.rules_cancel)) }
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

        TransactionField.ACCOUNT -> {
            // Account dropdown picker
            var accountDropdownExpanded by remember { mutableStateOf(false) }

            val selectedAccount = allAccounts.firstOrNull {
                "${it.bankName}||${it.accountLast4}" == condition.value
            }

            val displayText = selectedAccount?.let {
                it.displayName
            } ?: if (condition.value.isNotBlank()) {
                // Show raw value if account no longer exists
                condition.value
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
                        label = { Text(stringResource(R.string.rules_field_account)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                                    onConditionChange(condition.copy(value = key))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogicalOperatorToggle(
    selected: LogicalOperator,
    onSelect: (LogicalOperator) -> Unit
) {
    val options = listOf(LogicalOperator.AND, LogicalOperator.OR)
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, op ->
            SegmentedButton(
                selected = selected == op,
                onClick = { onSelect(op) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        text = stringResource(
                            if (op == LogicalOperator.AND) R.string.rules_logical_and else R.string.rules_logical_or
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    }
}

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
private fun ActionEditor(
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
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                                onClick = { onActionChange(action.copy(value = category)) },
                                label = { Text(category, style = MaterialTheme.typography.bodySmall) }
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