package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.domain.model.rule.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleTemplateService @Inject constructor() {

    fun getDefaultRuleTemplates(): List<TransactionRule> {
        return listOf(
            // Just one simple example rule to get users started
            createSmallPaymentsToFoodRule()
        )
    }

    private fun createSmallPaymentsToFoodRule(): TransactionRule {
        return TransactionRule(
            id = UUID.randomUUID().toString(),
            name = "Small Payments to Food",
            description = "Categorize small expense payments (under 200) as Food & Dining",
            priority = 100,
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.AMOUNT,
                    operator = ConditionOperator.LESS_THAN,
                    value = "200",
                    logicalOperator = LogicalOperator.AND
                ),
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "EXPENSE",
                    logicalOperator = LogicalOperator.AND
                )
            ),
            actions = listOf(
                RuleAction(
                    field = TransactionField.CATEGORY,
                    actionType = ActionType.SET,
                    value = "Food & Dining"
                )
            ),
            isActive = false, // Users can enable this
            isSystemTemplate = true
        )
    }
}
