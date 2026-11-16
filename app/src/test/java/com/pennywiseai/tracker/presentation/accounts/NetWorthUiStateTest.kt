package com.pennywiseai.tracker.presentation.accounts

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.domain.spending.NetWorth
import com.pennywiseai.tracker.domain.spending.NetWorthAccount
import com.pennywiseai.tracker.domain.spending.NetWorthPoint
import com.pennywiseai.tracker.domain.spending.NetWorthSnapshot
import com.pennywiseai.tracker.domain.spending.PeopleOwed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class NetWorthUiStateTest {
    private fun line(last4: String, currency: String) = NetWorthAccount(
        AccountBalanceEntity(
            bankName = "Test Bank", accountLast4 = last4, balance = BigDecimal("100"),
            timestamp = LocalDateTime.of(2026, 11, 1, 9, 0), currency = currency,
        ),
        source = null, transactionsSince = 0, daysSinceUpdate = 0,
    )

    private val accounts = listOf(line("1111", "INR"), line("2222", "INR"), line("3333", "USD"))
    private val snapshot = NetWorthSnapshot(
        accounts = accounts,
        totals = NetWorth.totals(accounts),
        peopleOwed = listOf(PeopleOwed("USD", BigDecimal.ZERO, BigDecimal.ZERO), PeopleOwed("INR", BigDecimal("50"), BigDecimal.ZERO)),
    )
    private fun point(day: Int) = NetWorthPoint(LocalDate.of(2026, 11, day), BigDecimal.ONE)

    @Test
    fun `one currency at a time, the busiest first`() {
        val state = netWorthUiState(snapshot, null, chosen = null, NetWorthRange.QUARTER)
        assertEquals(listOf("INR", "USD"), state.currencies)
        assertEquals("INR", state.currency)
        assertEquals(2, state.accounts.size)
        assertEquals(BigDecimal("50"), state.peopleOwed?.owedToYou)
        assertNull(state.trend)
    }

    @Test
    fun `a chosen currency sticks while it has accounts`() {
        val state = netWorthUiState(snapshot, emptyMap(), chosen = "USD", NetWorthRange.YEAR)
        assertEquals("USD", state.currency)
        assertEquals(listOf("3333"), state.accounts.map { it.account.accountLast4 })
        // Nothing outstanding either way is no line at all.
        assertNull(state.peopleOwed)
        assertEquals("INR", netWorthUiState(snapshot, null, chosen = "EUR", NetWorthRange.YEAR).currency)
    }

    @Test
    fun `a single day of history is no line`() {
        val one = netWorthUiState(snapshot, mapOf("INR" to listOf(point(1))), null, NetWorthRange.MONTH)
        assertTrue(one.trend!!.isEmpty())
        val two = netWorthUiState(snapshot, mapOf("INR" to listOf(point(1), point(2))), null, NetWorthRange.MONTH)
        assertEquals(2, two.trend!!.size)
    }

    @Test
    fun `a year of days is thinned, still ending on today`() {
        val year = (0L..365L).map { NetWorthPoint(LocalDate.of(2025, 11, 16).plusDays(it), BigDecimal(it)) }
        val drawn = netWorthUiState(snapshot, mapOf("INR" to year), null, NetWorthRange.YEAR).trend!!
        assertTrue(drawn.size <= 50)
        assertEquals(year.last(), drawn.last())
        // Each point is a real day's figure, never an average of several.
        assertTrue(drawn.all { it in year })
    }
}
