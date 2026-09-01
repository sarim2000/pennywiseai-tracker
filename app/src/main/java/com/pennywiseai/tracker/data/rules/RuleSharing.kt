package com.pennywiseai.tracker.data.rules

import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single smart rule in the portable, shareable form (#741).
 *
 * Deliberately narrower than [TransactionRule]: ids, timestamps and the
 * system-template flag are all local facts about *this* install, so they are
 * dropped on export and regenerated on import. What travels is the part a
 * peer actually wants — the name, what the rule matches, and what it does.
 *
 * Every field carries a default so a file written by an older or newer build
 * still decodes.
 */
@Serializable
data class SharedRule(
    val name: String = "",
    val description: String? = null,
    val priority: Int = 100,
    val conditions: List<RuleCondition> = emptyList(),
    val actions: List<RuleAction> = emptyList(),
    val isActive: Boolean = true
)

/**
 * The exported file's envelope. [version] lets a future format change be
 * detected rather than silently mis-read.
 */
@Serializable
data class SharedRuleSet(
    val version: Int = CURRENT_VERSION,
    val rules: List<SharedRule> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Encodes and decodes [SharedRuleSet] files. Kept separate from `RuleEngine`'s
 * internal column serialization: that one persists conditions/actions inside a
 * Room row, this one is a user-facing file that other people's installs read.
 */
object RuleSharingCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    const val MIME_TYPE = "application/json"
    const val FILE_EXTENSION = "json"

    /**
     * The exportable subset of [rules]: the user's own rules. Built-in
     * templates are excluded — every install already ships them, so sharing
     * them would only create duplicates on the other side.
     */
    fun exportable(rules: List<TransactionRule>): List<TransactionRule> =
        rules.filterNot { it.isSystemTemplate }

    fun encode(rules: List<TransactionRule>): String = json.encodeToString(
        SharedRuleSet(
            rules = exportable(rules).map { rule ->
                SharedRule(
                    name = rule.name,
                    description = rule.description,
                    priority = rule.priority,
                    conditions = rule.conditions,
                    actions = rule.actions,
                    isActive = rule.isActive
                )
            }
        )
    )

    /**
     * Parses [text] into the rules it holds, keeping only entries that would
     * pass [TransactionRule.validate] — a hand-edited or truncated file
     * shouldn't be able to insert a rule the create screen would reject.
     *
     * @throws IllegalArgumentException if the file isn't a rule set, is empty,
     *   or was written by a newer format version.
     */
    fun decode(text: String): List<TransactionRule> {
        val parsed = try {
            json.decodeFromString<SharedRuleSet>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This doesn't look like a PennyWise rules file.", e)
        }
        require(parsed.version <= SharedRuleSet.CURRENT_VERSION) {
            "This rules file was made by a newer version of PennyWise."
        }
        val rules = parsed.rules
            .map { shared ->
                TransactionRule(
                    name = shared.name.trim(),
                    description = shared.description?.trim()?.takeIf { it.isNotBlank() },
                    priority = shared.priority,
                    conditions = shared.conditions,
                    actions = shared.actions,
                    isActive = shared.isActive,
                    isSystemTemplate = false
                )
            }
            .filter { it.validate() }
        require(rules.isNotEmpty()) { "No usable rules found in this file." }
        return rules
    }
}
