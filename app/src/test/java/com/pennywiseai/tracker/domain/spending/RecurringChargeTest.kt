package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
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

    @Test
    fun `the next payment shown keeps today and moves past dates on a month at a time`() {
        val today = LocalDate.of(2026, 11, 16)
        assertEquals(today, RecurringCharge.nextPaymentShown(today, "Monthly", today))
        assertEquals(LocalDate.of(2026, 12, 10), RecurringCharge.nextPaymentShown(LocalDate.of(2026, 10, 10), "Monthly", today))
        assertEquals(LocalDate.of(2026, 11, 20), RecurringCharge.nextPaymentShown(LocalDate.of(2026, 11, 20), "Monthly", today))
    }

    @Test
    fun `the next payment shown moves on by the subscription's own cycle`() {
        val today = LocalDate.of(2026, 11, 17)
        // Weekly: Nov 13 is next due Nov 20, not Dec 13.
        assertEquals(LocalDate.of(2026, 11, 20), RecurringCharge.nextPaymentShown(LocalDate.of(2026, 11, 13), "Weekly", today))
        // Annual: a missed March date is next due next March, with no monthly dates between.
        assertEquals(LocalDate.of(2027, 3, 5), RecurringCharge.nextPaymentShown(LocalDate.of(2026, 3, 5), "Annual", today))
        assertEquals(LocalDate.of(2027, 2, 1), RecurringCharge.nextPaymentShown(LocalDate.of(2026, 11, 1), "Quarterly", today))
        // Due today stays today, whatever the cycle.
        assertEquals(today, RecurringCharge.nextPaymentShown(today, "Weekly", today))
    }

    @Test
    fun `a month-end payment lands on February's last day and comes back to the 31st`() {
        val jan31 = LocalDate.of(2027, 1, 31)
        assertEquals(LocalDate.of(2027, 2, 28), RecurringCharge.nextPaymentShown(jan31, "Monthly", LocalDate.of(2027, 2, 10)))
        assertEquals(
            LocalDate.of(2028, 2, 29),
            RecurringCharge.nextPaymentShown(LocalDate.of(2028, 1, 31), "Monthly", LocalDate.of(2028, 2, 10)),
        )
        assertEquals(LocalDate.of(2027, 3, 31), RecurringCharge.nextPaymentShown(jan31, "Monthly", LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun `advance moves by the cycle, either way`() {
        val date = LocalDate.of(2026, 11, 13)
        assertEquals(LocalDate.of(2026, 11, 20), RecurringCharge.advance(date, "Weekly"))
        assertEquals(LocalDate.of(2026, 11, 6), RecurringCharge.advance(date, "WEEKLY", -1))
        assertEquals(LocalDate.of(2027, 5, 13), RecurringCharge.advance(date, "Semi-Annual"))
        assertEquals(LocalDate.of(2027, 11, 13), RecurringCharge.advance(date, "Yearly"))
        assertEquals(LocalDate.of(2026, 12, 13), RecurringCharge.advance(date, "Fortnightly"))
    }

    @Test
    fun `a charge pays only from every part of the account the bill names`() {
        fun sub(bank: String?, last4: String?) = SubscriptionEntity(
            merchantName = "Streamer", amount = BigDecimal("100"), nextPaymentDate = null, bankName = bank, accountLast4 = last4,
        )
        fun charge(bank: String?, account: String?) = TransactionEntity(
            amount = BigDecimal("100"), merchantName = "Streamer", category = "Bills", transactionType = TransactionType.EXPENSE,
            dateTime = LocalDate.of(2026, 11, 17).atStartOfDay(), transactionHash = "h", bankName = bank, accountNumber = account,
        )
        assertTrue(RecurringCharge.couldBePaidFrom(sub("Bank A", "1111"), charge("bank a", "1111")))
        assertFalse(RecurringCharge.couldBePaidFrom(sub("Bank A", "1111"), charge("Bank A", "2222")))
        assertFalse(RecurringCharge.couldBePaidFrom(sub("Bank A", "1111"), charge("Bank B", "1111")))
        // Missing on the charge: not enough to pay an account-tied bill.
        assertFalse(RecurringCharge.couldBePaidFrom(sub("Bank A", "1111"), charge("Bank A", null)))
        assertFalse(RecurringCharge.couldBePaidFrom(sub("Bank A", "1111"), charge(null, null)))
        assertFalse(RecurringCharge.couldBePaidFrom(sub("Bank A", null), charge(null, "1111")))
        // Only what the bill names has to match.
        assertTrue(RecurringCharge.couldBePaidFrom(sub("Bank A", null), charge("Bank A", "2222")))
        assertTrue(RecurringCharge.couldBePaidFrom(sub(null, null), charge(null, null)))
    }

    private fun plan(total: Int?, made: Int) = SubscriptionEntity(
        merchantName = "Synthetic EMI", amount = BigDecimal("1500.50"), currency = "INR",
        nextPaymentDate = LocalDate.of(2026, 12, 5), totalPayments = total, paymentsMade = made,
    )

    @Test
    fun `a payment on a plan counts and moves the schedule until the last one`() {
        val paid = LocalDate.of(2026, 12, 5)
        val next = LocalDate.of(2027, 1, 5)

        val third = RecurringCharge.afterPayment(plan(total = 12, made = 2), paid, next)
        assertEquals(3, third.paymentsMade)
        assertEquals(next, third.nextPaymentDate)
        assertEquals(paid, third.lastPaidAt)
        assertEquals(SubscriptionState.ACTIVE, third.state)
    }

    @Test
    fun `the last payment ends the plan without moving its date on`() {
        val paid = LocalDate.of(2026, 12, 5)

        val last = RecurringCharge.afterPayment(plan(total = 12, made = 11), paid, LocalDate.of(2027, 1, 5))

        assertEquals(12, last.paymentsMade)
        assertEquals(SubscriptionState.ENDED, last.state)
        assertEquals(LocalDate.of(2026, 12, 5), last.nextPaymentDate)
        assertEquals(paid, last.lastPaidAt)
    }

    @Test
    fun `a bill with no end only moves on and counts nothing`() {
        val next = LocalDate.of(2027, 1, 5)

        val after = RecurringCharge.afterPayment(plan(total = null, made = 0), LocalDate.of(2026, 12, 5), next)

        assertEquals(0, after.paymentsMade)
        assertEquals(next, after.nextPaymentDate)
        assertEquals(SubscriptionState.ACTIVE, after.state)
    }

    @Test
    fun `what's left is one payment for each still to make`() {
        assertEquals(BigDecimal("13504.50"), RecurringCharge.amountLeft(plan(total = 12, made = 3)))
        assertEquals(0, BigDecimal.ZERO.compareTo(RecurringCharge.amountLeft(plan(total = 12, made = 12))))
        // A plan shortened below what's been paid owes nothing, never a negative amount.
        assertEquals(0, BigDecimal.ZERO.compareTo(RecurringCharge.amountLeft(plan(total = 2, made = 3))))
        assertNull(RecurringCharge.amountLeft(plan(total = null, made = 0)))
    }

    @Test
    fun `a plan added 3 payments in owes 9 and counts on from 3`() {
        val midWay = plan(total = 12, made = 3)

        assertEquals(BigDecimal("1500.50").multiply(BigDecimal(9)), RecurringCharge.amountLeft(midWay))
        val fourth = RecurringCharge.afterPayment(midWay, LocalDate.of(2026, 12, 5), LocalDate.of(2027, 1, 5))
        assertEquals(4, fourth.paymentsMade)
        assertEquals(SubscriptionState.ACTIVE, fourth.state)
        assertEquals(BigDecimal("1500.50").multiply(BigDecimal(8)), RecurringCharge.amountLeft(fourth))
    }
}
