package com.pennywiseai.tracker.worker

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** A hidden manual account keeps its threshold, so the check has to skip it itself. */
class MoneyAlertCheckerTest {

    private val account = AccountBalanceEntity(
        bankName = BANK,
        accountLast4 = ACCT,
        balance = BigDecimal("50"),
        timestamp = LocalDateTime.of(2026, 1, 15, 12, 0),
        lowBalanceThreshold = BigDecimal("100")
    )
    private val balances = mockk<AccountBalanceRepository>()
    private val preferences = mockk<UserPreferencesRepository>()
    private val ignoredStore = mockk<IgnoredAccountsStore>()
    private val checker = MoneyAlertChecker(mockk(), mockk(), balances, preferences, ignoredStore)

    @Before
    fun setUp() {
        mockkObject(MoneyAlertNotifications)
        justRun { MoneyAlertNotifications.showLowBalance(any(), any()) }
        coEvery { balances.getAllLatestBalancesOnce() } returns listOf(account)
        coEvery { balances.isManualAccount(BANK, ACCT) } returns true
        coEvery { preferences.claimLowBalanceAlert(BANK, ACCT, true) } returns true
    }

    @After
    fun tearDown() = unmockkObject(MoneyAlertNotifications)

    @Test
    fun `a low manual account alerts`() = runBlocking {
        every { ignoredStore.keys() } returns emptySet()

        checker.checkManualBalances()

        verify(exactly = 1) { MoneyAlertNotifications.showLowBalance(any(), account) }
    }

    @Test
    fun `an ignored account neither alerts nor spends its one alert`() = runBlocking {
        every { ignoredStore.keys() } returns setOf(IgnoredAccountsStore.keyFor(BANK, ACCT))

        checker.checkManualBalances()

        verify(exactly = 0) { MoneyAlertNotifications.showLowBalance(any(), any()) }
        coVerify(exactly = 0) { preferences.claimLowBalanceAlert(any(), any(), any()) }
    }

    private companion object {
        const val BANK = "Cash"
        const val ACCT = "0001"
    }
}
