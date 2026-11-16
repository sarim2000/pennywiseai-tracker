package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A balance the user gives is remembered as the account's anchor, with when and how. */
class BalanceAnchorTest {

    private val balanceDao = mockk<AccountBalanceDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val repo = AccountBalanceRepository(balanceDao, transactionDao, database, mockk(relaxed = true))

    private val statementEnd = LocalDateTime.of(2026, 10, 31, 23, 59)

    private fun row(source: String) = AccountBalanceEntity(
        bankName = BANK, accountLast4 = ACCT, balance = BigDecimal("400"),
        timestamp = LocalDateTime.of(2026, 10, 1, 9, 0), sourceType = source,
    )

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { transactionDao.getTransactionsForAccountStrict(BANK, ACCT) } returns emptyList()
        coEvery { transactionDao.getTransfersForAccount(ACCT) } returns emptyList()
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    @Test
    fun `an SMS account's update is anchored at the moment the figure was true`() = runBlocking {
        coEvery { balanceDao.getOpeningRow(BANK, ACCT) } returns null
        coEvery { balanceDao.getLatestBalance(BANK, ACCT) } returns row("TRANSACTION")

        repo.setCurrentBalance(BANK, ACCT, BigDecimal("1250"), statementEnd, AccountBalanceRepository.ANCHOR_STATEMENT)

        coVerify { balanceDao.setAnchor(BANK, ACCT, BigDecimal("1250"), statementEnd, "STATEMENT") }
    }

    @Test
    fun `a manual account's update is anchored now, as typed`() = runBlocking {
        coEvery { balanceDao.getOpeningRow(BANK, ACCT) } returns row("OPENING")
        coEvery { balanceDao.getManualCurrentRow(BANK, ACCT) } returns row("MANUAL")
        val before = LocalDateTime.now()

        repo.setCurrentBalance(BANK, ACCT, BigDecimal("900"))

        val at = slot<LocalDateTime>()
        coVerify { balanceDao.setAnchor(BANK, ACCT, BigDecimal("900"), capture(at), "MANUAL") }
        assertTrue(!at.captured.isBefore(before))
    }

    @Test
    fun `a new manual account starts anchored to its opening balance`() = runBlocking {
        val inserted = mutableListOf<AccountBalanceEntity>()
        coEvery { balanceDao.insertBalance(capture(inserted)) } returns 1L

        repo.seedManualAccount(row("MANUAL"), BigDecimal("300"))

        assertEquals(2, inserted.size)
        inserted.forEach {
            assertEquals(BigDecimal("300"), it.anchorAmount)
            assertEquals("MANUAL", it.anchorSource)
            assertTrue(it.anchorAt != null)
        }
    }

    private companion object {
        const val BANK = "Test Bank"
        const val ACCT = "1234"
    }
}
