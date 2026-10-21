package com.pennywiseai.tracker.domain.usecase

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
    fun `a pick remembers the merchant and moves its unsorted rows of the same kind`() = runBlocking {
        given("Shein")
        coEvery { transactions.recategorizeMerchant("Shein", isIncome = false, from = "Others", to = "Shopping") } returns 3

        assertEquals(3, learn(1, "Shopping"))
        coVerify { mappings.setMapping("Shein", "Shopping") }
    }

    @Test
    fun `no merchant, a pick of Others, or a deleted row teaches nothing`() = runBlocking {
        given("Unknown Merchant")
        assertNull(learn(1, "Groceries"))
        given("Shein")
        assertNull(learn(1, "Others"))
        given("Shein", deleted = true)
        assertNull(learn(1, "Shopping"))

        coVerify(exactly = 0) { mappings.setMapping(any(), any()) }
    }
}
