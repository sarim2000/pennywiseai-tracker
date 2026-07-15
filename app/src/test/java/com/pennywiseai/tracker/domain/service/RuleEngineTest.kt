package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.LogicalOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

/**
 * Covers the account (BANK_NAME) rule action added for issue #373 — previously a
 * rule could select "account" but the engine silently ignored it.
 */
class RuleEngineTest {

    private val engine = RuleEngine()

    // --- HEAD: BANK_NAME action tests (SET / CLEAR) ---

    private fun txn(bankName: String? = null) = TransactionEntity(
        amount = BigDecimal("100.00"),
        merchantName = "Coffee Shop",
        category = "Food",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 1, 1, 10, 0),
        transactionHash = "hash-1",
        bankName = bankName
    )

    private fun rule(action: RuleAction) = TransactionRule(
        name = "rule",
        conditions = listOf(
            RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Coffee")
        ),
        actions = listOf(action)
    )

    @Test
    fun `SET account action sets the bank name`() {
        val (result, applications) = engine.evaluateRules(
            txn(),
            smsText = null,
            rules = listOf(rule(RuleAction(TransactionField.BANK_NAME, ActionType.SET, "HDFC Bank")))
        )

        assertEquals("HDFC Bank", result.bankName)
        assertTrue(applications.isNotEmpty())
    }

    @Test
    fun `CLEAR account action clears the bank name`() {
        val (result, _) = engine.evaluateRules(
            txn(bankName = "HDFC Bank"),
            smsText = null,
            rules = listOf(rule(RuleAction(TransactionField.BANK_NAME, ActionType.CLEAR, "")))
        )

        assertNull(result.bankName)
    }

    // --- Incoming: ACCOUNT condition tests (EQUALS, CONTAINS, etc.) ---

    @Test
    fun `evaluates ACCOUNT EQUALS when bankName and accountNumber match`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertTrue("Expected rule to match", applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT NOT_EQUALS when account differs`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.NOT_EQUALS,
            value = "HDFC Bank||9999"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertTrue("Expected rule to match because account differs", applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT EQUALS as false when bankName differs`() {
        val txn = transaction(
            bankName = "State Bank of India",
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected rule to NOT match because bankName differs", applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT EQUALS as false when accountNumber is null`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = null
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected rule to NOT match when accountNumber is null",
            applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT EQUALS as false when bankName is null`() {
        val txn = transaction(
            bankName = null,
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected rule to NOT match because bankName is null", applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT NOT_EQUALS as false when accountNumber is null`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = null
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.NOT_EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected rule to NOT match when accountNumber is null",
            applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT NOT_EQUALS as false when bankName is null`() {
        val txn = transaction(
            bankName = null,
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.NOT_EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected rule to NOT match when bankName is null",
            applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT NOT_EQUALS as false when both bankName and accountNumber are null`() {
        val txn = transaction(
            bankName = null,
            accountNumber = null
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.NOT_EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected rule to NOT match when both bankName and accountNumber are null",
            applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT NOT_EQUALS as false when account matches`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.NOT_EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected NOT_EQUALS to NOT match when account matches",
            applications.isNotEmpty())
    }

    @Test
    fun `evaluates ACCOUNT EQUALS correctly with mixed case bank name`() {
        val txn = transaction(
            bankName = "hdfc bank",
            accountNumber = "12345678"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "HDFC Bank||5678"
        )
        val rule = rule(condition)

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertTrue("Expected EQUALS to be case-insensitive", applications.isNotEmpty())
    }

    // --- Combination with other fields ---

    @Test
    fun `ACCOUNT EQUALS combined with AMOUNT condition using AND`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = "12345678",
            amount = BigDecimal("500.00")
        )
        val conditions = listOf(
            RuleCondition(
                field = TransactionField.ACCOUNT,
                operator = ConditionOperator.EQUALS,
                value = "HDFC Bank||5678"
            ),
            RuleCondition(
                field = TransactionField.AMOUNT,
                operator = ConditionOperator.GREATER_THAN,
                value = "100",
                logicalOperator = LogicalOperator.AND
            )
        )
        val rule = TransactionRule(
            name = "Combined AND Rule",
            conditions = conditions,
            actions = listOf(RuleAction(
                field = TransactionField.CATEGORY,
                actionType = ActionType.SET,
                value = "Matched"
            ))
        )

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertTrue("Expected combined AND conditions to match", applications.isNotEmpty())
    }

    @Test
    fun `ACCOUNT EQUALS with AMOUNT condition using AND when amount fails`() {
        val txn = transaction(
            bankName = "HDFC Bank",
            accountNumber = "12345678",
            amount = BigDecimal("50.00")
        )
        val conditions = listOf(
            RuleCondition(
                field = TransactionField.ACCOUNT,
                operator = ConditionOperator.EQUALS,
                value = "HDFC Bank||5678"
            ),
            RuleCondition(
                field = TransactionField.AMOUNT,
                operator = ConditionOperator.GREATER_THAN,
                value = "100",
                logicalOperator = LogicalOperator.AND
            )
        )
        val rule = TransactionRule(
            name = "Combined AND Rule - Should Fail",
            conditions = conditions,
            actions = listOf(RuleAction(
                field = TransactionField.CATEGORY,
                actionType = ActionType.SET,
                value = "Matched"
            ))
        )

        val (_, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertFalse("Expected combined AND conditions to NOT match when amount is too low",
            applications.isNotEmpty())
    }

    @Test
    fun `shouldBlockTransaction returns rule when ACCOUNT condition matches with BLOCK action`() {
        val txn = transaction(
            bankName = "Suspicious Bank",
            accountNumber = "99998888"
        )
        val condition = RuleCondition(
            field = TransactionField.ACCOUNT,
            operator = ConditionOperator.EQUALS,
            value = "Suspicious Bank||8888"
        )
        val rule = TransactionRule(
            name = "Block Suspicious Account",
            conditions = listOf(condition),
            actions = listOf(RuleAction(
                field = TransactionField.CATEGORY,
                actionType = ActionType.BLOCK,
                value = ""
            ))
        )

        val result = engine.shouldBlockTransaction(txn, null, listOf(rule))

        assertNotNull("Expected blocking rule to match", result)
    }

    // --- TYPE action / condition tests (#515) ---

    @Test
    fun `SET TYPE action can set transaction type to EXPENSE`() {
        val txn = transaction(
            merchantName = "Amazon",
            transactionType = TransactionType.CREDIT
        )
        val rule = TransactionRule(
            name = "Mark card spend as expense",
            conditions = listOf(
                RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Amazon")
            ),
            actions = listOf(
                RuleAction(TransactionField.TYPE, ActionType.SET, "EXPENSE")
            )
        )

        val (result, applications) = engine.evaluateRules(txn, null, listOf(rule))

        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertTrue(applications.isNotEmpty())
    }

    @Test
    fun `SET TYPE action with unknown value keeps the original type`() {
        val txn = transaction(
            merchantName = "Amazon",
            transactionType = TransactionType.CREDIT
        )
        val rule = TransactionRule(
            name = "Bad type value",
            conditions = listOf(
                RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Amazon")
            ),
            actions = listOf(
                RuleAction(TransactionField.TYPE, ActionType.SET, "Outgoing")
            )
        )

        val (result, _) = engine.evaluateRules(txn, null, listOf(rule))

        assertEquals(TransactionType.CREDIT, result.transactionType)
    }

    @Test
    fun `evaluateRulesForType applies a TYPE EXPENSE rule to expense transactions`() {
        val txn = transaction(transactionType = TransactionType.EXPENSE)
        val rule = TransactionRule(
            name = "Expense-only rule",
            conditions = listOf(
                RuleCondition(TransactionField.TYPE, ConditionOperator.EQUALS, "EXPENSE")
            ),
            actions = listOf(
                RuleAction(TransactionField.CATEGORY, ActionType.SET, "General")
            )
        )

        val (_, applications) = engine.evaluateRulesForType(
            txn,
            smsText = null,
            rules = listOf(rule),
            type = TransactionType.EXPENSE
        )

        assertTrue(applications.isNotEmpty())
    }

    @Test
    fun `evaluateRulesForType filters out a TYPE EXPENSE rule for income transactions`() {
        val txn = transaction(transactionType = TransactionType.INCOME)
        val rule = TransactionRule(
            name = "Expense-only rule",
            conditions = listOf(
                RuleCondition(TransactionField.TYPE, ConditionOperator.EQUALS, "EXPENSE")
            ),
            actions = listOf(
                RuleAction(TransactionField.CATEGORY, ActionType.SET, "General")
            )
        )

        val (_, applications) = engine.evaluateRulesForType(
            txn,
            smsText = null,
            rules = listOf(rule),
            type = TransactionType.INCOME
        )

        assertTrue(applications.isEmpty())
    }

    // --- factory helpers (shared) ---

    private fun transaction(
        bankName: String? = "HDFC Bank",
        accountNumber: String? = "12345678",
        amount: BigDecimal = BigDecimal("100.00"),
        merchantName: String = "Test",
        category: String = "",
        transactionType: TransactionType = TransactionType.EXPENSE,
        dateTime: LocalDateTime = LocalDateTime.now(),
        transactionHash: String = UUID.randomUUID().toString()
    ): TransactionEntity = TransactionEntity(
        id = 1,
        amount = amount,
        merchantName = merchantName,
        category = category,
        transactionType = transactionType,
        dateTime = dateTime,
        bankName = bankName,
        accountNumber = accountNumber,
        transactionHash = transactionHash
    )

    private fun rule(condition: RuleCondition): TransactionRule = TransactionRule(
        name = "Test Rule",
        conditions = listOf(condition),
        actions = listOf(RuleAction(
            field = TransactionField.CATEGORY,
            actionType = ActionType.SET,
            value = "Auto-Categorized"
        ))
    )
}
