package com.pennywiseai.tracker.domain.model.rule

import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.serialization.Serializable

/**
 * Parses a stored [TransactionField.TYPE] value (an enum name like "EXPENSE") into a
 * [TransactionType], or null if it doesn't map to a known type. Case/whitespace tolerant
 * so older or hand-edited values still resolve.
 */
fun parseRuleTransactionType(value: String): TransactionType? =
    runCatching { TransactionType.valueOf(value.trim().uppercase()) }.getOrNull()

/**
 * Operators for a [TransactionField.TYPE] condition that we can evaluate purely from the
 * transaction's type, without running the full rule. Anything outside this set is passed
 * through for full evaluation (see [isRuleApplicableToTransactionType]).
 */
private val TYPE_PRE_FILTER_OPERATORS = setOf(
    ConditionOperator.EQUALS,
    ConditionOperator.IN,
    ConditionOperator.NOT_EQUALS,
    ConditionOperator.NOT_IN
)

/** Whether a single TYPE condition matches [typeName] (an enum name), for the pre-filter operators. */
fun matchesTransactionTypeCondition(condition: RuleCondition, typeName: String): Boolean =
    when (condition.operator) {
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

/**
 * Pre-filter for rules scoped by transaction type. Returns true when the rule *could* match a
 * transaction of [type], so callers can cheaply narrow rules before full evaluation.
 *
 * Conservatively passes a rule through (returns true) when it has no TYPE condition, mixes in
 * any OR logic (a failing TYPE condition can still match via an OR'd sibling), or uses a TYPE
 * operator we can't evaluate here — those are decided by the full [RuleEngine] evaluation.
 */
fun isRuleApplicableToTransactionType(rule: TransactionRule, type: TransactionType): Boolean {
    val typeConditions = rule.conditions.filter { it.field == TransactionField.TYPE }
    val hasOrLogic = rule.conditions.any { it.logicalOperator == LogicalOperator.OR }
    if (typeConditions.isEmpty() || hasOrLogic) return true

    if (typeConditions.any { it.operator !in TYPE_PRE_FILTER_OPERATORS }) return true

    return typeConditions.any { matchesTransactionTypeCondition(it, type.name) }
}

/**
 * The operators that mean anything for [field], in the order the rule editor
 * offers them (the first is the field's default).
 *
 * This is the single source of truth for the pairing: the create screen builds
 * its picker from it, and the rule importer checks arbitrary file content
 * against it. A comparison like MERCHANT / GREATER_THAN can't be produced by the
 * pickers, but a hand-written or hand-edited import file can carry one, and it
 * would persist as a rule that never fires.
 */
fun supportedOperators(field: TransactionField): List<ConditionOperator> = when (field) {
    TransactionField.AMOUNT -> listOf(
        ConditionOperator.LESS_THAN,
        ConditionOperator.GREATER_THAN,
        ConditionOperator.EQUALS
    )
    TransactionField.TYPE -> listOf(
        ConditionOperator.EQUALS,
        ConditionOperator.NOT_EQUALS
    )
    TransactionField.TRANSACTION_TIME -> listOf(
        ConditionOperator.LESS_THAN,
        ConditionOperator.GREATER_THAN,
        ConditionOperator.GREATER_THAN_OR_EQUAL,
        ConditionOperator.LESS_THAN_OR_EQUAL,
        ConditionOperator.EQUALS
    )
    TransactionField.TRANSACTION_HOUR -> listOf(
        ConditionOperator.EQUALS,
        ConditionOperator.LESS_THAN,
        ConditionOperator.GREATER_THAN
    )
    TransactionField.TRANSACTION_DAY_OF_WEEK -> listOf(
        ConditionOperator.EQUALS,
        ConditionOperator.IN,
        ConditionOperator.NOT_EQUALS
    )
    TransactionField.TRANSACTION_DAY_OF_MONTH -> listOf(
        ConditionOperator.EQUALS,
        ConditionOperator.IN,
        ConditionOperator.LESS_THAN,
        ConditionOperator.GREATER_THAN
    )
    TransactionField.TRANSACTION_DATE -> listOf(
        ConditionOperator.EQUALS,
        ConditionOperator.LESS_THAN,
        ConditionOperator.GREATER_THAN
    )
    TransactionField.ACCOUNT -> listOf(
        ConditionOperator.EQUALS,
        ConditionOperator.NOT_EQUALS
    )
    else -> listOf(
        ConditionOperator.CONTAINS,
        ConditionOperator.EQUALS,
        ConditionOperator.STARTS_WITH
    )
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
    TYPE,                    // A TransactionType name: INCOME, EXPENSE, CREDIT, TRANSFER, or INVESTMENT
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