package com.pennywiseai.tracker.domain.usecase

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A category picked from an alert teaches the merchant, but only when there is one to teach. */
class LearnCategoryUseCaseTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val mappings = mockk<MerchantMappingRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val learn = LearnCategoryUseCase(transactions, mappings, database)

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() = unmockkStatic("androidx.room.RoomDatabaseKt")

    private fun given(merchant: String, type: TransactionType = TransactionType.EXPENSE, deleted: Boolean = false, id: Long = 1) =
        coEvery { transactions.getTransactionById(id) } returns TransactionEntity(
            id = id, amount = BigDecimal("4.25"), merchantName = merchant, category = "Others",
            transactionType = type, dateTime = LocalDateTime.now(), currency = "JOD",
            transactionHash = "h$id", isDeleted = deleted
        )

    /** Makes the stored mapping the one [learned] wrote, as the DAO would after setMapping. */
    private fun stored(learned: LearnCategoryUseCase.Learned) =
        coEvery { mappings.getMapping("Shein") } returns
            MerchantMappingEntity("Shein", learned.category, updatedAt = learned.mappedAt!!)

    @Test
    fun `a pick remembers the merchant and moves its unsorted purchases`() = runBlocking {
        given("Shein")
        val purchases = setOf(TransactionType.EXPENSE, TransactionType.CREDIT)
        coEvery { transactions.getMovableMerchantRowIds("Shein", "Others", purchases) } returns listOf(7L, 8L, 9L)

        val learned = learn(1, "Shopping")!!

        assertEquals(listOf(7L, 8L, 9L), learned.movedIds)
        coVerify { mappings.setMapping("Shein", "Shopping", any()) }
        coVerify { transactions.moveCategory(listOf(7L, 8L, 9L), from = "Others", to = "Shopping") }
    }

    @Test
    fun `money in only moves money in`() = runBlocking {
        given("Ahmad", type = TransactionType.INCOME)
        learn(1, "Salary")
        coVerify { transactions.getMovableMerchantRowIds("Ahmad", "Others", setOf(TransactionType.INCOME)) }
    }

    @Test
    fun `no merchant, a pick of Others, or a deleted row teaches nothing`() = runBlocking {
        given("Unknown Merchant")
        assertNull(learn(1, "Groceries")!!.merchant)
        given("Shein")
        assertNull(learn(1, "Others")!!.merchant)
        given("Shein", deleted = true)
        assertNull(learn(1, "Shopping"))

        coVerify(exactly = 0) { mappings.setMapping(any(), any(), any()) }
        coVerify(exactly = 0) { transactions.moveCategory(any(), any(), any()) }
    }

    @Test
    fun `a parser placeholder files only the tapped row`() = runBlocking {
        // One tap on a "UPI Payment" alert once mapped that name and moved 399 rows.
        listOf(
            "UPI Payment", " upi payment ", "ATM Withdrawal", "ATM", "ATM - MG Road", "PNB ATM Withdrawal",
            "Fund Transfer", "Transfer", "Bank Transfer", "Cash Withdrawal", "NEFT Transfer", "IMPS Credit",
            "Transfer via ADCB Banking"
        ).forEach { name ->
            given(name)
            val learned = learn(1, "Shopping")!!
            assertNull(name, learned.merchant)
            assertTrue(name, learned.movedIds.isEmpty())
        }
        coVerify(exactly = 0) { mappings.setMapping(any(), any(), any()) }
        coVerify(exactly = 0) { transactions.getMovableMerchantRowIds(any(), any(), any()) }
    }

    @Test
    fun `a real merchant that mentions a channel word is still learned`() {
        listOf("Shein", "Swiggy", "Bank of Baroda Canteen", "UPI Fashion Store", "Atmosphere Cafe").forEach {
            assertFalse(it, LearnCategoryUseCase.isPlaceholderMerchant(it))
        }
    }

    @Test
    fun `undo puts back the category, the old mapping and the moved rows`() = runBlocking {
        given("Shein")
        coEvery { mappings.getCategoryForMerchant("Shein") } returns "Entertainment"
        coEvery { transactions.getMovableMerchantRowIds(any(), any(), any()) } returns listOf(7L)
        val learned = learn(1, "Shopping")!!
        stored(learned)

        assertTrue(learn.undo(learned))

        coVerify { transactions.moveCategory(listOf(1L), from = "Shopping", to = "Others") }
        coVerify { mappings.setMapping("Shein", "Entertainment", any()) }
        coVerify { transactions.moveCategory(listOf(7L), from = "Shopping", to = "Others") }
    }

    @Test
    fun `undo of a first lesson forgets the merchant`() = runBlocking {
        given("Shein")
        coEvery { mappings.getCategoryForMerchant("Shein") } returns null
        val learned = learn(1, "Shopping")!!
        stored(learned)

        learn.undo(learned)

        coVerify { mappings.removeMapping("Shein") }
    }

    @Test
    fun `only the latest lesson for a merchant can be undone`() = runBlocking {
        // Pick A, then B, then undo A, then undo B: the user must not end up with A.
        given("Shein", id = 1)
        coEvery { mappings.getCategoryForMerchant("Shein") } returns null
        val a = learn(1, "Food & Dining")!!
        given("Shein", id = 2)
        coEvery { mappings.getCategoryForMerchant("Shein") } returns "Food & Dining"
        val b = learn(2, "Shopping")!!.copy(mappedAt = a.mappedAt!!.plusSeconds(30))
        stored(b)

        assertFalse(learn.undo(a))
        coVerify(exactly = 0) { transactions.moveCategory(listOf(1L), from = "Food & Dining", to = "Others") }

        assertTrue(learn.undo(b))
        // Undoing B returns to the state before B, which had A.
        coVerify(exactly = 2) { mappings.setMapping("Shein", "Food & Dining", any()) } // lesson A, then B's undo
        coVerify(exactly = 0) { mappings.removeMapping(any()) }
    }
}
