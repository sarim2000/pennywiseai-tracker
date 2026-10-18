package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
        assertTrue(repo.deleteDuplicate(duplicateId = 8, keeperId = 9))
        coVerifyOrder {
            goals.moveContributions(8, 9)
            dao.softDeleteTransaction(8)
        }
    }

    @Test
    fun aDuplicateWithSplitSharesMovesThemToTheKeeper() = runBlocking {
        coEvery { dao.getShareAmounts(8) } returns listOf(BigDecimal("300"))
        coEvery { dao.moveSharesToKeeper(8, 9) } returns true
        assertTrue(repo.deleteDuplicate(duplicateId = 8, keeperId = 9))
        coVerifyOrder {
            dao.moveSharesToKeeper(8, 9)
            dao.softDeleteTransaction(8)
        }
    }

    @Test
    fun aDuplicateWhoseSplitSharesCantMoveIsKept() = runBlocking {
        // #346: the keeper is loan-linked, split, another currency or smaller.
        coEvery { dao.getShareAmounts(8) } returns listOf(BigDecimal("300"))
        coEvery { dao.moveSharesToKeeper(8, 9) } returns false
        assertFalse(repo.deleteDuplicate(duplicateId = 8, keeperId = 9))
        coVerify(exactly = 0) { dao.softDeleteTransaction(8) }
        coVerify(exactly = 0) { goals.moveContributions(any(), any()) }
    }

    @Test
    fun aStaleWriteCantDropOrBreakASplitBill() = runBlocking {
        // #346: the share rows say split even though the stored total is stale (null);
        // a stale copy (e.g. an SMS re-parse) arrives as a transfer in another currency.
        coEvery { dao.getShareAmounts(9) } returns listOf(BigDecimal("400"), BigDecimal("200"))
        coEvery { dao.getTransactionById(9) } returns txn("1000")
        repo.updateTransaction(txn("1000").copy(transactionType = TransactionType.TRANSFER, currency = "USD"))
        coVerify {
            dao.updateTransaction(match {
                it.transactionType == TransactionType.EXPENSE && it.currency == "INR" &&
                    it.lentSharesTotal?.compareTo(BigDecimal("600")) == 0
            })
        }
    }

    @Test
    fun undoingADeleteRewritesTheSplitTotalFromTheShares() = runBlocking {
        // #346: the snapshot claims ₹600 shared, but the shares were removed while it was deleted.
        val snapshot = txn("1000").copy(isDeleted = true, lentSharesTotal = BigDecimal("600"))
        coEvery { dao.getShareAmounts(9) } returns emptyList()
        repo.undoDeleteTransaction(snapshot)
        coVerify { dao.updateTransaction(match { !it.isDeleted && it.lentSharesTotal == null }) }
    }
}
