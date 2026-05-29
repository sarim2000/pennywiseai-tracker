package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Covers the account (BANK_NAME) rule action added for issue #373 — previously a
 * rule could select "account" but the engine silently ignored it.
 */
class RuleEngineTest {

    private val engine = RuleEngine()

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
}
