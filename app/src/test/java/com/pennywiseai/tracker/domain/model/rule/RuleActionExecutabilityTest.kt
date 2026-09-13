package com.pennywiseai.tracker.domain.model.rule

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.service.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
 *
 * "Carried out" means the entity changed, or — for [TransactionField.TAGS],
 * which isn't a column — the run recorded a [FieldModification] on TAGS with
 * that action type for the persisting site to apply via `applyTagActions`.
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
                val (result, applications) = engine.evaluateRules(
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

                val recordedTagMod = applications.flatMap { it.fieldsModified }.any {
                    it.field == TransactionField.TAGS && it.actionType == actionType
                }
                val engineCarriedOut = result != transaction || recordedTagMod
                assertEquals(
                    "$field + $actionType: supportedActionTypes says " +
                        "${actionType in supportedActionTypes(field)} but the engine " +
                        "${if (engineCarriedOut) "did" else "did not"} apply it",
                    actionType in supportedActionTypes(field),
                    engineCarriedOut
                )
            }
        }
    }

    private fun tagRule(vararg actions: RuleAction) = TransactionRule(
        name = "tags",
        conditions = listOf(
            RuleCondition(
                field = TransactionField.MERCHANT,
                operator = ConditionOperator.CONTAINS,
                value = "Zomato"
            )
        ),
        actions = actions.toList()
    )

    @Test
    fun `tag actions are recorded but leave the entity untouched`() {
        val (result, applications) = engine.evaluateRules(
            transaction, null,
            listOf(
                tagRule(
                    RuleAction(TransactionField.TAGS, ActionType.ADD_TAG, "Swiggy"),
                    RuleAction(TransactionField.TAGS, ActionType.REMOVE_TAG, "Old")
                )
            )
        )

        assertEquals(transaction, result)
        assertEquals(
            listOf(
                FieldModification(TransactionField.TAGS, null, "Swiggy", ActionType.ADD_TAG),
                FieldModification(TransactionField.TAGS, null, "Old", ActionType.REMOVE_TAG)
            ),
            applications.single().fieldsModified
        )
    }

    @Test
    fun `applyTagActions merges case-insensitively and removes case-insensitively`() {
        val (_, applications) = engine.evaluateRules(
            transaction, null,
            listOf(
                tagRule(
                    RuleAction(TransactionField.TAGS, ActionType.ADD_TAG, "Swiggy"),
                    RuleAction(TransactionField.TAGS, ActionType.ADD_TAG, "food"),
                    RuleAction(TransactionField.TAGS, ActionType.REMOVE_TAG, "OLD")
                )
            )
        )

        assertEquals(
            listOf("Food", "Work", "Swiggy"),
            applications.applyTagActions(listOf("Food", "Old", "Work"))
        )
    }

    @Test
    fun `a rule that does not touch tags leaves the tag list identical`() {
        val (_, applications) = engine.evaluateRules(
            transaction, null,
            listOf(tagRule(RuleAction(TransactionField.CATEGORY, ActionType.SET, "Groceries")))
        )
        val existing = listOf("Food")

        assertSame(existing, applications.applyTagActions(existing))
    }

    @Test
    fun `BLOCK is executable on any field`() {
        for (field in TransactionField.entries) {
            val action = RuleAction(field = field, actionType = ActionType.BLOCK, value = "")
            assert(action.isExecutable()) { "$field + BLOCK should be executable" }
        }
    }

    @Test
    fun `tagChanges lists named adds and removes, later action wins`() {
        val apps = listOf(
            RuleApplication(
                ruleId = "r", ruleName = "r", transactionId = "1",
                fieldsModified = listOf(
                    FieldModification(TransactionField.TAGS, null, "Food", ActionType.ADD_TAG),
                    FieldModification(TransactionField.TAGS, null, " ", ActionType.ADD_TAG),
                    FieldModification(TransactionField.TAGS, null, "Old", ActionType.REMOVE_TAG),
                    // A later REMOVE of a name added earlier wins, case-insensitively.
                    FieldModification(TransactionField.TAGS, null, "food", ActionType.REMOVE_TAG),
                    FieldModification(TransactionField.CATEGORY, "a", "b", ActionType.SET)
                )
            )
        )

        val (add, remove) = apps.tagChanges()

        assertEquals(emptyList<String>(), add)
        assertEquals(listOf("Old", "food"), remove)
    }
}
