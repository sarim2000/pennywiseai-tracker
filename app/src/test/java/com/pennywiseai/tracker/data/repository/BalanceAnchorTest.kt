package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.domain.spending.BalanceSource
import com.pennywiseai.tracker.domain.spending.NetWorth
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
import java.time.LocalDate
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
    fun `a statement on a manual account isn't claimed as the statement's figure`() = runBlocking {
        coEvery { balanceDao.getOpeningRow(BANK, ACCT) } returns row("OPENING")
        coEvery { balanceDao.getManualCurrentRow(BANK, ACCT) } returns row("MANUAL")

        // The import passes today's balance shifted by the statement's difference.
        repo.setCurrentBalance(BANK, ACCT, BigDecimal("950"), statementEnd, AccountBalanceRepository.ANCHOR_STATEMENT)

        val at = slot<LocalDateTime>()
        coVerify { balanceDao.setAnchor(BANK, ACCT, BigDecimal("950"), capture(at), "MANUAL") }
        coVerify(exactly = 0) { balanceDao.setAnchor(any(), any(), any(), any(), "STATEMENT") }
        assertTrue(at.captured.isAfter(statementEnd))
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

    @Test
    fun `balances entered after a currency change never take the trend back over it`() = runBlocking {
        // The account's anchor as the database holds it, rewritten by every setAnchor.
        var anchor = Triple<BigDecimal?, LocalDateTime?, String?>(null, null, null)
        coEvery { balanceDao.setAnchor(BANK, ACCT, any(), any(), any()) } answers {
            anchor = Triple(thirdArg(), arg(3), arg(4))
            1
        }
        coEvery { balanceDao.getLatestBalance(BANK, ACCT) } answers { row("MANUAL").copy(anchorSource = anchor.third) }
        coEvery { balanceDao.getOpeningRow(BANK, ACCT) } returns row("OPENING")
        coEvery { balanceDao.getManualCurrentRow(BANK, ACCT) } returns row("MANUAL")
        val changed = LocalDateTime.now().minusDays(10)

        // USD cash with no transactions moved to EUR, then its EUR balance edited, then
        // "Update balance": neither later anchor may drop the change's mark.
        repo.setAnchor(BANK, ACCT, BigDecimal("100"), changed, AccountBalanceRepository.ANCHOR_CURRENCY_CHANGE)
        repo.setAnchor(BANK, ACCT, BigDecimal("120"), changed.plusDays(5))
        assertEquals(AccountBalanceRepository.ANCHOR_CURRENCY_CHANGE, anchor.third)
        repo.setCurrentBalance(BANK, ACCT, BigDecimal("150"))
        assertEquals(AccountBalanceRepository.ANCHOR_CURRENCY_CHANGE, anchor.third)

        val account = row("MANUAL").copy(
            balance = BigDecimal("150"), currency = "EUR",
            anchorAmount = anchor.first, anchorAt = anchor.second, anchorSource = anchor.third,
        )
        val history = NetWorth.derivedHistory(account, emptyList(), firstRow = changed.minusDays(30))
        val trend = NetWorth.trend(emptyList(), mapOf((BANK to ACCT) to history), changed.toLocalDate().minusDays(5), LocalDate.now())
        assertTrue(trend.getValue("EUR").all { !it.date.isBefore(changed.toLocalDate()) })
        // Still a balance the user entered.
        assertEquals(
            BalanceSource.Entered(BigDecimal("150"), anchor.second!!, fromStatement = false),
            NetWorth.source(account, null, unanchored = false),
        )
    }

    @Test
    fun `only an entered balance keeps a currency change's mark`() {
        val change = AccountBalanceRepository.ANCHOR_CURRENCY_CHANGE
        assertEquals(change, AccountBalanceRepository.anchorSourceAfter(change, AccountBalanceRepository.ANCHOR_ENTERED))
        assertEquals("STATEMENT", AccountBalanceRepository.anchorSourceAfter(change, AccountBalanceRepository.ANCHOR_STATEMENT))
        assertEquals("MANUAL", AccountBalanceRepository.anchorSourceAfter("MANUAL", AccountBalanceRepository.ANCHOR_ENTERED))
        assertEquals(change, AccountBalanceRepository.anchorSourceAfter(null, change))
    }

    private companion object {
        const val BANK = "Test Bank"
        const val ACCT = "1234"
    }
}
