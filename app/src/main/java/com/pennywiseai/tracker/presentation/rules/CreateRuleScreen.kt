package com.pennywiseai.tracker.presentation.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
internal val RULE_DAYS_OF_WEEK: List<Pair<String, Int>> = listOf(
    "1" to R.string.rules_day_mon,
    "2" to R.string.rules_day_tue,
    "3" to R.string.rules_day_wed,
    "4" to R.string.rules_day_thu,
    "5" to R.string.rules_day_fri,
    "6" to R.string.rules_day_sat,
    "7" to R.string.rules_day_sun
)

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
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
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
                            actions.all { it.validate() },
                        shapes = ButtonDefaults.shapes()
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            },
                            shapes = ButtonDefaults.shapes()
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
                                            modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                                            shapes = IconButtonDefaults.shapes()
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                },
                                shapes = ButtonDefaults.shapes()
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
                                            modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                                            shapes = IconButtonDefaults.shapes()
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
                                    allAccounts = allAccounts,
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
                                            TransactionField.TRANSFER_FROM_ACCOUNT -> R.string.rules_preview_field_transfer_from_account
                                            TransactionField.TRANSFER_TO_ACCOUNT -> R.string.rules_preview_field_transfer_to_account
                                        }
                                    )
                                    val operatorText = stringResource(
                                        when {
                                            // before/after reads as time; amounts compare as numbers.
                                            condition.field == TransactionField.AMOUNT && condition.operator == ConditionOperator.LESS_THAN -> R.string.rules_preview_op_less_than
                                            condition.field == TransactionField.AMOUNT && condition.operator == ConditionOperator.GREATER_THAN -> R.string.rules_preview_op_greater_than
                                            else -> when (condition.operator) {
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
                                        }
                                    )
                                    val valueText = when (condition.field) {
                                        TransactionField.TYPE -> ruleTransactionTypeLabel(condition.value)
                                        TransactionField.CATEGORY -> categoryLabel(condition.value)
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
                                                TransactionField.TRANSFER_FROM_ACCOUNT -> R.string.rules_preview_field_transfer_from_account
                                                TransactionField.TRANSFER_TO_ACCOUNT -> R.string.rules_preview_field_transfer_to_account
                                                else -> R.string.rules_preview_field_generic
                                            }
                                        )
                                        // Show user-friendly labels for transaction types in actions too
                                        val displayValue = if (action.field == TransactionField.TYPE) {
                                            ruleTransactionTypeLabel(action.value)
                                        } else if (action.field == TransactionField.CATEGORY) {
                                            categoryLabel(action.value)
                                        } else if (action.field == TransactionField.TRANSFER_FROM_ACCOUNT ||
                                            action.field == TransactionField.TRANSFER_TO_ACCOUNT
                                        ) {
                                            parseRuleAccountKey(action.value)
                                                ?.let { (bank, last4) -> AccountBalanceEntity.accountLabel(bank, last4) }
                                                ?: action.value
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
                                    joinPreviewConditions(
                                        conditionTexts,
                                        conditions.map { it.logicalOperator },
                                        andFormat = stringResource(R.string.rules_preview_conditions_and),
                                        orFormat = stringResource(R.string.rules_preview_conditions_or)
                                    ),
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
                }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_block_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockAction = null }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.rules_cancel)) }
            }
        )
    }
}

@Composable
private fun LogicalOperatorToggle(
    selected: LogicalOperator,
    onSelect: (LogicalOperator) -> Unit
) {
    ConnectedChoiceRow(
        options = listOf(LogicalOperator.AND, LogicalOperator.OR),
        selected = selected,
        onSelect = onSelect,
        label = { op ->
            stringResource(
                if (op == LogicalOperator.AND) R.string.rules_logical_and else R.string.rules_logical_or
            )
        },
        fillMaxWidth = false,
        textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    )
}

/**
 * Joins condition phrases the way [com.pennywiseai.tracker.domain.service.RuleEngine] evaluates
 * them: left to right, no precedence. When the connector changes, what came before is
 * bracketed, so "A OR B AND C" reads "(A OR B) AND C".
 */
internal fun joinPreviewConditions(
    texts: List<String>,
    operators: List<LogicalOperator>,
    andFormat: String,
    orFormat: String
): String {
    var result = texts.first()
    for (i in 1 until texts.size) {
        if (i >= 2 && operators[i] != operators[i - 1]) result = "($result)"
        result = (if (operators[i] == LogicalOperator.OR) orFormat else andFormat).format(result, texts[i])
    }
    return result
}
