package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.model.BillsDue
import com.pennywiseai.tracker.data.model.BudgetStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ChatSuggestionsTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val noBills = BillsDue(today.plusDays(7), emptyList(), emptyList())
    private fun bill(due: LocalDate) = SubscriptionEntity(merchantName = "Rent", amount = BigDecimal("3000"), nextPaymentDate = due, currency = "SAR")
    private fun budget(spent: String) =
        BudgetStatus("Food", "SAR", BigDecimal("2000"), BigDecimal(spent), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))

    private val standing = listOf(
        ChatSuggestion(QuickAction.MONTH_SPEND),
        ChatSuggestion(QuickAction.TOP_CATEGORIES),
        ChatSuggestion(QuickAction.COMPARE_MONTH),
    )

    @Test
    fun `with nothing to act on, only the standing questions show`() {
        assertEquals(standing, ChatSuggestions.pick(noBills, emptyList()))
        assertEquals(ChatSuggestions.STANDING, ChatSuggestions.pick(noBills, emptyList()))
    }

    @Test
    fun `what needs doing comes first, and never more than four compete`() {
        val all = ChatSuggestions.pick(BillsDue(today.plusDays(7), emptyList(), listOf(bill(today.plusDays(2)))), listOf(budget("500")))
        assertEquals(
            listOf(
                ChatSuggestion(QuickAction.BILLS),
                ChatSuggestion(QuickAction.BUDGETS),
                ChatSuggestion(QuickAction.MONTH_SPEND),
                ChatSuggestion(QuickAction.TOP_CATEGORIES),
            ),
            all
        )
        assertEquals(ChatSuggestions.MAX_SHOWN, all.size)
    }

    @Test
    fun `overdue bills are counted apart from those still to come`() {
        val bills = BillsDue(today.plusDays(7), overdue = listOf(bill(today.minusDays(3)), bill(today.minusDays(1))), upcoming = listOf(bill(today)))
        assertEquals(ChatSuggestion(QuickAction.BILLS, overdue = 2), ChatSuggestions.pick(bills, emptyList()).first())
        val overdueOnly = BillsDue(today.plusDays(7), overdue = listOf(bill(today.minusDays(3))), upcoming = emptyList())
        assertEquals(ChatSuggestion(QuickAction.BILLS, overdue = 1), ChatSuggestions.pick(overdueOnly, emptyList()).first())
    }

    @Test
    fun `a spent limit still has something to say`() {
        // Over its limit, or without a rate to show it in: the answer says so, so the prompt stays.
        assertEquals(ChatSuggestion(QuickAction.BUDGETS), ChatSuggestions.pick(noBills, listOf(budget("2500"))).first())
        assertEquals(ChatSuggestion(QuickAction.BUDGETS), ChatSuggestions.pick(noBills, listOf(budget("10").copy(limit = null))).first())
    }

    private fun suggestions(
        bills: Flow<BillsDue> = flowOf(noBills),
        budgets: Flow<List<BudgetStatus>> = flowOf(emptyList()),
    ) = ChatSuggestions(
        planningRepository = mockk {
            every { observeBillsDue(today, any()) } returns bills
            every { observeBudgets(today) } returns budgets
        },
    )

    @Test
    fun `the bills prompt goes once nothing is due`() = runTest {
        val bills = MutableStateFlow(BillsDue(today.plusDays(7), listOf(bill(today.minusDays(1))), emptyList()))
        val suggestions = suggestions(bills = bills)
        assertEquals(ChatSuggestion(QuickAction.BILLS, overdue = 1), suggestions.observe(today).first().first())

        bills.value = noBills
        assertEquals(standing, suggestions.observe(today).first())
    }

    @Test
    fun `a source that fails or is slow drops only its own prompt`() = runTest {
        // Bills fail to read; budgets wait on a rate fetch that hasn't come back.
        val failing = suggestions(bills = flow { throw IllegalStateException("boom") }, budgets = flow { awaitCancellation() })
        assertEquals(standing, failing.observe(today).first())

        // Budgets still waiting don't hold back the bills prompt.
        val slowBudgets = suggestions(
            bills = flowOf(BillsDue(today.plusDays(7), emptyList(), listOf(bill(today.plusDays(1))))),
            budgets = flow { awaitCancellation() },
        )
        assertEquals(listOf(ChatSuggestion(QuickAction.BILLS)) + standing.take(3), slowBudgets.observe(today).first())
    }
}
