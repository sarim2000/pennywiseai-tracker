package com.pennywiseai.tracker.domain.spending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BudgetPaceTest {

    private fun pace(spent: Int, budget: Int, elapsed: Int, days: Int) =
        budgetPace(BigDecimal(spent), BigDecimal(budget), elapsed, days)

    @Test
    fun `spending exactly on the even line is on pace`() {
        val p = pace(spent = 1000, budget = 3000, elapsed = 10, days = 30)!!
        assertEquals(0, p.daysOver)
        assertEquals(10f / 30, p.todayFraction, 0.0001f)
        assertFalse(p.isOverBudget)
    }

    @Test
    fun `under and over pace count whole days of budget`() {
        // 100/day: 700 spent by day 10 is 3 days under; 1250 is 2.5 → 2 days over.
        assertEquals(-3, pace(700, 3000, 10, 30)!!.daysOver)
        assertEquals(2, pace(1250, 3000, 10, 30)!!.daysOver)
        // Less than a full day off either way still reads as on pace.
        assertEquals(0, pace(1090, 3000, 10, 30)!!.daysOver)
        assertEquals(0, pace(910, 3000, 10, 30)!!.daysOver)
    }

    @Test
    fun `first day`() {
        val p = pace(0, 3000, 1, 30)!!
        assertEquals(-1, p.daysOver)
        assertEquals(1f / 30, p.todayFraction, 0.0001f)
        // A big purchase on day one is many days ahead.
        assertEquals(5, pace(600, 3000, 1, 30)!!.daysOver)
    }

    @Test
    fun `last day puts the tick at the end`() {
        val p = pace(2950, 3000, 30, 30)!!
        assertEquals(1f, p.todayFraction, 0.0001f)
        assertEquals(0, p.daysOver)
        assertFalse(p.isOverBudget)
    }

    @Test
    fun `over budget`() {
        val p = pace(3600, 3000, 20, 30)!!
        assertTrue(p.isOverBudget)
        assertEquals(16, p.daysOver)
        // Exactly at the limit isn't over.
        assertFalse(pace(3000, 3000, 30, 30)!!.isOverBudget)
    }

    @Test
    fun `no pace without a budget or a running window`() {
        assertNull(pace(100, 0, 5, 30))
        assertNull(pace(100, -10, 5, 30))
        assertNull(pace(100, 3000, 0, 30))
        assertNull(pace(100, 3000, 31, 30))
        assertNull(pace(100, 3000, 0, 0))
    }
}
