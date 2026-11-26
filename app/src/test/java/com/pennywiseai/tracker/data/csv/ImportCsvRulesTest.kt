package com.pennywiseai.tracker.data.csv

import com.pennywiseai.tracker.data.statement.ImportRules
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.service.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

/** A CSV import's rows go through the learned mappings and rules, unless the file chose the category. */
class ImportCsvRulesTest {

    private val header = "Date,Time,Merchant,Category,Type,Amount,Currency,Bank,Account,Balance After,Description"

    private fun rows(vararg lines: String) =
        CsvTransactionImporter().parse(StringReader((listOf(header) + lines).joinToString("\n")), "INR").transactions

    private fun merchantRule(merchant: String, action: RuleAction) = TransactionRule(
        name = "$merchant rule",
        conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, merchant)),
        actions = listOf(action)
    )

    private fun rules(vararg rules: TransactionRule) = ImportRules(
        merchantCategories = mapOf("Coffee Hut" to "Snacks"),
        categoryIsIncome = emptyMap(),
        rules = rules.toList(),
        ruleEngine = RuleEngine()
    )

    @Test
    fun `an uncategorized row gets the learned category and a categorized row keeps its own`() {
        val (kept, blocked) = ImportCsvUseCase.applyRules(
            rows(
                "2026-01-15,09:30:00,Coffee Hut,,Expense,250.00,INR,DemoBank,1234,,",
                "2026-01-16,09:30:00,Coffee Hut,Travel,Expense,90.00,INR,DemoBank,1234,,"
            ),
            rules()
        )

        assertEquals(0, blocked)
        assertEquals(listOf("Snacks", "Travel"), kept.map { it.entity.category })
    }

    @Test
    fun `rules categorize only uncategorized rows but block any row`() {
        val (kept, blocked) = ImportCsvUseCase.applyRules(
            rows(
                "2026-01-15,09:30:00,Coffee Hut,Others,Expense,250.00,INR,DemoBank,1234,,",
                "2026-01-16,09:30:00,Coffee Hut,Travel,Expense,90.00,INR,DemoBank,1234,,",
                "2026-01-17,09:30:00,Spam Shop,Shopping,Expense,10.00,INR,DemoBank,1234,,"
            ),
            rules(
                merchantRule("Coffee", RuleAction(TransactionField.CATEGORY, ActionType.SET, "Cafes")),
                merchantRule("Spam Shop", RuleAction(TransactionField.CATEGORY, ActionType.BLOCK, ""))
            )
        )

        assertEquals(1, blocked)
        assertEquals(listOf("Cafes", "Travel"), kept.map { it.entity.category })
        assertEquals(1, kept[0].applications.size)
        assertTrue(kept[1].applications.isEmpty())
    }
}
