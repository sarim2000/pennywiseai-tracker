package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class RebuildRestoreTest {

    private val snapshot = AccountBalanceEntity(
        bankName = "HDFC Bank",
        accountLast4 = "1234",
        balance = BigDecimal("100.00"),
        timestamp = LocalDateTime.of(2026, 10, 1, 9, 0),
        accountType = "CREDIT",
        isCreditCard = true,
    )
    private val accounts = mockk<AccountBalanceRepository>(relaxed = true).also {
        coEvery { it.getAllLatestBalancesOnce() } returns emptyList()
    }

    @Test
    fun `a force resync whose read breaks off still keeps the accounts' fields`() = runTest {
        val thrown = runCatching {
            restoringAccountsAfterRebuild(accounts, listOf(snapshot), listOf(snapshot), rebuilt = true) {
                throw IllegalStateException("SMS read broke off")
            }
        }.exceptionOrNull()

        assertTrue("the scan still fails", thrown is IllegalStateException)
        coVerify { accounts.restoreAccountType(snapshot) }
        coVerify { accounts.keepAccountFields(snapshot) }
        coVerify { accounts.getAllLatestBalancesOnce() }
    }

    @Test
    fun `a failed rebuild still re-derives the Cash wallets`() = runTest {
        val wallet = AccountBalanceEntity(
            bankName = "Cash",
            accountLast4 = "0000",
            balance = BigDecimal("50.00"),
            timestamp = LocalDateTime.of(2026, 10, 1, 9, 0),
            accountType = "CASH",
        )
        coEvery { accounts.getAllLatestBalancesOnce() } returns listOf(snapshot, wallet)

        val thrown = runCatching {
            restoringAccountsAfterRebuild(accounts, emptyList(), emptyList(), rebuilt = true) {
                throw IllegalStateException("SMS read broke off")
            }
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        coVerify(exactly = 1) { accounts.recomputeManualBalance(any(), any()) }
        coVerify { accounts.recomputeManualBalance("Cash", "0000") }
    }

    @Test
    fun `one repair that throws doesn't skip the others, and the scan's error still throws`() = runTest {
        val other = snapshot.copy(accountLast4 = "5678")
        coEvery { accounts.keepAccountFields(snapshot) } throws IllegalStateException("bad row")
        val readError = IllegalStateException("SMS read broke off")

        val thrown = runCatching {
            restoringAccountsAfterRebuild(accounts, listOf(snapshot), listOf(snapshot, other), rebuilt = true) {
                throw readError
            }
        }.exceptionOrNull()

        assertSame(readError, thrown)
        assertEquals("bad row", thrown!!.suppressed.single().message)
        coVerify { accounts.keepAccountFields(other) }
        coVerify { accounts.getAllLatestBalancesOnce() }
    }

    @Test
    fun `a scan that returned throws a repair that failed, after the rest ran`() = runTest {
        coEvery { accounts.restoreAccountType(snapshot) } throws IllegalStateException("bad row")

        val thrown = runCatching {
            restoringAccountsAfterRebuild(accounts, listOf(snapshot), listOf(snapshot), rebuilt = true) { 42 }
        }.exceptionOrNull()

        assertEquals("bad row", thrown?.message)
        coVerify { accounts.keepAccountFields(snapshot) }
    }

    @Test
    fun `a scan that isn't a rebuild restores nothing`() = runTest {
        restoringAccountsAfterRebuild(accounts, emptyList(), emptyList(), rebuilt = false) {}

        coVerify(exactly = 0) { accounts.getAllLatestBalancesOnce() }
    }
}
