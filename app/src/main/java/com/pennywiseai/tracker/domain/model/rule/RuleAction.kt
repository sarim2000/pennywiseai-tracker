package com.pennywiseai.tracker.domain.model.rule

import kotlinx.serialization.Serializable

/**
 * The action types [com.pennywiseai.tracker.domain.service.RuleEngine] can
 * actually carry out on [field].
 *
 * Mirrors that engine's `applyAction` exactly — anything outside these sets
 * falls into its `else` branch, leaves the transaction untouched, and records no
 * modification, so a rule built from one is silently dead. The rule importer
 * checks arbitrary file content against this; `RuleActionExecutabilityTest`
 * pins the two together so they can't drift.
 *
 * [ActionType.BLOCK] is absent on purpose: it never modifies a transaction (the
 * engine skips it in `applyActions` and handles it in `shouldBlockTransaction`),
 * so it is valid regardless of field and is checked separately.
 *
 * [ActionType.ADD_TAG] / [ActionType.REMOVE_TAG] are absent because nothing in
 * the app implements them — they exist only as labels in the rule editor.
 */
fun supportedActionTypes(field: TransactionField): Set<ActionType> = when (field) {
    TransactionField.CATEGORY -> setOf(ActionType.SET, ActionType.CLEAR)
    TransactionField.MERCHANT,
    TransactionField.NARRATION -> setOf(
        ActionType.SET,
        ActionType.APPEND,
        ActionType.PREPEND,
        ActionType.CLEAR
    )
    TransactionField.TYPE -> setOf(ActionType.SET)
    TransactionField.BANK_NAME -> setOf(ActionType.SET, ActionType.CLEAR)
    else -> emptySet()
}

/**
 * Whether the engine can carry this action out at all. See [supportedActionTypes].
 */
fun RuleAction.isExecutable(): Boolean =
    actionType == ActionType.BLOCK || actionType in supportedActionTypes(field)

@Serializable
data class RuleAction(
    val field: TransactionField,
    val actionType: ActionType,
    val value: String
) {
    fun validate(): Boolean {
        return when (actionType) {
            // Setting TYPE needs a value the engine can parse back into a
            // TransactionType: applyAction falls back to the transaction's
            // existing type otherwise, so the rule silently does nothing.
            ActionType.SET ->
                if (field == TransactionField.TYPE) parseRuleTransactionType(value) != null
                else value.isNotBlank()
            ActionType.APPEND, ActionType.PREPEND -> value.isNotBlank()
            ActionType.CLEAR -> true
            ActionType.ADD_TAG -> value.isNotBlank()
            ActionType.REMOVE_TAG -> value.isNotBlank()
            ActionType.BLOCK -> true  // BLOCK action doesn't need a value
        }
    }
}

@Serializable
enum class ActionType {
    SET,           // Set field to value
    APPEND,        // Append value to field
    PREPEND,       // Prepend value to field
    CLEAR,         // Clear field
    ADD_TAG,       // Add a tag
    REMOVE_TAG,    // Remove a tag
    BLOCK          // Block the transaction from being saved
}