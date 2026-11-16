package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.LoanDirection
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.LoanRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        every { balances.getUnanchoredAccounts() } returns flowOf(emptySet())
        every { balances.getBalanceEvidence() } returns flowOf(emptyMap())
        every { balances.getTrendRows(any()) } returns flowOf(
            listOf(account("1111", "1000"), account("2222", "500"), account("3333", "300", profileId = WORK))
        )
        coEvery { balances.isManualAccount(any(), any()) } returns false
        coEvery { balances.countTransactionsSince(any(), any(), any()) } returns 0

        val loans = mockk<LoanRepository>()
        every { loans.getActiveLoans() } returns flowOf(
            listOf(
                LoanEntity(
                    personName = "Friend", direction = LoanDirection.LENT, originalAmount = BigDecimal("200"),
                    remainingAmount = BigDecimal("200"), currency = "INR",
                )
            )
        )
        val ignored = mockk<IgnoredAccountsStore>()
        every { ignored.keysFlow } returns flowOf(setOf(IgnoredAccountsStore.keyFor(BANK, "2222")))
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.selectedProfileId } returns flowOf(profileId)
        return NetWorthLoader(balances, loans, ignored, preferences)
    }

    @Test
    fun `all profiles leave out ignored accounts and count IOUs beside them`() = runBlocking {
        val snapshot = loader(profileId = null).observe().first()

        assertEquals(listOf("1111", "3333"), snapshot.accounts.map { it.account.accountLast4 })
        assertEquals(BigDecimal("1500"), snapshot.totals.single().net)
        assertEquals(BigDecimal("200"), snapshot.peopleOwed.single().owedToYou)
    }

    @Test
    fun `a selected profile shows only its accounts, and no IOUs`() = runBlocking {
        val snapshot = loader(profileId = PERSONAL).observe().first()

        assertEquals(listOf("1111"), snapshot.accounts.map { it.account.accountLast4 })
        assertEquals(BigDecimal("1200"), snapshot.totals.single().net)
        assertTrue(snapshot.peopleOwed.isEmpty())
    }

    @Test
    fun `the trend covers the same accounts`() = runBlocking {
        val day = at.toLocalDate()
        val trend = loader(profileId = PERSONAL).observeTrend(day, day).first()

        assertEquals(listOf(BigDecimal("1000")), trend.getValue("INR").map { it.net })
    }

    private companion object {
        const val BANK = "Test Bank"
        const val PERSONAL = 1L
        const val WORK = 2L
    }
}
