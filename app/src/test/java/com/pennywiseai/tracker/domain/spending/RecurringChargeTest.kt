package com.pennywiseai.tracker.domain.spending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RecurringChargeTest {

    @Test
    fun `merchant match ignores case, punctuation and a trailing suffix`() {
        assertTrue(RecurringCharge.sameMerchant("NETFLIX", "Netflix Entertainment"))
        assertTrue(RecurringCharge.sameMerchant("netflix.com", "NETFLIX"))
        assertFalse(RecurringCharge.sameMerchant("Netflix", "Spotify"))
        // Too short to prefix-match: would swallow every merchant starting with it.
        assertFalse(RecurringCharge.sameMerchant("ABC", "ABC Supermarket"))
        assertFalse(RecurringCharge.sameMerchant("", "Netflix"))
    }

    @Test
    fun `amount match is a symmetric 5 percent band`() {
        assertTrue(RecurringCharge.sameAmount(BigDecimal("100"), BigDecimal("104.99")))
        assertTrue(RecurringCharge.sameAmount(BigDecimal("104.99"), BigDecimal("100")))
        assertFalse(RecurringCharge.sameAmount(BigDecimal("15.49"), BigDecimal("17.99")))
    }

    @Test
    fun `gap between charges maps to a billing cycle`() {
        assertEquals("Weekly", RecurringCharge.cycleForGap(7))
        assertEquals("Monthly", RecurringCharge.cycleForGap(28))
        assertEquals("Monthly", RecurringCharge.cycleForGap(31))
        assertEquals("Quarterly", RecurringCharge.cycleForGap(91))
        assertEquals("Annual", RecurringCharge.cycleForGap(365))
        assertNull(RecurringCharge.cycleForGap(3))
        assertNull(RecurringCharge.cycleForGap(50))
    }
}
