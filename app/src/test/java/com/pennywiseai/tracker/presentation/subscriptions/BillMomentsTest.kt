package com.pennywiseai.tracker.presentation.subscriptions

import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class BillMomentsTest {

    private fun payment(type: TransactionType, amount: String = "12.99", currency: String = "EUR") = TransactionEntity(
        amount = BigDecimal(amount),
        merchantName = "Streaming",
        category = "Subscriptions",
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 11, 1, 0, 0),
        transactionHash = "subpay-1-2026-11-01",
        currency = currency,
    )

    @Test
    fun `tracking a bill says so, income doesn't, and nothing new tracked says nothing`() {
        assertEquals(MascotMoment.BillTracked, billTrackedMoment(SubscriptionDirection.EXPENSE))
        assertNull(billTrackedMoment(SubscriptionDirection.INCOME))
        // A suggestion someone already tracks returns no subscription.
        assertNull(billTrackedMoment(null))
    }

    @Test
    fun `a bill paid carries the payment's own amount and currency`() {
        assertEquals(
            MascotMoment.BillPaid(Money(BigDecimal("12.99"), "EUR")),
            billPaidMoment(payment(TransactionType.EXPENSE)),
        )
        assertEquals(
            MascotMoment.BillPaid(Money(BigDecimal("4500"), "JPY")),
            billPaidMoment(payment(TransactionType.EXPENSE, amount = "4500", currency = "JPY")),
        )
    }

    @Test
    fun `income marked received, or no payment at all, says nothing`() {
        assertNull(billPaidMoment(payment(TransactionType.INCOME)))
        assertNull(billPaidMoment(null))
    }
}
