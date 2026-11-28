package com.pennywiseai.tracker.presentation.groups

import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class MarkAllReimbursableTest {
    private fun txn(
        id: Long,
        type: TransactionType = TransactionType.EXPENSE,
        status: ReimbursementStatus? = null,
        deleted: Boolean = false
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal("10"),
        merchantName = "Synthetic shop",
        category = "Others",
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 12, 2, 10, 0),
        transactionHash = "h$id",
        reimbursementStatus = status,
        isDeleted = deleted
    )

    @Test
    fun marksOnlyUnmarkedLiveSpending() {
        val rows = listOf(
            txn(1),
            txn(2, TransactionType.CREDIT),
            txn(3, TransactionType.INCOME),
            txn(4, TransactionType.TRANSFER),
            txn(5, TransactionType.INVESTMENT),
            txn(6, status = ReimbursementStatus.PENDING),
            txn(7, status = ReimbursementStatus.REIMBURSED),
            txn(8, deleted = true)
        )
        assertEquals(listOf(1L, 2L), reimbursementMarkIds(rows))
    }

    @Test
    fun nothingToMarkGivesAnEmptyList() {
        assertEquals(emptyList<Long>(), reimbursementMarkIds(listOf(txn(1, TransactionType.INCOME))))
    }
}
