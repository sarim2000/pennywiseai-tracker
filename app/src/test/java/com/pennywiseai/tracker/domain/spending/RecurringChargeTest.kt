package com.pennywiseai.tracker.domain.spending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

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

    private val due = LocalDate.of(2026, 11, 24)

    @Test
    fun `a new price near the due date is a price change`() {
        assertTrue(RecurringCharge.isPriceChange(BigDecimal("15.49"), due, BigDecimal("17.99"), due))
        assertTrue(RecurringCharge.isPriceChange(BigDecimal("15.49"), due, BigDecimal("12.99"), due.plusDays(5)))
        assertTrue(RecurringCharge.isPriceChange(BigDecimal("100"), due, BigDecimal("200"), due.minusDays(5)))
        assertTrue(RecurringCharge.isPriceChange(BigDecimal("100"), due, BigDecimal("50"), due))
    }

    @Test
    fun `same amount, far from due date or wild amount is not a price change`() {
        // Same price is a plain match, not a change.
        assertFalse(RecurringCharge.isPriceChange(BigDecimal("15.49"), due, BigDecimal("15.49"), due))
        assertFalse(RecurringCharge.isPriceChange(BigDecimal("15.49"), due, BigDecimal("17.99"), due.plusDays(6)))
        assertFalse(RecurringCharge.isPriceChange(BigDecimal("15.49"), due, BigDecimal("17.99"), due.minusDays(6)))
        assertFalse(RecurringCharge.isPriceChange(BigDecimal("100"), due, BigDecimal("200.01"), due))
        assertFalse(RecurringCharge.isPriceChange(BigDecimal("100"), due, BigDecimal("49.99"), due))
        assertFalse(RecurringCharge.isPriceChange(BigDecimal("15.49"), null, BigDecimal("17.99"), due))
    }
}
