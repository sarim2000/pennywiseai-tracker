package com.pennywiseai.tracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class SpendingSummaryWidgetTest {

    private val budget = BudgetWidgetData(totalLimit = BigDecimal("360"), remaining = BigDecimal("120"), currency = "JOD")

    @Test
    fun `uses a set budget in the same currency`() {
        assertEquals(budget, budgetIn(budget, "JOD"))
    }

    @Test
    fun `ignores a budget in another currency`() {
        assertNull(budgetIn(budget, "USD"))
    }

    @Test
    fun `ignores a budget with no limit`() {
        assertNull(budgetIn(budget.copy(totalLimit = BigDecimal.ZERO), "JOD"))
    }
}
