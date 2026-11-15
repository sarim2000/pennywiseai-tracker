package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.ledger.countedAmount
import com.pennywiseai.tracker.domain.loan.LoanOverflow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A repayment beyond what's owed can turn the loan around instead of counting
 * as ordinary money: a ₹1,000 advance overspent by a ₹1,500 bill leaves ₹500
 * owed to you, and neither row counts in your totals.
 */
class LoanOverflowTest {
    private val loanDao = mockk<LoanDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val repo = LoanRepository(loanDao, transactionDao, mockk(relaxed = true))

    private val advance = LoanEntity(
        id = 2, personName = "Office", direction = LoanDirection.BORROWED,
        originalAmount = BigDecimal("1000"), remainingAmount = BigDecimal("1000"), currency = "INR"
    )

    private fun txn(id: Long, amount: String, type: TransactionType, loanId: Long? = null) = TransactionEntity(
        id = id, amount = BigDecimal(amount), merchantName = "Shop", category = "Others",
        transactionType = type, dateTime = LocalDateTime.of(2026, 11, 1, 12, 0),
        transactionHash = "h$id", loanId = loanId
    )

    private val advanceIn = txn(8, "1000", TransactionType.INCOME, loanId = 2)
    private val bill = txn(9, "1500", TransactionType.EXPENSE)

    @Before
    fun setUp() {
        coEvery { loanDao.getLoanById(2) } returns advance
        coEvery { loanDao.getSharesForLoan(2) } returns emptyList()
        coEvery { loanDao.getActiveLoanByPersonAndDirection(any(), any(), any()) } returns null
        coEvery { transactionDao.getTransactionById(9) } returns bill
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(advanceIn)
    }

    @Test
    fun `the extra beyond what is owed is offered`() {
        val extra = runBlocking { repo.flippableOverflow(2, 9, null) }
        assertEquals(0, BigDecimal("500").compareTo(extra))
    }

    @Test
    fun `a payment within what is owed offers nothing`() {
        coEvery { transactionDao.getTransactionById(9) } returns bill.copy(amount = BigDecimal("800"))
        assertNull(runBlocking { repo.flippableOverflow(2, 9, null) })
    }

    @Test
    fun `a partial contribution is what is weighed, not the whole payment`() {
        // Only ₹900 of the ₹1,500 bill is for the loan: nothing beyond the ₹1,000 owed.
        assertNull(runBlocking { repo.flippableOverflow(2, 9, BigDecimal("900")) })
    }

    @Test
    fun `a loan holding split-bill shares can't turn around`() {
        coEvery { loanDao.getSharesForLoan(2) } returns listOf(LoanShareEntity(transactionId = 5, loanId = 2, amount = BigDecimal("100")))
        assertNull(runBlocking { repo.flippableOverflow(2, 9, null) })
    }

    @Test
    fun `a person with an open loan the other way can't get a second one`() {
        coEvery { loanDao.getActiveLoanByPersonAndDirection("Office", LoanDirection.LENT.name, "INR") } returns
            advance.copy(id = 3, direction = LoanDirection.LENT)
        assertNull(runBlocking { repo.flippableOverflow(2, 9, null) })
    }

    @Test
    fun `a settled loan can't turn around`() {
        coEvery { loanDao.getLoanById(2) } returns advance.copy(status = LoanStatus.SETTLED, remainingAmount = BigDecimal.ZERO)
        assertNull(runBlocking { repo.flippableOverflow(2, 9, null) })
    }

    @Test
    fun `turning the loan around leaves the extra owed to you`() {
        // What's owed is read before the bill links; the turn reads the rows after.
        coEvery { loanDao.getLinkedTransactions(2) } returnsMany listOf(
            listOf(advanceIn),
            listOf(advanceIn, bill.copy(loanId = 2))
        )
        val saved = slot<LoanEntity>()
        coEvery { loanDao.updateLoan(capture(saved)) } returns Unit

        runBlocking { repo.recordRepayment(2, 9, overflow = LoanOverflow.FLIP_DEBT) }

        coVerify { loanDao.linkTransaction(9, 2) }
        // The full bill links: no contribution override is written.
        coVerify(exactly = 0) { transactionDao.updateTransaction(any()) }
        with(saved.captured) {
            assertEquals(LoanDirection.LENT, direction)
            assertEquals(0, BigDecimal("1500").compareTo(originalAmount))
            assertEquals(0, BigDecimal("500").compareTo(remainingAmount))
            assertEquals(LoanStatus.ACTIVE, status)
            assertNull(settledAt)
        }
        // Neither the advance nor the bill counts in any total.
        assertEquals(0, BigDecimal.ZERO.compareTo(bill.copy(loanId = 2).countedAmount()))
        assertEquals(0, BigDecimal.ZERO.compareTo(advanceIn.countedAmount()))
    }

