package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
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
    private val repo = LoanRepository(loanDao, transactionDao, mockk(relaxed = true))

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
    fun `a payment on a loan with nothing left owed counts nothing toward it`() {
        // Reopened after being fully repaid: the whole payment stays ordinary income.
        assertEquals(0, BigDecimal.ZERO.compareTo(savedContribution("0", txn("100"), null)))
    }

    @Test
    fun `a manual repayment above what is owed is capped`() {
        coEvery { loanDao.getLoanById(2) } returns loan("130")
        val inserted = slot<TransactionEntity>()
        coEvery { transactionDao.insertTransaction(capture(inserted)) } returns 11L
        runBlocking { repo.recordManualRepayment(2, BigDecimal("500"), "Pal", "INR") }
        assertEquals(BigDecimal("130"), inserted.captured.loanContribution)
    }

    @Test
    fun `a manual repayment within what is owed keeps the full amount`() {
        coEvery { loanDao.getLoanById(2) } returns loan("130")
        val inserted = slot<TransactionEntity>()
        coEvery { transactionDao.insertTransaction(capture(inserted)) } returns 11L
        runBlocking { repo.recordManualRepayment(2, BigDecimal("100"), "Pal", "INR") }
        assertNull(inserted.captured.loanContribution)
    }

    // #876: a borrowed ₹500 repaid by a ₹220 UPI payment the parser typed CREDIT.
    private val borrowed = LoanEntity(
        id = 2, personName = "Pal", direction = LoanDirection.BORROWED,
        originalAmount = BigDecimal("500"), remainingAmount = BigDecimal("500"), currency = "INR"
    )
    private val borrowedFrom = txn("500", loanId = 2).copy(id = 8)
    private val upiCredit = txn("220").copy(transactionType = TransactionType.CREDIT, merchantName = "UPI")

    @Test
    fun `a CREDIT payment linked to a borrowed loan repays it`() {
        coEvery { loanDao.getLoanById(2) } returns borrowed
        coEvery { transactionDao.getTransactionById(9) } returns upiCredit
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(borrowedFrom, upiCredit.copy(loanId = 2))
        val saved = slot<LoanEntity>()
        coEvery { loanDao.updateLoan(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9) }
        assertEquals(0, BigDecimal("280").compareTo(saved.captured.remainingAmount))
        assertEquals(LoanStatus.ACTIVE, saved.captured.status)
    }

    @Test
    fun `opening Loans corrects a remainder saved before CREDIT repayments counted`() {
        coEvery { loanDao.getActiveLoans() } returns flowOf(listOf(borrowed))
        coEvery { loanDao.getLoanById(2) } returns borrowed
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(borrowedFrom, upiCredit.copy(loanId = 2))
        val saved = slot<LoanEntity>()
        coEvery { loanDao.updateLoan(capture(saved)) } returns Unit
        runBlocking { repo.refreshActiveLoans() }
        assertEquals(0, BigDecimal("280").compareTo(saved.captured.remainingAmount))
    }

    @Test
    fun `a loan already right is not rewritten`() {
        coEvery { loanDao.getActiveLoans() } returns flowOf(listOf(borrowed))
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(borrowedFrom)
        runBlocking { repo.refreshActiveLoans() }
        coVerify(exactly = 0) { loanDao.updateLoan(any()) }
    }

    @Test
    fun `a note given with a repayment is kept on that transaction`() {
        coEvery { loanDao.getLoanById(2) } returns borrowed
        coEvery { transactionDao.getTransactionById(9) } returns upiCredit
        val saved = slot<TransactionEntity>()
        coEvery { transactionDao.updateTransaction(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9, note = " first part ") }
        assertEquals("first part", saved.captured.description)
    }

    @Test
    fun `a note never overwrites the transaction's own description`() {
        coEvery { loanDao.getLoanById(2) } returns borrowed
        coEvery { transactionDao.getTransactionById(9) } returns upiCredit.copy(description = "groceries")
        val saved = slot<TransactionEntity>()
        coEvery { transactionDao.updateTransaction(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9, note = "first part") }
        assertEquals("groceries · first part", saved.captured.description)
    }

    @Test
    fun `relinking a row already on the loan counts its own share as still owed`() {
        // ₹50 left after this row's ₹80; re-recording it may repay up to ₹130.
        assertEquals(BigDecimal("130"), savedContribution("50", txn("200", loanId = 2, contribution = "80"), null))
    }
}
