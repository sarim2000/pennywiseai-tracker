package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class NetWorthTest {
    private fun d(v: String) = BigDecimal(v)

    private fun account(
        last4: String,
        balance: String,
        type: String? = "SAVINGS",
        currency: String = "INR",
        card: Boolean = false,
    ) = AccountBalanceEntity(
        bankName = "Test Bank", accountLast4 = last4, balance = d(balance),
        timestamp = LocalDateTime.of(2026, 11, 15, 12, 0), isCreditCard = card, accountType = type, currency = currency,
    )

    @Test
    fun `cards and loan accounts are taken off what's held`() {
        val total = NetWorth.totals(
            listOf(
                account("1111", "50000"),
                account("2222", "2000", type = "CASH"),
                // The full outstanding, billed and unbilled.
                account("3333", "12000", type = "CREDIT", card = true),
                account("4444", "30000", type = "LOAN"),
            )
        ).single()
        assertEquals(d("52000"), total.assets)
        assertEquals(d("12000"), total.cards)
        assertEquals(d("30000"), total.loans)
        assertEquals(d("10000"), total.net)
    }

    @Test
    fun `owing more than is held is a negative net worth`() {
        val total = NetWorth.totals(listOf(account("1111", "1000"), account("4444", "5000", type = "LOAN"))).single()
        assertEquals(d("-4000"), total.net)
    }

    @Test
    fun `an overdrawn account lowers assets`() {
        val total = NetWorth.totals(listOf(account("1111", "-250"), account("2222", "1000"))).single()
        assertEquals(d("750"), total.assets)
    }

    @Test
    fun `each currency is its own total, never summed`() {
        val totals = NetWorth.totals(
            listOf(
                account("1111", "1000", currency = "INR"),
                account("2222", "2000", currency = "INR"),
                account("3333", "500", currency = "USD"),
                account("4444", "200", currency = "USD", type = "CREDIT", card = true),
            )
        )
        assertEquals(listOf("INR", "USD"), totals.map { it.currency })
        assertEquals(d("3000"), totals[0].net)
        assertEquals(d("300"), totals[1].net)
    }

    @Test
    fun `an account counts in the group of what it is`() {
        assertEquals(NetWorthGroup.ASSETS, account("1111", "1").netWorthGroup)
        assertEquals(NetWorthGroup.CARDS, account("2222", "1", type = "CREDIT", card = true).netWorthGroup)
        assertEquals(NetWorthGroup.LOANS, account("3333", "1", type = "LOAN").netWorthGroup)
    }
}
