package com.pennywiseai.tracker.data.repository

import android.content.Context
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.PendingChatAction
import com.pennywiseai.tracker.data.model.SpendingComparison
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
    private val aiContext = mockk<AiContextRepository>(relaxed = true) {
        coEvery { profileShown() } returns null
    }
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true) {
        every { baseCurrency } returns flowOf("INR")
        every { countCreditCardAsExpense } returns flowOf(false)
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
        every { getString(R.string.chat_reply_category_spend, any(), any(), any(), any()) } returns "SPEND"
        every { getString(R.string.chat_reply_nothing_spent, any(), any()) } returns "NOTHING_SPENT"
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
        coEvery { aiContext.categorySpending(any(), any()) } returns spent("120")
        events = listOf(LlmEvent.Text("You've spent about ₹5,000 on food. "), LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to "طعام")))
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
        every { context.getString(R.string.chat_reply_transaction_count, 12) } returns "COUNT"
        coEvery { aiContext.spending(any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))),
            spent = mapOf("USD" to BigDecimal("5"), "INR" to BigDecimal("600")),
            income = mapOf("INR" to BigDecimal("5000")),
            forgiven = mapOf("INR" to BigDecimal("400")),
            estimate = com.pennywiseai.tracker.data.ledger.ConvertedEstimate("INR", BigDecimal("1000"), mapOf("XYZ" to BigDecimal("3"))),
            transactionCount = 12,
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.MONTH_SUMMARY, emptyMap()))
        val typed = send("how much did I spend this month?")
        // The user's own currency first, its forgiven loan named inside the figure; a gap in the estimate said.
        val lines = typed.lines()
        assertEquals(listOf("SPENT", "WITH_FORGIVEN"), lines.take(2))
        assertTrue(lines[2], lines[2].contains("5") && "$" in lines[2])
        // Typed, it also gives the count monthSummary promises the model.
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
        coEvery { aiContext.spending(any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            scope,
            spent = mapOf("USD" to BigDecimal("5"), "INR" to BigDecimal("600")),
            income = emptyMap(),
            estimate = com.pennywiseai.tracker.data.ledger.ConvertedEstimate("USD", BigDecimal("12.50"), mapOf("XYZ" to BigDecimal("3"))),
        )
        repository.getFormattedContextForDisplay()
        verify { context.getString(R.string.chat_welcome, "1 Nov–16 Nov", match<String> { it.lines().drop(1) == listOf("ESTIMATE", "LEFT_OUT") && "$" in it.lines()[0] }) }

        coEvery { aiContext.spending(any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(scope, spent = emptyMap(), income = emptyMap())
        repository.getFormattedContextForDisplay()
        verify { context.getString(R.string.chat_welcome, "1 Nov–16 Nov", match<String> { "$" in it && "₹" !in it && "\n" !in it }) }
    }

    @Test
    fun `a seeded profile is named in the app's language, on totals and comparisons alike`() = runTest {
        val business = ProfileEntity(ProfileEntity.BUSINESS_ID, "Business", "#000")
        every { context.getString(R.string.profile_default_business) } returns "عمل"
        coEvery { aiContext.spending(any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59)), business),
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        verify { context.getString(R.string.chat_reply_month_spend, "1 Nov–16 Nov · عمل") }

        // A comparison covers the same profile, so it says so too.
        coEvery { aiContext.profileShown() } returns business
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("700"), BigDecimal("500"), BigDecimal("40"))))
        repository.answerQuickAction(QuickAction.COMPARE_MONTH)
        verify { context.getString(R.string.chat_reply_compare_title, "1 Nov–16 Nov", "1 Nov–16 Nov · عمل") }

        // A renamed one shows as the user named it.
        coEvery { aiContext.profileShown() } returns business.copy(name = "Shop")
        repository.answerQuickAction(QuickAction.COMPARE_MONTH)
        verify { context.getString(R.string.chat_reply_compare_title, "1 Nov–16 Nov", "1 Nov–16 Nov · Shop") }
    }

    @Test
    fun `a typed month summary says nothing was received, in no currency it had no activity in`() = runTest {
        every { context.getString(R.string.chat_reply_month_spend, any()) } returns "SPENT"
        every { context.getString(R.string.chat_reply_received_nothing) } returns "NOTHING_RECEIVED"
        // The user's own currency is USD; all of the month was spent in rupees, and nothing came in.
        every { preferences.baseCurrency } returns flowOf("USD")
        coEvery { aiContext.spending(any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
            com.pennywiseai.tracker.data.model.ChatScope(SpendingComparison.Window(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 16, 23, 59))),
            spent = mapOf("INR" to BigDecimal("700")),
            income = emptyMap(),
        )
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.MONTH_SUMMARY, emptyMap()))
        val reply = send("how much did I earn this month?")
        // The question is answered, with no "Received: $0" in a currency nothing happened in.
        assertTrue(reply, "NOTHING_RECEIVED" in reply.lines())
        verify(exactly = 0) { context.getString(R.string.chat_reply_received, any()) }
    }

    @Test
    fun `a category's spend is per currency and dated`() = runTest {
        coEvery { aiContext.categorySpending(any(), any()) } returns spent("120").copy(category = "طعام", byCurrency = mapOf("USD" to BigDecimal("3"), "INR" to BigDecimal("120")))
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to "طعام")))
        assertEquals("SPEND", send("how much on food?"))
        // Both currencies, the user's own first, never added; the dates it covers.
        verify {
            context.getString(
                R.string.chat_reply_category_spend,
                match<String> { it.indexOf("120") in 0 until it.indexOf("3") && " · " in it },
                "طعام",
                "1 Nov–16 Nov",
                1
            )
        }
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
        coEvery { aiContext.categorySpending(any(), any()) } returns spent()
        coEvery { aiContext.categorySpending("طعام", any()) } returns spent("120")
        suspend fun lookup(category: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to category)))
            return send("how much on $category?")
        }
        assertEquals("SPEND", lookup("طعام"))
        assertEquals("UNKNOWN_CATEGORY", lookup("سفر"))
    }

    @Test
    fun `a category lookup reads hidden categories and names only found on transactions`() = runTest {
        coEvery { aiContext.categorySpending(any(), any()) } returns spent()
        suspend fun lookup(category: String): String {
            events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to category)))
            return send("how much on $category?")
        }
        assertEquals("NOTHING_SPENT", lookup("Hobbies")) // Hidden, but it exists.
        coEvery { aiContext.categorySpending("Hobbies", any()) } returns spent("40")
        assertEquals("SPEND", lookup("Hobbies"))
        coEvery { aiContext.categorySpending("Old Name", any()) } returns spent("15")
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

    @Test
    fun `a typed total leaves the rebuilt window with its question, as a one-tap one does`() = runTest {
        savedChat(1)
        coEvery { aiContext.categorySpending(any(), any()) } returns spent("120")
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to "طعام")))
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
        assertEquals(listOf("u0" to true, "a0" to false), histories.single())
    }

    @Test
    fun `a rebuilt window holds no line the app wrote for the model to copy`() = runTest {
        val w = SpendingComparison.Window(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 9, 0, 0))
        coEvery { aiContext.compareSpending(any(), any(), any(), any()) } returns
            SpendingComparison.Result.Rows(w, w, listOf(SpendingComparison.Row("INR", BigDecimal("700"), BigDecimal("500"), BigDecimal("40"))))
        coEvery { aiContext.categorySpending(any(), any()) } returns spent("120")
        coEvery { aiContext.spending(any()) } returns com.pennywiseai.tracker.data.model.PeriodSpending(
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
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.MONTH_SUMMARY, emptyMap()))
        send("how much this month?")
        repository.answerQuickAction(QuickAction.MONTH_SPEND)
        // A change the model claimed and didn't make: the app's line takes its place.
        events = listOf(LlmEvent.Text("I have added it"))
        assertEquals("NOTHING_CHANGED", send("coffee 50"))
        events = listOf(LlmEvent.Text("Sure! "), add(45.0))
        send("taxi 45")
        repository.completePendingAction("Added ₹45 at taxi (Transport).")

        val histories = rebuilt()
        events = listOf(LlmEvent.ToolCall(PennyWiseTools.SPENDING_BY_CATEGORY, mapOf("category" to "طعام")))
        assertEquals("SPEND", send("كم صرفت على الطعام؟"))
        // Only the model's own words: an app line replayed as its reply is one it can write back.
        assertEquals(listOf("hi" to true, "Hello!" to false), histories.single())
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
    fun `a follow-up after the app closed the conversation still sees the comparison it follows`() = runTest {
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
        // The question's words, not the figures the app wrote under it.
        assertEquals(listOf("compare food with last month" to true), histories.single())

        // For the next message only: a later rebuild leaves that exchange out whole.
        histories.clear()
        send("thanks")
        assertEquals(listOf("and transport?" to true, "ok" to false), histories.single())
    }

    @Test
    fun `a request the app answered with a correction stays in view for the next message`() = runTest {
        events = listOf(LlmEvent.Text("I have added it"))
        assertEquals("NOTHING_CHANGED", send("coffee 50"))
        val histories = rebuilt()
        events = listOf(LlmEvent.Text("ok"))
        send("then add it")
        assertEquals(listOf("coffee 50" to true), histories.single())
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
