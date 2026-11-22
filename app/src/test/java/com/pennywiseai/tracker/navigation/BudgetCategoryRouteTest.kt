package com.pennywiseai.tracker.navigation

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.presentation.budgetgroups.categoryRows
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.getCycleAwareDateRange
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** A budget's category line opens the rows it counted: its own days, its currency, every profile. */
class BudgetCategoryRouteTest {
    private val oct25 = LocalDate.of(2026, 10, 25)
    private val nov22 = LocalDate.of(2026, 11, 22)

    // A monthly budget resetting on the 25th, on a phone whose budget cycle starts on the 1st.
    private val budget = BudgetEntity(
        id = 1, name = "Monthly", limitAmount = BigDecimal("5000"), periodType = BudgetPeriodType.MONTHLY,
        startDate = oct25, endDate = oct25.plusMonths(1).minusDays(1), monthStartDay = 25,
        currency = "INR", groupType = BudgetGroupType.LIMIT
    )

    private fun spending(window: BudgetWindow, cap: LocalDate) = BudgetGroupSpending(
        group = BudgetWithCategories(budget, emptyList()),
        categorySpending = emptyList(),
        totalBudget = BigDecimal.ZERO,
        totalActual = BigDecimal.ZERO,
        remaining = BigDecimal.ZERO,
        percentageUsed = 0f,
        dailyAllowance = BigDecimal.ZERO,
        daysRemaining = 0,
        daysElapsed = 0,
        windowStart = window.start,
        windowEnd = window.end,
        windowDays = window.days,
        displayedCapDate = cap
    )

    @Test
    fun `a category line opens the budget's own days, not the list's This Month`() {
        val window = resolveBudgetWindow(budget, nov22, globalStartDay = 1)
        assertEquals(oct25 to LocalDate.of(2026, 11, 24), window.start to window.end)
        // What the line used to open: This Month on the phone's cycle, Nov 1-30, so spend
        // from Oct 25-31 was on the line but never in the list.
        assertEquals(
            LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 30),
            getCycleAwareDateRange(TimePeriod.THIS_MONTH, 1, nov22)
        )

        assertEquals(
            TransactionsWithFilter(
                category = "Others",
                period = "CUSTOM",
                currency = "INR",
                startDate = oct25.toEpochDay(),
                // Counted up to today, so a later-dated row isn't listed either.
                endDate = nov22.toEpochDay(),
                currencyOnly = true,
                allProfiles = true,
            ),
            transactionsFor(spending(window, cap = nov22).categoryRows("Others", "INR", unified = false))
        )
    }

    @Test
    fun `a past window opens to its end, and unified mode lists every currency`() {
        val october = BudgetWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 31)
        val rows = transactionsFor(spending(october, cap = LocalDate.of(2026, 10, 31)).categoryRows("Food", "EUR", unified = true))
        assertEquals(october.start.toEpochDay() to october.end.toEpochDay(), rows.startDate to rows.endDate)
        assertEquals("EUR" to false, rows.currency to rows.currencyOnly)
        assertEquals(true, rows.allProfiles)
    }
}
