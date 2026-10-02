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
        coEvery { transactions.getWebhookChanges(cursorAt, any(), "INR", profile.id) } returns (1L..251L).map {
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

    @Test fun `backward clock replays edits before the old cursor before resetting it`() = runBlocking {
        val now = LocalDateTime.of(2026, 1, 1, 10, 0)
        val oldCursor = now.plusHours(2)
        val epoch = LocalDateTime.of(1970, 1, 1, 0, 0)
        coEvery { transactions.getWebhookChanges(epoch, now, "INR", profile.id) } returns listOf(
            TransactionEntity(id = 1, amount = BigDecimal.ONE, merchantName = "Test merchant", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = now.minusYears(1), transactionHash = "clock",
                updatedAt = now.minusMinutes(1))
        )
        val payload = builder.buildAt(profile, setOf(WebhookDataType.TRANSACTIONS),
            listOf(WebhookCursorEntity(profile.id, WebhookDataType.TRANSACTIONS, oldCursor)), false, now).single()
        assertEquals(epoch.toString(), payload.envelope.request.start)
        assertEquals("txn_1", payload.envelope.transactions.single().id)
        assertEquals(now, payload.cursorUpdates.single().successAt)
    }

    @Test fun `currency removal contains no other currency financial details`() = runBlocking {
        coEvery { transactions.getWebhookChanges(any(), any(), "INR", profile.id) } returns listOf(
            TransactionEntity(id = 1, amount = BigDecimal.ONE, merchantName = "Test merchant", category = "Food",
                currency = "USD", transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.now(), transactionHash = "currency")
        )
        val payload = builder.build(profile, setOf(WebhookDataType.TRANSACTIONS), emptyList(), false)
            .single().envelope.transactions.single()
        assertEquals("delete", payload.action)
        assertNull(payload.currency)
        assertNull(payload.amount)
        assertNull(payload.merchant)
    }

    @Test fun `wallet marker is exported independently of cash account type`() = runBlocking {
        val now = LocalDateTime.of(2026, 1, 1, 12, 0)
        every { accounts.getAllLatestBalances() } returns flowOf(listOf(
            AccountBalanceEntity(bankName = "Test wallet", accountLast4 = AccountBalanceEntity.WALLET_ACCOUNT_MARKER,
                balance = BigDecimal.ONE, timestamp = now, accountType = "SAVINGS"),
            AccountBalanceEntity(bankName = "Test cash", accountLast4 = "0000",
                balance = BigDecimal.ONE, timestamp = now, accountType = "CASH")))
        val rows = builder.build(profile, setOf(WebhookDataType.ACCOUNTS), emptyList(), false).single().envelope.accounts
        assertTrue(rows[0].isWallet)
        assertFalse(rows[1].isWallet)
    }

    @Test fun `all snapshot sections are bounded without losing records or sharing different batch ids`() = runBlocking {
        val now = LocalDateTime.of(2026, 1, 1, 12, 0)
        val budget = BudgetEntity(name = "Test", limitAmount = BigDecimal("500"), periodType = BudgetPeriodType.MONTHLY,
            startDate = now.toLocalDate(), endDate = now.toLocalDate().plusMonths(1))
        every { budgets.getActiveBudgets() } returns flowOf((1L..251L).map { budget.copy(id = it) })
        every { budgets.getCategoriesForBudget(any()) } returns flowOf(emptyList())
        coEvery { preferences.getBudgetCycleStartDay() } returns 1
        coEvery { groups.getBudgetWindowBreakdown(any(), any(), "INR") } answers {
            WindowBreakdown(secondArg(), emptyList(), BigDecimal("500"), BigDecimal.ZERO)
        }
        every { accounts.getAllLatestBalances() } returns flowOf((1L..501L).map {
            AccountBalanceEntity(bankName = "Test bank $it", accountLast4 = "0000", balance = BigDecimal.ONE, timestamp = now)
        })
        every { subscriptions.getAllSubscriptions() } returns flowOf((1L..501L).map {
            SubscriptionEntity(id = it, merchantName = "Test subscription", amount = BigDecimal.ONE, nextPaymentDate = null)
        })
        coEvery { transactions.getWebhookChanges(any(), any(), "INR", profile.id) } returns (1L..251L).map {
            TransactionEntity(id = it, amount = BigDecimal.ONE, merchantName = "Test merchant", category = "Food",
                transactionType = TransactionType.EXPENSE, dateTime = now, transactionHash = "snapshot_$it")
        }
        val batches = builder.buildAt(profile, setOf(WebhookDataType.TRANSACTIONS, WebhookDataType.BUDGETS,
            WebhookDataType.ACCOUNTS, WebhookDataType.SUBSCRIPTIONS), emptyList(), false, now)
        assertEquals(3, batches.size)
        val envelopes = batches.map { it.envelope }
        assertEquals(1, envelopes.map { it.batch.id }.distinct().size)
        assertEquals(listOf(1, 2, 3), envelopes.map { it.batch.index })
        assertTrue(envelopes.all { it.batch.count == 3 && it.transactions.size <= 250 && it.budgets.size <= 250 &&
            it.accounts.size <= 250 && it.subscriptions.size <= 250 })
        assertEquals(251, envelopes.flatMap { it.transactions }.map { it.id }.distinct().size)
        assertEquals(251, envelopes.flatMap { it.budgets }.map { it.id }.distinct().size)
        assertEquals(501, envelopes.flatMap { it.accounts }.map { it.id }.distinct().size)
        assertEquals(501, envelopes.flatMap { it.subscriptions }.map { it.id }.distinct().size)
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
