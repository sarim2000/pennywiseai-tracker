package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.utils.TokenUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #959: a fresh chat must always fit; a long one sheds its oldest turns from the model's window. */
class ChatPromptBudgetTest {

    private fun prompt(expense: List<String>, income: List<String>, accounts: List<String>) =
        LlmRepository.composeSystemPrompt(
            currentDate = "2026-10-21",
            monthLine = "₹12,345 spent, ₹50,000 income, 58 transactions",
            expenseCategories = expense,
            incomeCategories = income,
            accounts = accounts
        )

    @Test
    fun `the window keeps room for the message and a reply`() {
        assertTrue(TokenUtils.fitsWindow(1200, "coffee 120"))
        assertFalse(TokenUtils.fitsWindow(3500, "coffee 120"))
        // A token a character: a Hindi or Arabic message is never undercounted.
        assertFalse(TokenUtils.fitsWindow(1200, "x".repeat(2000)))
    }

    @Test
    fun `short lists are listed in full`() {
        val p = prompt(listOf("Food & Dining", "Groceries"), listOf("Salary"), listOf("Test Bank 1111"))
        assertTrue(p.contains("Expense categories: Food & Dining, Groceries\n"))
        assertTrue(p.contains("Income categories: Salary\n"))
        assertTrue(p.contains("Accounts: Test Bank 1111\n"))
        assertFalse(p.contains(" more\n"))
    }

    @Test
    fun `no accounts reads as none yet`() {
        assertTrue(prompt(emptyList(), emptyList(), emptyList()).contains("Accounts: none yet\n"))
    }

    @Test
    fun `long lists are capped and the rest counted`() {
        val names = (1..100).map { "Category number $it" }
        val listed = LlmRepository.promptList(names, maxChars = 200)
        assertTrue(listed.length <= 200 + ", and 100 more".length)
        assertTrue(listed.startsWith("Category number 1, Category number 2, "))
        val shown = listed.split(", ").size - 1
        assertTrue(listed.endsWith(", and ${100 - shown} more"))
    }

    @Test
    fun `a name longer than the cap is counted, not listed`() {
        assertEquals("and 1 more", LlmRepository.promptList(listOf("x".repeat(500)), maxChars = 100))
    }

    @Test
    fun `fresh chat fits however many categories and accounts the user has`() {
        val longName = "A Rather Long User Defined Category Name"
        val p = prompt(
            expense = (1..300).map { "$longName $it" },
            income = (1..100).map { "$longName income $it" },
            accounts = (1..200).map { "Joint Household Savings Account Number $it" }
        )
        // The prompt is a bounded slice of the window, leaving room for the tools and to chat.
        assertTrue(TokenUtils.estimateTokens(p) < TokenUtils.MODEL_CONTEXT_TOKENS / 4)
        assertTrue(TokenUtils.fitsWindow(TokenUtils.estimateTokens(p) + TOOLS_TOKENS, "how much did I spend on groceries this month?"))
    }

    @Test
    fun `a long chat leaves the window from its oldest half, at a user turn`() {
        val chat = listOf(true, false, true, false, false, true, false).mapIndexed { i, user ->
            ChatMessage(message = "m$i", isUser = user, timestamp = i.toLong())
        }
        // Drops m0..m3, then the reply m4 that would open the window mid-exchange.
        assertEquals(listOf("m5", "m6"), LlmRepository.dropOlderHalf(chat).map { it.message })
        assertEquals(emptyList<ChatMessage>(), LlmRepository.dropOlderHalf(chat.take(1)))
    }

    @Test
    fun `quick-action questions and their replies stay out of the model's window`() {
        val chat = listOf("Top categories" to true, "Food: ₹10" to false, "coffee 50" to true, "Added" to false, "Top categories" to true)
            .map { (text, user) -> ChatMessage(message = text, isUser = user) }
        // The trailing question has no reply yet; it goes too, and nothing typed is lost.
        assertEquals(listOf("coffee 50", "Added"), LlmRepository.withoutQuickAnswers(chat, setOf("Top categories")).map { it.message })
    }

    private companion object {
        // The tool schemas LiteRT-LM adds to the system turn, roughly.
        const val TOOLS_TOKENS = 700
    }
}
