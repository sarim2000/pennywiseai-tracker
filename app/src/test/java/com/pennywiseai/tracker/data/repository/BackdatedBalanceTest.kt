package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A transaction dated at or before the bank's latest reported balance is already
 * in that figure, so on an SMS account it must not move the balance: not when
 * added, edited or deleted (#1022). A manual account has no bank figure and
 * always moves.
 */
class BackdatedBalanceTest {
    private val now = LocalDateTime.now()
    private val rows = mutableListOf<AccountBalanceEntity>()
    private val transactions = mutableListOf<TransactionEntity>()

    private fun latest(acct: String) = rows.filter { it.accountLast4 == acct }.maxByOrNull { it.timestamp }

    private val balanceDao = mockk<AccountBalanceDao>(relaxed = true) {
        coEvery { getLatestBalance(any(), any()) } answers { latest(secondArg()) }
        coEvery { getOpeningRow(any(), any()) } answers {
            rows.firstOrNull { it.accountLast4 == secondArg<String>() && it.sourceType == AccountBalanceRepository.SOURCE_OPENING }
        }
        coEvery { getManualCurrentRow(any(), any()) } answers {
            rows.lastOrNull { it.accountLast4 == secondArg<String>() && it.sourceType == AccountBalanceRepository.SOURCE_MANUAL }
        }
        coEvery { countSmsSourcedBalances(any(), any()) } answers {
            rows.count { it.accountLast4 == secondArg<String>() && it.smsSource != null }
        }
        // The SQL's bank-figure test, reduced to the rows this fixture writes: an SMS row stated a balance.
        coEvery { hasBankBalanceAtOrAfter(any(), any(), any()) } answers {
            rows.any { it.accountLast4 == secondArg<String>() && it.smsSource != null && it.timestamp >= thirdArg<LocalDateTime>() }
        }
        coEvery { insertBalance(any()) } answers {
            rows += firstArg<AccountBalanceEntity>().copy(id = rows.size + 1L)
            rows.size.toLong()
        }
        coEvery { updateBalanceAndTimestampById(any(), any(), any()) } answers {
            val i = rows.indexOfFirst { it.id == firstArg<Long>() }
            rows[i] = rows[i].copy(balance = secondArg(), timestamp = thirdArg())
        }
    }
    private val transactionDao = mockk<TransactionDao>(relaxed = true) {
        coEvery { insertTransaction(any()) } answers {
            transactions += firstArg<TransactionEntity>().copy(id = transactions.size + 1L)
            transactions.size.toLong()
        }
        coEvery { getTransactionsForAccountStrict(any(), any()) } answers {
            transactions.filter { it.accountNumber == secondArg<String>() }
        }
        coEvery { getTransfersForAccount(any()) } returns emptyList()
    }
    private val database = mockk<PennyWiseDatabase>()
    private val repo = AccountBalanceRepository(balanceDao, transactionDao, database, mockk(relaxed = true))

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        // The bank said 10,000 in an SMS yesterday.
        rows += row(SMS, "10000", now.minusDays(1), source = "TRANSACTION", sms = "balance SMS")
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    private fun row(acct: String, balance: String, at: LocalDateTime, source: String, sms: String? = null) =
        AccountBalanceEntity(
            id = rows.size + 1L, bankName = BANK, accountLast4 = acct, balance = BigDecimal(balance),
            timestamp = at, sourceType = source, smsSource = sms
        )

    private fun spend(acct: String, at: LocalDateTime) = TransactionEntity(
        amount = BigDecimal("500"), merchantName = "Shop", category = "Others",
        transactionType = TransactionType.EXPENSE, dateTime = at, transactionHash = "h$acct$at",
        bankName = BANK, accountNumber = acct
    )

    private suspend fun add(txn: TransactionEntity): TransactionEntity =
        txn.copy(id = repo.insertTransactionWithBalance(txn, BANK, txn.accountNumber!!))

    private fun assertBalance(acct: String, expected: String) =
        assertEquals(0, BigDecimal(expected).compareTo(latest(acct)!!.balance))

    @Test
    fun `a spend dated before the bank's figure leaves an SMS account alone`() = runBlocking {
        add(spend(SMS, now.minusDays(2)))
        assertBalance(SMS, "10000")
        assertEquals(1, rows.size)
    }

    @Test
    fun `deleting that back-dated spend leaves it alone too`() = runBlocking {
        val txn = add(spend(SMS, now.minusDays(2)))
        repo.applyDeleteBalanceShift(txn)
        assertBalance(SMS, "10000")
        assertEquals(1, rows.size)
    }

    @Test
    fun `a spend dated after the bank's figure moves it, and its delete moves it back`() = runBlocking {
        val txn = add(spend(SMS, now))
        assertBalance(SMS, "9500")
        repo.applyDeleteBalanceShift(txn)
        assertBalance(SMS, "10000")
    }

    @Test
    fun `moving a back-dated spend's date past the bank's figure starts counting it`() = runBlocking {
        val txn = add(spend(SMS, now.minusDays(2)))
        val moved = txn.copy(dateTime = now)
        repo.reflectTransactionEdit(txn, moved)
        assertBalance(SMS, "9500")
        repo.applyDeleteBalanceShift(moved)
        assertBalance(SMS, "10000")
    }

    @Test
    fun `a back-dated spend on a manual account still moves it`() = runBlocking {
        rows += row(CASH, "2000", now.minusDays(30), source = AccountBalanceRepository.SOURCE_OPENING)
        rows += row(CASH, "2000", now.minusDays(1), source = AccountBalanceRepository.SOURCE_MANUAL)
        add(spend(CASH, now.minusDays(2)))
        assertBalance(CASH, "1500")
    }

    private companion object {
        const val BANK = "Test Bank"
        const val SMS = "1234"
        const val CASH = "0000"
    }
}
