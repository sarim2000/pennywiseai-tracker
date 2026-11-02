package com.pennywiseai.tracker.presentation.home

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class HomeFiguresTest {
    private fun account(balance: String, currency: String, limit: String? = null, card: Boolean = false) =
        AccountBalanceEntity(
            bankName = "Bank",
            accountLast4 = "0000",
            balance = BigDecimal(balance),
            timestamp = LocalDateTime.of(2026, 10, 1, 12, 0),
            currency = currency,
            creditLimit = limit?.let(::BigDecimal),
            isCreditCard = card,
        )

    @Test fun `balance never adds another currency`() {
        val t = balanceTotals(
            accounts = listOf(account("500", "INR"), account("10", "USD")),
            creditCards = listOf(account("100", "INR", "1000", card = true), account("5", "USD", "50", card = true)),
            currency = "INR",
        )
        assertEquals(0, BigDecimal("500").compareTo(t.balance))
        assertEquals(0, BigDecimal("900").compareTo(t.availableCredit))
        assertTrue(t.hasOtherCurrencies)
    }

    @Test fun `single currency is not flagged`() =
        assertFalse(balanceTotals(listOf(account("1", "INR")), emptyList(), "INR").hasOtherCurrencies)
}
