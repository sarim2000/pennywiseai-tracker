package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun runWriteAndCapBlocks() {
        coEvery { goals.writeAndCap<Any?>(any(), any()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
    }

    @Test
    fun editingATransactionWritesInsideWriteAndCap() = runBlocking {
        repo.updateTransaction(txn("250"))
        coVerify { goals.writeAndCap<Any?>(9, any()) }
        coVerify { dao.updateTransaction(match { it.amount.compareTo(BigDecimal("250")) == 0 }) }
    }

    @Test
    fun parserRepairWritesInsideWriteAndCap() = runBlocking {
        coEvery { dao.repairParsedAmount(any(), any(), any(), any(), any(), any()) } returns 1
        assertTrue(repo.correctParsedAmount(txn("5000"), txn("50")))
        coVerify { goals.writeAndCap<Any?>(9, any()) }
        coVerify { dao.repairParsedAmount(9, BigDecimal("50"), "INR", any(), BigDecimal("5000"), "INR") }
    }

    @Test
    fun removingADuplicateMovesItsContributionsFirst() = runBlocking {
        repo.deleteDuplicate(duplicateId = 8, keeperId = 9)
        coVerifyOrder {
            goals.moveContributions(8, 9)
            dao.moveSharesToKeeper(8, 9)
            dao.softDeleteTransaction(8)
        }
    }

    @Test
    fun aStaleWriteCantDropOrBreakASplitBill() = runBlocking {
        // #346: the persisted row is split; a stale copy (e.g. an SMS re-parse) arrives as a transfer with no total.
        coEvery { dao.getTransactionById(9) } returns txn("1000").copy(lentSharesTotal = BigDecimal("600"))
        repo.updateTransaction(txn("1000").copy(transactionType = TransactionType.TRANSFER, currency = "USD"))
        coVerifyOrder {
            dao.updateTransaction(match { it.transactionType == TransactionType.EXPENSE && it.currency == "INR" })
            dao.syncLentSharesTotal(9)
        }
    }
}
