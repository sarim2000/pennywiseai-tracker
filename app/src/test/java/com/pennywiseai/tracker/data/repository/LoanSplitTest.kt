package com.pennywiseai.tracker.data.repository

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.LoanDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.LoanShareEntity
import com.pennywiseai.tracker.data.database.entity.LoanStatus
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.loan.SplitShare
import com.pennywiseai.tracker.utils.countedAmount
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * #346: a split bill's shares land on each person's loan, and editing or
 * removing the split undoes exactly what it added. The DAOs are backed by
 * in-memory maps so the repository's real bookkeeping runs.
 */
class LoanSplitTest {
    private val loans = linkedMapOf<Long, LoanEntity>()
    private val shares = mutableListOf<LoanShareEntity>()
    private val txns = linkedMapOf<Long, TransactionEntity>()
    private var nextLoanId = 1L
    private var nextShareId = 1L

    private val loanDao = mockk<LoanDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val repo = LoanRepository(loanDao, transactionDao, database)

    private fun d(v: String) = BigDecimal(v)

    private fun txn(id: Long, amount: String, type: TransactionType = TransactionType.EXPENSE, loanId: Long? = null) =
        TransactionEntity(
            id = id, amount = d(amount), merchantName = "Dinner", category = "Food",
            transactionType = type, dateTime = LocalDateTime.of(2026, 10, 1, 20, 0),
            transactionHash = "h$id", currency = "INR", loanId = loanId
        ).also { txns[id] = it }

    private fun loanOf(name: String) = loans.values.single { it.personName == name }

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers { secondArg<suspend () -> Any?>().invoke() }

