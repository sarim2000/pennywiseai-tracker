package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.GoalDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.GoalContributionEntity
import com.pennywiseai.tracker.data.database.entity.GoalEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** #914: the write-side invariants GoalRepository enforces. */
class GoalRepositoryTest {
    private val goalDao = mockk<GoalDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val repo = GoalRepository(goalDao, transactionDao, database)

    private val goal = GoalEntity(
        id = 1, uid = "goal-uid", name = "Trip", targetAmount = BigDecimal("1000"), currency = "INR",
        dueDate = LocalDate.of(2027, 6, 1), createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)
    )

    private fun txn(amount: String, currency: String = "INR", deleted: Boolean = false) = TransactionEntity(
        id = 9, amount = BigDecimal(amount), merchantName = "Shop", category = "Others",
        transactionType = TransactionType.INCOME, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
        transactionHash = "h9", currency = currency, isDeleted = deleted
    )

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { goalDao.getGoal(1) } returns goal
    }

    @After
    fun tearDown() = unmockkStatic("androidx.room.RoomDatabaseKt")

    @Test
    fun currencyIsImmutableOnceTheGoalHasContributions() = runBlocking {
        coEvery { goalDao.countContributions(1) } returns 1
        val result = runCatching { repo.updateGoal(goal.copy(currency = "USD")) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { goalDao.updateGoal(any()) }

        // Other edits still save, keeping uid and the pace start.
        val saved = slot<GoalEntity>()
        coEvery { goalDao.updateGoal(capture(saved)) } returns Unit
        repo.updateGoal(goal.copy(name = "Trip 2", uid = "other", createdAt = LocalDateTime.now()))
        assertEquals("Trip 2", saved.captured.name)
        assertEquals("goal-uid", saved.captured.uid)
        assertEquals(goal.createdAt, saved.captured.createdAt)
    }

    @Test
    fun currencyMayChangeWhileTheGoalHasNoContributions() = runBlocking {
        coEvery { goalDao.countContributions(1) } returns 0
        repo.updateGoal(goal.copy(currency = "USD"))
        coVerify { goalDao.updateGoal(match { it.currency == "USD" }) }
    }

    @Test
    fun currencyIsImmutableOnceTheGoalHasAlreadySavedMoney() = runBlocking {
        // No contributions, but ₹5,000 already saved: switching to USD would turn it into $5,000.
        coEvery { goalDao.getGoal(1) } returns goal.copy(alreadySaved = BigDecimal("5000"))
        coEvery { goalDao.countContributions(1) } returns 0
        val result = runCatching { repo.updateGoal(goal.copy(currency = "USD", alreadySaved = BigDecimal("5000"))) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { goalDao.updateGoal(any()) }
    }

    @Test
    fun linkedContributionMustFitWhatTheTransactionHasLeft() = runBlocking {
        coEvery { transactionDao.getTransactionById(9) } returns txn("500")
        coEvery { goalDao.getContributionsForTransaction(9) } returns listOf(
            GoalContributionEntity(id = 4, goalId = 2, amount = BigDecimal("300"), originalAmount = BigDecimal("300"), originalCurrency = "INR", transactionId = 9)
        )

        val tooMuch = runCatching { repo.addContribution(1, BigDecimal("250"), transactionId = 9) }
        assertTrue(tooMuch.exceptionOrNull() is IllegalArgumentException)

        val inserted = slot<GoalContributionEntity>()
        coEvery { goalDao.insertContribution(capture(inserted)) } returns 5
        repo.addContribution(1, BigDecimal("200"), transactionId = 9)
        assertEquals(0, BigDecimal("200").compareTo(inserted.captured.originalAmount))
        assertEquals("INR", inserted.captured.originalCurrency)
    }

    @Test
    fun crossCurrencyLinkNeedsTheTransactionShare() = runBlocking {
        coEvery { transactionDao.getTransactionById(9) } returns txn("50", currency = "USD")
        coEvery { goalDao.getContributionsForTransaction(9) } returns emptyList()

        assertTrue(runCatching { repo.addContribution(1, BigDecimal("4100"), transactionId = 9) }.isFailure)

        val inserted = slot<GoalContributionEntity>()
        coEvery { goalDao.insertContribution(capture(inserted)) } returns 5
        repo.addContribution(1, BigDecimal("4100"), originalAmount = BigDecimal("50"), transactionId = 9)
        assertEquals("USD", inserted.captured.originalCurrency)
        assertEquals(0, BigDecimal("4100").compareTo(inserted.captured.amount))
    }

    @Test
    fun withdrawalIsStoredNegative_deletedTransactionCantBeLinked() = runBlocking {
        val inserted = slot<GoalContributionEntity>()
        coEvery { goalDao.insertContribution(capture(inserted)) } returns 5
        repo.addWithdrawal(1, BigDecimal("75"))
        assertEquals(0, BigDecimal("-75").compareTo(inserted.captured.amount))

        coEvery { transactionDao.getTransactionById(9) } returns txn("500", deleted = true)
        assertTrue(runCatching { repo.addContribution(1, BigDecimal("10"), transactionId = 9) }.isFailure)
        assertTrue(runCatching { repo.addContribution(1, BigDecimal("-10")) }.isFailure)
    }

    @Test
    fun canCreateGoalCountsOnlyActiveGoals() = runBlocking {
        val today = LocalDate.of(2026, 10, 16)
        val reached = goal.copy(id = 2)
        coEvery { goalDao.observeGoals() } returns flowOf(listOf(reached, goal.copy(id = 3, isArchived = true)))
        coEvery { goalDao.observeEffectiveContributions() } returns flowOf(
            listOf(GoalContributionEntity(goalId = 2, amount = BigDecimal("1000")))
        )
        assertTrue(repo.canCreateGoal(isPro = false, today = today))

        coEvery { goalDao.observeGoals() } returns flowOf(listOf(reached, goal))
        assertFalse(repo.canCreateGoal(isPro = false, today = today))
        assertTrue(repo.canCreateGoal(isPro = true, today = today))
    }

    @Test
    fun repeatForNextYearStartsFreshAYearLater() = runBlocking {
        val inserted = slot<GoalEntity>()
        coEvery { goalDao.insertGoal(capture(inserted)) } returns 7
        repo.repeatForNextYear(1)
        with(inserted.captured) {
            assertEquals(0L, id)
            assertTrue(uid != goal.uid)
            assertEquals(LocalDate.of(2028, 6, 1), dueDate)
            assertEquals(0, BigDecimal.ZERO.compareTo(alreadySaved))
            assertEquals(goal.name, name)
            assertEquals(goal.currency, currency)
            assertTrue(createdAt.isAfter(goal.createdAt))
        }
    }

    @Test
    fun editedAmountAndOriginalAmountMustShareASign() = runBlocking {
        val converted = GoalContributionEntity(
            id = 6, uid = "c-uid", goalId = 1, amount = BigDecimal("4100"),
            originalAmount = BigDecimal("50"), originalCurrency = "USD"
        )
        coEvery { goalDao.getContribution(6) } returns converted

        // Turned into a withdrawal but the original amount left positive → rejected.
        val mismatch = runCatching { repo.updateContribution(converted.copy(amount = BigDecimal("-4100"))) }
        assertTrue(mismatch.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { goalDao.updateContribution(any()) }

        // Both flipped → saved.
        repo.updateContribution(converted.copy(amount = BigDecimal("-4100"), originalAmount = BigDecimal("-50")))
        coVerify { goalDao.updateContribution(match { it.amount.signum() < 0 && it.originalAmount!!.signum() < 0 }) }
    }

    private fun linked(id: Long, share: String, goalId: Long = 1) = GoalContributionEntity(
        id = id, goalId = goalId, amount = BigDecimal(share), originalAmount = BigDecimal(share),
        originalCurrency = "INR", transactionId = 9
    )

    @Test
    fun writeAndCapCapsToTheStoredAmountInsideTheSameTransaction() = runBlocking {
        coEvery { goalDao.getContributionsForTransaction(9) } returns listOf(linked(1, "300"), linked(2, "200", goalId = 2))
        var stored = txn("500")
        var inTransaction = false
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            inTransaction = true
            try { secondArg<suspend () -> Any?>().invoke() } finally { inTransaction = false }
        }
        coEvery { transactionDao.getTransactionById(9) } answers { stored }
        val written = mutableListOf<List<GoalContributionEntity>>()
        coEvery { goalDao.updateContributions(any()) } answers {
            assertTrue("cap must run in the write's DB transaction", inTransaction)
            written += firstArg<List<GoalContributionEntity>>()
        }

        repo.writeAndCap(9) { stored = txn("800") } // raised: shares still fit
        assertTrue(written.isEmpty())

        repo.writeAndCap(9) { stored = txn("250") } // lowered: scaled to what's stored now
        assertEquals(0, BigDecimal("250").compareTo(written.single().sumOf { it.originalAmount!! }))
    }

    @Test
    fun writeAndCapUsesTheStoredAmountNotTheOneTheCallerMeant() = runBlocking {
        // A repair whose guard didn't match writes nothing: the stored ₹500 still holds both shares.
        coEvery { goalDao.getContributionsForTransaction(9) } returns listOf(linked(1, "300"), linked(2, "200", goalId = 2))
        coEvery { transactionDao.getTransactionById(9) } returns txn("500")
        val result = repo.writeAndCap(9) { false }
        assertFalse(result)
        coVerify(exactly = 0) { goalDao.updateContributions(any()) }
    }

    @Test
    fun writeAndCapWithNoContributionsDoesNotReadTheTransaction() = runBlocking {
        coEvery { goalDao.getContributionsForTransaction(9) } returns emptyList()
        repo.writeAndCap(9) { }
        coVerify(exactly = 0) { transactionDao.getTransactionById(any()) }
        coVerify(exactly = 0) { goalDao.updateContributions(any()) }
    }

    @Test
    fun moveContributionsRepointsADuplicatesContributionsToTheSurvivor() = runBlocking {
        coEvery { goalDao.getContributionsForTransaction(8) } returns listOf(linked(1, "400").copy(transactionId = 8))
        coEvery { goalDao.getContributionsForTransaction(9) } returns emptyList()
        coEvery { transactionDao.getTransactionById(9) } returns txn("500")
        val written = slot<List<GoalContributionEntity>>()
        coEvery { goalDao.updateContributions(capture(written)) } returns Unit

        repo.moveContributions(fromTransactionId = 8, toTransactionId = 9)
        assertEquals(listOf(9L), written.captured.map { it.transactionId })
    }
}
