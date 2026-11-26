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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val off = stored.copy(id = "off", name = "Off", isActive = false)
    private val blocking = stored.copy(id = "blk", name = "Blocker", actions = listOf(RuleAction(TransactionField.CATEGORY, ActionType.BLOCK, "")))
    // A pairing the rule editor never offers, as an older build or an import could have stored it.
    private val legacy = stored.copy(id = "old", name = "Old", conditions = listOf(RuleCondition(TransactionField.MERCHANT, ConditionOperator.GREATER_THAN, "x")))

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
        coEvery { rules.getRuleById("off") } returns off
        coEvery { rules.getRuleById("blk") } returns blocking
        coEvery { rules.getRuleById("old") } returns legacy
        coEvery { transactions.getAllTransactionsList() } returns listOf(swiggyOthers, swiggyDining, uberOthers)
        coEvery { transactions.getUncategorizedTransactions() } returns listOf(swiggyOthers, uberOthers)
        every { tags.observeTransactionTagNames() } returns flowOf(emptyMap())
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT)

    private fun call(name: String, json: String): Pair<Boolean, JsonObject?> = runBlocking { callS(name, json) }

    /** An error answer comes back as {"error": message}. */
    private suspend fun callS(name: String, json: String): Pair<Boolean, JsonObject?> {
        val r = tools.call(name, Json.parseToJsonElement(json).jsonObject)
        val text = r["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        val isError = r["isError"]!!.jsonPrimitive.content.toBoolean()
        return isError to if (isError) JsonObject(mapOf("error" to JsonPrimitive(text))) else Json.parseToJsonElement(text).jsonObject
    }

    private fun error(name: String, json: String) = call(name, json).let { (isError, out) ->
        assertTrue(isError)
        out!!["error"]!!.jsonPrimitive.content
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

    private fun hidden(field: String, value: String = "A") = """{"field":"$field","operator":"CONTAINS","value":"$value"}"""

    private fun orJoined(field: String) = hidden(field).replace("}", ""","logicalOperator":"OR"}""")

    private val merchantCondition = """{"field":"MERCHANT","operator":"CONTAINS","value":"Swiggy","logicalOperator":"AND"}"""

    @Test
    fun `update refuses supplied conditions on SMS text or a note`() {
        for (field in listOf("SMS_TEXT", "NARRATION")) {
            assertTrue(error("update_rule", """{"id":"r1","conditions":[${hidden(field)}]}""").contains("created or changed"))
            assertTrue(error("update_rule", """{"id":"r1","conditions":[$merchantCondition,${orJoined(field)}]}""").contains("created or changed"))
        }
        coVerify(exactly = 0) { rules.updateRule(any()) }
    }

    @Test
    fun `a stored SMS text rule can be renamed but not given actions or conditions, and can be deleted`() {
        val smsRule = stored.copy(id = "sms", name = "Sms", conditions = listOf(RuleCondition(TransactionField.SMS_TEXT, ConditionOperator.CONTAINS, "real words")))
        coEvery { rules.getRuleById("sms") } returns smsRule
        assertFalse(call("update_rule", """{"id":"sms","name":"Renamed","description":"d","priority":2}""").first)
        coVerify { rules.updateRule(match { it.id == "sms" && it.name == "Renamed" && it.description == "d" && it.priority == 2 && it.conditions == smsRule.conditions && it.actions == smsRule.actions }) }
        val refusal = "so only its name, description and priority can be changed from here"
        assertTrue(error("update_rule", """{"id":"sms","actions":[{"field":"CATEGORY","actionType":"SET","value":"Food"}]}""").contains(refusal))
        assertTrue(error("update_rule", """{"id":"sms","conditions":[$merchantCondition]}""").contains(refusal))
        coVerify(exactly = 1) { rules.updateRule(any()) }
        assertFalse(call("delete_rule", """{"id":"sms"}""").first)
        coVerify(exactly = 1) { rules.deleteRule("sms") }
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

    private fun withActions(action: String, extra: String = "") =
        """{"name":"Cafe","conditions":[{"field":"MERCHANT","operator":"CONTAINS","value":"Swiggy"}],"actions":[$action]$extra}"""

    private val sms = """{"name":"Cafe","conditions":[{"field":"SMS_TEXT","operator":"CONTAINS","value":"A"}],
        "actions":[{"field":"CATEGORY","actionType":"SET","value":"Food"}]"""

    @Test
    fun `a rule on SMS text or a note can't be created or previewed, alone or OR-joined with an allowed condition`() {
        val action = """"actions":[{"field":"CATEGORY","actionType":"SET","value":"Food"}]"""
        for (field in listOf("SMS_TEXT", "NARRATION")) {
            val alone = """{"name":"Cafe","conditions":[${hidden(field)}],$action"""
            val joined = """{"name":"Cafe","conditions":[$merchantCondition,${orJoined(field)}],$action"""
            for (rule in listOf(alone, joined)) {
                for (extra in listOf("}", ""","apply_to_past":"none"}""", ""","apply_to_past":"all"}""")) {
                    assertTrue(error("create_rule", rule + extra).contains("created or changed"))
                }
                assertTrue(error("preview_rule", rule + "}").contains("created or changed"))
            }
        }
        coVerify(exactly = 0) { rules.insertRule(any()) }
        assertFalse(call("create_rule", create("")).first)
        coVerify(exactly = 1) { rules.insertRule(any()) }
    }

    @Test
    fun `a stored SMS text condition stops a preview too, or save-then-preview would leak it`() {
        val smsRule = stored.copy(id = "sms", name = "Sms", conditions = listOf(RuleCondition(TransactionField.SMS_TEXT, ConditionOperator.CONTAINS, "A")))
        coEvery { rules.getRuleById("sms") } returns smsRule
        assertTrue(error("preview_rule", """{"id":"sms","name":"Renamed"}""").contains("SMS text is hidden"))
        assertTrue(error("update_rule", """{"id":"sms","name":"Renamed","apply_to_past":"all"}""").contains("SMS text is hidden"))
    }

    @Test
    fun `a rule that writes a note can't be created or given such an action, but one the user made can be renamed`() {
        val setNote = """{"field":"NARRATION","actionType":"SET","value":"x"}"""
        assertTrue(error("create_rule", withActions(setNote)).contains("write a transaction's note"))
        assertTrue(error("preview_rule", withActions(setNote)).contains("write a transaction's note"))
        assertTrue(error("update_rule", """{"id":"r1","actions":[$setNote]}""").contains("write a transaction's note"))
        coVerify(exactly = 0) { rules.insertRule(any()) }
        coVerify(exactly = 0) { rules.updateRule(any()) }
        val noteRule = stored.copy(id = "n1", name = "Notes", actions = listOf(RuleAction(TransactionField.NARRATION, ActionType.SET, "mine")))
        coEvery { rules.getRuleById("n1") } returns noteRule
        assertFalse(call("update_rule", """{"id":"n1","name":"Renamed"}""").first)
    }

    @Test
    fun `an ordinary merchant rule still creates, updates, previews and applies`() {
        assertFalse(call("create_rule", create(""","apply_to_past":"all"""")).first)
        assertFalse(call("update_rule", """{"id":"r1","conditions":[$merchantCondition],"apply_to_past":"all"}""").first)
        assertFalse(call("preview_rule", create("")).first)
        assertFalse(call("preview_rule", """{"id":"r1"}""").first)
    }

    @Test
    fun `only repeat-safe actions are applied or previewed, others can still be saved with none`() {
        val append = """{"field":"MERCHANT","actionType":"APPEND","value":" [ai]"}"""
        val setType = """{"field":"TYPE","actionType":"SET","value":"INCOME"}"""
        val setBank = """{"field":"BANK_NAME","actionType":"SET","value":"Other"}"""
        assertTrue(error("create_rule", withActions(append, ""","apply_to_past":"all"""")).contains("appends text"))
        assertTrue(error("create_rule", withActions(setType, ""","apply_to_past":"uncategorized"""")).contains("changes more than categories or tags"))
        assertTrue(error("preview_rule", withActions(setBank)).contains("changes more than categories or tags"))
        assertTrue(error("preview_rule", withActions(append, ""","apply_to_past":"all"""")).contains("appends text"))
        assertTrue(error("update_rule", """{"id":"r1","apply_to_past":"all","actions":[$append]}""").contains("Apply it from PennyWise"))
        coVerify(exactly = 0) { rules.insertRule(any()) }
        coVerify(exactly = 0) { rules.updateRule(any()) }
        assertFalse(call("create_rule", withActions(append, ""","apply_to_past":"none"""")).first)
        assertFalse(call("update_rule", """{"id":"r1","actions":[$setType]}""").first)
        coVerify(exactly = 0) { transactions.updateTransaction(any()) }
        // Notes and merchant names stay out: the old text is lost with the rule, and a count would read notes.
        assertTrue(error("preview_rule", withActions("""{"field":"MERCHANT","actionType":"SET","value":"x"}""")).contains("changes more than categories or tags"))
        assertTrue(error("preview_rule", withActions("""{"field":"MERCHANT","actionType":"CLEAR","value":""}""")).contains("changes more than categories or tags"))
        // A note action is refused outright, before the past-run check.
        assertTrue(error("preview_rule", withActions("""{"field":"NARRATION","actionType":"CLEAR","value":""}""")).contains("write a transaction's note"))
        // Setting a category and adding a tag repeat safely.
        val safe = """{"field":"CATEGORY","actionType":"SET","value":"Food"},{"field":"TAGS","actionType":"ADD_TAG","value":"trip"}"""
        assertFalse(call("preview_rule", withActions(safe)).first)
    }

    @Test
    fun `a switched-off rule is not applied or previewed, but can be saved with none and stays off`() {
        assertTrue(error("update_rule", """{"id":"off","apply_to_past":"all"}""").contains("switched off"))
        assertTrue(error("preview_rule", """{"id":"off"}""").contains("Turn it on in PennyWise"))
        coVerify(exactly = 0) { rules.updateRule(any()) }
        assertFalse(call("update_rule", """{"id":"off","name":"Still off"}""").first)
        coVerify { rules.updateRule(match { it.id == "off" && !it.isActive }) }
    }

    @Test
    fun `a built-in rule can't be updated or previewed, and a blocking rule can't be deleted`() {
        assertTrue(error("update_rule", """{"id":"sys","name":"x"}""").contains("built-in"))
        assertTrue(error("preview_rule", """{"id":"sys"}""").contains("built-in"))
        assertTrue(error("delete_rule", """{"id":"blk"}""").contains("blocks transactions"))
        assertTrue(error("update_rule", """{"id":"blk","name":"x"}""").contains("blocks transactions"))
        coVerify(exactly = 0) { rules.updateRule(any()) }
        coVerify(exactly = 0) { rules.deleteRule(any()) }
    }

    @Test
    fun `a stored rule the validator rejects is reported as such, not blamed on the caller`() {
        assertEquals(
            "This rule uses a condition that can't be written from here. Edit it in PennyWise → Rules.",
            error("update_rule", """{"id":"old","name":"x"}"""),
        )
        // Supplying the conditions replaces the stored ones, so that rule can be repaired from here.
        assertFalse(call("update_rule", """{"id":"old","conditions":[{"field":"MERCHANT","operator":"CONTAINS","value":"x"}]}""").first)
    }

    @Test
    fun `the past run holds the rule lock, so a delete waits for it`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        coEvery { transactions.getUncategorizedTransactions() } coAnswers {
            gate.await()
            listOf(swiggyOthers)
        }
        val update = async { callS("update_rule", """{"id":"r1","apply_to_past":"uncategorized"}""") }
        repeat(5) { yield() }
        val delete = async { callS("delete_rule", """{"id":"r1"}""") }
        repeat(5) { yield() }
        assertTrue(update.isActive)
        assertFalse(delete.isCompleted)
        coVerify(exactly = 0) { rules.deleteRule(any()) }
        gate.complete(Unit)
        assertFalse(update.await().first)
        assertFalse(delete.await().first)
        coVerify(exactly = 1) { rules.deleteRule("r1") }
    }

    @Test
    fun `a write queued behind a past run is refused if changes were turned off meanwhile`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        coEvery { transactions.getUncategorizedTransactions() } coAnswers {
            gate.await()
            listOf(swiggyOthers)
        }
        val update = async { callS("update_rule", """{"id":"r1","apply_to_past":"uncategorized"}""") }
        repeat(5) { yield() }
        val delete = async { callS("delete_rule", """{"id":"r1"}""") }
        repeat(5) { yield() }
        writeAllowed.value = false
        gate.complete(Unit)
        update.await()
        val (isError, out) = delete.await()
        assertTrue(isError)
        assertTrue(out!!["error"]!!.jsonPrimitive.content.contains("Changes from AI apps are off"))
        coVerify(exactly = 0) { rules.deleteRule(any()) }
    }

    @Test
    fun `apply_to_past that is not a word or a boolean is refused`() {
        assertTrue(error("create_rule", create(""","apply_to_past":{}""")).contains("apply_to_past must be"))
        assertTrue(error("create_rule", create(""","apply_to_past":[]""")).contains("apply_to_past must be"))
        coVerify(exactly = 0) { rules.insertRule(any()) }
    }
}
