package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.domain.loan.LoanOverflow
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A repayment reports whether it paid the loan off, decided under the repayment lock. */
class LoanPaidOffRepositoryTest {
    private val loanDao = mockk<LoanDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val repo = LoanRepository(loanDao, transactionDao, database)

    /** ₹130 lent: the loan and its rows as stored. */
    private var loan = LoanEntity(
        id = 2, personName = "Pal", direction = LoanDirection.LENT,
        originalAmount = BigDecimal("130"), remainingAmount = BigDecimal("130"), currency = "INR"
    )
    private val rows = mutableMapOf<Long, TransactionEntity>()

    private fun row(id: Long, amount: String, type: TransactionType, loanId: Long? = null) = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Pal", category = "Others",
        transactionType = type, dateTime = LocalDateTime.of(2026, 11, 1, 12, 0),
        transactionHash = "h$id", loanId = loanId
    ).also { rows[id] = it }

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }
        row(1, "130", TransactionType.EXPENSE, loanId = 2)
        coEvery { loanDao.getLoanById(2) } answers { loan }
        coEvery { loanDao.updateLoan(any()) } answers { loan = firstArg() }
        coEvery { loanDao.getLinkedTransactions(2) } answers { rows.values.filter { it.loanId == 2L } }
        coEvery { loanDao.linkTransaction(any(), 2) } answers { rows[firstArg()] = rows.getValue(firstArg()).copy(loanId = 2) }
        coEvery { transactionDao.getTransactionById(any()) } answers { rows[firstArg()] }
        coEvery { transactionDao.updateTransaction(any()) } answers { firstArg<TransactionEntity>().let { rows[it.id] = it } }
        coEvery { transactionDao.insertTransaction(any()) } answers {
            val id = rows.keys.max() + 1
            rows[id] = firstArg<TransactionEntity>().copy(id = id)
            id
        }
        coEvery { loanDao.getSharesForLoan(2) } returns emptyList()
        coEvery { loanDao.getActiveLoanByPersonAndDirection(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    private fun repay(id: Long, amount: String, overflow: LoanOverflow = LoanOverflow.COUNT_AS_OWN): Boolean {
        if (id !in rows) row(id, amount, TransactionType.INCOME)
        return runBlocking { repo.recordRepayment(2, id, overflow = overflow) }
    }

    @Test
    fun `a repayment that clears what is owed paid it off`() {
        assertTrue(repay(9, "130"))
        assertEquals(LoanStatus.SETTLED, loan.status)
    }

    @Test
    fun `a partial repayment did not`() {
        assertFalse(repay(9, "50"))
        assertEquals(LoanStatus.ACTIVE, loan.status)
    }

    @Test
    fun `a partial repayment is judged by what it wrote, not by a later edit`() {
        // An edit that settles the loan lands right after the repayment's own write.
        coEvery { loanDao.updateLoan(any()) } answers {
            loan = firstArg<LoanEntity>().copy(remainingAmount = BigDecimal.ZERO, status = LoanStatus.SETTLED)
        }
        assertFalse(repay(9, "50"))
        loan = loan.copy(remainingAmount = BigDecimal("80"), status = LoanStatus.ACTIVE)
        assertFalse(runBlocking { repo.recordManualRepayment(2, BigDecimal("30"), "Pal", "INR") }!!.paidOff)
    }

    @Test
    fun `an overpayment that turned the loan around did not`() {
        assertFalse(repay(9, "200", LoanOverflow.FLIP_DEBT))
        assertEquals(LoanDirection.BORROWED, loan.direction)
        assertEquals(LoanStatus.ACTIVE, loan.status)
    }

    @Test
    fun `a second link of the payment that settled it did not`() {
        assertTrue(repay(9, "130"))
        assertFalse(repay(9, "130"))
        // Nor another payment on the settled loan.
        assertFalse(repay(10, "20"))
    }

    @Test
    fun `a typed-in repayment says whether it paid the loan off`() {
        val partial = runBlocking { repo.recordManualRepayment(2, BigDecimal("30"), "Pal", "INR") }
        assertFalse(partial!!.paidOff)
        val rest = runBlocking { repo.recordManualRepayment(2, BigDecimal("100"), "Pal", "INR") }
        assertTrue(rest!!.paidOff)
        assertEquals(LoanStatus.SETTLED, loan.status)
        val again = runBlocking { repo.recordManualRepayment(2, BigDecimal("10"), "Pal", "INR") }
        assertFalse(again!!.paidOff)
    }

    @Test
    fun `a typed-in overpayment that turned the loan around did not`() {
        val turned = runBlocking { repo.recordManualRepayment(2, BigDecimal("200"), "Pal", "INR", LoanOverflow.FLIP_DEBT) }
        assertFalse(turned!!.paidOff)
        assertEquals(LoanDirection.BORROWED, loan.direction)
    }

    @Test
    fun `a repayment on a loan that's gone has nothing to say`() {
        coEvery { loanDao.getLoanById(2) } returns null
        assertEquals(null, runBlocking { repo.recordManualRepayment(2, BigDecimal("30"), "Pal", "INR") })
    }
}
