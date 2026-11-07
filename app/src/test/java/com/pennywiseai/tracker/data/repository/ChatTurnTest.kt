package com.pennywiseai.tracker.data.repository

import android.content.Context
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ChatContext
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** One chat turn: what reaches the model, and what the tool calls turn into. */
class ChatTurnTest {
    private val saved = mutableListOf<ChatMessage>()
    private val chatDao = mockk<ChatDao>(relaxed = true) {
        coEvery { getAllMessagesForContext() } answers { saved.toList() }
        coEvery { insertMessage(any()) } answers { saved += firstArg<ChatMessage>() }
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
        coEvery { getChatContext() } returns ChatContext(
            currentDate = LocalDate.of(2026, 11, 4),
            monthSummary = mockk(relaxed = true),
            recentTransactions = emptyList(),
            activeSubscriptions = emptyList(),
            topCategories = emptyList(),
            quickStats = mockk(relaxed = true)
        )
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

    private fun add(amount: Double) =
        LlmEvent.ToolCall(PennyWiseTools.ADD_TRANSACTION, mapOf("amount" to amount, "merchant" to "shop"))

    private suspend fun send(message: String) = repository.sendMessageStream(message).toList().joinToString("")

    @Test
    fun `the first card stays and the user hears the rest were not proposed`() = runTest {
        events = listOf(add(120.0), add(300.0))
        val reply = send("coffee 120 and lunch 300")
        val card = repository.pendingAction.value as PendingChatAction.Add
        assertEquals(0, BigDecimal("120").compareTo(card.draft.amount))
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

    @Test
    fun `a rebuilt conversation gets a fresh prompt, not the row saved when the chat began`() = runTest {
        saved += ChatMessage(message = "Today is 2020-01-01.", isUser = false, isSystemPrompt = true)
        every { llmService.hasActiveConversation() } returns false
        val prompt = slot<String>()
        coEvery { llmService.createConversation(capture(prompt), any(), any()) } returns Result.success(Unit)
        send("hello")
        assertTrue(prompt.captured, prompt.captured.contains("Today is 2026-11-04."))
        verify { llmService.sendMessageEvents("hello") }
    }
}
