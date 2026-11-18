package com.pennywiseai.tracker.domain.spending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class MoneyAlertRulesTest {

    private val start = LocalDate.of(2026, 11, 1)
    private val end = LocalDate.of(2026, 11, 30)

    private fun tier(spent: String, day: Int, limit: String = "1000") = MoneyAlertRules.budgetTier(
        BigDecimal(spent), BigDecimal(limit), start, end, start.withDayOfMonth(day)
    )

    @Test
    fun `warns at 80 percent early in the window`() {
        assertEquals(BudgetAlertTier.WARNING, tier("800", day = 10))
        assertNull(tier("799.99", day = 10))
    }

    @Test
    fun `no warning once 80 percent of the window has gone`() {
        assertEquals(BudgetAlertTier.WARNING, tier("820", day = 23))
        assertNull(tier("820", day = 24))
        assertNull(tier("820", day = 28))
    }

    @Test
    fun `exactly the limit is not over, a cent more is, on any day`() {
        assertEquals(BudgetAlertTier.WARNING, tier("1000", day = 5))
        assertNull(tier("1000", day = 29))
        assertEquals(BudgetAlertTier.OVER, tier("1000.01", day = 5))
        assertEquals(BudgetAlertTier.OVER, tier("1000.01", day = 29))
    }

    @Test
    fun `scale does not change the comparison`() {
        assertEquals(BudgetAlertTier.WARNING, tier("800.00", day = 10, limit = "1000.000"))
    }

    @Test
    fun `no alert outside the window or without a limit`() {
        val after = end.plusDays(1)
        assertNull(MoneyAlertRules.budgetTier(BigDecimal("5000"), BigDecimal("1000"), start, end, after))
        assertNull(MoneyAlertRules.budgetTier(BigDecimal("5000"), BigDecimal("1000"), start, end, start.minusDays(1)))
        assertNull(tier("500", day = 5, limit = "0"))
    }

    @Test
    fun `each tier fires once a window and only upward`() {
        val w = "2026-11-01|2026-11-30|1000"
        val warned = MoneyAlertRules.nextBudgetLatch(null, w, BudgetAlertTier.WARNING)
        assertNotNull(warned)
        assertNull(MoneyAlertRules.nextBudgetLatch(warned, w, BudgetAlertTier.WARNING))
        val over = MoneyAlertRules.nextBudgetLatch(warned, w, BudgetAlertTier.OVER)
        assertNotNull(over)
        assertNull(MoneyAlertRules.nextBudgetLatch(over, w, BudgetAlertTier.OVER))
        // A refund drops it to the warning band: nothing to say twice.
        assertNull(MoneyAlertRules.nextBudgetLatch(over, w, BudgetAlertTier.WARNING))
    }

    @Test
    fun `a new window or a changed limit starts over`() {
        val over = MoneyAlertRules.nextBudgetLatch(null, "2026-11-01|2026-11-30|1000", BudgetAlertTier.OVER)
        assertNotNull(MoneyAlertRules.nextBudgetLatch(over, "2026-12-01|2026-12-31|1000", BudgetAlertTier.WARNING))
        assertNotNull(MoneyAlertRules.nextBudgetLatch(over, "2026-11-01|2026-11-30|2000", BudgetAlertTier.OVER))
    }

    @Test
    fun `an unreadable latch is treated as nothing told`() {
        assertNotNull(MoneyAlertRules.nextBudgetLatch("garbage", "w", BudgetAlertTier.WARNING))
        assertNotNull(MoneyAlertRules.nextBudgetLatch("w#NOPE", "w", BudgetAlertTier.WARNING))
    }

    @Test
    fun `low balance fires only on the crossing`() {
        val threshold = BigDecimal("500")
        assertTrue(MoneyAlertRules.crossedLowBalance(BigDecimal("501"), BigDecimal("500"), threshold))
        assertTrue(MoneyAlertRules.crossedLowBalance(BigDecimal("900"), BigDecimal("10"), threshold))
        // Already low, or still above.
        assertFalse(MoneyAlertRules.crossedLowBalance(BigDecimal("500"), BigDecimal("400"), threshold))
        assertFalse(MoneyAlertRules.crossedLowBalance(BigDecimal("900"), BigDecimal("501"), threshold))
        // Recovering isn't falling.
        assertFalse(MoneyAlertRules.crossedLowBalance(BigDecimal("400"), BigDecimal("900"), threshold))
    }

    @Test
    fun `low balance needs a threshold and a previous balance`() {
        assertFalse(MoneyAlertRules.crossedLowBalance(BigDecimal("900"), BigDecimal("10"), null))
        assertFalse(MoneyAlertRules.crossedLowBalance(null, BigDecimal("10"), BigDecimal("500")))
    }
}
