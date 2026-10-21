package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.MerchantMappingRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A category picked from an alert teaches the merchant, but only when there is one to teach. */
class LearnCategoryUseCaseTest {
    private val transactions = mockk<TransactionRepository>(relaxed = true)
    private val mappings = mockk<MerchantMappingRepository>(relaxed = true)
    private val learn = LearnCategoryUseCase(transactions, mappings)

    private fun given(merchant: String, type: TransactionType = TransactionType.EXPENSE, deleted: Boolean = false) =
        coEvery { transactions.getTransactionById(1) } returns TransactionEntity(
            id = 1, amount = BigDecimal("4.25"), merchantName = merchant, category = "Others",
            transactionType = type, dateTime = LocalDateTime.now(), currency = "JOD",
            transactionHash = "h1", isDeleted = deleted
        )

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

    /** Makes the stored mapping the one [learned] wrote, as the DAO would after setMapping. */
    private fun stored(learned: LearnCategoryUseCase.Learned) =
        coEvery { mappings.getMapping("Shein") } returns
            MerchantMappingEntity("Shein", learned.category, updatedAt = learned.mappedAt!!)

    @Test
    fun `undo puts back the category, the old mapping and the moved rows`() = runBlocking {
        given("Shein")
        coEvery { mappings.getCategoryForMerchant("Shein") } returns "Entertainment"
        coEvery { transactions.getMovableMerchantRowIds(any(), any(), any()) } returns listOf(7L)
        val learned = learn(1, "Shopping")!!
        stored(learned)

        learn.undo(learned)

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
    fun `undoing an older alert keeps a newer pick for the same merchant`() = runBlocking {
        given("Shein")
        coEvery { mappings.getCategoryForMerchant("Shein") } returns null
        val older = learn(1, "Food & Dining")!!
        // A second alert taught Shopping afterwards.
        coEvery { mappings.getMapping("Shein") } returns
            MerchantMappingEntity("Shein", "Shopping", updatedAt = older.mappedAt!!.plusSeconds(30))

        learn.undo(older)

        coVerify(exactly = 0) { mappings.removeMapping(any()) }
        coVerify(exactly = 1) { mappings.setMapping(any(), any(), any()) } // only the original lesson
        // Rows are moved back only while still in the older pick's category.
        coVerify { transactions.moveCategory(listOf(1L), from = "Food & Dining", to = "Others") }
    }
}
