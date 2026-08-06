package com.pennywiseai.shared.domain.rules

import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Evaluates stored Smart Rules against transactions entering through the
 * parsing pipeline (statement import). Until this existed, rules were
 * write-only: the screen created and stored them but nothing ever consulted
 * them.
 *
 * Rules run on *parsed* data only — a manually added transaction carries the
 * category the user just picked, and a rule silently overriding an explicit
 * choice would be surprising. This mirrors Android, where the RuleEngine sits
 * in the SMS parsing pipeline.
 *
 * The rule builder currently persists exactly one condition shape
 * (`{"type":"merchant_contains","value":…}`) and one action shape
 * (`{"category":…,"transactionType":…}`). Anything unrecognized — future
 * condition types, malformed JSON — makes that rule a non-match rather than an
 * error, so old evaluators never misfire on rules written by newer builders.
 */
object SharedRuleEvaluator {

    private val json = Json { ignoreUnknownKeys = true }

    /** What a matched rule wants to change; null fields mean "leave as is". */
    data class Adjustment(
        val category: String?,
        val transactionType: String?,
        val ruleId: String,
        val ruleName: String
    )

    /**
     * Returns the adjustment from the first matching active rule, or null.
     * Rules are tried in ascending [SharedRuleEntity.priority] order (then by
     * creation time for stability); the first match wins, matching the
     * priority semantics the rules screen exposes.
     */
    fun firstMatch(rules: List<SharedRuleEntity>, merchantName: String): Adjustment? {
        val merchant = merchantName.trim()
        if (merchant.isEmpty()) return null
        return rules
            .asSequence()
            .filter { it.isActive }
            .sortedWith(compareBy({ it.priority }, { it.createdAtEpochMillis }))
            .mapNotNull { rule -> adjustmentIfMatched(rule, merchant) }
            .firstOrNull()
    }

    private fun adjustmentIfMatched(rule: SharedRuleEntity, merchant: String): Adjustment? {
        val condition = parseObject(rule.conditionsJson) ?: return null
        val type = condition["type"]?.jsonPrimitive?.content ?: return null
        if (type != "merchant_contains") return null
        val keyword = condition["value"]?.jsonPrimitive?.content?.trim() ?: return null
        if (keyword.isEmpty() || !merchant.contains(keyword, ignoreCase = true)) return null

        val action = parseObject(rule.actionsJson) ?: return null
        val category = action["category"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val transactionType = action["transactionType"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (category == null && transactionType == null) return null
        return Adjustment(
            category = category,
            transactionType = transactionType,
            ruleId = rule.id,
            ruleName = rule.name
        )
    }

    private fun parseObject(raw: String) = try {
        json.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        null
    }
}
