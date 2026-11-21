package com.pennywiseai.tracker.presentation.rules

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.parseRuleAccountKey
import com.pennywiseai.tracker.ui.icons.categoryLabel

/*
 * A rule in words, shared by the rule editor's preview and the Rules list's
 * WHEN / THEN rows, so both describe a rule the same way.
 */

/** The rule's conditions as one phrase ("merchant contains Uber OR …"); null when it has none. */
@Composable
internal fun ruleConditionsText(conditions: List<RuleCondition>): String? {
    if (conditions.isEmpty()) return null
    val texts = conditions.map { ruleConditionText(it) }
    return joinPreviewConditions(
        texts,
        conditions.map { it.logicalOperator },
        andFormat = stringResource(R.string.rules_preview_conditions_and),
        orFormat = stringResource(R.string.rules_preview_conditions_or)
    )
}

/** The rule's actions as one phrase ("set category to Food, and add tag x"); null when it has none. */
@Composable
internal fun ruleActionsText(actions: List<RuleAction>): String? {
    if (actions.isEmpty()) return null
    val andFormat = stringResource(R.string.rules_preview_actions_and)
    return actions.map { ruleActionText(it) }.reduce { acc, text -> andFormat.format(acc, text) }
}

@Composable
private fun ruleConditionText(condition: RuleCondition): String {
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
    return stringResource(R.string.rules_preview_condition, fieldName, operatorText, valueText)
}

@Composable
private fun ruleActionText(action: RuleAction): String {
    if (action.actionType == ActionType.BLOCK) {
        return stringResource(R.string.rules_preview_action_block)
    }
    if (action.field == TransactionField.TAGS) {
        return stringResource(
            if (action.actionType == ActionType.ADD_TAG) R.string.rules_preview_action_add_tag
            else R.string.rules_preview_action_remove_tag,
            action.value
        )
    }
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
    // User-facing labels for transaction types, categories and accounts.
    val displayValue = when (action.field) {
        TransactionField.TYPE -> ruleTransactionTypeLabel(action.value)
        TransactionField.CATEGORY -> categoryLabel(action.value)
        TransactionField.TRANSFER_FROM_ACCOUNT,
        TransactionField.TRANSFER_TO_ACCOUNT ->
            parseRuleAccountKey(action.value)
                ?.let { (bank, last4) -> AccountBalanceEntity.accountLabel(bank, last4) }
                ?: action.value
        else -> action.value
    }
    return when (action.actionType) {
        ActionType.APPEND -> stringResource(R.string.rules_preview_action_append, displayValue, fieldName)
        ActionType.PREPEND -> stringResource(R.string.rules_preview_action_prepend, displayValue, fieldName)
        ActionType.CLEAR -> stringResource(R.string.rules_preview_action_clear, fieldName)
        else -> stringResource(R.string.rules_preview_action_set, fieldName, displayValue)
    }
}
