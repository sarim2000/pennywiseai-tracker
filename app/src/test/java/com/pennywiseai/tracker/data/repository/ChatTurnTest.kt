package com.pennywiseai.tracker.data.repository

import android.content.Context
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames
import com.pennywiseai.tracker.data.model.BillsDue
import com.pennywiseai.tracker.data.model.BudgetStatus
import com.pennywiseai.tracker.data.model.ChatLink
import com.pennywiseai.tracker.data.model.ChatScope
import com.pennywiseai.tracker.data.model.PendingChatAction
import com.pennywiseai.tracker.data.model.PeriodSpending
import com.pennywiseai.tracker.data.model.SpendingComparison
import com.pennywiseai.tracker.data.model.SpendingFilter
import com.pennywiseai.tracker.data.model.SpendingQuery
import com.pennywiseai.tracker.data.model.TopCategories
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
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
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale

/** One chat turn: what reaches the model, and what the tool calls turn into. */
class ChatTurnTest {
    private val saved = mutableListOf<ChatMessage>()
    private val chatDao = mockk<ChatDao>(relaxed = true) {
        coEvery { getAllMessagesForContext() } answers { saved.toList() }
        coEvery { insertMessage(any()) } answers { saved += firstArg<ChatMessage>() }
        coEvery { insertMessages(any()) } answers { saved += firstArg<List<ChatMessage>>() }
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
    private val aiContext = mockk<AiContextRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
        every { baseCurrency } returns flowOf("INR")
        every { countCreditCardAsExpense } returns flowOf(false)
        every { budgetCycleStartDay } returns flowOf(1)
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
        every { resources.getQuantityString(R.plurals.chat_reply_category_spend, any(), any(), any(), any(), any()) } returns "SPEND"
        every { getString(R.string.chat_reply_nothing_spent, any(), any()) } returns "NOTHING_SPENT"
    }
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val accountBalances = mockk<AccountBalanceRepository>(relaxed = true)
    private val planning = mockk<PlanningRepository>(relaxed = true)
    private var ignored = emptySet<String>()
    private val ignoredAccounts = mockk<IgnoredAccountsStore>(relaxed = true) {
        every { keys() } answers { ignored }
    }
    private val repository = LlmRepository(
        context = context,
        llmService = llmService,
        chatDao = chatDao,
        modelRepository = mockk { every { modelState } returns flowOf(ModelState.READY) },
        aiContextRepository = aiContext,
        userPreferencesRepository = preferences,
        categoryRepository = categories,
        accountBalanceRepository = accountBalances,
        transactionRepository = transactions,
        ignoredAccountsStore = ignoredAccounts,
        planningRepository = planning,
    ).apply { today = { LocalDate.of(2026, 11, 16) } }

    private fun add(vararg amounts: Double) = LlmEvent.ToolCall(
        PennyWiseTools.ADD_TRANSACTIONS,
        mapOf("items" to amounts.map { mapOf("amount" to it, "merchant" to "shop") })
    )

    // Everything the screen was shown during the turn, in order.
    private val shown = mutableListOf<String>()

    // What the screen ends up showing: ChatViewModel joins the parts the same way.
    private suspend fun send(message: String) = repository.sendMessageStream(message).toList().also { shown += it }.joinToString("")

