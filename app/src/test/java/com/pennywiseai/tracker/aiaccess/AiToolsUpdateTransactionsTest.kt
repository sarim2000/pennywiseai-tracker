package com.pennywiseai.tracker.aiaccess

import com.pennywiseai.tracker.billing.EntitlementGate
import com.pennywiseai.tracker.core.TransactionWidgets
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.TagRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
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
import java.time.LocalDate
import java.time.LocalDateTime

private const val ROOM_COMPAT_UT = "com.pennywiseai.tracker.data.database.RoomCompatKt"

/** update_transactions through [AiTools.call], over mocked storage. */
class AiToolsUpdateTransactionsTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val tags = mockk<TagRepository>(relaxed = true)
    private val categories = mockk<CategoryRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val widgets = mockk<TransactionWidgets>(relaxed = true)
    private val writeAllowed = MutableStateFlow(true)
    private val tools: AiTools

    private val coffee = TransactionEntity(
        id = 7, amount = BigDecimal("12"), merchantName = "Test Cafe", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 5, 12, 0),
        transactionHash = "h7", currency = "INR",
    )
    private val deleted = coffee.copy(id = 8, transactionHash = "h8", isDeleted = true)
    private val split = coffee.copy(id = 9, transactionHash = "h9")

    init {
        val gate = mockk<EntitlementGate> { every { isProEntitled } returns MutableStateFlow(true) }
        val access = mockk<AiWriteAccess> { every { allowed } returns writeAllowed }
        tools = AiTools(
            transactions, mockk(relaxed = true), mockk(relaxed = true), gate, categories, mockk(relaxed = true),
            mockk(relaxed = true), tags, mockk(relaxed = true), mockk(relaxed = true), database, access, widgets,
        )
    }

    @Before
    fun setUp() {
        mockkStatic(ROOM_COMPAT_UT)
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
        every { categories.getAllCategories() } returns flowOf(listOf(CategoryEntity(name = "Dining", color = "#fff"), CategoryEntity(name = "Travel", color = "#fff"), CategoryEntity(name = "Salary", color = "#fff", isIncome = true), CategoryEntity(name = "Old", color = "#fff", isHidden = true)))
        coEvery { transactions.getTransactionById(7) } returns coffee
        coEvery { transactions.getTransactionById(404) } returns null
        coEvery { transactions.getTransactionById(8) } returns deleted
        coEvery { transactions.getTransactionById(9) } returns split
        coEvery { transactions.hasSplits(9) } returns true
        coEvery { tags.getTagNamesForTransaction(any()) } returns emptyList()
        every { tags.observeTransactionTagNames() } returns flowOf(emptyMap())
        every { tags.observeAllTagNames() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() = unmockkStatic(ROOM_COMPAT_UT)

    private fun call(name: String, json: String): Pair<Boolean, JsonObject> = runBlocking {
        val r = tools.call(name, Json.parseToJsonElement(json).jsonObject)
        val text = r["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        r["isError"]!!.jsonPrimitive.content.toBoolean() to (if (text.startsWith("{")) Json.parseToJsonElement(text).jsonObject else JsonObject(emptyMap()))
    }

    private fun results(json: String): List<Pair<String, String?>> {
        val (err, out) = call("update_transactions", json)
        assertFalse(err)
        return out["results"]!!.jsonArray.map { it.jsonObject["result"]!!.jsonPrimitive.content to it.jsonObject["reason"]?.jsonPrimitive?.content }
    }

    @Test
    fun `the id search returns is the one update takes`() {
        coEvery { transactions.getTransactionsBetweenDates(any<LocalDate>(), any<LocalDate>()) } returns flowOf(listOf(coffee))
        val (_, found) = call("search_transactions", """{"from":"2026-10-01","to":"2026-10-31"}""")
        val id = found["transactions"]!!.jsonArray[0].jsonObject["id"]!!.jsonPrimitive.content
        assertEquals("7", id)
        assertEquals(listOf("updated"), results("""{"updates":[{"id":"$id","category":"dining"}]}""").map { it.first })
        coVerify { transactions.updateCategory(7, "Dining") }
    }

    @Test
    fun `a mixed list answers each row in order`() {
        val r = results(
            """{"updates":[{"id":"7","category":"Travel","tags":["Trip","work"],"excluded_from_analytics":true},
            {"id":"404","category":"Travel"},{"id":"7x","tags":[]},{"id":"7","category":"Nope"},{"id":"8","category":"Travel"},
            {"id":"9","category":"Travel"},{"id":"10","amount":5},"oops"]}"""
        )
        assertEquals(listOf("updated", "refused", "refused", "refused", "refused", "refused", "refused", "refused"), r.map { it.first })
        assertEquals("not found", r[1].second)
        assertEquals("not found", r[2].second)
        // Row 3 is a repeat of id 7.
        assertEquals("duplicate id in this call", r[3].second)
        assertEquals("not found", r[4].second)
        assertTrue(r[5].second!!.contains("split"))
        assertTrue(r[6].second!!.contains("\"amount\""))
        coVerify(exactly = 1) { transactions.updateCategory(any(), any()) }
        coVerify { tags.setTagsForTransaction(7, listOf("Trip", "work")) }
        coVerify { transactions.setExcludedFromAnalytics(7, true) }
    }

    @Test
    fun `an unknown category refuses only that row`() {
        val r = results("""{"updates":[{"id":"7","category":"Nope"}]}""")
        assertTrue(r[0].second!!.startsWith("No category"))
        coVerify(exactly = 0) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `sending the same values again writes nothing`() {
        val done = coffee.copy(category = "Dining", excludedFromAnalytics = true)
        coEvery { transactions.getTransactionById(7) } returns done
        coEvery { tags.getTagNamesForTransaction(7) } returns listOf("work", "Trip")
        val r = results("""{"updates":[{"id":"7","category":"Dining","tags":["trip","Work"],"excluded_from_analytics":true}]}""")
        assertEquals(listOf("unchanged"), r.map { it.first })
        coVerify(exactly = 0) { transactions.updateCategory(any(), any()) }
        coVerify(exactly = 0) { tags.setTagsForTransaction(any(), any()) }
        coVerify(exactly = 0) { transactions.setExcludedFromAnalytics(any(), any()) }
    }

    @Test
    fun `writes off refuses the whole call`() {
        writeAllowed.value = false
        val (err, _) = call("update_transactions", """{"updates":[{"id":"7","category":"Dining"}]}""")
        assertTrue(err)
        coVerify(exactly = 0) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `an unknown key refuses the row and names it`() {
        val r = results("""{"updates":[{"id":"7","type":"INCOME"}]}""")
        assertEquals("refused", r[0].first)
        assertTrue(r[0].second!!.contains("\"type\""))
        coVerify(exactly = 0) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `more than 100 updates is refused as a whole`() {
        val many = (1..101).joinToString(",") { """{"id":"7","category":"Dining"}""" }
        val (err, _) = call("update_transactions", """{"updates":[$many]}""")
        assertTrue(err)
        coVerify(exactly = 0) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `a repeated id is refused after the first`() {
        val r = results("""{"updates":[{"id":"7","category":"Dining"},{"id":"7","category":"Travel"}]}""")
        assertEquals(listOf("updated", "refused"), r.map { it.first })
        assertEquals("duplicate id in this call", r[1].second)
        coVerify(exactly = 1) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `malformed input is an error or a row refusal, never a write`() {
        assertTrue(call("update_transactions", """{"updates":"x"}""").first)
        assertTrue(call("update_transactions", """{}""").first)
        val r = results("""{"updates":[{"category":"Dining"},{"id":7,"category":"Dining"},{"id":"7","tags":"a"},{"id":"7","excluded_from_analytics":"yes"},{"id":"7"}]}""")
        assertTrue(r.all { it.first == "refused" })
        coVerify(exactly = 0) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `switch turned off mid batch stops the remaining rows`() {
        coEvery { transactions.updateCategory(7, any()) } coAnswers { writeAllowed.value = false }
        coEvery { transactions.getTransactionById(10) } returns coffee.copy(id = 10, transactionHash = "h10")
        val r = results("""{"updates":[{"id":"7","category":"Dining"},{"id":"10","category":"Dining"}]}""")
        assertEquals(listOf("updated", "refused"), r.map { it.first })
        assertTrue(r[1].second!!.contains("turned off"))
        coVerify(exactly = 1) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `only canonical ids resolve`() {
        val r = results("""{"updates":[{"id":"07","category":"Dining"},{"id":"+7","category":"Dining"},{"id":"7","category":"Dining"},{"id":"7 ","category":"Dining"}]}""")
        assertEquals(listOf("refused", "refused", "updated", "refused"), r.map { it.first })
        assertEquals("not found", r[0].second)
        assertEquals("not found", r[1].second)
        assertEquals("duplicate id in this call", r[3].second)
        coVerify(exactly = 1) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `a category must fit the row's type and be visible`() {
        coEvery { transactions.getTransactionById(11) } returns coffee.copy(id = 11, transactionHash = "h11", transactionType = TransactionType.INCOME)
        coEvery { transactions.getTransactionById(12) } returns coffee.copy(id = 12, transactionHash = "h12", transactionType = TransactionType.TRANSFER)
        coEvery { transactions.getTransactionById(13) } returns coffee.copy(id = 13, transactionHash = "h13", transactionType = TransactionType.TRANSFER)
        coEvery { transactions.getTransactionById(14) } returns coffee.copy(id = 14, transactionHash = "h14", transactionType = TransactionType.INCOME)
        coEvery { transactions.getTransactionById(15) } returns coffee.copy(id = 15, transactionHash = "h15")
        val r = results(
            """{"updates":[{"id":"7","category":"Salary"},{"id":"11","category":"Dining"},{"id":"14","category":"Salary"},
            {"id":"12","category":"Salary"},{"id":"13","category":"Travel"},{"id":"15","category":"Old"}]}"""
        )
        assertEquals(listOf("refused", "refused", "updated", "refused", "updated", "refused"), r.map { it.first })
        coVerify(exactly = 1) { transactions.updateCategory(13, "Travel") }
        coVerify(exactly = 1) { transactions.updateCategory(14, "Salary") }
        coVerify(exactly = 2) { transactions.updateCategory(any(), any()) }
    }

    @Test
    fun `tag names with control or invisible characters are refused`() {
        val r = results("""{"updates":[{"id":"7","tags":["a\u200bb"]},{"id":"10","tags":["x\u202ey"]},{"id":"11","tags":["\ufeffz"]},{"id":"12","tags":["t\u0007"]}]}""")
        assertTrue(r.all { it.first == "refused" })
        coVerify(exactly = 0) { tags.setTagsForTransaction(any(), any()) }
    }

    @Test
    fun `a call can create at most 20 new tags`() {
        every { tags.observeAllTagNames() } returns flowOf(listOf("Known"))
        val rows = (1..5).map { i -> coffee.copy(id = 20L + i, transactionHash = "t$i") }
        rows.forEach { coEvery { transactions.getTransactionById(it.id) } returns it }
        val updates = rows.mapIndexed { i, t ->
            val names = (1..8).joinToString(",") { "\"n${if (i == 4) 0 else i}_$it\"" }
            """{"id":"${t.id}","tags":["known",$names]}"""
        }
        val r = results("""{"updates":[${updates.joinToString(",")}]}""")
        // 8 + 8 new tags fit in 20; the third and fourth would make 24; the fifth reuses the first's, which are no longer new.
        assertEquals(listOf("updated", "updated", "refused", "refused", "updated"), r.map { it.first })
    }
}
