package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** A foreign-currency spend must never be summed into a balance kept in another currency (#888). */
class BalanceAfterCurrencyTest {

    @Test
    fun `a USD spend on an INR card leaves the INR balance alone`() {
        val balance = BalanceCalculator.balanceAfter(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.CREDIT,
            transactionAmount = BigDecimal("100"),
            transactionCurrency = "USD",
            accountCurrency = "INR",
            currentBalance = BigDecimal("5000.00")
        )
        assertEquals(BigDecimal("5000.00"), balance)
    }

    @Test
    fun `a foreign spend takes the explicit balance the SMS reports`() {
        val balance = BalanceCalculator.balanceAfter(
            explicitBalance = BigDecimal("4200.00"),
            isCreditCard = false,
            transactionType = TransactionType.EXPENSE,
            transactionAmount = BigDecimal("10"),
            transactionCurrency = "USD",
            accountCurrency = "INR",
            currentBalance = BigDecimal("5000.00")
        )
        assertEquals(BigDecimal("4200.00"), balance)
    }

    @Test
    fun `a same-currency spend is applied as before`() {
        val balance = BalanceCalculator.balanceAfter(
            explicitBalance = null,
            isCreditCard = true,
            transactionType = TransactionType.CREDIT,
            transactionAmount = BigDecimal("100.00"),
            transactionCurrency = "INR",
            accountCurrency = "INR",
            currentBalance = BigDecimal("5000.00")
        )
        assertEquals(BigDecimal("5100.00"), balance)
    }
}
