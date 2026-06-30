package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Locks down [resolveBudgetWindow] — the read-time helper the BudgetCard /
 * widget / edit screen use to figure out which [start, end] window a
 * budget covers *right now*, given its period type and cadence anchor.
 *
 * Rolling Weekly and Monthly budgets are the whole point of this helper:
 * their row's persisted [BudgetEntity.startDate, endDate] is a "last
 * computed" cache, but the resolver always returns the *current* window
 * from the anchor + today's date, so a budget created weeks/months ago
 * still shows the right "this week" / "this month" window.
 */
class BudgetWindowTest {

    private fun budgetOf(
        periodType: BudgetPeriodType,
        weekStartDay: Int? = null,
        monthStartDay: Int? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ) = BudgetEntity(
        id = 1L,
        name = "Test",
        limitAmount = BigDecimal.ZERO,
        periodType = periodType,
        startDate = startDate ?: LocalDate.of(2026, 10, 5),
        endDate = endDate ?: LocalDate.of(2026, 10, 5).plusDays(6),
        weekStartDay = weekStartDay,
        monthStartDay = monthStartDay
    )

    // ── Weekly ───────────────────────────────────────────────────────────

    @Test
    fun `weekly budget anchored to Monday returns the Mon-Sun window containing today`() {
        // Oct 7, 2026 is a Wednesday. The week containing it, starting on
        // Monday, is Oct 5..Oct 11.
        val today = LocalDate.of(2026, 10, 7)
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.WEEKLY, weekStartDay = 1), today)
        assertEquals(LocalDate.of(2026, 10, 5), w.start)
        assertEquals(LocalDate.of(2026, 10, 11), w.end)
        assertEquals(7, w.days)
    }

    @Test
    fun `weekly budget anchored to Wednesday returns the Wed-Tue window containing today`() {
        val today = LocalDate.of(2026, 10, 7) // Wed
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.WEEKLY, weekStartDay = 3), today)
        // Window starts on the most recent Wednesday on or before today.
        assertEquals(LocalDate.of(2026, 10, 7), w.start)
        assertEquals(LocalDate.of(2026, 10, 13), w.end)
        assertEquals(7, w.days)
    }

    @Test
    fun `weekly budget with no anchor falls back to Monday`() {
        val today = LocalDate.of(2026, 10, 7) // Wed
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.WEEKLY, weekStartDay = null), today)
        assertEquals(LocalDate.of(2026, 10, 5), w.start) // Monday
        assertEquals(LocalDate.of(2026, 10, 11), w.end)
    }

    // ── Monthly ──────────────────────────────────────────────────────────

    @Test
    fun `monthly budget anchored to the 25th on Oct 5 returns Sep 25 through Oct 24`() {
        val today = LocalDate.of(2026, 10, 5)
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = 25), today)
        assertEquals(LocalDate.of(2026, 9, 25), w.start)
        assertEquals(LocalDate.of(2026, 10, 24), w.end)
        assertEquals(30, w.days)
    }

    @Test
    fun `monthly budget anchored to the 25th on Oct 26 returns Oct 25 through Nov 24`() {
        val today = LocalDate.of(2026, 10, 26)
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = 25), today)
        assertEquals(LocalDate.of(2026, 10, 25), w.start)
        assertEquals(LocalDate.of(2026, 11, 24), w.end)
    }

    @Test
    fun `monthly budget anchored to the 1st behaves like the calendar month`() {
        val today = LocalDate.of(2026, 10, 5)
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = 1), today)
        assertEquals(LocalDate.of(2026, 10, 1), w.start)
        assertEquals(LocalDate.of(2026, 10, 31), w.end)
    }

    @Test
    fun `monthly budget with no anchor falls back to the global startDay preference`() {
        val today = LocalDate.of(2026, 10, 5)
        val w = resolveBudgetWindow(
            budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = null),
            today,
            globalStartDay = 25
        )
        assertEquals(LocalDate.of(2026, 9, 25), w.start)
        assertEquals(LocalDate.of(2026, 10, 24), w.end)
    }

    @Test
    fun `monthly budget anchored to 31 on Feb 15 non-leap returns Jan 31 through Feb 27`() {
        // Feb 31 doesn't exist, so the cycle's start clamps to Jan 31.
        val today = LocalDate.of(2025, 2, 15)
        val w = resolveBudgetWindow(budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = 31), today)
        assertEquals(LocalDate.of(2025, 1, 31), w.start)
        assertEquals(LocalDate.of(2025, 2, 27), w.end)
    }

    // ── Custom (one-time) ────────────────────────────────────────────────

    @Test
    fun `custom budget returns its literal saved range regardless of today`() {
        val today = LocalDate.of(2026, 10, 5)
        val w = resolveBudgetWindow(
            budgetOf(
                BudgetPeriodType.CUSTOM,
                startDate = LocalDate.of(2026, 12, 20),
                endDate = LocalDate.of(2027, 1, 5)
            ),
            today
        )
        // No rolling — the literal range the user picked.
        assertEquals(LocalDate.of(2026, 12, 20), w.start)
        assertEquals(LocalDate.of(2027, 1, 5), w.end)
        assertEquals(17, w.days)
    }

    @Test
    fun `custom budget ignores the anchor fields entirely`() {
        val today = LocalDate.of(2026, 10, 5)
        val w = resolveBudgetWindow(
            budgetOf(
                BudgetPeriodType.CUSTOM,
                weekStartDay = 1,
                monthStartDay = 15,
                startDate = LocalDate.of(2026, 11, 1),
                endDate = LocalDate.of(2026, 11, 30)
            ),
            today
        )
        // Even with weekly/monthly anchors set, CUSTOM uses the literal
        // startDate / endDate the user picked.
        assertEquals(LocalDate.of(2026, 11, 1), w.start)
        assertEquals(LocalDate.of(2026, 11, 30), w.end)
    }
}
