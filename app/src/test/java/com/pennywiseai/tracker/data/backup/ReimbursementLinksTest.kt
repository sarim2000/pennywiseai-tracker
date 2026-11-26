package com.pennywiseai.tracker.data.backup

import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** Restoring the link from a reimbursed expense to the payment that paid it back (#1389). */
class ReimbursementLinksTest {

    private fun txn(id: Long, type: TransactionType = TransactionType.EXPENSE, paidBy: Long? = null) = TransactionEntity(
        id = id,
        amount = BigDecimal("100.00"),
        merchantName = "Example $id",
        category = "Travel",
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
        transactionHash = "synthetic-$id",
        reimbursementStatus = paidBy?.let { ReimbursementStatus.REIMBURSED },
        reimbursedById = paidBy,
    )

    @Test
    fun mergeRemapsBothEnds_andOnePaymentCanPayBackMany() {
        val rows = listOf(txn(1, paidBy = 3), txn(2, paidBy = 3), txn(3, TransactionType.INCOME))
        val idMap = mapOf(1L to 11L, 2L to 12L, 3L to 13L)

        val links = reimbursementLinks(rows, idMap) { true }

        assertEquals(listOf(11L to 13L, 12L to 13L), links)
    }

    @Test
    fun paymentThatDidNotRestore_dropsTheLink() {
        val rows = listOf(txn(1, paidBy = 3))
        assertEquals(emptyList<Pair<Long, Long>>(), reimbursementLinks(rows, mapOf(1L to 11L)) { true })
    }

    @Test
    fun rowTheImportDidNotInsert_keepsItsOwnState() {
        // A MERGE hash duplicate maps to the local row, which is left as it is.
        val rows = listOf(txn(1, paidBy = 3), txn(3, TransactionType.INCOME))
        val idMap = mapOf(1L to 21L, 3L to 13L)
        assertEquals(emptyList<Pair<Long, Long>>(), reimbursementLinks(rows, idMap) { it == 3L })
    }

    @Test
    fun paymentThatIsALocalDuplicate_isLinked() {
        val rows = listOf(txn(1, paidBy = 3), txn(3, TransactionType.INCOME))
        val idMap = mapOf(1L to 11L, 3L to 5L)
        assertEquals(listOf(11L to 5L), reimbursementLinks(rows, idMap) { it == 1L })
    }

    @Test
    fun selfLink_isDropped() {
        assertEquals(emptyList<Pair<Long, Long>>(), reimbursementLinks(listOf(txn(1, paidBy = 1)), mapOf(1L to 1L)) { true })
    }
}