    @Test
    fun `prose beside any tool call never reaches the screen or the save`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.NoData(w, w, w)
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(w),
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        every { context.getString(R.string.chat_reply_delete, any()) } returns "DELETE"
        every { context.getString(R.string.chat_reply_update, any(), any()) } returns "UPDATE"
        every { context.getString(R.string.chat_reply_compare_no_data, any()) } returns "NO_DATA"
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        val now = LocalDateTime.now()
        val row = TransactionEntity(
            id = 1, amount = BigDecimal("50"), merchantName = "coffee", category = "Food",
            transactionType = TransactionType.EXPENSE, dateTime = now, transactionHash = "h1"
        )
        every { transactions.getTransactionsBetweenDates(any<LocalDateTime>(), any<LocalDateTime>()) } returns flowOf(listOf(row))
        val calls = listOf(
            add(120.0),
            LlmEvent.ToolCall(PennyWiseTools.DELETE_TRANSACTION, mapOf("merchant" to "coffee")),
            LlmEvent.ToolCall(PennyWiseTools.UPDATE_TRANSACTION, mapOf("merchant" to "coffee", "new_category" to "طعام")),
            LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()),
            LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")),
            LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month")),
        )
        for (call in calls) {
            for (before in listOf(true, false)) {
                repository.clearPendingAction()
                shown.clear()
                val prose = LlmEvent.Text("Done, I saved ₹999 and you spent nine hundred. ")
                events = if (before) listOf(prose, call) else listOf(call, prose)
                val reply = send("coffee")
                val where = "${call.name}, prose ${if (before) "before" else "after"}"
                assertTrue(where, shown.none { "999" in it || "nine" in it })
                assertFalse(where, "999" in saved.last().message || "nine" in saved.last().message)
                assertEquals(where, reply, saved.last().message)
                assertFalse(where, "UNREADABLE" in reply)
            }
        }
        // Each of those turns left prose in the live conversation: the next one rebuilds without it.
        coVerify(exactly = calls.size * 2) { llmService.closeConversation() }
    }

    @Test
    fun `prose beside a card and a total in one turn is dropped, the app's lines kept`() = runTest {
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
        events = listOf(
            LlmEvent.Text("Sure! "),
            add(120.0),
            LlmEvent.Text("You have spent ₹5,000. "),
            LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")),
        )
        assertEquals("CARD\nSPEND", send("coffee 120, and how much on food?"))
        assertEquals(listOf("CARD\nSPEND"), shown)
    }

    @Test
    fun `a filler preamble beside a card keeps the live conversation, a claim doesn't`() = runTest {
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        events = listOf(LlmEvent.Text("Sure! "), add(120.0))
        assertEquals("CARD", send("coffee 120"))
        // Rebuilding would prefill the whole chat again for nothing that could mislead a later turn.
        coVerify(exactly = 0) { llmService.closeConversation() }

        // The card's own amount in the preamble says nothing the saved card line doesn't.
        repository.clearPendingAction()
        events = listOf(LlmEvent.Text("Adding ₹120 for coffee. "), add(120.0))
        assertEquals("CARD", send("coffee 120"))
        coVerify(exactly = 0) { llmService.closeConversation() }

        repository.clearPendingAction()
        events = listOf(LlmEvent.Text("Done, I've added it. "), add(120.0))
        assertEquals("CARD", send("coffee 120"))
        coVerify(exactly = 1) { llmService.closeConversation() }

        // Any other number could mislead the next turn.
        repository.clearPendingAction()
        events = listOf(LlmEvent.Text("Adding ₹150 for coffee. "), add(120.0))
        assertEquals("CARD", send("coffee 120"))
        coVerify(exactly = 2) { llmService.closeConversation() }
    }

    @Test
    fun `prose in front of a call that breaks is dropped for the note that nothing changed`() = runTest {
        events = listOf(LlmEvent.Text("I've added ₹300 for shoes. "))
        failure = RuntimeException("Failed to parse tool calls from code block: {\"name\"")
        assertEquals("UNREADABLE", send("shoes 300"))
        assertEquals(listOf("UNREADABLE"), shown)
    }

    @Test
    fun `a reply with no tool call is shown once, whole`() = runTest {
        events = listOf(LlmEvent.Text("Hello"), LlmEvent.Text(" there!"))
        assertEquals("Hello there!", send("hi"))
        assertEquals(listOf("Hello there!"), shown)
    }

    @Test
    fun `prose streamed before a comparison leaves the screen, and the save`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.NoData(w, w, w)
        every { context.getString(R.string.chat_reply_compare_no_data, any()) } returns "NO_DATA"
        events = listOf(LlmEvent.Text("You spent ₹999 more. "), LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month")))
        assertEquals("NO_DATA", send("compare with last month"))
        assertEquals("NO_DATA", saved.last().message)
        // The model must not remember the figures it wrote: the next turn rebuilds from the save.
        coVerify(exactly = 1) { llmService.closeConversation() }
    }

    @Test
    fun `prose streamed before a total leaves the screen, and the save`() = runTest {
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
        events = listOf(LlmEvent.Text("You've spent about ₹5,000 on food. "), LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("SPEND", send("how much on food?"))
        assertEquals("SPEND", saved.last().message)
        coVerify(exactly = 1) { llmService.closeConversation() }
    }

    @Test
    fun `a comparison with no prose keeps the live conversation`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.NoData(w, w, w)
        every { context.getString(R.string.chat_reply_compare_no_data, any()) } returns "NO_DATA"
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month")))
        send("compare with last month")
        coVerify(exactly = 0) { llmService.closeConversation() }
    }

    @Test
    fun `a comparison for a category nobody has is unknown, not nothing spent`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.Rows(w, w, emptyList())
        every { context.getString(R.string.chat_reply_compare_nothing_category, any(), any(), any()) } returns "NOTHING"
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "Unicorns")))
        assertEquals("UNKNOWN_CATEGORY", send("compare unicorns with last month"))
        // A category the user has, with nothing spent, is still zero.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        assertEquals("NOTHING", send("قارن الطعام بالشهر الماضي"))
        // "all" from the model means all spending.
        every { context.getString(R.string.chat_reply_compare_nothing, any(), any()) } returns "NOTHING_AT_ALL"
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "All")))
        assertEquals("NOTHING_AT_ALL", send("compare with last month"))
    }

    @Test
    fun `a category only transactions carry is that category, even when it reads like "all"`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("100"), BigDecimal.ZERO, null)))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "General")))
        send("compare general with last month")
        coVerify(exactly = 0) { aiContext.compareSpending(any(), null, any(), any()) }
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
    fun `a question's add call proposes nothing, and looks up what it named`() = runTest {
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        val filter = answerSpending()
        fun income(merchant: String?) = LlmEvent.ToolCall(
            PennyWiseTools.ADD_TRANSACTIONS,
            mapOf("items" to listOf(mapOf("amount" to 25.0, "merchant" to merchant, "type" to "INCOME")))
        )
        // What Qwen2.5 sent for it in 2 runs of 3.
        events = listOf(income("ستاربكس"))
        assertTrue(send("كم صرفت في ستاربكس هالشهر؟").startsWith("SPENT"))
        assertEquals(null, repository.pendingAction.value)
        assertEquals("ستاربكس", filter.captured.merchant)
        events = listOf(income("Starbucks"))
        assertTrue(send("how much did I spend at Starbucks").startsWith("SPENT"))
        assertEquals(null, repository.pendingAction.value)
        // Nothing to look up: the usual "couldn't read" reply, still no card.
        events = listOf(income(null))
        assertEquals("UNREADABLE", send("what did I spend this month?"))
        assertEquals(null, repository.pendingAction.value)

        // Asked to add, or stating the amount, it's still a card.
        events = listOf(income("Starbucks"))
        assertEquals("CARD", send("can you add 25 from Starbucks?"))
        repository.clearPendingAction()
        events = listOf(income("Starbucks"))
        assertEquals("CARD", send("got 25 back from Starbucks?"))
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
    fun `the latest reply is known by its id, with the tool it answered and its figure`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("80"), BigDecimal("100"), BigDecimal("-20"))))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month")))
        send("compare with last month")
        val compared = repository.lastReply.value!!
        assertEquals(saved.last().id, compared.messageId)
        assertEquals(PennyWiseTools.COMPARE_SPENDING, compared.tool)
        assertEquals(-1, compared.trend)

        events = listOf(LlmEvent.Text("Hello!"))
        send("hi")
        assertEquals(saved.last().id, repository.lastReply.value!!.messageId)
        assertEquals(null, repository.lastReply.value!!.tool)

        events = listOf(add(120.0))
        send("coffee 120")
        assertEquals(PennyWiseTools.ADD_TRANSACTIONS, repository.lastReply.value!!.tool)
        repository.completePendingAction("Added ₹120 at shop (Food).")
        val added = repository.lastReply.value!!
        assertEquals(saved.last().id, added.messageId)
        assertTrue(added.saved)

        repository.deleteAllMessages()
        assertEquals(null, repository.lastReply.value)
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
    fun `a claim after a cancelled card is replaced by what happened`() = runTest {
        every { context.getString(R.string.chat_line_expense, any(), any()) } answers { (args[1] as Array<*>).joinToString(" at ") }
        every { context.getString(R.string.chat_reply_card_cancelled, any()) } answers { "No — you cancelled ${(args[1] as Array<*>)[0]}." }
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.ADD_TRANSACTIONS, mapOf("items" to listOf(mapOf("amount" to 220.0, "merchant" to "TasteHouse")))))
        send("add 220 for lunch at TasteHouse")
        repository.clearPendingAction()
        events = listOf(LlmEvent.Text("Yes, I added lunch at TasteHouse for ₹220."))
        val reply = send("did you add the lunch?")
        assertTrue(reply, reply.startsWith("No — you cancelled ") && reply.contains("220") && reply.contains("TasteHouse"))
        assertFalse(reply, reply.contains("Yes") || reply.contains("NOTHING_CHANGED"))
        assertEquals(reply, saved.last().message)
        // The live conversation holds the claim: the next turn rebuilds from the save.
        coVerify { llmService.closeConversation() }
    }

    @Test
    fun `any wording of a claim about a cancelled card is replaced, a denial isn't`() = runTest {
        every { context.getString(R.string.chat_line_expense, any(), any()) } answers { (args[1] as Array<*>).joinToString(" at ") }
        every { context.getString(R.string.chat_reply_card_cancelled, any()) } answers { "CANCELLED" }
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.ADD_TRANSACTIONS, mapOf("items" to listOf(mapOf("amount" to 220.0, "merchant" to "TasteHouse")))))
        send("add 220 for lunch at TasteHouse")
        repository.clearPendingAction()
        for (claim in listOf("Yes, the lunch at TasteHouse for ₹220 was added this month.", "Done! I've logged ₹220 at TasteHouse.", "Yes, TasteHouse is in your spending.")) {
            events = listOf(LlmEvent.Text(claim))
            assertEquals(claim, "CANCELLED", send("did you add the lunch?"))
        }
        val denial = "You cancelled the TasteHouse card, so nothing was added."
        events = listOf(LlmEvent.Text(denial))
        assertEquals(denial, send("did you add the lunch?"))
        // A "yes" in another sentence isn't about the card.
        val help = "You cancelled the TasteHouse card, so nothing was added. Yes, I can help with your budget."
        events = listOf(LlmEvent.Text(help))
        assertEquals(help, send("did you add the lunch?"))
        // A "yes" about something else isn't about the card.
        events = listOf(LlmEvent.Text("Yes, you spent ₹900 on groceries."))
        assertEquals("Yes, you spent ₹900 on groceries.", send("did I spend on groceries?"))
    }

    @Test
    fun `a negation elsewhere in the sentence doesn't hide a claim`() = runTest {
        events = listOf(LlmEvent.Text("I have added your coffee, but I couldn't find an account."))
        assertEquals("NOTHING_CHANGED", send("coffee 50"))
        // A denial anywhere earlier in the claim's own clause stands.
        for (denial in listOf(
            "No transactions from your message were added because the amount is missing. Please tell me the amount.",
            "Sorry, nothing was added.",
        )) {
            events = listOf(LlmEvent.Text(denial))
            assertEquals(denial, send("coffee"))
        }
    }

    @Test
    fun `a saved card doesn't excuse a claim about a later request`() = runTest {
        events = listOf(add(220.0))
        send("lunch 220")
        repository.completePendingAction("Added ₹220 at shop (Food).")
        events = listOf(LlmEvent.Text("You spent ₹220 today."))
        send("how much today?")
        events = listOf(LlmEvent.Text("I added it."))
        assertEquals("NOTHING_CHANGED", send("coffee 50 at Starbucks"))
        // Still fine when it names the saved card.
        events = listOf(LlmEvent.Text("Yes, I added ₹220 at shop."))
        assertEquals("Yes, I added ₹220 at shop.", send("did the shop one save?"))
    }

    @Test
    fun `a claim with no card is replaced by the note alone`() = runTest {
        events = listOf(LlmEvent.Text("Yes, I added lunch."))
        assertEquals("NOTHING_CHANGED", send("did you add the lunch?"))
    }

    @Test
    fun `a claim after a confirmed card stands`() = runTest {
        events = listOf(add(220.0))
        send("lunch 220")
        repository.completePendingAction("Added ₹220 at shop (Food).")
        events = listOf(LlmEvent.Text("Yes, I added lunch for ₹220."))
        assertEquals("Yes, I added lunch for ₹220.", send("did you add the lunch?"))
    }

    @Test
    fun `the typed month summary and the one-tap answer give Home's figures, the same way`() = runTest {
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        every { context.getString(R.string.chat_reply_spent_with_forgiven, any(), any()) } returns "WITH_FORGIVEN"
        every { context.getString(R.string.chat_reply_converted_estimate, any(), any()) } returns "ESTIMATE"
        every { context.getString(R.string.chat_reply_converted_left_out, any()) } returns "LEFT_OUT"
        every { context.getString(R.string.chat_reply_received, any()) } returns "RECEIVED"
        every { context.resources.getQuantityString(R.plurals.chat_reply_transaction_count, 12, 12) } returns "COUNT"
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))),
            spent = mapOf("USD" to BigDecimal("5"), "INR" to BigDecimal("600")),
            income = mapOf("INR" to BigDecimal("5000")),
            forgiven = mapOf("INR" to BigDecimal("400")),
            estimate = com.pennywiseai.tracker.data.ledger.ConvertedEstimate("INR", BigDecimal("1000"), mapOf("XYZ" to BigDecimal("3"))),
            transactionCount = 12,
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()))
        val typed = send("how much did I spend this month?")
        // The user's own currency first, its forgiven loan named inside the figure; a gap in the estimate said.
        val lines = typed.lines()
        assertEquals(listOf("SPENT", "WITH_FORGIVEN"), lines.take(2))
        assertTrue(lines[2], lines[2].contains("5") && "$" in lines[2])
        // Typed, it also gives the count querySpending promises the model.
        assertEquals(listOf("ESTIMATE", "LEFT_OUT", "RECEIVED", "COUNT"), lines.drop(3))
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        assertEquals(lines.dropLast(1).joinToString("\n"), saved.last().message)
    }

    @Test
    fun `the welcome gives the estimate the answers give, and nothing spent in the currency Home shows`() = runTest {
        every { context.getString(R.string.chat_reply_converted_estimate, any(), any()) } returns "ESTIMATE"
        every { context.getString(R.string.chat_reply_converted_left_out, any()) } returns "LEFT_OUT"
        every { preferences.unifiedCurrencyMode } returns flowOf(true)
        every { preferences.displayCurrency } returns flowOf("USD")
        val scope = com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59)))
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            scope,
            spent = mapOf("USD" to BigDecimal("5"), "INR" to BigDecimal("600")),
            income = emptyMap(),
            estimate = com.pennywiseai.tracker.data.ledger.ConvertedEstimate("USD", BigDecimal("12.50"), mapOf("XYZ" to BigDecimal("3"))),
        )
        repository.getFormattedContextForDisplay()
        verify { context.getString(R.string.chat_welcome, "1 Nov–16 Nov", match<String> { it.lines().drop(1) == listOf("ESTIMATE", "LEFT_OUT") && "$" in it.lines()[0] }) }

        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(scope, spent = emptyMap(), income = emptyMap())
        repository.getFormattedContextForDisplay()
        verify { context.getString(R.string.chat_welcome, "1 Nov–16 Nov", match<String> { "$" in it && "₹" !in it && "\n" !in it }) }
    }

    @Test
    fun `a seeded profile is named in the app's language, on totals and comparisons alike`() = runTest {
        val business = ProfileEntity(ProfileEntity.BUSINESS_ID, "Business", "#000")
        every { context.getString(R.string.profile_default_business) } returns "عمل"
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59)), business),
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        verify { context.getString(R.string.chat_reply_month_spend, "1 Nov–16 Nov · عمل") }
        // Its link opens that profile's rows, whatever Home shows by the time it's tapped.
        assertEquals(listOf(ProfileEntity.BUSINESS_ID), repository.links.value[saved.last().id]?.map { it.profileId })

        // A comparison covers the same profile, so it says so too.
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))
        val rows = SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("700"), BigDecimal("500"), BigDecimal("40"))), profile = business)
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns rows
        repository.answerQuickAction(QuickAction.COMPARE_MONTH)
        verify { context.getString(R.string.chat_reply_compare_title, "1 Nov–16 Nov", "1 Nov–16 Nov · عمل") }
        assertEquals(listOf(ProfileEntity.BUSINESS_ID, ProfileEntity.BUSINESS_ID), repository.links.value[saved.last().id]?.map { it.profileId })

        // A renamed one shows as the user named it.
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns rows.copy(profile = business.copy(name = "Shop"))
        repository.answerQuickAction(QuickAction.COMPARE_MONTH)
        verify { context.getString(R.string.chat_reply_compare_title, "1 Nov–16 Nov", "1 Nov–16 Nov · Shop") }
    }

    @Test
    fun `a typed month summary says nothing was received, in no currency it had no activity in`() = runTest {
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        every { context.getString(R.string.chat_reply_received_nothing) } returns "NOTHING_RECEIVED"
        // The user's own currency is USD; all of the month was spent in rupees, and nothing came in.
        every { preferences.baseCurrency } returns flowOf("USD")
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))),
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()))
        val reply = send("how much did I earn this month?")
        // The question is answered, with no "Received: $0" in a currency nothing happened in.
        assertTrue(reply, "NOTHING_RECEIVED" in reply.lines())
        verify(exactly = 0) { context.getString(R.string.chat_reply_received, any()) }
    }

    @Test
    fun `a category's spend is per currency and dated`() = runTest {
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(category = "طعام", byCurrency = mapOf("USD" to BigDecimal("3"), "INR" to BigDecimal("120")))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("SPEND", send("how much on food?"))
        // Both currencies, the user's own first, never added; the dates it covers.
        verify {
            context.resources.getQuantityString(
                R.plurals.chat_reply_category_spend,
                1,
                match<String> { it.indexOf("120") in 0 until it.indexOf("3") && " · " in it },
                "طعام",
                "1 Nov–16 Nov",
                1
            )
        }
    }

    @Test
    fun `a total links to its days' transactions, one per currency, and only for this session`() = runTest {
        every { context.getString(R.string.chat_link_transactions) } returns "SEE"
        every { context.getString(R.string.chat_link_transactions_in, any()) } answers { "SEE ${(args[1] as Array<*>)[0]}" }
        val (nov1, nov16) = LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 16)
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(nov1.atStartOfDay(), nov16.atTime(23, 59))),
            spent = mapOf("USD" to BigDecimal("5"), "INR" to BigDecimal("600")),
            income = mapOf("AED" to BigDecimal("50")),
            forgiven = mapOf("EUR" to BigDecimal("9")),
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()))
        send("how much this month?")
        // The user's own currency first, each pinned; a forgiven loan has no transactions to show.
        assertEquals(
            listOf("INR", "AED", "USD").map { com.pennywiseai.tracker.data.model.ChatLink("SEE $it", nov1, nov16, null, it, null) },
            repository.links.value[saved.last().id]
        )

        // A category in one currency: one link, pinned, named after the category.
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(category = "طعام")
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        send("how much on food?")
        assertEquals(listOf(com.pennywiseai.tracker.data.model.ChatLink("SEE طعام", nov1, nov16, "طعام", "INR", null)), repository.links.value[saved.last().id])

        // Nothing found, nothing to open; the model's own words never get a link.
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent()
        send("how much on food?")
        events = listOf(LlmEvent.Text("Hello!"))
        send("hi")
        assertEquals(2, repository.links.value.size)

        // One-tap answers link the same way.
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        assertEquals(3, repository.links.value[saved.last().id]?.size)

        repository.deleteAllMessages()
        assertEquals(emptyMap<String, Any>(), repository.links.value)
    }

    @Test
    fun `a comparison links to both of its periods, each pinned to a currency`() = runTest {
        every { context.getString(R.string.chat_link_transactions_in, any()) } answers { "SEE ${(args[1] as Array<*>)[0]}" }
        val now = SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))
        val before = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 16, 23, 59))
        val (nov1, nov16) = LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 16)
        val (oct1, oct16) = LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 16)
        val inr = SpendingComparison.Row("INR", BigDecimal("700"), BigDecimal("500"), BigDecimal("40"))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.Rows(now, before, listOf(inr))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        send("compare food with last month")
        assertEquals(
            listOf(
                com.pennywiseai.tracker.data.model.ChatLink("SEE طعام · 1 Nov–16 Nov", nov1, nov16, "طعام", "INR", null),
                com.pennywiseai.tracker.data.model.ChatLink("SEE طعام · 1 Oct–16 Oct", oct1, oct16, "طعام", "INR", null),
            ),
            repository.links.value[saved.last().id]
        )

        // Several currencies: one pinned link per currency and period, so USD isn't behind the base.
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(now, before, listOf(inr.copy(currency = "USD"), inr))
        repository.answerQuickAction(QuickAction.COMPARE_MONTH)
        assertEquals(
            listOf(
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Nov–16 Nov · INR", nov1, nov16, null, "INR", null),
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Nov–16 Nov · USD", nov1, nov16, null, "USD", null),
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Oct–16 Oct · INR", oct1, oct16, null, "INR", null),
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Oct–16 Oct · USD", oct1, oct16, null, "USD", null),
            ),
            repository.links.value[saved.last().id]
        )

        // A currency only a forgiven loan has, or one a period spent nothing in, has no rows to open.
        val eurForgiven = SpendingComparison.Row("EUR", BigDecimal("5"), BigDecimal.ZERO, null)
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.Rows(
            now, before, listOf(eurForgiven, inr, inr.copy(currency = "USD", previous = BigDecimal.ZERO)),
            currentSpentIn = setOf("INR", "USD"), previousSpentIn = setOf("INR"),
        )
        repository.answerQuickAction(QuickAction.COMPARE_MONTH)
        assertEquals(
            listOf(
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Nov–16 Nov · INR", nov1, nov16, null, "INR", null),
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Nov–16 Nov · USD", nov1, nov16, null, "USD", null),
                com.pennywiseai.tracker.data.model.ChatLink("SEE 1 Oct–16 Oct", oct1, oct16, null, "INR", null),
            ),
            repository.links.value[saved.last().id]
        )
    }

    @Test
    fun `links from several lookups in a turn say what each opens, once each`() = runTest {
        every { context.getString(R.string.chat_link_transactions) } returns "SEE"
        every { context.getString(R.string.chat_link_transactions_in, any()) } answers { "SEE ${(args[1] as Array<*>)[0]}" }
        coEvery { aiContext.categorySpending("طعام", any(), any()) } returns spent("120").copy(category = "طعام")
        coEvery { aiContext.categorySpending("Hobbies", any(), any()) } returns spent("40").copy(category = "Hobbies")
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            spent("1").scope,
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        val food = LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام"))
        events = listOf(food, LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "Hobbies")), food,
            LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()))
        send("how much on food and hobbies, and this month?")
        assertEquals(listOf("SEE طعام", "SEE Hobbies", "SEE"), repository.links.value[saved.last().id]?.map { it.label })
    }

    @Test
    fun `a category figure that took refunds off says so, since its list doesn't hold them`() = runTest {
        every { context.getString(R.string.chat_reply_refunds_taken_off, any(), any()) } answers { "REFUNDED ${(args[1] as Array<*>)[1]}" }
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(category = "طعام", refunded = mapOf("INR" to BigDecimal("30")))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("SPEND\nREFUNDED ₹30", send("how much on food?"))

        // No refunds, no line.
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(category = "طعام")
        assertEquals("SPEND", send("how much on food?"))

        // A category comparison notes each period's refunds.
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))
        coEvery { aiContext.compareSpending(any(), "طعام", any(), any()) } returns SpendingComparison.Result.Rows(
            w, w, listOf(SpendingComparison.Row("INR", BigDecimal("90"), BigDecimal("100"), BigDecimal("-10"))),
            previousRefunded = mapOf("INR" to BigDecimal("5"))
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        assertTrue(send("food this month vs last month?").endsWith("\nREFUNDED ₹5"))
    }

    @Test
    fun `a category's list that nets another category's refund says it is lower, and by how much`() = runTest {
        every { context.getString(R.string.chat_reply_refunds_taken_off, any(), any()) } answers { "REFUNDED ${(args[1] as Array<*>)[1]}" }
        every { context.getString(R.string.chat_reply_refunds_list_lower, any(), any()) } answers { "LOWER ${(args[1] as Array<*>)[1]}" }
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("100").copy(category = "طعام", refunded = mapOf("INR" to BigDecimal("-10")))
        assertEquals("SPEND\nLOWER ₹10", send("how much on food?"))

        // Both ways at once, one currency each: each its own line.
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("100").copy(
            category = "طعام",
            byCurrency = mapOf("INR" to BigDecimal("100"), "USD" to BigDecimal("8")),
            refunded = mapOf("INR" to BigDecimal("-10"), "USD" to BigDecimal("2")),
        )
        assertEquals("SPEND\nREFUNDED $2\nLOWER ₹10", send("how much on food?"))
    }

    @Test
    fun `a figure that counted card purchases says the list totals them under Credit`() = runTest {
        every { context.getString(R.string.chat_reply_card_purchases) } returns "CARD"
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(category = "طعام", cardPurchases = true)
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("SPEND\nCARD", send("how much on food?"))

        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            spent("1").scope, spent = mapOf("INR" to BigDecimal("60")), income = emptyMap(), cardPurchases = true,
        )
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        assertTrue("CARD" in saved.last().message.lines())

        // None counted, no line.
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(category = "طعام")
        assertEquals("SPEND", send("how much on food?"))
    }

    /** A category lookup's result: nothing spent, or [inr] rupees. */
    private fun spent(inr: String? = null) = com.pennywiseai.tracker.data.model.CategorySpend(
        com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))),
        category = "x",
        byCurrency = inr?.let { mapOf("INR" to BigDecimal(it)) }.orEmpty(),
        transactionCount = if (inr == null) 0 else 1,
    )

    @Test
    fun `a category lookup matches non-Latin names and refuses unknown ones`() = runTest {
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent()
        coEvery { aiContext.categorySpending("طعام", any(), any()) } returns spent("120")
        suspend fun lookup(category: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to category)))
            return send("how much on $category?")
        }
        assertEquals("SPEND", lookup("طعام"))
        assertEquals("UNKNOWN_CATEGORY", lookup("سفر"))
    }

    @Test
    fun `a category lookup reads hidden categories and names only found on transactions`() = runTest {
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent()
        suspend fun lookup(category: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to category)))
            return send("how much on $category?")
        }
        assertEquals("NOTHING_SPENT", lookup("Hobbies")) // Hidden, but it exists.
        coEvery { aiContext.categorySpending("Hobbies", any(), any()) } returns spent("40")
        assertEquals("SPEND", lookup("Hobbies"))
        coEvery { aiContext.categorySpending("Old Name", any(), any()) } returns spent("15")
        assertEquals("SPEND", lookup("Old Name")) // No such category now, but rows still carry it.
        // The figure matched the rows in any case; the list would match the model's spelling
        // exactly and open on nothing, so there's no link.
        assertEquals(null, repository.links.value[saved.last().id])
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))
        coEvery { aiContext.compareSpending(any(), "Old Name", any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("15"), BigDecimal("5"), BigDecimal("200"))))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "Old Name")))
        send("compare old name with last month")
        assertEquals(null, repository.links.value[saved.last().id])
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
        saved += ChatMessage(id = LlmRepository.MODEL_ID + i, message = "a$i", isUser = false, timestamp = i * 2L + 1)
    }

    /** The screen closed and freed the model: the next message rebuilds the window from the save. */
    private fun rebuilt(): MutableList<List<Pair<String, Boolean>>> {
        every { llmService.hasActiveConversation() } returns false
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } returns Result.success(Unit)
        coEvery { llmService.tokenCount() } returns 1500
        return histories
    }

    private fun call(name: String, args: Map<String, Any?>) = LlmRepository.callsText(listOf(LlmEvent.ToolCall(name, args)))

    @Test
    fun `a call is replayed as Qwen writes it and the template renders it`() {
        assertEquals(
            "<tool_call>\n{\"name\": \"compare_spending\", \"arguments\": {\"period\": \"month\", \"category\": \"طعام \\\"x\\\"\", \"n\": 2}}\n</tool_call>\n" +
                "<tool_call>\n{\"name\": \"query_spending\", \"arguments\": {}}\n</tool_call>",
            LlmRepository.callsText(
                listOf(
                    LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام \"x\"", "n" to 2)),
                    LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()),
                )
            )
        )
    }

    @Test
    fun `a typed total is replayed as the call the model made, not the figures`() = runTest {
        savedChat(1)
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        send("how much on food?")
        assertEquals(listOf("u0", "a0", "how much on food?", "SPEND"), saved.filter { !it.isSystemPrompt }.map { it.message })

        // A follow-up in the live conversation goes on from the call the model made: nothing rebuilt.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        send("and last month?")
        coVerify(exactly = 0) { llmService.createConversation(any(), any(), any()) }

        val histories = rebuilt()
        events = listOf(LlmEvent.Text("hi"))
        send("and now?")
        // Replayed, any line standing for those figures was what the model wrote back to a new
        // question (the emulator, after a language change), instead of asking the tool.
        assertEquals(
            listOf(
                "u0" to true, "a0" to false,
                "how much on food?" to true, call(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")) to false,
                "and last month?" to true, call(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")) to false,
            ),
            histories.single()
        )
    }

    @Test
    fun `a rebuilt window holds no line the app wrote for the model to copy`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("700"), BigDecimal("500"), BigDecimal("40"))))
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
        coEvery { aiContext.spending(any(), any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(w),
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        every { context.getString(R.string.chat_reply_add, any()) } returns "ADD_CARD"
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "MONTH"
        events = listOf(LlmEvent.Text("Hello!"))
        send("hi")
        // Prose before the call: the live conversation holds it, so it closes.
        events = listOf(LlmEvent.Text("Let me check. "), LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        send("compare food with last month")
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()))
        send("how much this month?")
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        // A change the model claimed and didn't make: the app's line takes its place.
        events = listOf(LlmEvent.Text("I have added it"))
        assertEquals("NOTHING_CHANGED", send("coffee 50"))
        events = listOf(LlmEvent.Text("Sure! "), add(45.0))
        send("taxi 45")
        repository.completePendingAction("Added ₹45 at taxi (Transport).")

        val histories = rebuilt()
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("SPEND", send("كم صرفت على الطعام؟"))
        // Only the model's own words and calls: an app line replayed as its reply is one it can
        // write back. The correction's and the card's requests go with them: left open, the
        // model answers them again, and a confirmed card comes back to be saved twice.
        assertEquals(
            listOf(
                "hi" to true, "Hello!" to false,
                "compare food with last month" to true, call(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")) to false,
                "how much this month?" to true, call(PennyWiseTools.QUERY_SPENDING, emptyMap()) to false,
            ),
            histories.single()
        )
    }

    @Test
    fun `a reply saved before the model's replies were marked is not replayed, nor its question`() = runTest {
        savedChat(1)
        // As 2.26.0 and earlier saved every reply, the app's figures too.
        saved += ChatMessage(message = "how much on food?", isUser = true, timestamp = 10)
        saved += ChatMessage(message = "You spent ₹120 on food this month.", isUser = false, timestamp = 11)
        val histories = rebuilt()
        events = listOf(LlmEvent.Text("hi"))
        send("and now?")
        assertEquals(listOf("u0" to true, "a0" to false), histories.single())
    }

    @Test
    fun `a follow-up after the app closed the conversation still sees the call it follows`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("700"), BigDecimal("500"), BigDecimal("40"))))
        // Prose before the call: the live conversation holds it, so the app closes it.
        events = listOf(LlmEvent.Text("Let me check. "), LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        send("compare food with last month")
        coVerify { llmService.closeConversation() }

        val histories = rebuilt()
        events = listOf(LlmEvent.Text("ok"))
        send("and transport?")
        // The call the model made, without its prose or the figures the app wrote under it.
        val compared = listOf("compare food with last month" to true, call(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")) to false)
        assertEquals(compared, histories.single())

        // A later rebuild still has the whole exchange: never a question left open.
        histories.clear()
        send("thanks")
        assertEquals(compared + listOf("and transport?" to true, "ok" to false), histories.single())
    }

    @Test
    fun `a request the app answered with a correction leaves the rebuilt window whole`() = runTest {
        events = listOf(LlmEvent.Text("I have added it"))
        assertEquals("NOTHING_CHANGED", send("coffee 50"))
        val histories = rebuilt()
        events = listOf(LlmEvent.Text("ok"))
        send("then add it")
        assertEquals(emptyList<Pair<String, Boolean>>(), histories.single())
    }

    @Test
    fun `a confirmed card rescued from a broken call leaves the rebuilt window whole`() = runTest {
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        events = emptyList()
        failure = RuntimeException("Failed to parse tool calls from code block: {\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 50, \"merchant\": \"coffee\"}]}};")
        assertEquals("CARD", send("coffee 50"))
        coVerify { llmService.closeConversation() }
        repository.completePendingAction("Added ₹50 at coffee.")

        val histories = rebuilt()
        failure = null
        events = listOf(add(30.0))
        send("taxi 30")
        // Left open, "coffee 50" was proposed again beside taxi, and confirming it saved it twice.
        assertEquals(emptyList<Pair<String, Boolean>>(), histories.single())
        assertEquals(listOf("(The user confirmed the last card. It is saved.)\ntaxi 30"), sent.takeLast(1))
    }

    @Test
    fun `a broken turn's window is rebuilt with its call, even when the screen warms up as it shows`() = runTest {
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
        var live = true
        every { llmService.hasActiveConversation() } answers { live }
        coEvery { llmService.closeConversation() } answers { live = false }
        val histories = mutableListOf<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(histories), any()) } answers { live = true; Result.success(Unit) }
        coEvery { llmService.tokenCount() } returns 1500
        // Prose beside a lookup: the live conversation holds it, so this turn closes it.
        val lookup = LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام"))
        events = listOf(LlmEvent.Text("You spent ₹999. "), lookup)
        // The user comes back to the chat while the reply shows, before it's saved.
        repository.sendMessageStream("how much on food?").collect { repository.warmUp() }

        events = listOf(LlmEvent.ToolCall(PennyWiseTools.COMPARE_SPENDING, mapOf("period" to "month", "category" to "طعام")))
        send("and last month?")
        // "and last month?" follows the call the model made, not a window without it.
        assertEquals(
            listOf("how much on food?" to true, call(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")) to false),
            histories.single()
        )
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
    fun `an add call let through as text after prose drops the prose too`() = runTest {
        every { context.getString(R.string.chat_reply_add, any()) } returns "CARD"
        events = listOf(LlmEvent.Text("Sure, adding ₹999 for shoes.\n<tool_call>\n{\"name\": \"add_transactions\", \"arguments\": {\"items\": [{\"amount\": 300, \"merchant\": \"shoes\"}]}}"))
        assertEquals("CARD", send("shoes 300"))
        assertEquals(listOf("CARD"), shown)
        assertTrue(repository.pendingAction.value is PendingChatAction.Add)
        assertEquals("CARD", saved.last().message)
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

    private val nov = SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))

    /** Answers spending() over the days it was asked for, and keeps what it was asked. */
    private fun answerSpending(): io.mockk.CapturingSlot<SpendingFilter> {
        val filter = slot<SpendingFilter>()
        coEvery { aiContext.spending(any(), capture(filter)) } answers {
            PeriodSpending(ChatScope(filter.captured.window ?: nov), spent = mapOf("INR" to BigDecimal("300")), income = emptyMap())
        }
        return filter
    }

    private fun account(bank: String, last4: String) =
        AccountBalanceEntity(bankName = bank, accountLast4 = last4, balance = BigDecimal.ZERO, timestamp = LocalDateTime.of(2026, 11, 1, 0, 0))

    @Test
    fun `a spending query takes its days and account, and links what the list can show`() = runTest {
        every { context.getString(R.string.chat_reply_month_spend, any()) } answers { "SPENT ${(args[1] as Array<*>)[0]}" }
        every { context.getString(R.string.chat_link_transactions_in, any()) } answers { "SEE ${(args[1] as Array<*>)[0]}" }
        coEvery { accountBalances.getAllLatestBalancesOnce() } returns listOf(account("HDFC", "1234"), account("SBI", "9876"))
        val filter = answerSpending()
        val (oct1, oct31) = LocalDate.of(2024, 10, 1) to LocalDate.of(2024, 10, 31)

        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("period" to "2024-10", "account" to "hdfc 1234", "group_by" to "none")))
        val reply = send("how much from my HDFC card in October 2024?")
        assertEquals(SpendingFilter(SpendingComparison.Window(oct1.atStartOfDay(), oct31.atTime(LocalTime.MAX)), null, "HDFC_1234"), filter.captured)
        // Another year's days say so: "1 Oct" alone would read as this October.
        assertTrue(reply, reply.startsWith("SPENT 1 Oct 2024–31 Oct 2024 · HDFC ••1234\n"))
        assertEquals(listOf(ChatLink("SEE HDFC ••1234", oct1, oct31, null, "INR", null, "HDFC_1234")), repository.links.value[saved.last().id])

        // A merchant's figure, but no link: the list's search also reads notes and SMS text.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("merchant" to "Starbucks", "account" to "", "category" to "all")))
        assertTrue(send("how much at starbucks?").startsWith("SPENT 1 Nov–16 Nov · Starbucks\n"))
        assertEquals(SpendingFilter(merchant = "Starbucks"), filter.captured)
        assertEquals(null, repository.links.value[saved.last().id])
        // Read-only figures stay out of the model's window, as the old lookups' did.
        assertTrue(saved.last().id.startsWith(LlmRepository.QUICK_ID))
    }

    @Test
    fun `a spending query the app can't read says so, and looks nothing up`() = runTest {
        every { context.getString(R.string.chat_reply_query_period_unknown) } returns "PERIOD"
        every { context.getString(R.string.chat_reply_query_date_unreadable, any()) } returns "DATE"
        every { context.getString(R.string.chat_reply_query_no_start) } returns "NO_START"
        every { context.getString(R.string.chat_reply_query_dates_reversed, any(), any()) } returns "REVERSED"
        every { context.getString(R.string.chat_reply_query_future) } returns "FUTURE"
        every { context.getString(R.string.chat_reply_query_group_unknown) } returns "GROUP"
        every { context.getString(R.string.chat_reply_unknown_account, any()) } returns "NO_ACCOUNT"
        every { context.getString(R.string.chat_reply_account_ambiguous, "1234", "HDFC ••1234, SBI ••1234") } returns "WHICH"
        coEvery { accountBalances.getAllLatestBalancesOnce() } returns listOf(account("HDFC", "1234"), account("SBI", "1234"))
        answerSpending()
        suspend fun ask(args: Map<String, Any?>, message: String = "how much?"): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, args))
            return send(message)
        }
        assertEquals("PERIOD", ask(mapOf("period" to "fortnight")))
        assertEquals("DATE", ask(mapOf("from" to "2025-02-29")))
        assertEquals("NO_START", ask(mapOf("to" to "2024-10-31")))
        assertEquals("REVERSED", ask(mapOf("from" to "2024-11-10", "to" to "2024-11-01")))
        assertEquals("FUTURE", ask(mapOf("period" to "2099-01")))
        assertEquals("GROUP", ask(mapOf("group_by" to "day")))
        assertEquals("NO_ACCOUNT", ask(mapOf("account" to "Barclays")))
        // Two cards end in 1234: the user says which, the app doesn't add them up.
        assertEquals("WHICH", ask(mapOf("account" to "1234")))
        // Left at this month while the user named two months, or a day in one: not this month's figure under them.
        assertEquals("PERIOD", ask(emptyMap(), "how much did I spend in September and October?"))
        assertEquals("PERIOD", ask(emptyMap(), "how much did I spend on 15 September?"))
        coVerify(exactly = 0) { aiContext.spending(any(), any()) }
        coVerify(exactly = 0) { aiContext.topCategories(any(), any(), any(), any()) }

        // An ignored account is hidden everywhere, so it's no candidate: the other card answers.
        ignored = setOf("SBI_1234")
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        assertTrue(ask(mapOf("account" to "1234")).startsWith("SPENT"))
        // Named, the ignored card isn't swapped for the other one ending the same.
        assertEquals("NO_ACCOUNT", ask(mapOf("account" to "SBI 1234")))
    }

    @Test
    fun `the user's last month or week beats a period the model left at this month`() = runTest {
        every { context.getString(R.string.chat_reply_compare_no_data, any()) } returns "NO_DATA"
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns SpendingComparison.Result.NoData(nov, nov, nov)
        val filter = answerSpending()
        fun asked(period: String) = (SpendingQuery.dates(period, null, null, LocalDateTime.of(2026, 11, 16, 12, 0), WeekFields.of(Locale.getDefault()).firstDayOfWeek, 1) as SpendingQuery.Dates.Of).window

        // Narrowed to a merchant it can't be a comparison: the figure covers the days named.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("merchant" to "Uber", "period" to "this_month")))
        send("how much on uber last week?")
        assertEquals(asked("last_week"), filter.captured.window)
        // Income isn't compared: last month's own figures.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, emptyMap()))
        send("how much income did I get last month?")
        assertEquals(asked("last_month"), filter.captured.window)
        // A plain lookup naming last month is still the comparison the Hinglish question wants.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("NO_DATA", send("pichle mahine se zyada khana kharcha?"))
        coVerify(exactly = 1) { aiContext.compareSpending(SpendingComparison.Period.MONTH, "طعام", any(), any()) }
        assertEquals(PennyWiseTools.COMPARE_SPENDING, repository.lastReply.value?.tool)
        // Asked for by the model, last month is last month's figure.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("period" to "last_month")))
        send("and how much last month?")
        assertEquals(asked("last_month"), filter.captured.window)
        // A day the user named, not this month's figure under it.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("period" to "")))
        send("how much did I spend yesterday?")
        assertEquals(asked("yesterday"), filter.captured.window)
    }

    @Test
    fun `an income question totals what came in under its category, never compared as spending`() = runTest {
        every { context.resources.getQuantityString(R.plurals.chat_reply_category_income, any(), any(), any(), any(), any()) } answers {
            (args[2] as Array<*>).let { "RECEIVED ${it[0]} ${it[1]}" }
        }
        every { context.getString(R.string.chat_reply_nothing_received_in, any(), any()) } returns "NOTHING_RECEIVED"
        every { categories.getAllCategories() } returns flowOf(
            listOf(CategoryEntity(id = 1, name = "طعام", color = "#000"), CategoryEntity(id = 3, name = "Salary", color = "#000", isIncome = true))
        )
        val filter = slot<SpendingFilter>()
        coEvery { aiContext.categoryIncome("Salary", any(), capture(filter)) } returns spent("500").copy(category = "Salary")
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")

        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "Salary")))
        assertEquals("RECEIVED ₹500 Salary", send("How much salary did I receive this month?"))
        // An income category is income without the word for it too.
        assertEquals("RECEIVED ₹500 Salary", send("salary?"))
        // Named with last month, it's last month's salary, not a comparison of spending in it.
        send("how much salary did I get last month?")
        coVerify(exactly = 0) { aiContext.compareSpending(any(), any(), any(), any()) }
        assertEquals(LocalDate.of(2026, 10, 1), filter.captured.window?.start?.toLocalDate())
        coVerify(exactly = 0) { aiContext.categorySpending(any(), any(), any()) }

        coEvery { aiContext.categoryIncome("Salary", any(), any()) } returns spent().copy(category = "Salary")
        assertEquals("NOTHING_RECEIVED", send("how much salary this month?"))
        // A spending category is still what was spent in it.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "طعام")))
        assertEquals("SPEND", send("how much on food?"))
    }

    @Test
    fun `a spending query's links open the profile its figure counted`() = runTest {
        val business = ChatScope(nov, ProfileEntity(ProfileEntity.BUSINESS_ID, "Business", "#000"))
        coEvery { aiContext.spending(any(), any()) } returns PeriodSpending(business, spent = mapOf("INR" to BigDecimal("700")), income = emptyMap())
        coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120").copy(scope = business, category = "طعام")
        coEvery { aiContext.topCategories(any(), any(), any(), any()) } returns TopCategories(business, mapOf("INR" to listOf("طعام" to BigDecimal("120"))))
        for (args in listOf(emptyMap(), mapOf("category" to "طعام"), mapOf("group_by" to "category"))) {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, args))
            send("how much this month?")
            // Whatever Home shows by the time the link is tapped.
            assertEquals(args.toString(), listOf(ProfileEntity.BUSINESS_ID), repository.links.value[saved.last().id]?.map { it.profileId })
        }
    }

    @Test
    fun `a merchant or account the call names is no date, and a year named is that year`() = runTest {
        every { context.getString(R.string.chat_reply_query_period_unknown) } returns "PERIOD"
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        coEvery { accountBalances.getAllLatestBalancesOnce() } returns listOf(account("HDFC", "2019"))
        val filter = answerSpending()
        fun asked(period: String) = (SpendingQuery.dates(period, null, null, LocalDateTime.of(2026, 11, 16, 12, 0), WeekFields.of(Locale.getDefault()).firstDayOfWeek, 1) as SpendingQuery.Dates.Of).window
        suspend fun ask(args: Map<String, Any?>, message: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, args))
            return send(message)
        }

        // "24/7" reads as a date, "in 2019" as a year: here they're the shop and the card.
        assertTrue(ask(mapOf("merchant" to "24/7"), "how much did I spend at 24/7 this month?").startsWith("SPENT"))
        assertEquals(SpendingFilter(merchant = "24/7"), filter.captured)
        assertTrue(ask(mapOf("account" to "2019"), "how much on the card ending in 2019?").startsWith("SPENT"))
        assertEquals(SpendingFilter(account = "HDFC_2019"), filter.captured)
        // A shop named for a day is no day.
        assertTrue(ask(mapOf("merchant" to "Today Mart"), "how much at today mart?").startsWith("SPENT"))
        assertEquals(null, filter.captured.window)
        // This year or last, left at this month by the model: the year, not "can't look up".
        ask(mapOf("period" to "this_month"), "how much did I spend this year?")
        assertEquals(asked("this_year"), filter.captured.window)
        ask(emptyMap(), "كم صرفت السنة الماضية؟")
        assertEquals(asked("last_year"), filter.captured.window)
        // A month within it is that month, not the whole year.
        ask(emptyMap(), "how much in March this year?")
        assertEquals(asked("2026-03"), filter.captured.window)
        // Named alone (seen on the emulator as "I can't look up those days"), the latest October begun.
        ask(mapOf("period" to "this_month"), "How much did I spend in October?")
        assertEquals(asked("2026-10"), filter.captured.window)
        ask(emptyMap(), "كم صرفت في أكتوبر؟")
        assertEquals(asked("2026-10"), filter.captured.window)
        // The model's "October" keeps the year and the days the user's words give it.
        ask(mapOf("period" to "October"), "How much did I spend in October last year?")
        assertEquals(asked("2025-10"), filter.captured.window)
        ask(mapOf("period" to "November"), "how much did I spend last November?")
        assertEquals(asked("2025-11"), filter.captured.window)
        assertEquals("PERIOD", ask(mapOf("period" to "October"), "How much have I spent since October?"))
        assertEquals("PERIOD", ask(emptyMap(), "how much did I spend in the first week of October?"))
        assertEquals("PERIOD", ask(emptyMap(), "كم صرفت منذ أكتوبر؟"))
    }

    @Test
    fun `a lookup that asks which one keeps the live conversation, and is never replayed`() = runTest {
        every { context.getString(R.string.chat_reply_account_ambiguous, any(), any()) } returns "WHICH"
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        coEvery { accountBalances.getAllLatestBalancesOnce() } returns listOf(account("HDFC", "1234"), account("HDFC", "5678"))
        answerSpending()
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("account" to "HDFC")))
        assertEquals("WHICH", send("how much on my HDFC card?"))
        // The app's line: a rebuilt window leaves it out, as any other, so it can't be written back.
        assertTrue(saved.last().id.startsWith(LlmRepository.QUICK_ID))
        // The live conversation holds the call the model made, so "the 5678 one" follows it there.
        coVerify(exactly = 0) { llmService.closeConversation() }
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("account" to "HDFC 5678")))
        assertTrue(send("the 5678 one").startsWith("SPENT"))
        coVerify(exactly = 0) { llmService.createConversation(any(), any(), any()) }
    }

    @Test
    fun `a merchant's figure isn't narrowed or ranked by what the model guessed beside it`() = runTest {
        every { context.getString(R.string.chat_reply_month_spend, any()) } answers { "SPENT ${(args[1] as Array<*>)[0]}" }
        coEvery { accountBalances.getAllLatestBalancesOnce() } returns listOf(account("Emirates NBD", "1234"))
        val filter = answerSpending()

        // Seen on the emulator: "Nothing spent … · Shopping · Swiggy" for "how much at Swiggy?".
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("period" to "this_month", "category" to "Shopping", "merchant" to "Swiggy", "group_by" to "category")))
        assertTrue(send("Swiggy spending this month?").startsWith("SPENT 1 Nov–16 Nov · Swiggy\n"))
        assertEquals(SpendingFilter(merchant = "Swiggy"), filter.captured)
        // And "Nothing spent … · Others · Emirates NBD ••1234": a category's name put in as the merchant.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("account" to "Emirates NBD", "merchant" to "Hobbies")))
        assertTrue(send("How much did I spend from my Emirates NBD account this month?").startsWith("SPENT 1 Nov–16 Nov · Emirates NBD ••1234\n"))
        assertEquals(SpendingFilter(account = "Emirates NBD_1234"), filter.captured)
        // And "Top accounts … Emirates NBD: Emirates NBD: AED127" for one account's spending.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("account" to "Emirates NBD", "group_by" to "account")))
        assertTrue(send("How much did I spend from my Emirates NBD account this month?").startsWith("SPENT 1 Nov–16 Nov · Emirates NBD ••1234\n"))
        coVerify(exactly = 0) { aiContext.topCategories(any(), any(), any(), any()) }
        // A built-in category is kept in English and asked about in the app's language.
        val shown = BuiltinCategoryNames.shown
        BuiltinCategoryNames.shown = { name, _ -> "طعام ومطاعم".takeIf { name == "Food & Dining" } }
        try {
            coEvery { aiContext.categorySpending(any(), any(), any()) } returns spent("120")
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("category" to "Food & Dining", "merchant" to "Starbucks")))
            assertEquals("SPEND", send("كم صرفت على الطعام في ستاربكس؟"))
            coVerify { aiContext.categorySpending("Food & Dining", any(), SpendingFilter(merchant = "Starbucks")) }
        } finally {
            BuiltinCategoryNames.shown = shown
        }
    }

    @Test
    fun `merchants and accounts are ranked under their own heading, spending on no account named`() = runTest {
        every { context.getString(R.string.chat_reply_top_accounts, any()) } answers { "TOP_ACCOUNTS ${(args[1] as Array<*>)[0]}" }
        every { context.getString(R.string.chat_group_no_account) } returns "NONE"
        coEvery { aiContext.topCategories(any(), any(), any(), any()) } returns TopCategories(
            ChatScope(nov),
            ranking = mapOf("INR" to listOf(AiContextRepository.NO_ACCOUNT to BigDecimal("200"), "Salary card" to BigDecimal("50"))),
            share = 0.6f,
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("group_by" to "accounts", "category" to "طعام")))
        assertEquals("TOP_ACCOUNTS 1 Nov–16 Nov · طعام\nNONE: ₹200\nSalary card: ₹50", send("which account do I pay for food from most?"))
        coVerify { aiContext.topCategories(any(), SpendingFilter(), SpendingQuery.GroupBy.ACCOUNT, "طعام") }
        // The biggest one's share: the mascot is a pie.
        assertEquals(0.6f, repository.lastReply.value?.share)
        assertEquals(PennyWiseTools.QUERY_SPENDING, repository.lastReply.value?.tool)
        // At one merchant, the cards asked for are ranked too: not the merchant's one total.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.QUERY_SPENDING, mapOf("group_by" to "account", "merchant" to "Amazon")))
        assertTrue(send("How much did I spend at Amazon on each card?").startsWith("TOP_ACCOUNTS"))
        coVerify { aiContext.topCategories(any(), SpendingFilter(merchant = "Amazon"), SpendingQuery.GroupBy.ACCOUNT, null) }
    }

    @Test
    fun `budgets are each their own, in their own window and currency, never one sum`() = runTest {
        every { context.getString(R.string.chat_reply_budgets_title) } returns "BUDGETS"
        every { context.getString(R.string.chat_reply_budget_left, any(), any(), any(), any(), any()) } answers {
            "LEFT " + (args[1] as Array<*>).joinToString("|")
        }
        every { context.getString(R.string.chat_reply_budget_over, any(), any(), any(), any(), any()) } answers {
            "OVER " + (args[1] as Array<*>).joinToString("|")
        }
        coEvery { planning.budgets(any()) } returns listOf(
            BudgetStatus("Food", "SAR", BigDecimal("2000"), BigDecimal("1500"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
            BudgetStatus("Coffee", "SAR", BigDecimal("100"), BigDecimal("130"), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22)),
        )
        events = listOf(LlmEvent.Text("You have SAR 470 left. "), LlmEvent.ToolCall(PennyWiseTools.PLANNING_STATUS, mapOf("kind" to "budgets")))
        val reply = send("how much budget do I have left?")
        val lines = reply.lines()
        assertEquals("BUDGETS", lines[0])
        assertTrue(lines[1], lines[1].startsWith("LEFT Food|1 Nov–30 Nov|") && lines[1].endsWith("500"))
        assertTrue(lines[2], lines[2].startsWith("OVER Coffee|16 Nov–22 Nov|") && lines[2].endsWith("30"))
        // The model's own sum never shows, and nothing adds the two windows together.
        assertEquals(3, lines.size)
        assertFalse(reply.contains("470"))
        assertEquals(PennyWiseTools.PLANNING_STATUS, repository.lastReply.value?.tool)
        // The gauge reads the most used limit: Coffee, past its own.
        assertEquals(1f, repository.lastReply.value?.share)
        assertTrue(saved.last().id.startsWith(LlmRepository.QUICK_ID))

        // A limit with no exchange rate into the shown currency: named, never dropped or made zero.
        every { context.getString(R.string.chat_reply_budget_no_rate, any(), any(), any(), any()) } answers {
            "NO_RATE " + (args[1] as Array<*>).joinToString("|")
        }
        coEvery { planning.budgets(any()) } returns listOf(
            BudgetStatus("Food", "USD", null, BigDecimal("40"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
        )
        val noRate = send("budget left?").lines()
        assertEquals("BUDGETS", noRate[0])
        assertTrue(noRate[1], noRate[1].startsWith("NO_RATE Food|1 Nov–30 Nov|") && noRate[1].endsWith("|USD"))

        every { context.getString(R.string.chat_reply_budgets_none) } returns "NO_BUDGETS"
        coEvery { planning.budgets(any()) } returns emptyList()
        assertEquals("NO_BUDGETS", send("budget?"))
    }

    @Test
    fun `bills due say which are overdue, each in its own currency`() = runTest {
        every { context.getString(R.string.chat_reply_bills_overdue) } returns "OVERDUE"
        every { context.getString(R.string.chat_reply_bills_due, any()) } answers { "DUE ${(args[1] as Array<*>)[0]}" }
        every { context.getString(R.string.chat_reply_bills_none, any()) } answers { "NONE ${(args[1] as Array<*>)[0]}" }
        every { context.getString(R.string.chat_reply_planning_unknown) } returns "UNKNOWN"
        fun bill(name: String, amount: String, currency: String, due: LocalDate) =
            SubscriptionEntity(merchantName = name, amount = BigDecimal(amount), nextPaymentDate = due, currency = currency)
        val through = LocalDate.of(2026, 11, 23)
        coEvery { planning.billsDue(any(), any()) } returns BillsDue(
            through,
            overdue = listOf(bill("Rent", "3000", "SAR", LocalDate.of(2026, 11, 13))),
            upcoming = listOf(bill("Netflix", "12", "USD", LocalDate.of(2026, 11, 18))),
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.PLANNING_STATUS, mapOf("kind" to "bills")))
        val lines = send("what bills are due?").lines()
        assertEquals("OVERDUE", lines[0])
        assertTrue(lines[1], lines[1].startsWith("Rent · ") && lines[1].endsWith(" · 13 Nov") && "3,000" in lines[1])
        assertEquals("DUE 23 Nov", lines[2])
        assertTrue(lines[3], lines[3].startsWith("Netflix · ") && "$" in lines[3] && lines[3].endsWith(" · 18 Nov"))

        coEvery { planning.billsDue(any(), any()) } returns BillsDue(through, emptyList(), emptyList())
        assertEquals("NONE 23 Nov", send("any bills?"))
        // A kind it doesn't know is said, not guessed.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.PLANNING_STATUS, mapOf("kind" to "goals")))
        assertEquals("UNKNOWN", send("how are my goals?"))
    }

    @Test
    fun `a money-in question sent to the plans is looked up instead`() = runTest {
        every { context.getString(R.string.chat_reply_planning_unknown) } returns "UNKNOWN"
        every { context.resources.getQuantityString(R.plurals.chat_reply_category_income, any(), any(), any(), any(), any()) } answers {
            (args[2] as Array<*>).let { "RECEIVED ${it[0]} ${it[1]}" }
        }
        every { categories.getAllCategories() } returns flowOf(listOf(CategoryEntity(id = 3, name = "Salary", color = "#000", isIncome = true)))
        coEvery { aiContext.categoryIncome("Salary", any(), any()) } returns spent("500").copy(category = "Salary")
        // What Qwen2.5 sent for it on device.
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.PLANNING_STATUS, mapOf("kind" to "salary")))
        assertEquals("RECEIVED ₹500 Salary", send("How much salary did I receive this month?"))
        assertEquals(PennyWiseTools.QUERY_SPENDING, repository.lastReply.value?.tool)
        // A kind that names no category: the month's figures, what came in among them.
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        every { context.getString(R.string.chat_reply_received_nothing) } returns "NONE_RECEIVED"
        answerSpending()
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.PLANNING_STATUS, mapOf("kind" to "status")))
        assertTrue("NONE_RECEIVED" in send("how much income did I get?").lines())
    }
}
