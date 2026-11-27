package com.pennywiseai.tracker.data.database

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** Removing a duplicate keeps its reimbursement (#1389): the keeper takes it, links follow the keeper. */
@RunWith(AndroidJUnit4::class)
class ReimbursementDuplicateTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()
    private val dao = db.transactionDao()

    @After fun close() = db.close()

    private suspend fun insert(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        status: ReimbursementStatus? = null,
        by: Long? = null,
    ) = dao.insertTransaction(
        TransactionEntity(
            id = id, amount = BigDecimal("100"), merchantName = "M", category = "Travel",
            transactionType = type, dateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
            transactionHash = "h$id", reimbursementStatus = status, reimbursedById = by,
        )
    )

    @Test fun keeperTakesTheDuplicatesMark() = runBlocking {
        insert(1, status = ReimbursementStatus.REIMBURSED, by = 5)
        insert(2)
        dao.softDeleteDuplicate(1, 2)
        val keeper = dao.getTransactionById(2)!!
        assertEquals(ReimbursementStatus.REIMBURSED, keeper.reimbursementStatus)
        assertEquals(5L, keeper.reimbursedById)
        assertTrue(dao.getTransactionById(1)!!.isDeleted)
    }

    @Test fun aKeeperWithItsOwnMarkKeepsIt() = runBlocking {
        insert(1, status = ReimbursementStatus.REIMBURSED, by = 5)
        insert(2, status = ReimbursementStatus.PENDING)
        dao.softDeleteDuplicate(1, 2)
        val keeper = dao.getTransactionById(2)!!
        assertEquals(ReimbursementStatus.PENDING, keeper.reimbursementStatus)
        assertNull(keeper.reimbursedById)
    }

    @Test fun expensesPaidByTheDuplicatePointAtTheKeeper() = runBlocking {
        insert(1, TransactionType.INCOME)
        insert(2, TransactionType.INCOME)
        insert(3, status = ReimbursementStatus.REIMBURSED, by = 1)
        dao.softDeleteDuplicate(1, 2)
        assertEquals(2L, dao.getTransactionById(3)!!.reimbursedById)
        assertNull(dao.getTransactionById(2)!!.reimbursementStatus)
    }
}
