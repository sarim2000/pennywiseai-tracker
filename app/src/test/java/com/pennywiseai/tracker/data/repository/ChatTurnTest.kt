package com.pennywiseai.tracker.data.repository

import android.content.Context
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.PendingChatAction
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.domain.service.LlmEvent
import com.pennywiseai.tracker.domain.service.LlmService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.pennywiseai.tracker.data.model.SpendingComparison
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** One chat turn: what reaches the model, and what the tool calls turn into. */
class ChatTurnTest {
    private val saved = mutableListOf<ChatMessage>()
    private val chatDao = mockk<ChatDao>(relaxed = true) {
        coEvery { getAllMessagesForContext() } answers { saved.toList() }
        coEvery { insertMessage(any()) } answers { saved += firstArg<ChatMessage>() }
        coEvery { deleteMessagesBefore(any()) } answers { saved.removeAll { it.timestamp < firstArg<Long>() } }
    }
    private val sent = mutableListOf<String>()
    private var events: List<LlmEvent> = emptyList()
    private var failure: Throwable? = null
    private val llmService = mockk<LlmService>(relaxed = true) {
        every { isInitialized() } returns true
        every { hasActiveConversation() } returns true
        every { sendMessageEvents(capture(sent)) } answers {
            flow {
                events.forEach { emit(it) }
                failure?.let { throw it }
            }
        }
    }
    private val aiContext = mockk<AiContextRepository>(relaxed = true) {
        coEvery { getMonthSummary() } returns mockk(relaxed = true)
    }
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
        every { baseCurrency } returns flowOf("INR")
    }
    private val categories = mockk<CategoryRepository>(relaxed = true) {
        every { getVisibleCategories() } returns flowOf(listOf(CategoryEntity(id = 1, name = "طعام", color = "#000")))
        every { getAllCategories() } returns flowOf(
            listOf(CategoryEntity(id = 1, name = "طعام", color = "#000"), CategoryEntity(id = 2, name = "Hobbies", color = "#000", isHidden = true))
        )
    }
    private val context = mockk<Context>(relaxed = true) {
        every { getString(R.string.chat_reply_one_card_at_a_time) } returns "ONE_CARD"
        every { getString(R.string.chat_reply_unreadable) } returns "UNREADABLE"
        every { getString(R.string.chat_reply_nothing_changed) } returns "NOTHING_CHANGED"
        every { getString(R.string.chat_reply_unknown_category, any()) } returns "UNKNOWN_CATEGORY"
        every { getString(R.string.chat_reply_category_spend, any(), any(), any()) } returns "SPEND"
        every { getString(R.string.chat_reply_nothing_spent, any()) } returns "NOTHING_SPENT"
    }
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val repository = LlmRepository(
        context = context,
        llmService = llmService,
        chatDao = chatDao,
        modelRepository = mockk { every { modelState } returns flowOf(ModelState.READY) },
        aiContextRepository = aiContext,
        userPreferencesRepository = preferences,
        categoryRepository = categories,
        accountBalanceRepository = mockk(relaxed = true),
        transactionRepository = transactions,
        ignoredAccountsStore = mockk(relaxed = true)
    )

    private fun add(vararg amounts: Double) = LlmEvent.ToolCall(
        PennyWiseTools.ADD_TRANSACTIONS,
        mapOf("items" to amounts.map { mapOf("amount" to it, "merchant" to "shop") })
    )

    // What the screen ends up showing: ChatViewModel folds a reset the same way.
    private suspend fun send(message: String) =
        repository.sendMessageStream(message).toList().fold("") { shown, part -> if (part == LlmRepository.RESET_REPLY) "" else shown + part }

    @Test
    fun `prose streamed before a comparison leaves the screen, and the save`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any()) } returns SpendingComparison.Result.NoData(w, w, w)
        every { context.getString(R.string.chat_reply_compare_no_data, any()) } returns "NO_DATA"
        events = listOf(LlmEvent.Text("You spent ₹999 more. "), LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month")))
        assertEquals("NO_DATA", send("compare with last month"))
        assertEquals("NO_DATA", saved.last().message)
    }

    @Test
    fun `a comparison for a category nobody has is unknown, not nothing spent`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any()) } returns SpendingComparison.Result.Rows(w, w, emptyList())
        every { context.getString(R.string.chat_reply_compare_nothing_category, any(), any(), any()) } returns "NOTHING"
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "Unicorns")))
        assertEquals("UNKNOWN_CATEGORY", send("compare unicorns with last month"))
        // A category the user has, with nothing spent, is still zero.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        assertEquals("NOTHING", send("قارن الطعام بالشهر الماضي"))
    }

    @Test
    fun `every add of a turn lands on one review card`() = runTest {
        events = listOf(add(120.0, 300.0))
        val reply = send("coffee 120 and lunch 300")
        val card = repository.pendingAction.value as PendingChatAction.Add
        assertEquals(listOf(0, 0), card.rows.zip(listOf("120", "300")).map { (row, want) -> BigDecimal(want).compareTo(row.draft.amount) })
        assertTrue(card.rows.all { it.selected })
        assertFalse(reply, reply.contains("ONE_CARD"))
    }

    @Test
    fun `a delete after an add waits, and the user hears it`() = runTest {
        events = listOf(add(120.0), LlmEvent.ToolCall(PennyWiseTools.DELETE_TRANSACTION, mapOf("merchant" to "shop")))
        val reply = send("coffee 120 and delete the shop")
        assertTrue(repository.pendingAction.value is PendingChatAction.Add)
        assertTrue(reply, reply.contains("ONE_CARD"))
    }

    @Test
    fun `bare newlines before a tool call leave no empty lines on top`() = runTest {
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        events = listOf(LlmEvent.Text("\n\n"), add(120.0))
        assertEquals("CARD", send("coffee 120"))
    }

    @Test
    fun `the model hears a confirmed card once, without it being shown or saved`() = runTest {
        events = listOf(add(120.0))
        send("coffee 120")
        repository.completePendingAction("Added ₹120 at shop (Food).")
        events = emptyList()
        send("how much this month?")
        send("and today?")
        assertEquals("(The user confirmed the last card. It is saved.)\nhow much this month?", sent[1])
        assertEquals("and today?", sent[2])
        assertTrue(saved.any { it.message == "how much this month?" && it.isUser })
        assertTrue(saved.any { it.message == "Added ₹120 at shop (Food)." && !it.isUser })
        assertTrue(saved.none { it.message.contains("confirmed the last card") })
    }

    @Test
    fun `the model hears a cancelled card, and nothing when no card was waiting`() = runTest {
        events = listOf(add(120.0))
        send("coffee 120")
        repository.clearPendingAction()
        events = emptyList()
        send("never mind")
        repository.clearPendingAction()
        send("thanks")
        assertEquals("(The user cancelled the last card. Nothing was changed.)\nnever mind", sent[1])
        assertEquals("thanks", sent[2])
    }

    @Test
    fun `clearing the chat forgets the outcome`() = runTest {
        events = listOf(add(120.0))
        send("coffee 120")
        repository.clearPendingAction()
        repository.deleteAllMessages()
        events = emptyList()
        send("hello")
        assertEquals("hello", sent.last())
    }

    @Test
    fun `an unreadable tool call becomes a reply and rebuilds the conversation`() = runTest {
        failure = RuntimeException("Status Code: 3. Message: Failed to parse tool calls from code block: {\"name\"")
        val reply = send("coffee")
        assertEquals("UNREADABLE", reply)
        assertTrue(saved.any { it.message == "UNREADABLE" && !it.isUser })
        coVerify { llmService.closeConversation() }
    }

    @Test(expected = IllegalStateException::class)
    fun `other engine errors still surface`() = runTest {
        failure = IllegalStateException("engine died")
        send("coffee")
    }

    @Test
    fun `a claimed change is corrected in Arabic and Hindi too`() = runTest {
        for (claim in listOf("I have added it", "تمت إضافة القهوة", "لقد سجلت المعاملة", "मैंने जोड़ दिया")) {
            events = listOf(LlmEvent.Text(claim))
            assertTrue(claim, send("coffee").contains("NOTHING_CHANGED"))
        }
        events = listOf(LlmEvent.Text("أنفقت 50 هذا الشهر"))
        assertFalse(send("coffee").contains("NOTHING_CHANGED"))
    }

    @Test
    fun `a category lookup matches non-Latin names and refuses unknown ones`() = runTest {
        coEvery { aiContext.getCategorySpending(any()) } returns null
        coEvery { aiContext.getCategorySpending("طعام") } returns mockk(relaxed = true)
        suspend fun lookup(category: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to category)))
            return send("how much on $category?")
        }
        assertEquals("SPEND", lookup("طعام"))
        assertEquals("UNKNOWN_CATEGORY", lookup("سفر"))
    }

    @Test
    fun `a category lookup reads hidden categories and names only found on transactions`() = runTest {
        coEvery { aiContext.getCategorySpending(any()) } returns null
        suspend fun lookup(category: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to category)))
            return send("how much on $category?")
        }
        assertEquals("NOTHING_SPENT", lookup("Hobbies")) // Hidden, but it exists.
        coEvery { aiContext.getCategorySpending("Hobbies") } returns mockk(relaxed = true)
        assertEquals("SPEND", lookup("Hobbies"))
        coEvery { aiContext.getCategorySpending("Old Name") } returns mockk(relaxed = true)
        assertEquals("SPEND", lookup("Old Name")) // No such category now, but rows still carry it.
    }

    @Test
    fun `a day the user stated beats the model's reading when finding a row`() = runTest {
        val now = LocalDateTime.now()
        fun row(id: Long, at: LocalDateTime) = TransactionEntity(
            id = id, amount = BigDecimal("50"), merchantName = "coffee", category = "Food",
            transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = "h$id"
        )
        every { transactions.getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } returns flowOf(listOf(row(1, now.minusDays(1)), row(2, now)))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.DELETE_TRANSACTION, mapOf("merchant" to "coffee", "amount" to 50.0, "days_ago" to 0)))
        send("delete coffee 50 from yesterday")
        assertEquals(1L, (repository.pendingAction.value as PendingChatAction.Delete).transaction.id)
    }

    private fun savedChat(pairs: Int) = repeat(pairs) { i ->
        saved += ChatMessage(message = "u$i", isUser = true, timestamp = i * 2L)
        saved += ChatMessage(message = "a$i", isUser = false, timestamp = i * 2L + 1)
    }

    @Test
    fun `a chat that outgrew the window drops its oldest turns from it, not the chat`() = runTest {
        savedChat(4)
        every { llmService.hasActiveConversation() } returns false
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } returns Result.success(Unit)
        coEvery { llmService.tokenCount() } returnsMany listOf(3500, 1500, 1600)
        send("hello")
        assertEquals(listOf(8, 4), histories.map { it.size })
        assertEquals("u2" to true, histories[1].first())
        assertEquals(10, saved.count { !it.isSystemPrompt }) // the screen keeps all of it, and the new turn
    }

    @Test
    fun `a message too long for any window is rejected without the model forgetting the chat`() = runTest {
        savedChat(4)
        every { llmService.hasActiveConversation() } returns false
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } returns Result.success(Unit)
        coEvery { llmService.tokenCount() } returnsMany listOf(3500, 3500, 3500, 3500, 1500)
        val rejected = runCatching { send("x".repeat(3000)) }.exceptionOrNull()
        assertEquals(LlmRepository.MESSAGE_TOO_LONG, rejected?.message)
        coVerify { llmService.closeConversation() }

        histories.clear()
        send("tea 20")
        // All of the chat again, without the message the model never took.
        assertEquals(8, histories.first().size)
        assertFalse(histories.first().any { it.first.startsWith("xxx") })
    }

    @Test
    fun `clearing the chat keeps a message sent while the clear waited for the model to load`() = runTest {
        savedChat(2)
        // Saved after the tap, while the clear queued behind the warm-up.
        saved += ChatMessage(message = "tea 20", isUser = true, timestamp = Long.MAX_VALUE)
        repository.deleteAllMessages()
        assertEquals(listOf("tea 20"), saved.map { it.message })
    }

    @Test
    fun `a reply with nothing in it says so instead of leaving an empty bubble`() = runTest {
        events = listOf(LlmEvent.Text("\n"))
        assertEquals("UNREADABLE", send("tea 20"))
    }

    @Test
    fun `a live conversation that filled up is rebuilt from the newer half straight away`() = runTest {
        savedChat(4)
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } returns Result.success(Unit)
        every { llmService.hasActiveConversation() } returns true
        coEvery { llmService.tokenCount() } returnsMany listOf(3500, 1500, 1600)
        send("hello")
        assertEquals(listOf(4), histories.map { it.size })
    }

    @Test
    fun `a history too long to prefill is halved, not reported`() = runTest {
        savedChat(4)
        every { llmService.hasActiveConversation() } returns false
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } returnsMany listOf(
            Result.failure(RuntimeException("Not enough space left in the context window for the input")),
            Result.success(Unit)
        )
        coEvery { llmService.tokenCount() } returns 1500
        send("hello")
        assertEquals(listOf(8, 4), histories.map { it.size })
    }

    @Test
    fun `a window that fills before any reply is retried once with fewer turns`() = runTest {
        savedChat(4)
        every { llmService.hasActiveConversation() } returns false
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } returns Result.success(Unit)
        coEvery { llmService.tokenCount() } returns 1000
        var calls = 0
        every { llmService.sendMessageEvents(any()) } answers {
            if (calls++ == 0) flow { throw RuntimeException("Max number of tokens reached, the context window is full.") }
            else flow { emit(LlmEvent.Text("ok")) }
        }
        assertEquals("ok", send("hello"))
        assertEquals(listOf(8, 4), histories.map { it.size })
    }

    @Test
    fun `a rebuilt conversation gets a fresh prompt, not the row saved when the chat began`() = runTest {
        saved += ChatMessage(message = "Today is 2020-01-01.", isUser = false, isSystemPrompt = true)
        every { llmService.hasActiveConversation() } returns false
        val prompt = slot<String>()
        coEvery { llmService.createConversation(capture(prompt), any(), any()) } returns Result.success(Unit)
        send("hello")
        assertTrue(prompt.captured, prompt.captured.contains("Today is ${LocalDate.now()}."))
        verify { llmService.sendMessageEvents("hello") }
    }

    @Test
    fun `an add call the runtime let through as text becomes a card, not a reply`() = runTest {
        every { context.getString(R.string.chat_reply_add_many) } returns "CARD"
        val call = "<tool_call>\n{\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 300, \"merchant\": \"shoes\"}, {\"amount\": 150, \"merchant\": \"socks\"}]}}\n\nI have recorded your expenses."
        events = listOf(LlmEvent.Text(call))
        assertEquals("CARD", send("shoes 300 and socks 150"))
        assertEquals(2, (repository.pendingAction.value as PendingChatAction.Add).rows.size)
        assertFalse(saved.any { it.message.contains("tool_call") })
    }

    @Test
    fun `a reply that claims a save without a tool call is corrected`() = runTest {
        events = listOf(LlmEvent.Text("₹52,000 income is added successfully."))
        assertTrue(send("got my salary 52000").contains("NOTHING_CHANGED"))
        events = listOf(LlmEvent.Text("Transaction added successfully!"))
        assertTrue(send("coffee 50").contains("NOTHING_CHANGED"))
        // The subject can be anything.
        events = listOf(LlmEvent.Text("Your groceries, milk 62, eggs 90, bread 45 have been recorded."))
        assertTrue(send("groceries").contains("NOTHING_CHANGED"))
        events = listOf(LlmEvent.Text("Your coffee at Starbucks has been updated to 60."))
        assertTrue(send("change my coffee to 60").contains("NOTHING_CHANGED"))
        events = listOf(LlmEvent.Text("Your rent has been changed by fifty rupees."))
        assertTrue(send("rent is 50 more").contains("NOTHING_CHANGED"))
        // Ordinary advice isn't a claim.
        events = listOf(LlmEvent.Text("Sugar is added to many packaged foods."))
        assertFalse(send("is soda bad?").contains("NOTHING_CHANGED"))
        events = listOf(LlmEvent.Text("The interest was changed by the bank."))
        assertFalse(send("why is my bill higher?").contains("NOTHING_CHANGED"))
    }

    @Test
    fun `prose that mentions the tool stays a reply`() = runTest {
        val prose = "Use add_transactions with {\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 50}]}}."
        events = listOf(LlmEvent.Text(prose))
        assertEquals(prose, send("show me the json for coffee 50"))
        assertEquals(null, repository.pendingAction.value)
    }

    @Test
    fun `the start of a tag isn't shown, and ordinary text after a false start is`() = runTest {
        events = listOf(LlmEvent.Text("<tool"), LlmEvent.Text("_call>\n{\"na"))
        assertFalse(send("x").contains("<tool"))
        events = listOf(LlmEvent.Text("<"), LlmEvent.Text("5 is small"))
        assertEquals("<5 is small", send("y"))
    }

    @Test
    fun `a rescued add waits behind a card already proposed`() = runTest {
        val transaction = mockk<TransactionEntity>(relaxed = true)
        every { transaction.merchantName } returns "shop"
        every { transaction.isDeleted } returns false
        every { transaction.amount } returns BigDecimal.TEN
        every { transaction.currency } returns "INR"
        every { transaction.dateTime } returns LocalDateTime.now()
        every { transactions.getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } returns flowOf(listOf(transaction))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.DELETE_TRANSACTION, mapOf("merchant" to "shop")))
        failure = RuntimeException("Failed to parse tool calls from code block: {\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 30, \"merchant\": \"lunch\"}]}};")
        val reply = send("delete the shop and add lunch 30")
        assertTrue(repository.pendingAction.value is PendingChatAction.Delete)
        assertTrue(reply, reply.contains("ONE_CARD"))
    }

    @Test
    fun `a call lost after a readable one is still reported`() = runTest {
        events = listOf(add(50.0))
        failure = RuntimeException("Failed to parse tool calls from code block: {\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amou")
        val reply = send("coffee 50 and lunch 100")
        assertTrue(repository.pendingAction.value is PendingChatAction.Add)
        assertTrue(reply, reply.contains("UNREADABLE"))
    }

    @Test
    fun `text held back in case of a tag isn't lost, or left in front of a card`() = runTest {
        events = listOf(LlmEvent.Text("<"))
        assertEquals("<", send("x"))
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        events = listOf(LlmEvent.Text("<"), add(120.0))
        assertEquals("CARD", send("coffee 120"))
        assertEquals("CARD", saved.last().message)
    }

    @Test
    fun `a half-written call shows no JSON and says it wasn't read`() = runTest {
        events = listOf(LlmEvent.Text("<tool_call>\n{\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amou"))
        every { context.getString(R.string.chat_reply_unreadable) } returns "Nothing was changed."
        val reply = send("shoes 300")
        assertFalse(reply, reply.contains("tool_call"))
        assertFalse(reply, reply.contains("NOTHING_CHANGED"))
        assertEquals(null, repository.pendingAction.value)
        coVerify { llmService.closeConversation() }
    }
}
