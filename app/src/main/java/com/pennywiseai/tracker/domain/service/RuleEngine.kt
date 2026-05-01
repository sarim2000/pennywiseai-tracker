package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.rule.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleEngine @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun evaluateRules(
        transaction: TransactionEntity,
        smsText: String?,
        rules: List<TransactionRule>
    ): Pair<TransactionEntity, List<RuleApplication>> {
        val sortedRules = rules
            .filter { it.isActive }
            .sortedBy { it.priority }

        var modifiedTransaction = transaction
        val applications = mutableListOf<RuleApplication>()

        for (rule in sortedRules) {
            if (evaluateConditions(modifiedTransaction, smsText, rule.conditions)) {
                val (newTransaction, fieldMods) = applyActions(modifiedTransaction, rule.actions)

                if (fieldMods.isNotEmpty()) {
                    modifiedTransaction = newTransaction
                    applications.add(
                        RuleApplication(
                            ruleId = rule.id,
                            ruleName = rule.name,
                            transactionId = transaction.id.toString(),
                            fieldsModified = fieldMods,
                            appliedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }

        return modifiedTransaction to applications
    }

    /**
     * Evaluates rules for a specific transaction type.
     * This is a convenience method that pre-filters rules based on transaction type.
     */
    fun evaluateRulesForType(
        transaction: TransactionEntity,
        smsText: String?,
        rules: List<TransactionRule>,
        type: TransactionType
    ): Pair<TransactionEntity, List<RuleApplication>> {
        // Pre-filter rules that apply to this transaction type. This is an optimization
        // for the common AND-only case; rules that mix in any OR logic are passed through
        // since a TYPE condition that fails on its own can still let the rule match via
        // an OR'd sibling.
        val applicableRules = rules.filter { rule ->
            val hasTypeCondition = rule.conditions.any { it.field == TransactionField.TYPE }
            val hasOrLogic = rule.conditions.any { it.logicalOperator == LogicalOperator.OR }
            if (!hasTypeCondition || hasOrLogic) {
                true
            } else {
                rule.conditions.any { condition ->
                    condition.field == TransactionField.TYPE &&
                    when (condition.operator) {
                        ConditionOperator.EQUALS -> condition.value.equals(type.name, ignoreCase = true)
                        ConditionOperator.IN -> condition.value.split(",")
                            .map { it.trim() }
                            .any { it.equals(type.name, ignoreCase = true) }
                        ConditionOperator.NOT_EQUALS -> !condition.value.equals(type.name, ignoreCase = true)
                        ConditionOperator.NOT_IN -> !condition.value.split(",")
                            .map { it.trim() }
                            .any { it.equals(type.name, ignoreCase = true) }
                        else -> false
                    }
                }
            }
        }

        return evaluateRules(transaction, smsText, applicableRules)
    }

    /**
     * Checks if a transaction should be blocked based on blocking rules.
     * Returns the first blocking rule that matches, or null if no blocking rules match.
     */
    fun shouldBlockTransaction(
        transaction: TransactionEntity,
        smsText: String?,
        rules: List<TransactionRule>
    ): TransactionRule? {
        // Filter for rules that have a BLOCK action
        val blockingRules = rules
            .filter { it.isActive }
            .filter { rule ->
                rule.actions.any { action -> action.actionType == ActionType.BLOCK }
            }
            .sortedBy { it.priority }

        // Return the first rule that matches the conditions
        for (rule in blockingRules) {
            if (evaluateConditions(transaction, smsText, rule.conditions)) {
                return rule
            }
        }

        return null
    }

    private fun evaluateConditions(
        transaction: TransactionEntity,
        smsText: String?,
        conditions: List<RuleCondition>
    ): Boolean {
        if (conditions.isEmpty()) return false

        // Each condition (after the first) carries a logicalOperator describing how it
        // combines with the running result so far. Evaluation is left-to-right with no
        // precedence — "A AND B OR C" reads as "(A AND B) OR C" — which matches the
        // most common user intent of "either these together, or that".
        var result = evaluateCondition(transaction, smsText, conditions.first())
        for (i in 1 until conditions.size) {
            val condition = conditions[i]
            val condResult = evaluateCondition(transaction, smsText, condition)
            result = when (condition.logicalOperator) {
                LogicalOperator.AND -> result && condResult
                LogicalOperator.OR -> result || condResult
            }
        }
        return result
    }

    private fun evaluateCondition(
        transaction: TransactionEntity,
        smsText: String?,
        condition: RuleCondition
    ): Boolean {
        val fieldValue = getFieldValue(transaction, smsText, condition.field)

        return when (condition.operator) {
            ConditionOperator.EQUALS -> fieldValue.equals(condition.value, ignoreCase = true)
            ConditionOperator.NOT_EQUALS -> !fieldValue.equals(condition.value, ignoreCase = true)
            ConditionOperator.CONTAINS -> fieldValue.contains(condition.value, ignoreCase = true)
            ConditionOperator.NOT_CONTAINS -> !fieldValue.contains(condition.value, ignoreCase = true)
            ConditionOperator.STARTS_WITH -> fieldValue.startsWith(condition.value, ignoreCase = true)
            ConditionOperator.ENDS_WITH -> fieldValue.endsWith(condition.value, ignoreCase = true)
            ConditionOperator.LESS_THAN -> compareNumeric(fieldValue, condition.value) < 0
            ConditionOperator.GREATER_THAN -> compareNumeric(fieldValue, condition.value) > 0
            ConditionOperator.LESS_THAN_OR_EQUAL -> compareNumeric(fieldValue, condition.value) <= 0
            ConditionOperator.GREATER_THAN_OR_EQUAL -> compareNumeric(fieldValue, condition.value) >= 0
            ConditionOperator.IN -> condition.value.split(",").map { it.trim() }
                .any { fieldValue.equals(it, ignoreCase = true) }
            ConditionOperator.NOT_IN -> !condition.value.split(",").map { it.trim() }
                .any { fieldValue.equals(it, ignoreCase = true) }
            ConditionOperator.REGEX_MATCHES -> fieldValue.matches(Regex(condition.value))
            ConditionOperator.IS_EMPTY -> fieldValue.isBlank()
            ConditionOperator.IS_NOT_EMPTY -> fieldValue.isNotBlank()
        }
    }

    private fun getFieldValue(
        transaction: TransactionEntity,
        smsText: String?,
        field: TransactionField
    ): String {
        return when (field) {
            TransactionField.AMOUNT -> transaction.amount.toString()
            TransactionField.TYPE -> transaction.transactionType.name
            TransactionField.CATEGORY -> transaction.category ?: ""
            TransactionField.MERCHANT -> transaction.merchantName
            TransactionField.NARRATION -> transaction.description ?: ""
            TransactionField.SMS_TEXT -> smsText ?: ""
            TransactionField.BANK_NAME -> transaction.bankName ?: ""
            TransactionField.TRANSACTION_HOUR ->
                String.format("%02d", transaction.dateTime.hour)
            TransactionField.TRANSACTION_TIME ->
                String.format("%02d:%02d", transaction.dateTime.hour, transaction.dateTime.minute)
            TransactionField.TRANSACTION_DAY_OF_WEEK ->
                transaction.dateTime.dayOfWeek.value.toString()
            TransactionField.TRANSACTION_DAY_OF_MONTH ->
                String.format("%02d", transaction.dateTime.dayOfMonth)
            TransactionField.TRANSACTION_DATE ->
                transaction.dateTime.toLocalDate().toString()
        }
    }

    private fun compareNumeric(value1: String, value2: String): Int {
        return try {
            BigDecimal(value1).compareTo(BigDecimal(value2))
        } catch (e: NumberFormatException) {
            value1.compareTo(value2)
        }
    }

    private fun applyActions(
        transaction: TransactionEntity,
        actions: List<RuleAction>
    ): Pair<TransactionEntity, List<FieldModification>> {
        var modifiedTransaction = transaction
        val modifications = mutableListOf<FieldModification>()

        for (action in actions) {
            // Skip BLOCK actions as they don't modify the transaction
            if (action.actionType == ActionType.BLOCK) {
                continue
            }

            val oldValue = getFieldValue(modifiedTransaction, null, action.field)
            val (newTransaction, newValue) = applyAction(modifiedTransaction, action)

            if (oldValue != newValue) {
                modifiedTransaction = newTransaction
                modifications.add(
                    FieldModification(
                        field = action.field,
                        oldValue = oldValue.takeIf { it.isNotBlank() },
                        newValue = newValue,
                        actionType = action.actionType
                    )
                )
            }
        }

        return modifiedTransaction to modifications
    }

    private fun applyAction(
        transaction: TransactionEntity,
        action: RuleAction
    ): Pair<TransactionEntity, String> {
        return when (action.field) {
            TransactionField.CATEGORY -> {
                val newValue = when (action.actionType) {
                    ActionType.SET -> action.value
                    ActionType.CLEAR -> ""
                    else -> transaction.category ?: ""
                }
                transaction.copy(category = newValue) to newValue
            }
            TransactionField.MERCHANT -> {
                val newValue = when (action.actionType) {
                    ActionType.SET -> action.value
                    ActionType.APPEND -> "${transaction.merchantName}${action.value}"
                    ActionType.PREPEND -> "${action.value}${transaction.merchantName}"
                    ActionType.CLEAR -> ""
                    else -> transaction.merchantName
                }
                transaction.copy(merchantName = newValue) to newValue
            }
            TransactionField.NARRATION -> {
                val newValue = when (action.actionType) {
                    ActionType.SET -> action.value
                    ActionType.APPEND -> "${transaction.description ?: ""}${action.value}"
                    ActionType.PREPEND -> "${action.value}${transaction.description ?: ""}"
                    ActionType.CLEAR -> ""
                    else -> transaction.description ?: ""
                }
                transaction.copy(description = newValue) to newValue
            }
            TransactionField.TYPE -> {
                val newType = when (action.actionType) {
                    ActionType.SET -> {
                        // Convert string value to TransactionType enum
                        try {
                            TransactionType.valueOf(action.value.uppercase())
                        } catch (e: Exception) {
                            transaction.transactionType
                        }
                    }
                    else -> transaction.transactionType
                }
                transaction.copy(transactionType = newType) to newType.name
            }
            else -> transaction to getFieldValue(transaction, null, action.field)
        }
    }

    fun serializeConditions(conditions: List<RuleCondition>): String {
        return json.encodeToString(conditions)
    }

    fun deserializeConditions(conditionsJson: String): List<RuleCondition> {
        return try {
            json.decodeFromString(conditionsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeActions(actions: List<RuleAction>): String {
        return json.encodeToString(actions)
    }

    fun deserializeActions(actionsJson: String): List<RuleAction> {
        return try {
            json.decodeFromString(actionsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeFieldModifications(modifications: List<FieldModification>): String {
        return json.encodeToString(modifications)
    }

    fun deserializeFieldModifications(modificationsJson: String): List<FieldModification> {
        return try {
            json.decodeFromString(modificationsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}