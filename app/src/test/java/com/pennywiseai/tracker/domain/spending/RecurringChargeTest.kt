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
    fun `merchant match ignores case and punctuation, not a suffix`() {
        assertTrue(RecurringCharge.sameMerchant("netflix.com", "NETFLIX.COM"))
        assertTrue(RecurringCharge.sameMerchant("Swiggy One", "SWIGGYONE"))
        assertFalse(RecurringCharge.sameMerchant("Swiggy", "Swiggy One"))
        assertFalse(RecurringCharge.sameMerchant("Netflix", "Spotify"))
        assertFalse(RecurringCharge.sameMerchant("", ""))
    }

    @Test
    fun `a charge pays the cycle from a few days before it is due`() {
        val due = LocalDate.of(2026, 12, 1)
        assertFalse(RecurringCharge.canPayCycle(due, LocalDate.of(2026, 11, 10)))
        assertFalse(RecurringCharge.canPayCycle(due, LocalDate.of(2026, 11, 25)))
        assertTrue(RecurringCharge.canPayCycle(due, LocalDate.of(2026, 11, 26)))
        // Overdue (schedule fell behind) still matches.
        assertTrue(RecurringCharge.canPayCycle(due, LocalDate.of(2027, 2, 1)))
        assertTrue(RecurringCharge.canPayCycle(null, LocalDate.of(2026, 11, 10)))
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

    @Test
    fun `re-tap guard refuses a second payment but not the next cycle`() {
        val mar10 = LocalDate.of(2026, 3, 10)
        val mar15 = LocalDate.of(2026, 3, 15)
        val mar30 = LocalDate.of(2026, 3, 30)
        val apr10 = LocalDate.of(2026, 4, 10)
        // Due Mar 10, paid late Mar 30 -> next Apr 10. A second tap that day is a re-tap.
        assertTrue(RecurringCharge.isRetap(mar30, LocalDate.of(2026, 4, 30), apr10, mar30))
        // Paid late Mar 15: the Apr 10 bill can still be paid on time, or a few days early.
        assertFalse(RecurringCharge.isRetap(mar15, LocalDate.of(2026, 4, 15), apr10, apr10))
        assertFalse(RecurringCharge.isRetap(mar15, LocalDate.of(2026, 4, 15), apr10, LocalDate.of(2026, 4, 7)))
        assertTrue(RecurringCharge.isRetap(mar15, LocalDate.of(2026, 4, 15), apr10, LocalDate.of(2026, 4, 6)))
        // Paid on time: a tap the same day is a re-tap.
        assertTrue(RecurringCharge.isRetap(mar10, apr10, apr10, mar10))
        // Weekly, due Nov 19, paid Nov 23 -> next Nov 26 is due soon, but a second tap is still a re-tap.
        val nov23 = LocalDate.of(2026, 11, 23)
        val nov26 = LocalDate.of(2026, 11, 26)
        assertTrue(RecurringCharge.isRetap(nov23, LocalDate.of(2026, 11, 30), nov26, nov23))
        assertFalse(RecurringCharge.isRetap(nov23, LocalDate.of(2026, 11, 30), nov26, nov26))
    }
}
