package com.pennywiseai.tracker.domain.model.rule

import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.serialization.Serializable

/** User-facing labels for transaction type values stored in rule conditions/actions. */
val RULE_TRANSACTION_TYPE_OPTIONS: List<Pair<String, String>> = listOf(
    TransactionType.INCOME.name to "Income",
    TransactionType.EXPENSE.name to "Expense",
    TransactionType.CREDIT.name to "Credit",
    TransactionType.TRANSFER.name to "Transfer",
    TransactionType.INVESTMENT.name to "Investment"
)

fun parseRuleTransactionType(value: String): TransactionType? {
    return runCatching { TransactionType.valueOf(value.trim().uppercase()) }.getOrNull()
}

private val TYPE_PRE_FILTER_OPERATORS = setOf(
    ConditionOperator.EQUALS,
    ConditionOperator.IN,
    ConditionOperator.NOT_EQUALS,
    ConditionOperator.NOT_IN
)

fun matchesTransactionTypeCondition(condition: RuleCondition, typeName: String): Boolean {
    return when (condition.operator) {
        ConditionOperator.EQUALS -> condition.value.equals(typeName, ignoreCase = true)
        ConditionOperator.IN -> condition.value.split(",")
            .map { it.trim() }
            .any { it.equals(typeName, ignoreCase = true) }
        ConditionOperator.NOT_EQUALS -> !condition.value.equals(typeName, ignoreCase = true)
        ConditionOperator.NOT_IN -> !condition.value.split(",")
            .map { it.trim() }
            .any { it.equals(typeName, ignoreCase = true) }
        else -> false
    }
}

/**
 * Pre-filter helper for rules scoped by transaction type. Rules whose TYPE conditions
 * use operators we cannot evaluate here are passed through for full evaluation.
 */
fun isRuleApplicableToTransactionType(rule: TransactionRule, type: TransactionType): Boolean {
    val hasTypeCondition = rule.conditions.any { it.field == TransactionField.TYPE }
    val hasOrLogic = rule.conditions.any { it.logicalOperator == LogicalOperator.OR }
    if (!hasTypeCondition || hasOrLogic) {
        return true
    }

    val typeConditions = rule.conditions.filter { it.field == TransactionField.TYPE }
    if (typeConditions.any { it.operator !in TYPE_PRE_FILTER_OPERATORS }) {
        return true
    }

    return typeConditions.any { matchesTransactionTypeCondition(it, type.name) }
}

