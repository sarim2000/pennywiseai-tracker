package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.service.RuleEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class AiToolLogicTest {

    private fun txn(
        merchant: String,
        amount: String,
        currency: String = "INR",
        category: String = "Food & Dining",
        type: TransactionType = TransactionType.EXPENSE,
        excluded: Boolean = false,
        day: Int = 5,
    ) = TransactionEntity(
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 10, day, 12, 0),
        transactionHash = "$merchant$amount$day",
        currency = currency,
        smsBody = "SMS TEXT THAT MUST NOT LEAVE THE PHONE",
        bankName = "HDFC",
        accountNumber = "XXXX1234",
        excludedFromAnalytics = excluded,
    )

    private fun JsonObject.totals(key: String = "grand_total") =
        this[key]!!.jsonArray.associate { it.jsonObject["currency"]!!.jsonPrimitive.content to it.jsonObject["amount"]!!.jsonPrimitive.content }

    @Test
    fun `summary keeps currencies apart, counts card spends and skips excluded`() {
        val result = AiToolLogic.spendingSummary(
            listOf(
                txn("Swiggy", "412"),
                txn("Zomato", "289.5"),
                txn("Amazon US", "18.99", currency = "USD", category = "Shopping"),
                txn("Card dinner", "100", type = TransactionType.CREDIT),
                txn("Excluded", "999", excluded = true),
                txn("Salary", "85000", type = TransactionType.INCOME, category = "Salary"),
            ),
            groupBy = "category",
            type = "EXPENSE",
        )
        assertEquals(mapOf("INR" to "801.50", "USD" to "18.99"), result.totals())
        val food = result["groups"]!!.jsonArray.first { it.jsonObject["group"]!!.jsonPrimitive.content == "Food & Dining" }.jsonObject
        assertEquals(mapOf("INR" to "801.50"), food.totals("totals"))
    }

    @Test
    fun `amounts keep their currency's decimals`() {
        val out = AiToolLogic.searchTransactions(listOf(txn("Grocer", "1.235", currency = "JOD")), query = "groc", category = null, type = null, limit = 10)
        val row = out["transactions"]!!.jsonArray.single().jsonObject
        assertEquals("1.235", row["amount"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search masks the account and never sends SMS text`() {
        val out = AiToolLogic.searchTransactions(listOf(txn("Swiggy", "412")), query = "swig", category = null, type = null, limit = 10)
        val row = out["transactions"]!!.jsonArray.single().jsonObject
        assertEquals("HDFC ••1234", row["account"]!!.jsonPrimitive.content)
        assertEquals("412.00", row["amount"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        assertFalse(out.toString().contains("SMS TEXT"))
        assertFalse(out.toString().contains("XXXX1234"))
    }

    @Test
    fun `search caps rows and reports how many matched`() {
        val many = (1..250).map { txn("Shop$it", "1", day = 1 + it % 28) }
        val out = AiToolLogic.searchTransactions(many, null, null, null, limit = 1000)
        assertEquals(250, out["total_matches"]!!.jsonPrimitive.content.toInt())
        assertEquals(AiToolLogic.MAX_ROWS, out["transactions"]!!.jsonArray.size)
    }

    @Test
    fun `accounts show credit card outstanding and hide the wallet marker`() {
        val out = AiToolLogic.accounts(
            listOf(
                AccountBalanceEntity(bankName = "ICICI", accountLast4 = "1093", balance = BigDecimal("3914"), timestamp = LocalDateTime.of(2026, 10, 12, 9, 0), isCreditCard = true),
                AccountBalanceEntity(bankName = "eMola", accountLast4 = AccountBalanceEntity.WALLET_ACCOUNT_MARKER, balance = BigDecimal("50"), timestamp = LocalDateTime.of(2026, 10, 12, 9, 0), currency = "MZN"),
            )
        )["accounts"]!!.jsonArray.map { it.jsonObject }
        assertEquals("ICICI ••1093", out[0]["account"]!!.jsonPrimitive.content)
        assertEquals("3914.00", out[0]["outstanding"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        assertEquals("eMola", out[1]["account"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search pages with offset, filters by account and sorts amounts within a currency`() {
        val list = listOf(
            txn("A", "10", day = 1), txn("B", "300", day = 2), txn("C", "50", day = 3),
            txn("D", "5", currency = "USD", day = 4),
            txn("E", "999", day = 5).copy(bankName = "ICICI", accountNumber = "9999"),
        )
        val page2 = AiToolLogic.searchTransactions(list, null, null, null, limit = 2, offset = 2)
        assertEquals(listOf("C", "B"), page2["transactions"]!!.jsonArray.map { it.jsonObject["merchant"]!!.jsonPrimitive.content })
        val hdfcOnly = AiToolLogic.searchTransactions(list, null, null, null, limit = 50, account = "HDFC ••1234")
        assertEquals(4, hdfcOnly["total_matches"]!!.jsonPrimitive.content.toInt())
        // Pickers list every account, not just the chosen one.
        assertEquals(listOf("HDFC ••1234", "ICICI ••9999"), hdfcOnly["accounts"]!!.jsonArray.map { it.jsonPrimitive.content })
        val byAmount = AiToolLogic.searchTransactions(list, null, null, null, limit = 50, sort = "amount_desc")
        assertEquals(listOf("E", "B", "C", "A", "D"), byAmount["transactions"]!!.jsonArray.map { it.jsonObject["merchant"]!!.jsonPrimitive.content })
    }

    @Test
    fun `month_category groups carry month and category, totals still per currency`() {
        val out = AiToolLogic.spendingSummary(
            listOf(txn("A", "10", day = 1), txn("B", "20", day = 2), txn("C", "7", currency = "USD", day = 3, category = "Shopping")),
            groupBy = "month_category", type = "EXPENSE",
        )
        val food = out["groups"]!!.jsonArray.map { it.jsonObject }.single { it["sub"]!!.jsonPrimitive.content == "Food & Dining" }
        assertEquals("2026-10", food["group"]!!.jsonPrimitive.content)
        assertEquals(mapOf("INR" to "30.00"), food.totals("totals"))
    }

    @Test
    fun `summary counts only your part of split bills and nets refunds per currency`() {
        val all = listOf(
            txn("Dinner", "1000").copy(lentSharesTotal = BigDecimal("400")),
            txn("Lent", "500", category = "Transfers").copy(loanId = 1),
            txn("Shop", "100", category = "Shopping"),
            txn("Shop refund", "40", type = TransactionType.INCOME, category = "Refund")
                .copy(budgetImpactType = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Shopping"),
            txn("Salary", "85000", type = TransactionType.INCOME, category = "Salary"),
        )
        val spend = AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE")
        assertEquals(mapOf("INR" to "660.00"), spend.totals())
        val shopping = spend["groups"]!!.jsonArray.map { it.jsonObject }.single { it["group"]!!.jsonPrimitive.content == "Shopping" }
        assertEquals(mapOf("INR" to "60.00"), shopping.totals("totals"))
        assertEquals(mapOf("INR" to "85000.00"), AiToolLogic.spendingSummary(all, groupBy = "category", type = "INCOME").totals())
    }

    @Test
    fun `summary limit keeps the busiest groups and reports the full count`() {
        val result = AiToolLogic.spendingSummary(
            listOf(txn("Swiggy", "1", day = 1), txn("Swiggy", "2", day = 2), txn("Zomato", "3"), txn("Uber", "4", category = "Travel")),
            groupBy = "merchant",
            type = "EXPENSE",
            limit = 1,
        )
        assertEquals(3, result["total_groups"]!!.jsonPrimitive.content.toInt())
        assertEquals(listOf("Swiggy"), result["groups"]!!.jsonArray.map { it.jsonObject["group"]!!.jsonPrimitive.content })
    }

    @Test
    fun `search shows a merchant's alias`() {
        val row = AiToolLogic.searchTransactions(listOf(txn("AMAZON PAY IN", "10")), null, null, null, 10, aliases = mapOf("AMAZON PAY IN" to "Amazon"))
            .getValue("transactions").jsonArray.single().jsonObject
        assertEquals("Amazon", row["alias"]!!.jsonPrimitive.content)
    }

    private fun JsonObject.groupNames() = this["groups"]!!.jsonArray.map { it.jsonObject["group"]!!.jsonPrimitive.content }

    private fun JsonObject.merchants() = this["transactions"]!!.jsonArray.map { it.jsonObject["merchant"]!!.jsonPrimitive.content }

    @Test
    fun `summary totals merchants aliased to one name as one group`() {
        val out = AiToolLogic.spendingSummary(
            listOf(txn("ZOMATO LTD", "100", day = 1), txn("ZOMATO MEDIA", "50", day = 2), txn("Swiggy", "30")),
            groupBy = "merchant",
            type = "EXPENSE",
            aliases = mapOf("ZOMATO LTD" to "Zomato", "ZOMATO MEDIA" to "Zomato"),
        )
        assertEquals(listOf("Zomato", "Swiggy"), out.groupNames())
        val zomato = out["groups"]!!.jsonArray.first().jsonObject
        assertEquals(2, zomato["count"]!!.jsonPrimitive.content.toInt())
        assertEquals(mapOf("INR" to "150.00"), zomato.totals("totals"))
    }

    @Test
    fun `summary leaves out an excluded category, refunds to it included`() {
        val all = listOf(
            txn("Swiggy", "100"),
            txn("Hotel", "500", category = "Travel"),
            txn("Hotel refund", "200", type = TransactionType.INCOME, category = "Refund")
                .copy(budgetImpactType = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Travel"),
        )
        assertEquals(mapOf("INR" to "400.00"), AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE").totals())
        val out = AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE", excludeCategories = listOf("travel", "No such category"))
        assertEquals(mapOf("INR" to "100.00"), out.totals())
        assertEquals(listOf("Food & Dining"), out.groupNames())
    }

    @Test
    fun `summary leaves out an excluded tag and can keep only one tag`() {
        val all = listOf(txn("Lunch", "100").copy(id = 1), txn("Cab", "300", category = "Travel").copy(id = 2), txn("Snack", "20").copy(id = 3))
        val tags = mapOf(2L to listOf("Work trip"), 3L to listOf("Home"))
        val without = AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE", tagsById = tags, excludeTags = listOf("work TRIP", "nope"))
        assertEquals(mapOf("INR" to "120.00"), without.totals())
        val only = AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE", tagsById = tags, tag = listOf("work trip"))
        assertEquals(mapOf("INR" to "300.00"), only.totals())
    }

    @Test
    fun `search filters by tag and leaves out excluded categories and tags`() {
        val all = listOf(
            txn("Lunch", "100", day = 1).copy(id = 1),
            txn("Cab", "300", category = "Travel", day = 2).copy(id = 2),
            txn("Snack", "20", day = 3).copy(id = 3),
        )
        val tags = mapOf(2L to listOf("Work trip"), 3L to listOf("Home"))
        assertEquals(listOf("Cab"), AiToolLogic.searchTransactions(all, null, null, null, 50, tagsById = tags, tag = listOf("WORK TRIP")).merchants())
        assertEquals(listOf("Snack", "Lunch"), AiToolLogic.searchTransactions(all, null, null, null, 50, excludeCategories = listOf("travel")).merchants())
        assertEquals(listOf("Cab", "Lunch"), AiToolLogic.searchTransactions(all, null, null, null, 50, tagsById = tags, excludeTags = listOf("home")).merchants())
    }

    @Test
    fun `tag takes several names and a row matches any of them`() {
        val all = listOf(txn("Lunch", "100", day = 1).copy(id = 1), txn("Cab", "300", day = 2).copy(id = 2), txn("Snack", "20", day = 3).copy(id = 3))
        val tags = mapOf(1L to listOf("Trip"), 2L to listOf("Work"), 3L to listOf("Home"))
        assertEquals(listOf("Cab", "Lunch"), AiToolLogic.searchTransactions(all, null, null, null, 50, tagsById = tags, tag = listOf("trip", "WORK")).merchants())
        assertEquals(mapOf("INR" to "400.00"), AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE", tagsById = tags, tag = listOf("trip", "work")).totals())
    }

    @Test
    fun `excluding a parent category also leaves out its sub-categories`() {
        val all = listOf(
            txn("Swiggy", "100", day = 1),
            txn("Cafe", "30", category = "Coffee", day = 2),
            txn("Hotel", "500", category = "Travel", day = 3),
        )
        val subs = mapOf("Food & Dining" to listOf("Coffee"))
        val summary = AiToolLogic.spendingSummary(all, groupBy = "category", type = "EXPENSE", excludeCategories = listOf("food & dining"), subCategories = subs)
        assertEquals(listOf("Travel"), summary.groupNames())
        val search = AiToolLogic.searchTransactions(all, null, null, null, 50, excludeCategories = listOf("Food & Dining"), subCategories = subs)
        assertEquals(listOf("Hotel"), search.merchants())
        // Without the map the sub-category row stays.
        assertEquals(listOf("Hotel", "Cafe"), AiToolLogic.searchTransactions(all, null, null, null, 50, excludeCategories = listOf("Food & Dining")).merchants())
    }

    @Test
    fun `search leaves out a refund that gives back to an excluded category`() {
        val all = listOf(
            txn("Hotel", "500", category = "Travel", day = 1),
            txn("Hotel refund", "200", type = TransactionType.INCOME, category = "Refund", day = 2)
                .copy(budgetImpactType = BudgetImpactType.DEDUCT_SPENT, budgetCategory = "Travel"),
            txn("Swiggy", "100", day = 3),
        )
        assertEquals(listOf("Swiggy"), AiToolLogic.searchTransactions(all, null, null, null, 50, excludeCategories = listOf("Travel")).merchants())
        // The row's own category leaves it out as well, so excluding and filtering by category agree.
        assertFalse("Hotel refund" in AiToolLogic.searchTransactions(all, null, null, null, 50, excludeCategories = listOf("refund")).merchants())
    }

    @Test
    fun `filters echo appears only when a filter was supplied`() {
        val all = listOf(txn("Swiggy", "100"))
        for (out in listOf(AiToolLogic.searchTransactions(all, null, null, null, 10), AiToolLogic.spendingSummary(all, "category", "EXPENSE"))) {
            assertFalse(out.containsKey("filters"))
        }
        for (out in listOf(
            AiToolLogic.searchTransactions(all, null, null, null, 10, tag = listOf("Trip"), excludeCategories = listOf("Shopping")),
            AiToolLogic.spendingSummary(all, "category", "EXPENSE", tag = listOf("Trip"), excludeCategories = listOf("Shopping")),
        )) {
            val f = out["filters"]!!.jsonObject
            assertEquals(listOf("Trip"), f["tag"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("Shopping"), f["exclude_categories"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertEquals(0, f["exclude_tags"]!!.jsonArray.size)
        }
    }

    @Test
    fun `search query matches the alias a merchant group shows`() {
        val out = AiToolLogic.searchTransactions(listOf(txn("ZMT*PAYU", "10"), txn("Swiggy", "5")), "zomato", null, null, 10, aliases = mapOf("ZMT*PAYU" to "Zomato"))
        assertEquals(listOf("ZMT*PAYU"), out.merchants())
    }

    @Test
    fun `excludes keep totals per currency`() {
        val all = listOf(
            txn("Swiggy", "100").copy(id = 1),
            txn("Amazon US", "18.99", currency = "USD", category = "Shopping").copy(id = 2),
            txn("Hotel", "500", category = "Travel").copy(id = 3),
            txn("Flight", "250", currency = "USD", category = "Travel").copy(id = 4),
            txn("Gift", "40", currency = "USD", category = "Shopping").copy(id = 5),
        )
        val out = AiToolLogic.spendingSummary(
            all, groupBy = "category", type = "EXPENSE",
            tagsById = mapOf(5L to listOf("Personal")), excludeCategories = listOf("Travel"), excludeTags = listOf("Personal"),
        )
        assertEquals(mapOf("INR" to "100.00", "USD" to "18.99"), out.totals())
    }

    private fun ruleArgs(actionType: String = "SET", operator: String = "CONTAINS") = Json.parseToJsonElement(
        """{"name":"Rent","conditions":[{"field":"MERCHANT","operator":"$operator","value":"Landlord"}],
           "actions":[{"field":"CATEGORY","actionType":"$actionType","value":"Housing"}],"apply_to_past":true}"""
    ).jsonObject

    @Test
    fun `create_rule accepts a valid rule`() {
        val rule = AiToolLogic.ruleFrom(ruleArgs())
        assertEquals("Rent", rule.name)
        assertEquals("Housing", rule.actions.single().value)
        assertFalse(rule.isSystemTemplate)
    }

    @Test
    fun `create_rule refuses BLOCK and operators that can't fire`() {
        assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleFrom(ruleArgs(actionType = "BLOCK")) }
        assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleFrom(ruleArgs(operator = "GREATER_THAN")) }
        assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleFrom(Json.parseToJsonElement("""{"name":"x"}""").jsonObject) }
    }

    @Test
    fun `rules shared with AI apps hide SMS text and note conditions`() {
        val rule = com.pennywiseai.tracker.domain.model.rule.TransactionRule(
            name = "r",
            conditions = listOf(
                com.pennywiseai.tracker.domain.model.rule.RuleCondition(
                    com.pennywiseai.tracker.domain.model.rule.TransactionField.SMS_TEXT,
                    com.pennywiseai.tracker.domain.model.rule.ConditionOperator.CONTAINS, "sample message text"
                ),
                com.pennywiseai.tracker.domain.model.rule.RuleCondition(
                    com.pennywiseai.tracker.domain.model.rule.TransactionField.NARRATION,
                    com.pennywiseai.tracker.domain.model.rule.ConditionOperator.CONTAINS, "sample note text"
                ),
                com.pennywiseai.tracker.domain.model.rule.RuleCondition(
                    com.pennywiseai.tracker.domain.model.rule.TransactionField.MERCHANT,
                    com.pennywiseai.tracker.domain.model.rule.ConditionOperator.CONTAINS, "cafe"
                )
            ),
            actions = emptyList()
        )
        val shared = AiToolLogic.shareableRules(listOf(rule)).single().conditions.map { it.value }
        assertEquals(listOf(AiToolLogic.HIDDEN_SMS_TEXT, AiToolLogic.HIDDEN_NOTE, "cafe"), shared)
    }

    private fun ruleWith(conditions: String) = Json.parseToJsonElement(
        """{"name":"R","conditions":$conditions,"actions":[{"field":"CATEGORY","actionType":"SET","value":"Housing"}]}"""
    ).jsonObject

    private fun matches(rule: TransactionRule, merchant: String, day: Int) =
        RuleEngine().evaluateRules(txn(merchant, "10", day = day), null, listOf(rule)).second.isNotEmpty()

    @Test
    fun `create_rule takes a supported negation, IN on a day field and a between-two-dates rule`() {
        fun rule(conditions: String) = AiToolLogic.ruleFrom(ruleWith(conditions))
        val notTransfer = rule("""[{"field":"TYPE","operator":"NOT_EQUALS","value":"TRANSFER"}]""")
        assertTrue(matches(notTransfer, "Swiggy", 5))
        assertFalse(matches(rule("""[{"field":"TYPE","operator":"NOT_EQUALS","value":"EXPENSE"}]"""), "Swiggy", 5))

        // 2026-10-05 is a Monday.
        val weekend = rule("""[{"field":"TRANSACTION_DAY_OF_WEEK","operator":"IN","value":"1, 7"}]""")
        assertTrue(matches(weekend, "Swiggy", 5))
        assertFalse(matches(weekend, "Swiggy", 6))

        // Both date bounds are exclusive, so 3 to 5 October is after the 2nd and before the 6th.
        val between = rule(
            """[{"field":"TRANSACTION_DATE","operator":"GREATER_THAN","value":"2026-10-02"},
               {"field":"TRANSACTION_DATE","operator":"LESS_THAN","value":"2026-10-06","logicalOperator":"AND"}]"""
        )
        assertFalse(matches(between, "Swiggy", 2))
        assertTrue(matches(between, "Swiggy", 3))
        assertTrue(matches(between, "Swiggy", 5))
        assertFalse(matches(between, "Swiggy", 6))
        assertThrows(IllegalArgumentException::class.java) {
            rule("""[{"field":"TRANSACTION_DATE","operator":"GREATER_THAN","value":"3 Oct"}]""")
        }
    }

    @Test
    fun `create_rule refuses operators the rule editor does not offer for a field`() {
        listOf(
            """[{"field":"MERCHANT","operator":"NOT_CONTAINS","value":"refund"}]""",
            """[{"field":"MERCHANT","operator":"IN","value":"a,b"}]""",
            """[{"field":"TRANSACTION_DATE","operator":"GREATER_THAN_OR_EQUAL","value":"2026-10-03"}]""",
            """[{"field":"AMOUNT","operator":"NOT_IN","value":"1,2"}]""",
        ).forEach { c ->
            val e = assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleFrom(ruleWith(c)) }
            assertTrue(e.message!!.startsWith("That rule isn't valid."))
        }
    }

    @Test
    fun `apply_to_past reads none, uncategorized and all, and true as uncategorized`() {
        fun mode(json: String, default: String = "none") = AiToolLogic.applyToPast(Json.parseToJsonElement(json).jsonObject, default)
        assertEquals("none", mode("{}"))
        assertEquals("uncategorized", mode("{}", "uncategorized"))
        assertEquals("none", mode("""{"apply_to_past":"none"}""", "all"))
        assertEquals("uncategorized", mode("""{"apply_to_past":"uncategorized"}"""))
        assertEquals("all", mode("""{"apply_to_past":"all"}"""))
        assertEquals("uncategorized", mode("""{"apply_to_past":true}"""))
        assertEquals("none", mode("""{"apply_to_past":false}""", "all"))
        assertThrows(IllegalArgumentException::class.java) { mode("""{"apply_to_past":"most"}""") }
    }

    @Test
    fun `ruleUpdated keeps what is not supplied, including the real SMS text`() {
        val existing = TransactionRule(
            name = "Old", description = "d", priority = 3, isActive = false,
            conditions = listOf(RuleCondition(TransactionField.SMS_TEXT, ConditionOperator.CONTAINS, "real sms words")),
            actions = listOf(RuleAction(TransactionField.CATEGORY, ActionType.SET, "Housing")),
        )
        val renamed = AiToolLogic.ruleUpdated(existing, Json.parseToJsonElement("""{"id":"x","name":"New","description":""}""").jsonObject)
        assertEquals("New", renamed.name)
        assertEquals(null, renamed.description)
        assertEquals(existing.id, renamed.id)
        assertEquals(3, renamed.priority)
        assertFalse(renamed.isActive)
        assertEquals(existing.conditions, renamed.conditions)
        assertEquals(existing.actions, renamed.actions)
    }

    @Test
    fun `ruleFrom refuses SMS text and note conditions, ruleUpdated keeps a stored one only for a rename`() {
        val action = RuleAction(TransactionField.CATEGORY, ActionType.SET, "Food")
        val merchantRule = TransactionRule(name = "m", conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "a")), actions = listOf(action))
        for (field in listOf("SMS_TEXT", "NARRATION")) {
            val args = ruleWith("""[{"field":"$field","operator":"CONTAINS","value":"x"}]""")
            assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleFrom(args) }
            assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleUpdated(merchantRule, args) }
        }
        val stored = TransactionRule(name = "n", conditions = listOf(RuleCondition(TransactionField.NARRATION, ConditionOperator.CONTAINS, "real note")), actions = listOf(action))
        assertEquals(stored.conditions, AiToolLogic.ruleUpdated(stored, Json.parseToJsonElement("""{"priority":4}""").jsonObject).conditions)
        assertThrows(IllegalArgumentException::class.java) { AiToolLogic.ruleUpdated(stored, ruleWith("""[{"field":"MERCHANT","operator":"CONTAINS","value":"x"}]""")) }
    }

    @Test
    fun `list_rules hides note conditions too`() {
        val own = TransactionRule(id = "abc", name = "Mine", conditions = listOf(RuleCondition(TransactionField.NARRATION, ConditionOperator.CONTAINS, "private note")), actions = emptyList())
        assertFalse(Json.parseToJsonElement(AiToolLogic.listRules(listOf(own))).toString().contains("private note"))
    }

    @Test
    fun `list_rules adds each rule's id and leaves built-in rules out`() {
        val own = TransactionRule(
            id = "abc", name = "Mine",
            conditions = listOf(RuleCondition(TransactionField.SMS_TEXT, ConditionOperator.CONTAINS, "secret")),
            actions = emptyList(),
        )
        val out = Json.parseToJsonElement(AiToolLogic.listRules(listOf(own.copy(id = "sys", isSystemTemplate = true), own))).jsonObject
        val rule = out["rules"]!!.jsonArray.single().jsonObject
        assertEquals("abc", rule["id"]!!.jsonPrimitive.content)
        assertFalse(out.toString().contains("secret"))
    }
}
