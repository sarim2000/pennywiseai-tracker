package com.pennywiseai.tracker.presentation.reimbursements

import com.pennywiseai.tracker.data.database.entity.ReimbursementStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.canMarkReimbursable
import com.pennywiseai.tracker.data.ledger.countedAmount
import com.pennywiseai.tracker.data.repository.keepReimbursement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ReimbursementsTest {

    private fun txn(
        amount: String,
        currency: String = "INR",
        type: TransactionType = TransactionType.EXPENSE,
        status: ReimbursementStatus? = null,
    ) = TransactionEntity(
        amount = BigDecimal(amount),
        merchantName = "Example",
        category = "Travel",
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
        transactionHash = "synthetic-$amount-$currency-$status",
        currency = currency,
        reimbursementStatus = status,
    )

    @Test
    fun pendingTotals_stayPerCurrency_andSkipReimbursed() {
        val rows = listOf(
            txn("500.00", status = ReimbursementStatus.PENDING),
            txn("250.50", status = ReimbursementStatus.PENDING),
            txn("10.00", currency = "USD", status = ReimbursementStatus.PENDING),
            txn("999.00", status = ReimbursementStatus.REIMBURSED),
            txn("42.00"),
        )

        val totals = pendingByCurrency(rows)

        assertEquals(setOf("INR", "USD"), totals.keys)
        assertEquals(0, BigDecimal("750.50").compareTo(totals.getValue("INR").amount))
        assertEquals(0, BigDecimal("10.00").compareTo(totals.getValue("USD").amount))
    }

    @Test
    fun onlyMoneyOutThatIsNotOneYet_canBeMarked() {
        assertTrue(canMarkReimbursable(txn("1")))
        assertTrue(canMarkReimbursable(txn("1", type = TransactionType.CREDIT)))
        assertFalse(canMarkReimbursable(txn("1", type = TransactionType.INCOME)))
        assertFalse(canMarkReimbursable(txn("1", type = TransactionType.TRANSFER)))
        assertFalse(canMarkReimbursable(txn("1", status = ReimbursementStatus.PENDING)))
        assertFalse(canMarkReimbursable(txn("1").copy(isDeleted = true)))
    }

    /** The conservative choice: a reimbursable row counts toward totals exactly as before. */
    @Test
    fun reimbursableRows_stillCountTowardTotals() {
        ReimbursementStatus.entries.forEach { status ->
            assertEquals(0, BigDecimal("500.00").compareTo(txn("500.00", status = status).countedAmount()))
        }
    }

    /** A full-row write (the edit form, a rule) can't undo a mark made after its copy was taken. */
    @Test
    fun aStaleCopy_keepsTheStoredReimbursement() {
        val stored = txn("1", status = ReimbursementStatus.REIMBURSED).copy(reimbursedById = 7)
        val written = keepReimbursement(stored, txn("2"))

        assertEquals(ReimbursementStatus.REIMBURSED, written.reimbursementStatus)
        assertEquals(7L, written.reimbursedById)
        assertEquals(0, BigDecimal("2").compareTo(written.amount))
    }

    @Test
    fun aRowRetypedAwayFromMoneyOut_dropsItsReimbursement() {
        val stored = txn("1", status = ReimbursementStatus.REIMBURSED).copy(reimbursedById = 7)
        listOf(TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.INVESTMENT).forEach { type ->
            val written = keepReimbursement(stored, txn("1", type = type))
            assertNull(written.reimbursementStatus)
            assertNull(written.reimbursedById)
        }
        assertEquals(ReimbursementStatus.REIMBURSED, keepReimbursement(stored, txn("1", type = TransactionType.CREDIT)).reimbursementStatus)
    }
}
