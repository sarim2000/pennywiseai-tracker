package com.pennywiseai.tracker.domain.spending

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SubscriptionCostTest {

    @Test
    fun `each billing cycle normalizes to a monthly equivalent`() {
        fun monthly(amount: String, cycle: String) =
            SubscriptionCost.monthlyEquivalent(BigDecimal(amount), cycle)

        assertEquals(BigDecimal("499.00"), monthly("499", "Monthly"))
        assertEquals(BigDecimal("433.33"), monthly("100", "Weekly"))
        assertEquals(BigDecimal("100.00"), monthly("300", "Quarterly"))
        assertEquals(BigDecimal("100.00"), monthly("600", "Semi-Annual"))
        assertEquals(BigDecimal("100.00"), monthly("1200", "Annual"))
        assertEquals(BigDecimal("83.33"), monthly("999.99", "yearly"))
        // An unknown cycle counts as monthly rather than vanishing from the total.
        assertEquals(BigDecimal("250.00"), monthly("250", "Fortnightly"))
    }
}
