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
     * Largest file [decode] will look at. An exported rule set runs to a few
     * hundred bytes per rule, so this holds thousands of them — while stopping
     * a mis-picked video from being pulled into memory whole by a picker that
     * has to accept every MIME type (see RulesScreen). A heap blow-up here would be an
     * OutOfMemoryError, which the import's `catch (Exception)` would not catch.
     */
    const val MAX_FILE_BYTES = 1L * 1024 * 1024

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
     * What a file yielded: the rules to import, plus the repeats collapsed on the
     * way. The count is reported back to the user — quietly importing fewer rules
     * than the file listed is worse than saying so.
     */
    data class DecodedRuleSet(
        val rules: List<TransactionRule>,
        /** Entries dropped because an earlier entry in the same file had that name. */
        val duplicatedInFile: Int
    )

    /**
     * Parses [text] into the rules it holds, all or nothing.
     *
     * An unusable entry fails the whole file rather than being skipped:
     * importing "most of" a shared rule set leaves the user with a half-applied
     * config they didn't ask for and can't easily tell apart from the real
     * thing. Better to reject it and let whoever exported it send a good file.
     *
     * Repeats of a name that already appeared earlier in the same file are the
     * one thing still collapsed rather than refused — the file is unambiguous
     * about what the rule should be (the first one wins), and the count is
     * reported.
     *
     * @throws IllegalArgumentException if the file isn't a rule set, holds an
     *   unusable entry, holds no rule at all, or was written by a newer format
     *   version.
     */
    /**
     * Whether a rule is usable in full.
     *
     * [TransactionRule.validate] only checks that the name, conditions and
     * actions are present — it never asks the conditions and actions whether
     * *they* are well-formed. That's fine for the create screen, which builds
     * them from pickers, but an imported file is arbitrary text: a condition
     * like AMOUNT / LESS_THAN / "abc" would sail through the shape check and be
     * persisted as a rule that can never match. So each part is validated too.
     */
    private fun TransactionRule.isFullyValid(): Boolean =
        validate() &&
            conditions.all { it.validate() } &&
            actions.all { it.validate() }

    fun decode(text: String): DecodedRuleSet {
        require(text.length <= MAX_FILE_BYTES) {
            "That file is too large to be a rules file."
        }
        val parsed = try {
            json.decodeFromString<SharedRuleSet>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("This doesn't look like a PennyWise rules file.", e)
        }
        require(parsed.version <= SharedRuleSet.CURRENT_VERSION) {
            "This rules file was made by a newer version of PennyWise."
        }

        val mapped = parsed.rules.map { shared ->
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
        val invalid = mapped.count { !it.isFullyValid() }
        require(invalid == 0) {
            if (invalid == 1) "1 rule in this file is incomplete, so nothing was imported."
            else "$invalid rules in this file are incomplete, so nothing was imported."
        }

        val deduped = mapped.distinctBy { it.name.lowercase() }
        require(deduped.isNotEmpty()) { "No rules found in this file." }
        return DecodedRuleSet(
            rules = deduped,
            duplicatedInFile = mapped.size - deduped.size
        )
    }
}
