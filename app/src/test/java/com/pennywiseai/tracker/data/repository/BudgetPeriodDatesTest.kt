package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Locks down the per-budget start-date math that the BudgetGroup edit screen
 * uses to seed a fresh budget and to recompute the end date on every
 * start-date picker change. The tests pin [BudgetRepository.calculatePeriodDates]'s
 * `today` parameter so the assertions are deterministic.
 */
class BudgetPeriodDatesTest {

    @Test
    fun `monthly budget anchored to a custom start date spans one calendar month`() {
        val (start, end) = BudgetRepository.calculatePeriodDates(
            periodType = BudgetPeriodType.MONTHLY,
            customStartDate = LocalDate.of(2026, 10, 5),
            today = LocalDate.of(2026, 10, 5)
        )

        // The customStartDate path ignores the global startDay and pins
        // the window to the user-chosen date. End is one calendar month later.
        assertEquals(LocalDate.of(2026, 10, 5), start)
        assertEquals(LocalDate.of(2026, 11, 4), end)
    }

    @Test
    fun `weekly budget anchored to a custom start date spans 7 days`() {
        val (start, end) = BudgetRepository.calculatePeriodDates(
            periodType = BudgetPeriodType.WEEKLY,
            customStartDate = LocalDate.of(2026, 10, 5),
            today = LocalDate.of(2026, 10, 5)
        )

        assertEquals(LocalDate.of(2026, 10, 5), start)
        assertEquals(LocalDate.of(2026, 10, 11), end)
    }

    @Test
    fun `custom budget anchored to a custom start date spans 30 days`() {
        val (start, end) = BudgetRepository.calculatePeriodDates(
            periodType = BudgetPeriodType.CUSTOM,
            customStartDate = LocalDate.of(2026, 12, 1),
            today = LocalDate.of(2026, 12, 1)
        )

        assertEquals(LocalDate.of(2026, 12, 1), start)
        assertEquals(LocalDate.of(2026, 12, 30), end)
    }

    @Test
    fun `endDateFor mirrors the period-type derivation`() {
        // Used by the screen to recompute the read-only "Ends on" label
        // whenever the user picks a new start date.
        assertEquals(
            LocalDate.of(2026, 10, 11),
            BudgetRepository.endDateFor(LocalDate.of(2026, 10, 5), BudgetPeriodType.WEEKLY)
        )
        assertEquals(
            LocalDate.of(2026, 11, 4),
            BudgetRepository.endDateFor(LocalDate.of(2026, 10, 5), BudgetPeriodType.MONTHLY)
        )
        assertEquals(
            LocalDate.of(2026, 12, 30),
            BudgetRepository.endDateFor(LocalDate.of(2026, 12, 1), BudgetPeriodType.CUSTOM)
        )
    }
}
