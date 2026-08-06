package com.pennywiseai.shared.domain.rules

import com.pennywiseai.shared.data.local.entity.SharedRuleEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedRuleEvaluatorTest {

    private fun rule(
        id: String = "r1",
        keyword: String = "swiggy",
        category: String? = "Food & Dining",
        type: String? = null,
        active: Boolean = true,
        priority: Int = 0,
        createdAt: Long = 0L,
        conditionsJson: String? = null
    ) = SharedRuleEntity(
        id = id,
        name = "rule-$id",
        priority = priority,
        conditionsJson = conditionsJson
            ?: """{"type":"merchant_contains","value":"$keyword"}""",
        actionsJson = buildString {
            append("{")
            category?.let { append("\"category\":\"$it\"") }
            if (category != null && type != null) append(",")
            type?.let { append("\"transactionType\":\"$it\"") }
            append("}")
        },
        isActive = active,
        isSystemTemplate = false,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = createdAt
    )

    @Test
    fun matches_case_insensitively_and_returns_the_action() {
        val hit = SharedRuleEvaluator.firstMatch(listOf(rule()), "SWIGGY BANGALORE")
        assertEquals("Food & Dining", hit?.category)
        assertNull(hit?.transactionType)
    }

    @Test
    fun inactive_rules_never_match() {
        assertNull(SharedRuleEvaluator.firstMatch(listOf(rule(active = false)), "Swiggy"))
    }

    @Test
    fun lower_priority_value_wins_when_both_match() {
        val hit = SharedRuleEvaluator.firstMatch(
            listOf(
                rule(id = "late", category = "Late", priority = 5),
                rule(id = "early", category = "Early", priority = 1)
            ),
            "swiggy order"
        )
        assertEquals("Early", hit?.category)
    }

    @Test
    fun malformed_or_unknown_condition_shapes_are_non_matches_not_errors() {
        val malformed = rule(conditionsJson = "not json at all")
        val unknownType = rule(conditionsJson = """{"type":"amount_over","value":"100"}""")
        assertNull(SharedRuleEvaluator.firstMatch(listOf(malformed, unknownType), "swiggy"))
    }

    @Test
    fun action_can_change_the_transaction_type() {
        val hit = SharedRuleEvaluator.firstMatch(
            listOf(rule(keyword = "salary", category = null, type = "INCOME")),
            "SALARY CREDIT AUG"
        )
        assertEquals("INCOME", hit?.transactionType)
        assertNull(hit?.category)
    }

    @Test
    fun empty_action_is_a_non_match() {
        assertNull(
            SharedRuleEvaluator.firstMatch(
                listOf(rule(category = null, type = null)),
                "swiggy"
            )
        )
    }
}