fun TransactionField.defaultConditionOperator(): ConditionOperator = when (this) {
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

fun conditionOperatorsForField(field: TransactionField): List<Pair<ConditionOperator, String>> = when (field) {
    TransactionField.AMOUNT -> listOf(
        ConditionOperator.LESS_THAN to "<",
        ConditionOperator.GREATER_THAN to ">",
        ConditionOperator.EQUALS to "="
    )
    TransactionField.TYPE -> listOf(
        ConditionOperator.EQUALS to "is",
        ConditionOperator.NOT_EQUALS to "is not"
    )
    TransactionField.TRANSACTION_TIME -> listOf(
        ConditionOperator.LESS_THAN to "before",
        ConditionOperator.GREATER_THAN to "after",
        ConditionOperator.GREATER_THAN_OR_EQUAL to "at or after",
        ConditionOperator.LESS_THAN_OR_EQUAL to "at or before",
        ConditionOperator.EQUALS to "exactly at"
    )
    TransactionField.TRANSACTION_HOUR -> listOf(
        ConditionOperator.EQUALS to "is",
        ConditionOperator.LESS_THAN to "before",
        ConditionOperator.GREATER_THAN to "after"
    )
    TransactionField.TRANSACTION_DAY_OF_WEEK -> listOf(
        ConditionOperator.EQUALS to "is",
        ConditionOperator.IN to "is any of",
        ConditionOperator.NOT_EQUALS to "is not"
    )
    TransactionField.TRANSACTION_DAY_OF_MONTH -> listOf(
        ConditionOperator.EQUALS to "is",
        ConditionOperator.IN to "is any of",
        ConditionOperator.LESS_THAN to "before",
        ConditionOperator.GREATER_THAN to "after"
    )
    TransactionField.TRANSACTION_DATE -> listOf(
        ConditionOperator.EQUALS to "is",
        ConditionOperator.LESS_THAN to "before",
        ConditionOperator.GREATER_THAN to "after"
    )
    TransactionField.ACCOUNT -> listOf(
        ConditionOperator.EQUALS to "is",
        ConditionOperator.NOT_EQUALS to "is not"
    )
    else -> listOf(
        ConditionOperator.CONTAINS to "contains",
        ConditionOperator.EQUALS to "equals",
        ConditionOperator.STARTS_WITH to "starts with"
    )
}

fun ruleTransactionTypeLabel(value: String): String {
    return RULE_TRANSACTION_TYPE_OPTIONS.firstOrNull { it.first.equals(value, ignoreCase = true) }?.second
        ?: value
}

@Serializable
data class RuleCondition(
    val field: TransactionField,
    val operator: ConditionOperator,
    val value: String,
    val logicalOperator: LogicalOperator = LogicalOperator.AND
) {
    fun validate(): Boolean {
        return value.isNotBlank() && when (field) {
            TransactionField.AMOUNT -> {
                when (operator) {
                    ConditionOperator.LESS_THAN,
                    ConditionOperator.GREATER_THAN,
                    ConditionOperator.LESS_THAN_OR_EQUAL,
                    ConditionOperator.GREATER_THAN_OR_EQUAL -> value.toBigDecimalOrNull() != null
                    else -> true
                }
            }
            TransactionField.TRANSACTION_HOUR -> {
                val hour = value.toIntOrNull()
                hour != null && hour in 0..23
            }
            TransactionField.TRANSACTION_TIME -> {
                value.matches(Regex("""\d{2}:\d{2}"""))
            }
            TransactionField.TRANSACTION_DAY_OF_WEEK -> {
                when (operator) {
                    ConditionOperator.IN, ConditionOperator.NOT_IN ->
                        value.split(",").all { it.trim().toIntOrNull()?.let { d -> d in 1..7 } == true }
                    else -> value.toIntOrNull()?.let { it in 1..7 } == true
                }
            }
            TransactionField.TRANSACTION_DAY_OF_MONTH -> {
                when (operator) {
                    ConditionOperator.IN, ConditionOperator.NOT_IN ->
                        value.split(",").all { it.trim().toIntOrNull()?.let { d -> d in 1..31 } == true }
                    else -> value.toIntOrNull()?.let { it in 1..31 } == true
                }
            }
            TransactionField.ACCOUNT -> {
                val parts = value.split("||")
                parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
            }
            TransactionField.TYPE -> parseRuleTransactionType(value) != null
            else -> true
        }
    }
}

@Serializable
enum class TransactionField {
    AMOUNT,                  // Transaction amount
    TYPE,                    // INCOME, EXPENSE, or TRANSFER
    CATEGORY,                // Transaction category
    MERCHANT,                // Merchant/vendor name
    NARRATION,               // Description/notes
    SMS_TEXT,                // Original SMS text
    BANK_NAME,               // Bank name from SMS
    TRANSACTION_HOUR,        // Hour (00-23)
    TRANSACTION_TIME,        // Time as HH:mm
    TRANSACTION_DAY_OF_WEEK, // 1=Monday .. 7=Sunday
    TRANSACTION_DAY_OF_MONTH,// 01-31
    TRANSACTION_DATE,        // yyyy-MM-dd
    /**
     * Composite key "BankName||Last4" scoping rules to a specific account.
     * Supports only [ConditionOperator.EQUALS] and [ConditionOperator.NOT_EQUALS].
     */
    ACCOUNT
}

@Serializable
enum class ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    LESS_THAN,
    GREATER_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN_OR_EQUAL,
    IN,
    NOT_IN,
    REGEX_MATCHES,
    IS_EMPTY,
    IS_NOT_EMPTY
}

@Serializable
enum class LogicalOperator {
    AND,
    OR
}