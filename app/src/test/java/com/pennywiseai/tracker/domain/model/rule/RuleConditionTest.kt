package com.pennywiseai.tracker.domain.model.rule

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConditionTest {

    // --- ACCOUNT field validation ---

    @Test
    fun `ACCOUNT with valid bankName and last4 format returns true`() {
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "HDFC Bank||1234"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `ACCOUNT with empty value returns false`() {
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = ""
        )
        assertFalse(condition.validate())
    }

    // --- Other fields still validate correctly ---

    @Test
    fun `AMOUNT with valid decimal value returns true`() {
        val condition = RuleCondition(
            field = TransactionField.AMOUNT,
            operator = ConditionOperator.GREATER_THAN,
            value = "100.50"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `AMOUNT with non-numeric value for GREATER_THAN returns false`() {
        val condition = RuleCondition(
            field = TransactionField.AMOUNT,
            operator = ConditionOperator.GREATER_THAN,
            value = "not-a-number"
        )
        assertFalse(condition.validate())
    }

    @Test
    fun `TRANSACTION_HOUR with valid hour returns true`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_HOUR,
            operator = ConditionOperator.EQUALS,
            value = "14"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `TRANSACTION_HOUR with out-of-range hour returns false`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_HOUR,
            operator = ConditionOperator.EQUALS,
            value = "24"
        )
        assertFalse(condition.validate())
    }

    @Test
    fun `TRANSACTION_HOUR with non-numeric value returns false`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_HOUR,
            operator = ConditionOperator.EQUALS,
            value = "abc"
        )
        assertFalse(condition.validate())
    }

    @Test
    fun `TRANSACTION_TIME with valid format returns true`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_TIME,
            operator = ConditionOperator.EQUALS,
            value = "09:30"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `TRANSACTION_TIME with invalid format returns false`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_TIME,
            operator = ConditionOperator.EQUALS,
            value = "9:30"
        )
        assertFalse(condition.validate())
    }

    @Test
    fun `TRANSACTION_DAY_OF_WEEK with valid day returns true`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_DAY_OF_WEEK,
            operator = ConditionOperator.EQUALS,
            value = "1"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `TRANSACTION_DAY_OF_WEEK with out-of-range value returns false`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_DAY_OF_WEEK,
            operator = ConditionOperator.EQUALS,
            value = "8"
        )
        assertFalse(condition.validate())
    }

    @Test
    fun `TRANSACTION_DAY_OF_MONTH with valid day returns true`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_DAY_OF_MONTH,
            operator = ConditionOperator.EQUALS,
            value = "31"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `TRANSACTION_DAY_OF_MONTH with out-of-range value returns false`() {
        val condition = RuleCondition(
            field = TransactionField.TRANSACTION_DAY_OF_MONTH,
            operator = ConditionOperator.EQUALS,
            value = "32"
        )
        assertFalse(condition.validate())
    }

    // --- TYPE field validation ---

    @Test
    fun `TYPE with EXPENSE value returns true`() {
        val condition = RuleCondition(
            field = TransactionField.TYPE,
            operator = ConditionOperator.EQUALS,
            value = "EXPENSE"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `TYPE with lowercase value returns true`() {
        val condition = RuleCondition(
            field = TransactionField.TYPE,
            operator = ConditionOperator.EQUALS,
            value = "income"
        )
        assertTrue(condition.validate())
    }

    @Test
    fun `TYPE with unknown value returns false`() {
        // A stale display label must not validate as a stored type value.
        val condition = RuleCondition(
            field = TransactionField.TYPE,
            operator = ConditionOperator.EQUALS,
            value = "Outgoing"
        )
        assertFalse(condition.validate())
    }

    // --- parseRuleTransactionType ---

    @Test
    fun `parseRuleTransactionType maps known names case- and whitespace-insensitively`() {
        assertEquals(TransactionType.EXPENSE, parseRuleTransactionType("EXPENSE"))
        assertEquals(TransactionType.INCOME, parseRuleTransactionType(" income "))
        assertNull(parseRuleTransactionType("Outgoing"))
    }

    // --- isRuleApplicableToTransactionType ---

    private fun typeRule(
        operator: ConditionOperator,
        value: String
    ) = TransactionRule(
        name = "Type rule",
        conditions = listOf(
            RuleCondition(field = TransactionField.TYPE, operator = operator, value = value)
        ),
        actions = listOf(
            RuleAction(field = TransactionField.CATEGORY, actionType = ActionType.SET, value = "Food")
        )
    )

    @Test
    fun `isRuleApplicableToTransactionType matches EXPENSE only for expense transactions`() {
        val rule = typeRule(ConditionOperator.EQUALS, "EXPENSE")
        assertTrue(isRuleApplicableToTransactionType(rule, TransactionType.EXPENSE))
        assertFalse(isRuleApplicableToTransactionType(rule, TransactionType.INCOME))
    }

    @Test
    fun `isRuleApplicableToTransactionType with NOT_EQUALS excludes only that type`() {
        val rule = typeRule(ConditionOperator.NOT_EQUALS, "EXPENSE")
        assertFalse(isRuleApplicableToTransactionType(rule, TransactionType.EXPENSE))
        assertTrue(isRuleApplicableToTransactionType(rule, TransactionType.INCOME))
    }

    @Test
    fun `isRuleApplicableToTransactionType passes through rules with no TYPE condition`() {
        val rule = TransactionRule(
            name = "Merchant rule",
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.CONTAINS,
                    value = "Amazon"
                )
            ),
            actions = listOf(
                RuleAction(field = TransactionField.CATEGORY, actionType = ActionType.SET, value = "Shopping")
            )
        )
        assertTrue(isRuleApplicableToTransactionType(rule, TransactionType.EXPENSE))
        assertTrue(isRuleApplicableToTransactionType(rule, TransactionType.INCOME))
    }

    @Test
    fun `isRuleApplicableToTransactionType passes through when any OR logic is present`() {
        // An OR'd sibling can still match, so a failing TYPE condition must not exclude the rule.
        val rule = TransactionRule(
            name = "Type OR merchant",
            conditions = listOf(
                RuleCondition(
                    field = TransactionField.TYPE,
                    operator = ConditionOperator.EQUALS,
                    value = "EXPENSE"
                ),
                RuleCondition(
                    field = TransactionField.MERCHANT,
                    operator = ConditionOperator.CONTAINS,
                    value = "Amazon",
                    logicalOperator = LogicalOperator.OR
                )
            ),
            actions = listOf(
                RuleAction(field = TransactionField.CATEGORY, actionType = ActionType.SET, value = "Shopping")
            )
        )
        assertTrue(isRuleApplicableToTransactionType(rule, TransactionType.INCOME))
    }
}
