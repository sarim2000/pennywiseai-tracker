package com.pennywiseai.tracker.domain.model.rule

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.service.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Pins [supportedActionTypes] to what [RuleEngine] actually does.
 *
 * The rule importer refuses actions the engine can't carry out, so the two must
 * agree: if the engine gains (or loses) the ability to apply some field/action
 * pair and this table isn't updated, imports would start rejecting rules that
 * work — or accepting rules that silently do nothing.
 */
class RuleActionExecutabilityTest {

    private val engine = RuleEngine()

    private val transaction = TransactionEntity(
        amount = BigDecimal("100.00"),
        merchantName = "Zomato",
        category = "Food & Dining",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 3, 21, 10, 0),
        smsBody = "irrelevant",
        transactionHash = "hash",
        description = "lunch",
        bankName = "Kotak"
    )

    /** A value that would be a visible change for [field], so a no-op is detectable. */
    private fun probeValue(field: TransactionField, actionType: ActionType): String =
        when {
            actionType == ActionType.CLEAR -> ""
            field == TransactionField.TYPE -> TransactionType.INCOME.name
            else -> "CHANGED"
        }

    @Test
    fun `the table matches what the engine actually applies`() {
        for (field in TransactionField.entries) {
            for (actionType in ActionType.entries) {
                // BLOCK never modifies a transaction — it's handled by
                // shouldBlockTransaction — so it can't be probed this way.
                if (actionType == ActionType.BLOCK) continue

                val action = RuleAction(
                    field = field,
                    actionType = actionType,
                    value = probeValue(field, actionType)
                )
                val (result, _) = engine.evaluateRules(
                    transaction = transaction,
                    smsText = null,
                    rules = listOf(
                        TransactionRule(
                            name = "probe",
                            conditions = listOf(
                                RuleCondition(
                                    field = TransactionField.MERCHANT,
                                    operator = ConditionOperator.CONTAINS,
                                    value = "Zomato"
                                )
                            ),
                            actions = listOf(action)
                        )
                    )
                )

                val engineChangedSomething = result != transaction
                assertEquals(
                    "$field + $actionType: supportedActionTypes says " +
                        "${actionType in supportedActionTypes(field)} but the engine " +
                        "${if (engineChangedSomething) "did" else "did not"} apply it",
                    actionType in supportedActionTypes(field),
                    engineChangedSomething
                )
            }
        }
    }

    @Test
    fun `BLOCK is executable on any field`() {
        for (field in TransactionField.entries) {
            val action = RuleAction(field = field, actionType = ActionType.BLOCK, value = "")
            assert(action.isExecutable()) { "$field + BLOCK should be executable" }
        }
    }
}
