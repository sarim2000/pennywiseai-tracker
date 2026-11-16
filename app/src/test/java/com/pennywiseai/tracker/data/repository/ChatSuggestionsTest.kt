package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.BillsDue
import com.pennywiseai.tracker.data.model.BudgetStatus
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.ChatSuggestion.Ask
import com.pennywiseai.tracker.data.repository.ChatSuggestion.Uncategorized
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class ChatSuggestionsTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val noBills = BillsDue(today.plusDays(7), emptyList(), emptyList())
    private fun bill(due: LocalDate) = SubscriptionEntity(merchantName = "Rent", amount = BigDecimal("3000"), nextPaymentDate = due, currency = "SAR")
    private fun budget(spent: String) =
        BudgetStatus("Food", "SAR", BigDecimal("2000"), BigDecimal(spent), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))

    private val standing = listOf(Ask(QuickAction.MONTH_SPEND), Ask(QuickAction.TOP_CATEGORIES), Ask(QuickAction.COMPARE_MONTH))

    @Test
    fun `with nothing to act on, only the standing questions show`() {
        assertEquals(standing, ChatSuggestions.pick(0, noBills, emptyList()))
    }

    @Test
    fun `what needs doing comes first, and never more than four compete`() {
        val all = ChatSuggestions.pick(12, BillsDue(today.plusDays(7), emptyList(), listOf(bill(today.plusDays(2)))), listOf(budget("500")))
        assertEquals(listOf(Uncategorized(12), Ask(QuickAction.BILLS), Ask(QuickAction.BUDGETS), Ask(QuickAction.MONTH_SPEND)), all)
        assertEquals(ChatSuggestions.MAX_SHOWN, all.size)
    }

    @Test
    fun `overdue bills are counted apart from those still to come`() {
        val bills = BillsDue(today.plusDays(7), overdue = listOf(bill(today.minusDays(3)), bill(today.minusDays(1))), upcoming = listOf(bill(today)))
        assertEquals(Ask(QuickAction.BILLS, overdue = 2), ChatSuggestions.pick(0, bills, emptyList()).first())
        val overdueOnly = BillsDue(today.plusDays(7), overdue = listOf(bill(today.minusDays(3))), upcoming = emptyList())
        assertEquals(Ask(QuickAction.BILLS, overdue = 1), ChatSuggestions.pick(0, overdueOnly, emptyList()).first())
    }

    @Test
    fun `a spent limit still has something to say`() {
        // Over its limit, or without a rate to show it in: the answer says so, so the prompt stays.
        assertEquals(Ask(QuickAction.BUDGETS), ChatSuggestions.pick(0, noBills, listOf(budget("2500"))).first())
        assertEquals(Ask(QuickAction.BUDGETS), ChatSuggestions.pick(0, noBills, listOf(budget("10").copy(limit = null))).first())
    }

    private fun row(id: Long, category: String, profileId: Long? = null, excluded: Boolean = false, deleted: Boolean = false) = TransactionEntity(
        id = id,
        amount = BigDecimal.TEN,
        merchantName = "Shop",
        category = category,
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 11, 10, 12, 0),
        transactionHash = "h$id",
        currency = "SAR",
        profileId = profileId,
        excludedFromAnalytics = excluded,
        isDeleted = deleted,
    )

    @Test
    fun `a row needs a category when it has none or sits in Others, whatever Others is called`() {
        assertTrue(row(1, "").needsCategory("Others"))
        assertTrue(row(2, "  ").needsCategory("Others"))
        assertTrue(row(3, "Misc").needsCategory("Misc"))
        assertFalse(row(4, "Others").needsCategory("Misc"))
        assertFalse(row(5, "Food").needsCategory("Others"))
        // Kept out of totals or deleted: nothing waits on it.
        assertFalse(row(6, "Others", excluded = true).needsCategory("Others"))
        assertFalse(row(7, "Others", deleted = true).needsCategory("Others"))
    }

    @Test
    fun `the count follows the profile Home shows, and goes as rows are filed`() = runTest {
        val unsorted = MutableStateFlow(listOf(row(1, "Others", profileId = 2), row(2, "Others", profileId = 1)))
        val profile = MutableStateFlow<Long?>(2)
        val suggestions = ChatSuggestions(
            transactionRepository = mockk { every { observeUncategorized() } returns unsorted },
            planningRepository = mockk {
                every { observeBillsDue(today, any()) } returns flowOf(noBills)
                every { observeBudgets(today) } returns flowOf(emptyList())
            },
            userPreferencesRepository = mockk<UserPreferencesRepository> { every { selectedProfileId } returns profile },
            accountBalanceRepository = mockk { every { getAllLatestBalances() } returns flowOf(emptyList()) },
        )
        assertEquals(Uncategorized(1), suggestions.observe(today).first().first())
        assertEquals(listOf(1L), suggestions.uncategorized().first().map { it.id })

        profile.value = null
        assertEquals(Uncategorized(2), suggestions.observe(today).first().first())

        unsorted.value = emptyList()
        assertEquals(standing, suggestions.observe(today).first())
    }
}
