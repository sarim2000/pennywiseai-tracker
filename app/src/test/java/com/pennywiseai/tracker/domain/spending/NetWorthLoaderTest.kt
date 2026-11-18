package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

/** Net worth covers the accounts in scope: not ignored ones, and only the selected profile's. */
class NetWorthLoaderTest {
    private val at = LocalDateTime.of(2026, 11, 1, 9, 0)

    private fun account(last4: String, balance: String, profileId: Long = PERSONAL, id: Long = 0) = AccountBalanceEntity(
        id = id, bankName = BANK, accountLast4 = last4, balance = BigDecimal(balance), timestamp = at,
        accountType = "SAVINGS", currency = "INR", profileId = profileId,
    )

    private fun loader(profileId: Long?): NetWorthLoader {
        val balances = mockk<AccountBalanceRepository>()
        every { balances.getAllLatestBalances() } returns flowOf(
            listOf(
                account("1111", "1000", id = 1),
                // The same account's newest timestamp read twice: counted once, the newer row kept.
                account("1111", "1200", id = 2),
                account("2222", "500"),
                account("3333", "300", profileId = WORK),
            )
        )
        val ignored = mockk<IgnoredAccountsStore>()
        every { ignored.keysFlow } returns flowOf(setOf(IgnoredAccountsStore.keyFor(BANK, "2222")))
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.selectedProfileId } returns flowOf(profileId)
        return NetWorthLoader(balances, ignored, preferences)
    }

    @Test
    fun `all profiles leave out ignored accounts`() = runBlocking {
        val snapshot = loader(profileId = null).observe().first()

        assertEquals(listOf("1111", "3333"), snapshot.accounts.map { it.accountLast4 }.sorted())
        assertEquals(BigDecimal("1500"), snapshot.totals.single().net)
    }

    @Test
    fun `a selected profile shows only its accounts`() = runBlocking {
        val snapshot = loader(profileId = PERSONAL).observe().first()

        assertEquals(listOf("1111"), snapshot.accounts.map { it.accountLast4 })
        assertEquals(BigDecimal("1200"), snapshot.totals.single().net)
    }

    @Test
    fun `an SMS account stored as INR counts in its bank's currency`() = runBlocking {
        val sms = AccountBalanceEntity(
            bankName = "BPCE", accountLast4 = "9999", balance = BigDecimal("40"), timestamp = at,
            currency = "INR", sourceType = "SMS_BALANCE", profileId = PERSONAL,
        )
        val balances = mockk<AccountBalanceRepository>()
        every { balances.getAllLatestBalances() } returns flowOf(listOf(account("1111", "1000"), sms))
        val ignored = mockk<IgnoredAccountsStore>()
        every { ignored.keysFlow } returns flowOf(emptySet())
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.selectedProfileId } returns flowOf(null)

        val totals = NetWorthLoader(balances, ignored, preferences).observe().first().totals

        assertEquals(mapOf("INR" to BigDecimal("1000"), "EUR" to BigDecimal("40")), totals.associate { it.currency to it.net })
    }

    private companion object {
        const val BANK = "Test Bank"
        const val PERSONAL = 1L
        const val WORK = 2L
    }
}
