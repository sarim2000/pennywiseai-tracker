package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.AccountBalanceDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.withTransaction
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
 * MoneyAlertWorker only sees the newest balance and doesn't run after an edit, so
 * it can't notice a manual account recovering. The balance write has to rearm the
 * alert, or the next dip stays silent.
 */
class ManualLowBalanceRearmTest {

    private val now = LocalDateTime.of(2026, 1, 15, 12, 0)
    private fun row(source: String, threshold: String?, balance: String = "400") = AccountBalanceEntity(
        bankName = BANK,
        accountLast4 = ACCT,
        balance = BigDecimal(balance),
        timestamp = now,
        sourceType = source,
        lowBalanceThreshold = threshold?.let(::BigDecimal)
    )

    private val balanceDao = mockk<AccountBalanceDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val repo = AccountBalanceRepository(balanceDao, transactionDao, database, preferences)

    @Before
    fun setUp() {
        mockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { transactionDao.getTransactionsForAccountStrict(BANK, ACCT) } returns emptyList()
        coEvery { transactionDao.getTransfersForAccount(ACCT) } returns emptyList()
        coEvery { balanceDao.getManualCurrentRow(BANK, ACCT) } returns row("MANUAL", "500")
    }

    @After
    fun tearDown() = unmockkStatic("com.pennywiseai.tracker.data.database.RoomCompatKt")

    private fun openingWithThreshold(threshold: String?) {
        coEvery { balanceDao.getOpeningRow(BANK, ACCT) } returns row("OPENING", threshold)
    }

    private fun verifyRearms(times: Int) = coVerify(exactly = times) {
        preferences.claimLowBalanceAlert(BANK, ACCT, false)
    }

    @Test
    fun `typing a balance back above the threshold rearms`() = runBlocking {
        openingWithThreshold("500")
        repo.setManualCurrentBalance(BANK, ACCT, BigDecimal("900"))
        verifyRearms(1)
    }

    @Test
    fun `a balance at or under the threshold stays told`() = runBlocking {
        openingWithThreshold("500")
        repo.setManualCurrentBalance(BANK, ACCT, BigDecimal("500"))
        repo.setManualCurrentBalance(BANK, ACCT, BigDecimal("400"))
        verifyRearms(0)
    }

    @Test
    fun `income rearms at once, so an expense right after it can alert`() = runBlocking {
        openingWithThreshold("500")
        val income = mockk<TransactionEntity> {
            every { transactionType } returns TransactionType.INCOME
            every { amount } returns BigDecimal("500")
            every { dateTime } returns now
        }
        coEvery { transactionDao.getTransactionsForAccountStrict(BANK, ACCT) } returns listOf(income)
        repo.recomputeManualBalance(BANK, ACCT)
        verifyRearms(1)
    }

    @Test
    fun `lowering the threshold under the balance rearms`() = runBlocking {
        coEvery { balanceDao.getLatestBalance(BANK, ACCT) } returns row("MANUAL", "500", balance = "400")
        repo.setLowBalanceThreshold(BANK, ACCT, BigDecimal("300"))
        verifyRearms(1)
    }

    @Test
    fun `raising the threshold while still under it stays told`() = runBlocking {
        coEvery { balanceDao.getLatestBalance(BANK, ACCT) } returns row("MANUAL", "500", balance = "400")
        repo.setLowBalanceThreshold(BANK, ACCT, BigDecimal("1000"))
        verifyRearms(0)
    }

    @Test
    fun `clearing the threshold rearms`() = runBlocking {
        coEvery { balanceDao.getLatestBalance(BANK, ACCT) } returns row("MANUAL", "500", balance = "400")
        repo.setLowBalanceThreshold(BANK, ACCT, null)
        verifyRearms(1)
    }

    @Test
    fun `renaming carries the alert over and survives a failed move`() = runBlocking {
        coEvery { balanceDao.updateAccountBankName(BANK, ACCT, "Wallet") } returns 3
        coEvery { preferences.moveLowBalanceAlert(BANK, ACCT, "Wallet") } throws IllegalStateException("disk full")

        assertEquals(3, repo.updateAccountBankName(BANK, ACCT, "Wallet"))

        coVerify(exactly = 1) { preferences.moveLowBalanceAlert(BANK, ACCT, "Wallet") }
    }

    private companion object {
        const val BANK = "Cash Wallet"
        const val ACCT = "0001"
    }
}
