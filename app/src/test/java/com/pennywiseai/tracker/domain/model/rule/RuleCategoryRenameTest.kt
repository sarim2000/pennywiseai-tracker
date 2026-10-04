package com.pennywiseai.tracker.domain.model.rule

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleCategoryRenameTest {

    private fun rule(conditions: List<RuleCondition>, actions: List<RuleAction>) =
        TransactionRule(id = "r", name = "r", conditions = conditions, actions = actions, createdAt = 0, updatedAt = 0)

    @Test
    fun `renames category conditions and SET-category actions`() {
        val renamed = rule(
            conditions = listOf(RuleCondition(TransactionField.CATEGORY, ConditionOperator.EQUALS, "food & dining")),
            actions = listOf(RuleAction(TransactionField.CATEGORY, ActionType.SET, "Food & Dining"))
        ).withCategoryRenamed("Food & Dining", "Eating out")

        assertEquals("Eating out", renamed.conditions.single().value)
        assertEquals("Eating out", renamed.actions.single().value)
    }

    @Test
    fun `leaves other fields and other categories alone`() {
        val original = rule(
            conditions = listOf(
                RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Food & Dining"),
                RuleCondition(TransactionField.CATEGORY, ConditionOperator.EQUALS, "Groceries")
            ),
            actions = listOf(RuleAction(TransactionField.MERCHANT, ActionType.SET, "Food & Dining"))
        )
        val renamed = original.withCategoryRenamed("Food & Dining", "Eating out")

        assertEquals(original, renamed)
    }
}
