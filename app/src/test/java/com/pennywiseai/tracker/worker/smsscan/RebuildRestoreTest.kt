package com.pennywiseai.tracker.worker.smsscan

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
    fun `a scan that isn't a rebuild restores nothing`() = runTest {
        restoringAccountsAfterRebuild(accounts, emptyList(), emptyList(), rebuilt = false) {}

        coVerify(exactly = 0) { accounts.getAllLatestBalancesOnce() }
    }
}
