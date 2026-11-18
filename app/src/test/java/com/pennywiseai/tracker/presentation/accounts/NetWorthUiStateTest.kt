package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.spending.NetWorth
import com.pennywiseai.tracker.domain.spending.NetWorthSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class NetWorthUiStateTest {
    private fun account(last4: String, currency: String) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = BigDecimal("100"),
        timestamp = LocalDateTime.of(2026, 11, 1, 9, 0), currency = currency,
    )

    private val accounts = listOf(account("1111", "INR"), account("2222", "INR"), account("3333", "USD"))
    private val snapshot = NetWorthSnapshot(accounts = accounts, totals = NetWorth.totals(accounts))

    @Test
    fun `one currency at a time, the busiest first`() {
        val state = netWorthUiState(snapshot, chosen = null)
        assertEquals(listOf("INR", "USD"), state.currencies)
        assertEquals("INR", state.currency)
        assertEquals(2, state.accounts.size)
        assertEquals(BigDecimal("200"), state.total?.net)
    }

    @Test
    fun `a chosen currency sticks while it has accounts`() {
        val state = netWorthUiState(snapshot, chosen = "USD")
        assertEquals("USD", state.currency)
        assertEquals(listOf("3333"), state.accounts.map { it.accountLast4 })
        assertEquals("INR", netWorthUiState(snapshot, chosen = "EUR").currency)
    }

    @Test
    fun `no accounts is the empty page`() {
        assertTrue(netWorthUiState(NetWorthSnapshot(emptyList(), emptyList()), chosen = null).isEmpty)
    }
}
