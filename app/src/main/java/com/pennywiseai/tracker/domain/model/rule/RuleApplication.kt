package com.pennywiseai.tracker.domain.model.rule

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class RuleApplication(
    val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val ruleName: String,
    val transactionId: String,
    val fieldsModified: List<FieldModification>,
    val appliedAt: Long = System.currentTimeMillis()
)

@Serializable
data class FieldModification(
    val field: TransactionField,
    val oldValue: String?,
    val newValue: String?,
    val actionType: ActionType
)

/**
 * Folds the tag actions recorded in these applications into [existing]:
 * ADD_TAG names are merged in (case-insensitive, no duplicates) and REMOVE_TAG
 * names dropped (case-insensitive). Returns [existing] itself when no rule
 * touched tags, so callers can skip the write.
 */
fun List<RuleApplication>.applyTagActions(existing: List<String>): List<String> {
    val tagMods = flatMap { it.fieldsModified }.filter { it.field == TransactionField.TAGS }
    if (tagMods.isEmpty()) return existing
    val result = existing.toMutableList()
    for (mod in tagMods) {
        val name = mod.newValue?.trim().orEmpty()
        if (name.isEmpty()) continue
        when (mod.actionType) {
            ActionType.ADD_TAG ->
                if (result.none { it.equals(name, ignoreCase = true) }) result.add(name)
            ActionType.REMOVE_TAG -> result.removeAll { it.equals(name, ignoreCase = true) }
            else -> Unit
        }
    }
    return result
}
