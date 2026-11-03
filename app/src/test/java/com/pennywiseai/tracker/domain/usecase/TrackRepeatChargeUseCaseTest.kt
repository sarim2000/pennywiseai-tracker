package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.CategoryDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** "Track as … bill" re-checks the charge at tap time, so an edited one isn't tracked. */
class TrackRepeatChargeUseCaseTest {
    private val transactionDao = mockk<TransactionDao>()
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val categoryDao = mockk<CategoryDao>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val useCase = TrackRepeatChargeUseCase(database, transactionDao, transactions, subscriptions, categoryDao, preferences)

    private val now = LocalDateTime.of(2026, 10, 25, 9, 0)
    private fun charge(id: Long, at: LocalDateTime, type: TransactionType = TransactionType.EXPENSE) = TransactionEntity(
        id = id, amount = BigDecimal("15.49"), merchantName = "Netflix.com", category = "Entertainment",
        transactionType = type, dateTime = at, currency = "USD", transactionHash = "h$id"
    )

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { transactionDao.getChargesInCurrencyBetween(any(), any(), any(), any(), any()) } returns
            listOf(charge(1, now.minusDays(30)))
        coEvery { subscriptions.hasAnySubscriptionFor(any(), any()) } returns false
        every { subscriptions.advance(any(), any()) } answers { firstArg<java.time.LocalDate>().plusMonths(1) }
        coEvery { subscriptions.insertSubscription(any()) } returns 9L
        coEvery { categoryDao.getAllCategoriesList() } returns emptyList()
        every { preferences.dismissedRecurringSuggestions } returns flowOf(emptySet())
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    @Test
    fun `a charge that still repeats is tracked`() = runBlocking {
        coEvery { transactions.getTransactionById(2) } returns charge(2, now)

        assertEquals(9L, useCase.track(2, "Monthly")?.id)
    }

    @Test
    fun `a charge edited into income since the alert is not tracked`() = runBlocking {
        coEvery { transactions.getTransactionById(2) } returns charge(2, now, TransactionType.INCOME)

        assertNull(useCase.track(2, "Monthly"))
        coVerify(exactly = 0) { subscriptions.insertSubscription(any()) }
    }

    @Test
    fun `a charge whose date no longer fits the alert's cycle is not tracked`() = runBlocking {
        // Moved a week earlier: a 23-day gap is no billing cycle.
        coEvery { transactions.getTransactionById(2) } returns charge(2, now.minusDays(7))

        assertNull(useCase.track(2, "Monthly"))
        coVerify(exactly = 0) { subscriptions.insertSubscription(any()) }
    }

    @Test
    fun `a same-amount purchase days ago means a frequent buy, not a bill`() = runBlocking {
        // Newest match wins on purpose: a coffee bought every few days would
        // otherwise find one ~30 days back and be offered as a monthly bill.
        coEvery { transactionDao.getChargesInCurrencyBetween(any(), any(), any(), any(), any()) } returns
            listOf(charge(3, now.minusDays(3)), charge(1, now.minusDays(30)))

        assertNull(useCase.suggestedCycle(charge(2, now)))
    }

    @Test
    fun `a repeat at an everyday-spending merchant is not offered`() = runBlocking {
        assertNull(useCase.suggestedCycle(charge(2, now).copy(category = "Transportation")))
        assertEquals("Monthly", useCase.suggestedCycle(charge(2, now)))
    }

    @Test
    fun `a merchant marked not recurring is not offered`() = runBlocking {
        every { preferences.dismissedRecurringSuggestions } returns flowOf(setOf("USD|netflixcom"))

        assertNull(useCase.suggestedCycle(charge(2, now)))
    }
}
