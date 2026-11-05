package com.pennywiseai.tracker.ui.screens.rules

import com.pennywiseai.tracker.ui.theme.pennyWiseButtonShapes
import com.pennywiseai.tracker.presentation.add.AddSaveBar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.pennywiseai.tracker.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import com.pennywiseai.tracker.domain.model.rule.*
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.FinancialAccountIdentity
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.viewmodel.RulesViewModel
import com.pennywiseai.tracker.ui.theme.Spacing
import java.util.UUID
import com.pennywiseai.tracker.ui.icons.categoryLabel

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
 * operator reads differently per field ("less than" for an amount, "before" for a time).
 */
private fun conditionOperatorsForField(
    field: TransactionField
): List<Pair<ConditionOperator, Int>> =
    supportedOperators(field).map { operator -> operator to operator.labelFor(field) }

@StringRes
private fun ConditionOperator.labelFor(field: TransactionField): Int = when (field) {
    TransactionField.AMOUNT -> when (this) {
        ConditionOperator.LESS_THAN -> R.string.rules_op_less_than
        ConditionOperator.GREATER_THAN -> R.string.rules_op_greater_than
        else -> R.string.rules_op_equals
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

private val COMMON_RULE_CATEGORIES = listOf(
    "Food & Dining", "Transportation", "Shopping",
    "Bills & Utilities", "Entertainment", "Healthcare",
    "Investments", "Others"
)

/** A value that isn't a shortcut always shows its text field: Save would use it, so it can't be hidden. */
internal fun isCustomRuleCategory(value: String, otherChosen: Boolean): Boolean =
    otherChosen || (value.isNotBlank() && value !in COMMON_RULE_CATEGORIES)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    // Rule name + all conditions have values + all actions are valid
    val isRuleValid = ruleName.isNotBlank() &&
        conditions.isNotEmpty() &&
        conditions.all { it.validate() } &&
        actions.isNotEmpty() &&
        actions.all { it.validate() }

    var templatesExpanded by rememberSaveable { mutableStateOf(false) }

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
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.rules_close))
                    }
                },
            )
        },
        // The same footer as Add transaction: pinned, and saying what Save is waiting for.
        bottomBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
                AddSaveBar(
                    hint = if (isRuleValid) null else stringResource(R.string.rules_save_hint),
                    enabled = isRuleValid,
                    isLoading = false,
                    onClick = {
                        if (isRuleValid) {
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
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .imePadding()
                .overScrollVertical()
                .verticalScroll(rememberScrollState())
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Templates stay out of the way until asked for
            GroupedList {
                GroupedRow(
                    position = if (templatesExpanded) ListItemPosition.Top else ListItemPosition.Single,
                    onClick = { templatesExpanded = !templatesExpanded }
                ) {
                    RowLabels(
                        title = stringResource(R.string.rules_quick_templates),
                        titleMaxLines = Int.MAX_VALUE
                    )
                    Icon(
                        if (templatesExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = stringResource(
                            if (templatesExpanded) R.string.rules_templates_hide else R.string.rules_templates_show
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(
                    visible = templatesExpanded,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
                ) {
                    GroupedColumn(position = ListItemPosition.Bottom) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            commonPresets.forEach { (label, applyPreset) ->
                                AssistChip(
                                    onClick = {
                                        applyPreset()
                                        templatesExpanded = false
                                    },
                                    label = { Text(stringResource(label)) }
                                )
                            }
                        }
                    }
                }
            }

            // Rule name and description
            RuleTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                label = { Text(stringResource(R.string.rules_name_label)) },
                placeholder = { Text(stringResource(R.string.rules_name_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            RuleTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.rules_description_label)) },
                placeholder = { Text(stringResource(R.string.rules_description_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            // Conditions section (supports multiple)
            RuleSection(
                title = stringResource(R.string.rules_when),
                action = {
                    TextButton(
                        onClick = {
                            conditions = (conditions + RuleCondition(
                                field = TransactionField.AMOUNT,
                                operator = ConditionOperator.LESS_THAN,
                                value = ""
                            )).toMutableList()
                        },
                        shapes = pennyWiseButtonShapes()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.rules_add_condition))
                    }
                }
            ) {
                // Display all conditions
                conditions.forEachIndexed { index, condition ->
                    if (index > 0) HorizontalDivider()

                    // Only multiple conditions need a heading, a connector or a remove button
                    if (conditions.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index == 0) {
                                Text(
                                    text = stringResource(R.string.rules_condition),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            IconButton(
                                onClick = {
                                    conditions = conditions.toMutableList().apply { removeAt(index) }
                                },
                                modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
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

            // Action section (supports multiple)
            RuleSection(
                title = stringResource(R.string.rules_then),
                // BLOCK drops the transaction, so it's terminal — no further
                // actions can run alongside it. Hide "Add Action" while one exists.
                action = if (actions.none { it.actionType == ActionType.BLOCK }) {
                    {
                        TextButton(
                            onClick = {
                                actions = actions + RuleAction(
                                    field = TransactionField.CATEGORY,
                                    actionType = ActionType.SET,
                                    value = ""
                                )
                            },
                            shapes = pennyWiseButtonShapes()
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.rules_add_action))
                        }
                    }
                } else {
                    null
                }
            ) {
                // Display all actions
                actions.forEachIndexed { index, action ->
                    if (index > 0) HorizontalDivider()

                    // Only multiple actions need a heading or a remove button
                    if (actions.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.rules_action_numbered, index + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    actions = actions.toMutableList().apply { removeAt(index) }
                                },
                                modifier = Modifier.size(Dimensions.Component.minTouchTarget),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
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

            // Preview
            if (isRuleValid) {
                PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = stringResource(R.string.rules_preview_title),
                            style = PennyWiseText.sectionHeader,
                            color = MaterialTheme.colorScheme.onSurface
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
                                                else -> R.string.rules_preview_field_generic
                                            }
                                        )
                                        // Show user-friendly labels for transaction types in actions too
                                        val displayValue = if (action.field == TransactionField.TYPE) {
                                            ruleTransactionTypeLabel(action.value)
                                        } else if (action.field == TransactionField.CATEGORY) {
                                            categoryLabel(action.value)
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.rules_block_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockAction = null }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.rules_cancel)) }
            }
        )
    }
}

/** One surface per When/Then section: a heading, then its fields directly inside. */
@Composable
private fun RuleSection(
    title: String,
    action: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionHeaderV2(title = title, action = action, topSpacing = Spacing.none)
            content()
        }
    }
}

/**
 * The one field style on this screen, matching the filled, borderless fields
 * Settings uses: a step up in tone from the section behind it, no underline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    label: @Composable () -> Unit,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
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
        RuleTextField(
            value = stringResource(fieldOptions.firstOrNull { it.first == condition.field }?.second ?: R.string.rules_field_amount),
            onValueChange = { },
            readOnly = true,
            label = { Text(stringResource(R.string.rules_condition_field_label)) },
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

    // Operator selector
    val operators = conditionOperatorsForField(condition.field)

    ConnectedChoiceRow(
        options = operators.map { it.first },
        selected = condition.operator,
        onSelect = { op -> onConditionChange(condition.copy(operator = op)) },
        label = { op -> stringResource(operators.first { it.first == op }.second) },
        // Up to five multi-word labels: they flow rather than squeezing into one line.
        wrap = true
    )

    // Value input
    when (condition.field) {
        TransactionField.TYPE -> {
            Text(
                text = stringResource(R.string.rules_select_transaction_type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        label = { Text(stringResource(displayLabel)) }
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
                            label = { Text(stringResource(label)) }
                        )
                    }
                } else {
                    days.forEach { (value, label) ->
                        FilterChip(
                            selected = condition.value == value,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = { onConditionChange(condition.copy(value = value)) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }

        TransactionField.TRANSACTION_DAY_OF_MONTH -> {
            RuleTextField(
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

            RuleTextField(
                value = condition.value,
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.rules_time_label)) },
                placeholder = { Text(stringResource(R.string.rules_time_placeholder)) },
                trailingIcon = {
                    IconButton(onClick = { showTimePicker = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = stringResource(R.string.rules_pick_time))
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
                        }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.rules_ok)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }, shapes = pennyWiseButtonShapes()) { Text(stringResource(R.string.rules_cancel)) }
                    },
                    text = { TimePicker(state = timePickerState) }
                )
            }
        }

        TransactionField.TRANSACTION_HOUR -> {
            RuleTextField(
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
            RuleTextField(
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
                    RuleTextField(
                        value = displayText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rules_field_account)) },
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
                                            Text(
                                                text = accountTypeLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            RuleTextField(
                value = condition.value,
                onValueChange = { onConditionChange(condition.copy(value = it)) },
                // The engine compares the raw number, whatever the transaction's currency.
                label = {
                    Text(
                        stringResource(
                            if (condition.field == TransactionField.AMOUNT) {
                                R.string.rules_value_label_amount
                            } else {
                                R.string.rules_value_label
                            }
                        )
                    )
                },
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
        fillMaxWidth = false
    )
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
            RuleTextField(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Outlined.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.rules_block_explainer),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Action field selector for non-BLOCK actions
            ExposedDropdownMenuBox(
                expanded = actionFieldDropdownExpanded,
                onExpandedChange = { actionFieldDropdownExpanded = !actionFieldDropdownExpanded }
            ) {
                RuleTextField(
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
                    label = {
                        Text(
                            stringResource(
                                if (action.actionType == ActionType.SET) {
                                    R.string.rules_action_field_label_set
                                } else {
                                    R.string.rules_action_field_label_change
                                }
                            )
                        )
                    },
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
                    // The text field is for a category that isn't one of the shortcuts. Always
                    // on screen, it read as a second, competing way to pick the same thing.
                    // Derived from the value because this editor outlives its action: a saved rule
                    // arrives after the first frame, and removing an action shifts the next into it.
                    var otherChosen by remember { mutableStateOf(false) }
                    val customCategory = isCustomRuleCategory(action.value, otherChosen)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        COMMON_RULE_CATEGORIES.forEach { category ->
                            FilterChip(
                                selected = !customCategory && action.value == category,
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                onClick = {
                                    otherChosen = false
                                    onActionChange(action.copy(value = category))
                                },
                                label = { Text(categoryLabel(category)) }
                            )
                        }
                        FilterChip(
                            selected = customCategory,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = {
                                // A shortcut's name isn't a starting point for a new category.
                                if (!customCategory) onActionChange(action.copy(value = ""))
                                otherChosen = true
                            },
                            label = { Text(stringResource(R.string.rules_category_other)) }
                        )
                    }

                    if (customCategory) {
                        RuleTextField(
                            value = action.value,
                            onValueChange = {
                                // Keeps the field up while typing through "" or "Shopping".
                                otherChosen = true
                                onActionChange(action.copy(value = it))
                            },
                            label = { Text(stringResource(R.string.rules_category_name_label)) },
                            placeholder = { Text(stringResource(R.string.rules_category_name_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                TransactionField.TYPE -> {
                    // Transaction type chips with user-friendly labels
                    Text(
                        text = stringResource(R.string.rules_select_transaction_type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                label = { Text(stringResource(displayLabel)) }
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
                                onClick = { onActionChange(action.copy(value = merchant)) },
                                label = { Text(merchant) }
                            )
                        }
                    }

                    RuleTextField(
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
                    RuleTextField(
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
                    RuleTextField(
                        value = action.value,
                        onValueChange = { onActionChange(action.copy(value = it)) },
                        label = { Text(stringResource(R.string.rules_account_bank_label)) },
                        placeholder = { Text(stringResource(R.string.rules_value_placeholder_bank)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                TransactionField.TAGS -> {
                    RuleTextField(
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
                    RuleTextField(
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