        coEvery { loanDao.getLoanById(any()) } answers { loans[firstArg()] }
        coEvery { loanDao.insertLoan(any()) } answers {
            val id = nextLoanId++
            loans[id] = firstArg<LoanEntity>().copy(id = id)
            id
        }
        coEvery { loanDao.updateLoan(any()) } answers { firstArg<LoanEntity>().let { loans[it.id] = it } }
        coEvery { loanDao.deleteLoan(any()) } answers { loans.remove(firstArg<LoanEntity>().id) }
        coEvery { loanDao.getActiveLoanByPersonAndDirection(any(), any(), any()) } answers {
            loans.values.firstOrNull {
                it.personName == firstArg() && it.direction.name == secondArg() && it.currency == thirdArg() && it.status == LoanStatus.ACTIVE
            }
        }
        coEvery { loanDao.insertShare(any()) } answers {
            val s = firstArg<LoanShareEntity>()
            if (shares.any { it.transactionId == s.transactionId && it.loanId == s.loanId }) -1L
            else (nextShareId++).also { shares += s.copy(id = it) }
        }
        coEvery { loanDao.updateShare(any()) } answers {
            val s = firstArg<LoanShareEntity>()
            shares.replaceAll { if (it.id == s.id) s else it }
        }
        coEvery { loanDao.deleteShare(any()) } answers { shares.removeAll { it.id == firstArg<LoanShareEntity>().id } }
        coEvery { loanDao.getSharesForTransaction(any()) } answers { shares.filter { it.transactionId == firstArg<Long>() } }
        coEvery { loanDao.getSharesForLoan(any()) } answers { shares.filter { it.loanId == firstArg<Long>() } }
        coEvery { loanDao.deleteSharesForLoan(any()) } answers { shares.removeAll { it.loanId == firstArg<Long>() } }
        coEvery { loanDao.getTotalRepaidByType(any(), any()) } answers {
            txns.values.filter { it.loanId == firstArg<Long>() && !it.isDeleted && it.transactionType.name == secondArg<String>() }
                .fold(BigDecimal.ZERO) { a, t -> a + (t.loanContribution ?: t.amount) }
        }
        coEvery { transactionDao.syncLentSharesTotal(any()) } answers {
            val id = firstArg<Long>()
            val amounts = shares.filter { it.transactionId == id }.map { it.amount }
            txns[id] = txns.getValue(id).copy(lentSharesTotal = amounts.takeIf { it.isNotEmpty() }?.fold(BigDecimal.ZERO, BigDecimal::add))
        }
        coEvery { loanDao.unlinkTransaction(any()) } answers {
            val id = firstArg<Long>()
            txns[id] = txns.getValue(id).copy(loanId = null, loanContribution = null)
        }
        coEvery { loanDao.unlinkAllTransactions(any()) } answers {
            val loanId = firstArg<Long>()
            txns.replaceAll { _, t -> if (t.loanId == loanId) t.copy(loanId = null, loanContribution = null) else t }
        }
        coEvery { transactionDao.getTransactionById(any()) } answers { txns[firstArg()] }
    }

    @After
    fun tearDown() = unmockkStatic("androidx.room.RoomDatabaseKt")

    @Test
    fun `each share becomes money owed on its own loan and the bill counts only your part`() = runBlocking {
        txn(1, "1000")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("333.33")), SplitShare("Sam", d("333.33"))))

        listOf("Alex", "Sam").forEach { name ->
            with(loanOf(name)) {
                assertEquals(LoanDirection.LENT, direction)
                assertEquals("INR", currency)
                assertEquals(d("333.33"), originalAmount)
                assertEquals(d("333.33"), remainingAmount)
            }
        }
        assertEquals(2, shares.size)
        assertEquals(d("666.66"), txns.getValue(1).lentSharesTotal)
        assertEquals(d("333.34"), txns.getValue(1).countedAmount())
    }

    @Test
    fun `a share joins the person's open loan`() = runBlocking {
        txn(1, "1000")
        val existing = repo.createLoan("Alex", LoanDirection.LENT, d("200"), "INR", null, sourceTransactionId = txn(2, "200").id)
        txns[2] = txns.getValue(2).copy(loanId = existing)

        repo.saveSplit(1, listOf(SplitShare("Alex", d("500"))))

        assertEquals(1, loans.size)
        assertEquals(d("700"), loanOf("Alex").originalAmount)
        assertEquals(d("700"), loanOf("Alex").remainingAmount)
    }

    @Test
    fun `editing a share moves its loan by the difference and keeps repayments`() = runBlocking {
        txn(1, "1000")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("500"))))
        val alex = loanOf("Alex").id
        // Alex paid back 100 (an incoming SMS, marked as repaid).
        txn(3, "100", TransactionType.INCOME, loanId = alex)
        loans[alex] = loans.getValue(alex).copy(remainingAmount = d("400"))

        repo.saveSplit(1, listOf(SplitShare("Alex", d("600"))))

        assertEquals(d("600"), loanOf("Alex").originalAmount)
        assertEquals(d("500"), loanOf("Alex").remainingAmount)
        assertEquals(alex, txns.getValue(3).loanId)
        assertEquals(d("600"), shares.single().amount)
        assertEquals(d("600"), txns.getValue(1).lentSharesTotal)
    }

    @Test
    fun `removing people and then the split undoes it cleanly`() = runBlocking {
        txn(1, "1000")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300")), SplitShare("Sam", d("300"))))

        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))
        assertEquals(listOf("Alex"), loans.values.map { it.personName })
        assertEquals(d("300"), txns.getValue(1).lentSharesTotal)

        repo.saveSplit(1, emptyList())
        assertTrue(loans.isEmpty())
        assertTrue(shares.isEmpty())
        assertNull(txns.getValue(1).lentSharesTotal)
        assertEquals(d("1000"), txns.getValue(1).countedAmount())
    }

    @Test
    fun `removing a share keeps a loan that has other money in it`() = runBlocking {
        txn(1, "1000")
        val existing = repo.createLoan("Alex", LoanDirection.LENT, d("200"), "INR", null, sourceTransactionId = txn(2, "200").id)
        txns[2] = txns.getValue(2).copy(loanId = existing)
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))

        repo.saveSplit(1, emptyList())

        assertEquals(d("200"), loanOf("Alex").originalAmount)
        assertEquals(d("200"), loanOf("Alex").remainingAmount)
        assertTrue(shares.isEmpty())
    }

    @Test
    fun `unlinking another transaction keeps the shares in the loan`() = runBlocking {
        txn(1, "1000")
        val existing = repo.createLoan("Alex", LoanDirection.LENT, d("200"), "INR", null, sourceTransactionId = txn(2, "200").id)
        txns[2] = txns.getValue(2).copy(loanId = existing)
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))

        repo.unlinkTransaction(2, existing)

        // Before #346 the recount saw no linked transactions and deleted the loan.
        assertEquals(d("300"), loanOf("Alex").originalAmount)
        assertEquals(1, shares.size)
    }

    @Test
    fun `a deleted bill's share still holds the loan up when another split is removed`() = runBlocking {
        txn(1, "1000")
        txn(2, "500")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))
        repo.saveSplit(2, listOf(SplitShare("Alex", d("200"))))
        txns[1] = txns.getValue(1).copy(isDeleted = true) // soft delete keeps the debt, as for loans

        repo.saveSplit(2, emptyList())

        assertEquals(d("300"), loanOf("Alex").originalAmount)
        assertEquals(1, shares.size)
    }

    @Test
    fun `debt kept without a bill survives removing the last split`() = runBlocking {
        // A restored loan: ₹500 owed, but only the ₹200 bill came back (the ₹300 one was deleted).
        txn(2, "500")
        repo.saveSplit(2, listOf(SplitShare("Alex", d("200"))))
        val alex = loanOf("Alex")
        loans[alex.id] = alex.copy(originalAmount = d("500"), remainingAmount = d("500"))

        repo.saveSplit(2, emptyList())

        assertEquals(d("300"), loanOf("Alex").originalAmount)
        assertEquals(d("300"), loanOf("Alex").remainingAmount)
    }

    private fun settle(name: String) {
        val loan = loanOf(name)
        loans[loan.id] = loan.copy(status = LoanStatus.SETTLED, remainingAmount = BigDecimal.ZERO, settledAt = LocalDateTime.now())
    }

    @Test
    fun `removing a share from a settled loan keeps it settled`() = runBlocking {
        // Alex owes 300 + 200, then Settle up (no repayment recorded).
        txn(1, "1000"); txn(2, "500")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))
        repo.saveSplit(2, listOf(SplitShare("Alex", d("200"))))
        settle("Alex")

        repo.saveSplit(1, emptyList())

        with(loanOf("Alex")) {
            assertEquals(LoanStatus.SETTLED, status)
            assertEquals(d("200"), originalAmount)
            assertEquals(BigDecimal.ZERO, remainingAmount)
        }
    }

    @Test
    fun `a share on a settled loan can't change amount`() = runBlocking {
        // Settle up or repaid in full: either way the share is closed until the loan is reopened.
        txn(1, "1000"); txn(2, "500")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))
        repo.saveSplit(2, listOf(SplitShare("Alex", d("200"))))
        settle("Alex")

        val error = runCatching { repo.saveSplit(1, listOf(SplitShare("Alex", d("301")))) }.exceptionOrNull()

        assertTrue(error is com.pennywiseai.tracker.domain.loan.SettledShareException)
        with(loanOf("Alex")) {
            assertEquals(LoanStatus.SETTLED, status)
            assertEquals(d("500"), originalAmount)
        }
        assertEquals(d("300"), shares.single { it.transactionId == 1L }.amount)
    }

    @Test
    fun `a split bill can't also be marked as a loan or a repayment`() = runBlocking {
        txn(1, "1000")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300"))))
        val alex = loanOf("Alex").id
        listOf<suspend () -> Unit>(
            { repo.createLoan("Kim", LoanDirection.LENT, d("100"), "INR", null, 1) },
            { repo.addToExistingLoan(alex, d("100"), 1) },
            { repo.recordRepayment(alex, 1) },
        ).forEach { call -> assertTrue(runCatching { call() }.exceptionOrNull() is IllegalStateException) }
        assertEquals(1, loans.size)
        assertNull(txns.getValue(1).loanId)
    }

    @Test
    fun `deleting a loan takes its share off the bill`() = runBlocking {
        txn(1, "1000")
        repo.saveSplit(1, listOf(SplitShare("Alex", d("300")), SplitShare("Sam", d("200"))))

        repo.deleteLoan(loanOf("Alex").id)

        assertEquals(d("200"), txns.getValue(1).lentSharesTotal)
        assertEquals(d("800"), txns.getValue(1).countedAmount())
    }

    @Test
    fun `only a valid split of an expense not marked as a loan saves`() = runBlocking {
        txn(1, "1000", TransactionType.INCOME)
        txn(2, "1000", loanId = 99)
        txn(3, "1000")
        listOf(
            1L to listOf(SplitShare("Alex", d("10"))),
            2L to listOf(SplitShare("Alex", d("10"))),
            3L to listOf(SplitShare("Alex", d("1000.01"))),
            3L to listOf(SplitShare("Alex", d("10")), SplitShare("Alex", d("10"))),
        ).forEach { (id, s) ->
            assertTrue(runCatching { repo.saveSplit(id, s) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(loans.isEmpty())
        assertTrue(shares.isEmpty())
    }
}
