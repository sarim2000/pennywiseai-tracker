package com.pennywiseai.tracker.data.rules

import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules are shared between people (#741), so the file has to survive the round
 * trip and has to refuse anything that isn't one.
 */
class RuleSharingCodecTest {

    private fun rule(
        name: String,
        isSystemTemplate: Boolean = false
    ) = TransactionRule(
        name = name,
        description = "Categorise $name",
        priority = 50,
        conditions = listOf(
            RuleCondition(
                field = TransactionField.MERCHANT,
                operator = ConditionOperator.CONTAINS,
                value = name
            )
        ),
        actions = listOf(
            RuleAction(
                field = TransactionField.CATEGORY,
                actionType = ActionType.SET,
                value = "Food & Dining"
            )
        ),
        isSystemTemplate = isSystemTemplate
    )

    @Test
    fun `round trips a rule's matching and its effect`() {
        val original = rule("Zomato")

        val decoded = RuleSharingCodec.decode(RuleSharingCodec.encode(listOf(original)))

        assertEquals(1, decoded.size)
        val imported = decoded.single()
        assertEquals(original.name, imported.name)
        assertEquals(original.description, imported.description)
        assertEquals(original.priority, imported.priority)
        assertEquals(original.conditions, imported.conditions)
        assertEquals(original.actions, imported.actions)
        assertEquals(original.isActive, imported.isActive)
    }

    @Test
    fun `imported rules get fresh ids so a file can be applied twice`() {
        val original = rule("Zomato")

        val imported = RuleSharingCodec.decode(RuleSharingCodec.encode(listOf(original))).single()

        assertNotEquals(original.id, imported.id)
        assertEquals(false, imported.isSystemTemplate)
    }

    @Test
    fun `built-in templates are not exported`() {
        val rules = listOf(rule("Zomato"), rule("Salary", isSystemTemplate = true))

        assertEquals(listOf("Zomato"), RuleSharingCodec.exportable(rules).map { it.name })
        assertEquals(listOf("Zomato"), RuleSharingCodec.decode(RuleSharingCodec.encode(rules)).map { it.name })
    }

    @Test
    fun `a file that is not a rule set is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RuleSharingCodec.decode("""{"transactions":[]}""")
        }
        assertTrue(error.message!!.isNotBlank())
    }

    @Test
    fun `a newer format version is refused rather than half-read`() {
        val text = """{"version":99,"rules":[]}"""

        assertThrows(IllegalArgumentException::class.java) { RuleSharingCodec.decode(text) }
    }

    @Test
    fun `rules missing conditions or actions are dropped`() {
        val text = """
            {"version":1,"rules":[
              {"name":"No conditions","conditions":[],"actions":[
                {"field":"CATEGORY","actionType":"SET","value":"Food & Dining"}]}
            ]}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) { RuleSharingCodec.decode(text) }
    }

    @Test
    fun `unknown keys from a future build are ignored`() {
        val text = """
            {"version":1,"somethingNew":true,"rules":[
              {"name":"Zomato","conditions":[
                {"field":"MERCHANT","operator":"CONTAINS","value":"Zomato"}],
               "actions":[{"field":"CATEGORY","actionType":"SET","value":"Food & Dining"}]}
            ]}
        """.trimIndent()

        assertEquals(listOf("Zomato"), RuleSharingCodec.decode(text).map { it.name })
    }
}
