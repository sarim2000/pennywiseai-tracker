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
     * What a file yielded: the rules worth importing, plus what was thrown away
     * getting there. The counts are reported back to the user — a file that
     * silently loses half its rules is worse than one that says so.
     */
    data class DecodedRuleSet(
        val rules: List<TransactionRule>,
        /** Entries that wouldn't pass [TransactionRule.validate]. */
        val invalid: Int,
        /** Entries dropped because an earlier entry in the same file had that name. */
        val duplicatedInFile: Int
    )

    /**
     * Parses [text] into the rules it holds.
     *
     * Entries that wouldn't pass [TransactionRule.validate] are dropped rather
     * than inserted — the create screen would reject them too — and so are
     * repeats of a name that already appeared earlier in the same file, which
     * would otherwise land as two rules the user can't tell apart. Both are
     * counted so the caller can say what happened.
     *
     * @throws IllegalArgumentException if the file isn't a rule set, holds no
     *   usable rule, or was written by a newer format version.
     */
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
        val valid = mapped.filter { it.validate() }
        val deduped = valid.distinctBy { it.name.lowercase() }

        require(deduped.isNotEmpty()) { "No usable rules found in this file." }
        return DecodedRuleSet(
            rules = deduped,
            invalid = mapped.size - valid.size,
            duplicatedInFile = valid.size - deduped.size
        )
    }
}
