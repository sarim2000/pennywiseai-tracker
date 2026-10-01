package com.pennywiseai.tracker.data.webhook

import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class WebhookPayloadBuilderTest {
    private val transactions = mockk<TransactionRepository>()
    private val budgets = mockk<BudgetRepository>()
    private val accounts = mockk<AccountBalanceRepository>()
    private val groups = mockk<BudgetGroupRepository>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val subscriptions = mockk<SubscriptionRepository>()
    private val splits = mockk<TransactionSplitDao>()
    private val builder = WebhookPayloadBuilder(transactions, budgets, accounts, groups, preferences, subscriptions, splits)
    private val profile = WebhookProfileEntity(name = "Test", url = "https://example.com")

    @Test fun `test sends only selected synthetic data without accessing financial repositories`() = runBlocking {
        val payload = builder.build(profile, setOf(WebhookDataType.BUDGETS), emptyList(), true).single()
        assertNull(payload.envelope.summary)
        assertTrue(payload.envelope.transactions.isEmpty())
        assertEquals(1, payload.envelope.budgets.size)
        assertTrue(payload.cursorUpdates.isEmpty())
        verify { listOf(transactions, budgets, accounts, groups, preferences, subscriptions, splits) wasNot Called }
    }

    @Test fun `incremental batching uses transaction cursor and a shared batch id`() = runBlocking {
        val cursorAt = LocalDateTime.now().minusHours(2)
        coEvery { transactions.getWebhookChanges(cursorAt, any(), "INR") } returns (1L..251L).map {
            TransactionEntity(id = it, amount = BigDecimal.ONE, merchantName = "Test merchant", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = cursorAt.minusYears(1), transactionHash = "test_$it")
        }
        val payloads = builder.build(profile, setOf(WebhookDataType.TRANSACTIONS),
            listOf(WebhookCursorEntity(profile.id, WebhookDataType.TRANSACTIONS, cursorAt)), false)
        assertEquals(listOf(250, 1), payloads.map { it.envelope.transactions.size })
        assertEquals(1, payloads.map { it.envelope.batch.id }.distinct().size)
        assertEquals(0, payloads.last().cursorUpdates.single().successAt.nano % 1_000_000)
        assertEquals(payloads.last().envelope.generatedAt, payloads.last().cursorUpdates.single().successAt.toString())
    }

    @Test fun `budget payload reports actual spending for resolved window in selected currency`() = runBlocking {
        val budget = BudgetEntity(id = 1, name = "Test", limitAmount = BigDecimal("500"),
            periodType = BudgetPeriodType.MONTHLY, startDate = LocalDate.of(2025, 1, 1), endDate = LocalDate.of(2025, 1, 31))
        every { budgets.getActiveBudgets() } returns flowOf(listOf(budget, budget.copy(id = 2, currency = "USD")))
        every { budgets.getCategoriesForBudget(1) } returns flowOf(emptyList())
        coEvery { preferences.getBudgetCycleStartDay() } returns 10
        coEvery { groups.getBudgetWindowBreakdown(budget, any(), "INR") } answers {
            WindowBreakdown(secondArg(), emptyList(), BigDecimal("500"), BigDecimal("125.50"))
        }
        val payload = builder.build(profile, setOf(WebhookDataType.BUDGETS), emptyList(), false).single().envelope.budgets.single()
        val window = resolveBudgetWindow(budget, LocalDate.now(), 10)
        assertEquals("125.5", payload.spending)
        assertEquals(window.start.toString(), payload.startDate)
        assertEquals(window.end.toString(), payload.endDate)
        assertEquals("INR", payload.currency)
        coVerify(exactly = 1) { groups.getBudgetWindowBreakdown(any(), any(), any()) }
    }
}
