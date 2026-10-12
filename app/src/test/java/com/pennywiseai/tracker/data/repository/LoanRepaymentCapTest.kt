package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** #831: a repayment never repays more than is still owed. */
class LoanRepaymentCapTest {
    private val loanDao = mockk<LoanDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val repo = LoanRepository(loanDao, transactionDao)

    private fun loan(remaining: String) = LoanEntity(
        id = 2, personName = "Pal", direction = LoanDirection.LENT,
        originalAmount = BigDecimal("130"), remainingAmount = BigDecimal(remaining), currency = "INR"
    )

    private fun txn(amount: String, loanId: Long? = null, contribution: String? = null) = TransactionEntity(
        id = 9, amount = BigDecimal(amount), merchantName = "From Pal", category = "Income",
        transactionType = TransactionType.INCOME, dateTime = LocalDateTime.of(2026, 10, 12, 12, 0),
        transactionHash = "h", loanId = loanId, loanContribution = contribution?.let(::BigDecimal)
    )

    private fun savedContribution(remaining: String, txn: TransactionEntity, contribution: BigDecimal?): BigDecimal? {
        coEvery { loanDao.getLoanById(2) } returns loan(remaining)
        coEvery { transactionDao.getTransactionById(9) } returns txn
        val saved = slot<TransactionEntity>()
        coEvery { transactionDao.updateTransaction(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9, contribution) }
        coVerify { loanDao.linkTransaction(9, 2) }
        return if (saved.isCaptured) saved.captured.loanContribution else null
    }

    @Test
    fun `overpayment is capped at what is owed`() {
        // ₹500 back on ₹130 owed: only ₹130 repays the loan.
        assertEquals(BigDecimal("130"), savedContribution("130", txn("500"), null))
    }

    @Test
    fun `an explicit contribution above what is owed is capped too`() {
        assertEquals(BigDecimal("130"), savedContribution("130", txn("500"), BigDecimal("500")))
    }

    @Test
    fun `a repayment within what is owed is left as the full amount`() {
        assertNull(savedContribution("300", txn("100"), null))
    }

    @Test
    fun `a partial contribution within what is owed is kept`() {
        assertEquals(BigDecimal("40"), savedContribution("300", txn("100"), BigDecimal("40")))
    }

    @Test
    fun `relinking a row already on the loan counts its own share as still owed`() {
        // ₹50 left after this row's ₹80; re-recording it may repay up to ₹130.
        assertEquals(BigDecimal("130"), savedContribution("50", txn("200", loanId = 2, contribution = "80"), null))
    }
}
