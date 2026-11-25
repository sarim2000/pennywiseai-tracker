package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.model.rule.ActionType
import com.pennywiseai.tracker.domain.model.rule.ConditionOperator
import com.pennywiseai.tracker.domain.model.rule.RuleAction
import com.pennywiseai.tracker.domain.model.rule.RuleCondition
import com.pennywiseai.tracker.domain.model.rule.TransactionField
import com.pennywiseai.tracker.domain.model.rule.TransactionRule
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.domain.usecase.ApplyRulesToPastTransactionsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

private const val ROOM_COMPAT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** update_rule, delete_rule, apply_to_past and dry_run through [AiTools.call], over mocked storage and the real rule engine. */
class AiToolsRulesTest {
    private val rules = mockk<RuleRepository>(relaxed = true)
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val tags = mockk<TagRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val writeAllowed = MutableStateFlow(true)
    private val tools: AiTools

    private val stored = TransactionRule(
        id = "r1", name = "Food", description = "keep me", priority = 7,
        conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.CONTAINS, "Swiggy")),
        actions = listOf(RuleAction(TransactionField.CATEGORY, ActionType.SET, "Food")),
    )
    private val builtIn = stored.copy(id = "sys", name = "Built in", isSystemTemplate = true)

    private fun txn(id: Long, merchant: String, category: String) = TransactionEntity(
        id = id, amount = BigDecimal("10"), merchantName = merchant, category = category,
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 5, 12, 0),
        transactionHash = "h$id", currency = "INR",
    )

    private val swiggyOthers = txn(1, "Swiggy", "Others")
    private val swiggyDining = txn(2, "Swiggy", "Dining")
    private val uberOthers = txn(3, "Uber", "Others")

    init {
        val gate = mockk<EntitlementGate> { every { isProEntitled } returns MutableStateFlow(true) }
        val access = mockk<AiWriteAccess> { every { allowed } returns writeAllowed }
        val useCase = ApplyRulesToPastTransactionsUseCase(transactions, rules, RuleEngine(), tags, mockk(relaxed = true), database)
        tools = AiTools(
            transactions, mockk(relaxed = true), mockk(relaxed = true), gate, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), tags, rules, useCase, database, access,
        )
    }

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
        every { rules.getAllRules() } returns flowOf(listOf(stored, builtIn))
        coEvery { rules.getRuleById("r1") } returns stored
        coEvery { rules.getRuleById("sys") } returns builtIn
        coEvery { rules.getRuleById("gone") } returns null
        coEvery { transactions.getAllTransactionsList() } returns listOf(swiggyOthers, swiggyDining, uberOthers)
        coEvery { transactions.getUncategorizedTransactions() } returns listOf(swiggyOthers, uberOthers)
        every { tags.observeTransactionTagNames() } returns flowOf(emptyMap())
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    private fun call(name: String, json: String): Pair<Boolean, JsonObject?> = runBlocking {
        val r = tools.call(name, Json.parseToJsonElement(json).jsonObject)
        val text = r["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val isError = r["isError"]!!.jsonPrimitive.content.toBoolean()
        isError to if (isError) null else Json.parseToJsonElement(text).jsonObject
    }

    private fun create(extra: String) = """{"name":"Cafe","conditions":[{"field":"MERCHANT","operator":"CONTAINS","value":"Swiggy"}],
        "actions":[{"field":"CATEGORY","actionType":"SET","value":"Food"}]$extra}"""

    private fun JsonObject.count(key: String) = this[key]!!.jsonPrimitive.content.toInt()

    @Test
    fun `update changes only the supplied fields`() {
        val (err, out) = call("update_rule", """{"id":"r1","name":"Eating out"}""")
        assertFalse(err)
        assertEquals("Eating out", out!!["updated"]!!.jsonPrimitive.content)
        coVerify {
            rules.updateRule(match {
                it.id == "r1" && it.name == "Eating out" && it.description == "keep me" && it.priority == 7 &&
                    it.conditions == stored.conditions && it.actions == stored.actions && it.isActive
            })
        }
    }

    @Test
    fun `update with an unknown id is an error and writes nothing`() {
        val (err, _) = call("update_rule", """{"id":"gone","name":"x"}""")
        assertTrue(err)
        coVerify(exactly = 0) { rules.updateRule(any()) }
    }

    @Test
    fun `update refuses a name another rule has`() {
        val (err, _) = call("update_rule", """{"id":"r1","name":"built in"}""")
        assertTrue(err)
        coVerify(exactly = 0) { rules.updateRule(any()) }
    }

    @Test
    fun `update refuses the hidden SMS text placeholder`() {
        val (err, _) = call(
            "update_rule",
            """{"id":"r1","conditions":[{"field":"SMS_TEXT","operator":"CONTAINS","value":"${AiToolLogic.HIDDEN_SMS_TEXT}"}]}""",
        )
        assertTrue(err)
        coVerify(exactly = 0) { rules.updateRule(any()) }
    }

    @Test
    fun `delete removes the rule, and an unknown or built-in id is an error`() {
        assertFalse(call("delete_rule", """{"id":"r1"}""").first)
        coVerify(exactly = 1) { rules.deleteRule("r1") }
        assertTrue(call("delete_rule", """{"id":"gone"}""").first)
        assertTrue(call("delete_rule", """{"id":"sys"}""").first)
        assertTrue(call("delete_rule", "{}").first)
        coVerify(exactly = 1) { rules.deleteRule(any()) }
    }

    @Test
    fun `update and delete refuse while changes from AI apps are off`() {
        writeAllowed.value = false
        assertTrue(call("update_rule", """{"id":"r1","name":"x"}""").first)
        assertTrue(call("delete_rule", """{"id":"r1"}""").first)
        coVerify(exactly = 0) { rules.updateRule(any()) }
        coVerify(exactly = 0) { rules.deleteRule(any()) }
    }

    @Test
    fun `apply_to_past none uncategorized and all report how many changed`() {
        assertEquals(null, call("create_rule", create("")).second!!.getOrNull("past_transactions_updated"))
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }

        assertEquals(1, call("create_rule", create(""","apply_to_past":"uncategorized"""")).second!!.count("past_transactions_updated"))
        assertEquals(2, call("create_rule", create(""","apply_to_past":"all"""")).second!!.count("past_transactions_updated"))
        // An older app client sent a boolean: true meant uncategorized.
        assertEquals(1, call("create_rule", create(""","apply_to_past":true""")).second!!.count("past_transactions_updated"))
        assertTrue(call("create_rule", create(""","apply_to_past":"sometimes"""")).first)
    }

    @Test
    fun `update applies to past only when asked`() {
        assertEquals(null, call("update_rule", """{"id":"r1","name":"A"}""").second!!.getOrNull("past_transactions_updated"))
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        assertEquals(2, call("update_rule", """{"id":"r1","apply_to_past":"all"}""").second!!.count("past_transactions_updated"))
    }

    private fun verifyNothingWritten() {
        coVerify(exactly = 0) { rules.insertRule(any()) }
        coVerify(exactly = 0) { rules.updateRule(any()) }
        coVerify(exactly = 0) { rules.deleteRule(any()) }
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        coVerify(exactly = 0) { rules.saveRuleApplications(any()) }
    }

    @Test
    fun `preview_rule counts for uncategorized and all and changes nothing`() {
        val all = call("preview_rule", create(""","apply_to_past":"all"""")).second!!
        assertEquals(2, all.count("past_transactions_would_update"))
        assertEquals("all", all["apply_to_past"]!!.jsonPrimitive.content)
        assertEquals("Cafe", all["rule"]!!.jsonPrimitive.content)
        // Uncategorized is the default.
        val dflt = call("preview_rule", create("")).second!!
        assertEquals(1, dflt.count("past_transactions_would_update"))
        assertEquals("uncategorized", dflt["apply_to_past"]!!.jsonPrimitive.content)
        verifyNothingWritten()
    }

    @Test
    fun `preview_rule with an id previews the rule as update_rule would build it`() {
        val renamed = call("preview_rule", """{"id":"r1","name":"Renamed","apply_to_past":"all"}""").second!!
        assertEquals("Renamed", renamed["rule"]!!.jsonPrimitive.content)
        assertEquals(2, renamed.count("past_transactions_would_update"))
        val uber = call(
            "preview_rule",
            """{"id":"r1","apply_to_past":"all","conditions":[{"field":"MERCHANT","operator":"CONTAINS","value":"Uber"}]}"""
        ).second!!
        assertEquals(1, uber.count("past_transactions_would_update"))
        verifyNothingWritten()
    }

    @Test
    fun `preview_rule refuses an unknown id, none, a duplicate name and an invalid rule`() {
        assertTrue(call("preview_rule", """{"id":"gone"}""").first)
        assertTrue(call("preview_rule", create(""","apply_to_past":"none"""")).first)
        assertTrue(call("preview_rule", create("").replace("Cafe", "food")).first)
        assertTrue(call("preview_rule", """{"name":"x"}""").first)
        verifyNothingWritten()
    }

    @Test
    fun `preview_rule works while changes from AI apps are off`() {
        writeAllowed.value = false
        assertEquals(1, call("preview_rule", create("")).second!!.count("past_transactions_would_update"))
    }

    @Test
    fun `create and update refuse dry_run instead of saving`() {
        assertTrue(call("create_rule", create(""","dry_run":true""")).first)
        assertTrue(call("update_rule", """{"id":"r1","name":"x","dry_run":true}""").first)
        verifyNothingWritten()
    }

    @Test
    fun `list_rules gives each rule its id and leaves built-in rules out`() {
        val (_, out) = call("list_rules", "{}")
        val listed = out!!["rules"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("r1"), listed.map { it["id"]!!.jsonPrimitive.content })
        assertEquals("Food", listed.single()["name"]!!.jsonPrimitive.content)
    }

    private fun JsonObject.getOrNull(key: String) = this[key]
}
