package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #914: every transaction amount write caps its goal contributions; duplicates hand theirs on. */
class TransactionRepositoryGoalCapTest {
    private val dao = mockk<TransactionDao>(relaxed = true)
    private val goals = mockk<GoalRepository>(relaxed = true)
    private val repo = TransactionRepository(dao, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), goals)

    private fun txn(amount: String) = TransactionEntity(
        id = 9, amount = BigDecimal(amount), merchantName = "Shop", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
        transactionHash = "h9", currency = "INR"
    )

    @Test
    fun editingATransactionCapsItsContributionsToTheNewAmount() = runBlocking {
        repo.updateTransaction(txn("250"))
        coVerifyOrder {
            dao.updateTransaction(any())
            goals.capContributionsToTransaction(9, BigDecimal("250"))
        }
    }

    @Test
    fun parserRepairCapsOnlyWhenTheRowWasRepaired() = runBlocking {
        coEvery { dao.repairParsedAmount(any(), any(), any(), any(), any(), any()) } returns 0
        repo.correctParsedAmount(txn("5000"), txn("50"))
        coVerify(exactly = 0) { goals.capContributionsToTransaction(any(), any()) }

        coEvery { dao.repairParsedAmount(any(), any(), any(), any(), any(), any()) } returns 1
        repo.correctParsedAmount(txn("5000"), txn("50"))
        coVerify { goals.capContributionsToTransaction(9, BigDecimal("50")) }
    }

    @Test
    fun removingADuplicateMovesItsContributionsFirst() = runBlocking {
        repo.deleteDuplicate(duplicateId = 8, keeperId = 9)
        coVerifyOrder {
            goals.moveContributions(8, 9)
            dao.softDeleteTransaction(8)
        }
    }
}
