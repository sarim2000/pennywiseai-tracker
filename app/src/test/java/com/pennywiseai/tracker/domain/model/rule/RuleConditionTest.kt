package com.pennywiseai.tracker.domain.model.rule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConditionTest {

    // --- ACCOUNT field validation ---

    @Test
    fun `ACCOUNT with valid bankName||last4 format returns true`() {
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
}
