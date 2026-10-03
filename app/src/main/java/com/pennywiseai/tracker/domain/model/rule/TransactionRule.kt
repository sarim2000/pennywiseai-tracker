package com.pennywiseai.tracker.domain.model.rule

import java.util.UUID

data class TransactionRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val priority: Int = 100, // Lower number = higher priority
    val conditions: List<RuleCondition>,
    val actions: List<RuleAction>,
    val isActive: Boolean = true,
    val isSystemTemplate: Boolean = false, // System rules can't be deleted
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun validate(): Boolean {
        return name.isNotBlank() &&
               conditions.isNotEmpty() &&
               actions.isNotEmpty()
    }
}

/**
 * This rule with category [oldName] replaced by [newName] wherever it names
 * that category: a CATEGORY condition's value (compared ignoring case, as the
 * engine matches it) and a SET-category action's value.
 */
fun TransactionRule.withCategoryRenamed(oldName: String, newName: String): TransactionRule = copy(
    conditions = conditions.map { c ->
        if (c.field == TransactionField.CATEGORY && c.value.equals(oldName, ignoreCase = true)) c.copy(value = newName) else c
    },
    actions = actions.map { a ->
        if (a.field == TransactionField.CATEGORY && a.actionType == ActionType.SET && a.value == oldName) a.copy(value = newName) else a
    }
)