    @Test
    fun `earlier repayments become principal when the loan turns around`() {
        // ₹1,000 borrowed, ₹300 paid back, then ₹1,000 more: ₹300 is now owed to you.
        val earlier = txn(7, "300", TransactionType.EXPENSE, loanId = 2)
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(advanceIn, earlier)
        coEvery { transactionDao.getTransactionById(9) } returns bill.copy(amount = BigDecimal("1000"))
        assertEquals(0, BigDecimal("300").compareTo(runBlocking { repo.flippableOverflow(2, 9, null) }))

        coEvery { loanDao.getLinkedTransactions(2) } returnsMany listOf(
            listOf(advanceIn, earlier),
            listOf(advanceIn, earlier, bill.copy(amount = BigDecimal("1000"), loanId = 2))
        )
        val saved = slot<LoanEntity>()
        coEvery { loanDao.updateLoan(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9, overflow = LoanOverflow.FLIP_DEBT) }

        assertEquals(LoanDirection.LENT, saved.captured.direction)
        assertEquals(0, BigDecimal("1300").compareTo(saved.captured.originalAmount))
        assertEquals(0, BigDecimal("300").compareTo(saved.captured.remainingAmount))
    }

    @Test
    fun `counting the extra as your own keeps the cap`() {
        val saved = slot<TransactionEntity>()
        coEvery { transactionDao.updateTransaction(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9, overflow = LoanOverflow.COUNT_AS_OWN) }
        assertEquals(0, BigDecimal("1000").compareTo(saved.captured.loanContribution))
    }

    @Test
    fun `a loan that can't turn around keeps the cap even when asked to`() {
        coEvery { loanDao.getSharesForLoan(2) } returns listOf(LoanShareEntity(transactionId = 5, loanId = 2, amount = BigDecimal("100")))
        val saved = slot<TransactionEntity>()
        coEvery { transactionDao.updateTransaction(capture(saved)) } returns Unit
        runBlocking { repo.recordRepayment(2, 9, overflow = LoanOverflow.FLIP_DEBT) }
        assertEquals(0, BigDecimal("1000").compareTo(saved.captured.loanContribution))
    }

    // After the turn: Office owes you ₹500 on a lent loan holding the advance and the bill.
    private val turned = advance.copy(
        direction = LoanDirection.LENT, originalAmount = BigDecimal("1500"), remainingAmount = BigDecimal("500")
    )

    @Test
    fun `unmarking the bill that turned the loan offers the debt before it`() {
        coEvery { loanDao.getLoanById(2) } returns turned
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(advanceIn, bill.copy(loanId = 2))
        assertEquals(0, BigDecimal("1000").compareTo(runBlocking { repo.unlinkReversal(9, 2) }))
    }

    @Test
    fun `keeping the reversal turns the loan back`() {
        coEvery { loanDao.getLoanById(2) } returns turned
        // Before the unlink both rows are linked; after it only the advance is.
        coEvery { loanDao.getLinkedTransactions(2) } returnsMany listOf(
            listOf(advanceIn, bill.copy(loanId = 2)),
            listOf(advanceIn)
        )
        val saved = slot<LoanEntity>()
        coEvery { loanDao.updateLoan(capture(saved)) } returns Unit

        runBlocking { repo.unlinkTransaction(9, 2, keepReversal = true) }

        coVerify { loanDao.unlinkTransaction(9) }
        coVerify(exactly = 0) { loanDao.unlinkAllTransactions(2) }
        with(saved.captured) {
            assertEquals(LoanDirection.BORROWED, direction)
            assertEquals(0, BigDecimal("1000").compareTo(originalAmount))
            assertEquals(0, BigDecimal("1000").compareTo(remainingAmount))
            assertEquals(LoanStatus.ACTIVE, status)
        }
    }

    @Test
    fun `just unmarking stays as before and never turns the loan`() {
        // ₹300 lent is left after the bill comes off, against ₹1,000 back: it would run
        // the other way, but without keepReversal the existing rule applies unchanged.
        val earlier = txn(7, "300", TransactionType.EXPENSE, loanId = 2)
        coEvery { loanDao.getLoanById(2) } returns turned
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(advanceIn, earlier)
        runBlocking { repo.unlinkTransaction(9, 2) }
        coVerify { loanDao.unlinkTransaction(9) }
        coVerify(exactly = 0) { loanDao.updateLoan(match { it.direction == LoanDirection.BORROWED }) }
    }

    @Test
    fun `unmarking with nothing running the other way offers nothing`() {
        // A plain lent loan, repaid in part: removing the repayment leaves only principal.
        val lent = advance.copy(direction = LoanDirection.LENT)
        val lentOut = txn(8, "1000", TransactionType.EXPENSE, loanId = 2)
        val back = txn(9, "400", TransactionType.INCOME, loanId = 2)
        coEvery { loanDao.getLoanById(2) } returns lent
        coEvery { loanDao.getLinkedTransactions(2) } returns listOf(lentOut, back)
        assertNull(runBlocking { repo.unlinkReversal(9, 2) })
    }

    @Test
    fun `a manual payment beyond what is owed can turn the loan around`() {
        val inserted = slot<TransactionEntity>()
        coEvery { transactionDao.insertTransaction(capture(inserted)) } returns 11L
        val saved = slot<LoanEntity>()
        coEvery { loanDao.updateLoan(capture(saved)) } returns Unit
        coEvery { loanDao.getLinkedTransactions(2) } answers {
            if (inserted.isCaptured) listOf(advanceIn, inserted.captured.copy(id = 11)) else listOf(advanceIn)
        }

        runBlocking { repo.recordManualRepayment(2, BigDecimal("1500"), "Office", "INR", LoanOverflow.FLIP_DEBT) }

        assertNull(inserted.captured.loanContribution)
        assertEquals(LoanDirection.LENT, saved.captured.direction)
        assertEquals(0, BigDecimal("500").compareTo(saved.captured.remainingAmount))
    }
}
