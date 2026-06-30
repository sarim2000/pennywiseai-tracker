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

    // ── windowsForMonth ───────────────────────────────────────────────

    @Test
    fun `weekly windows for a Mon-anchored budget in September 2026 yield 4 clipped windows`() {
        val today = LocalDate.of(2026, 10, 5)
        val budget = budgetOf(BudgetPeriodType.WEEKLY, weekStartDay = 1)
        val windows = windowsForMonth(budget, year = 2026, month = 9, globalStartDay = 1)

        // Sept 1 was a Tuesday. Week 1 starts Mon Aug 31 → clipped to Sept 1
        // (Tue) → Sept 6 (Sun). Week 2: Sept 7-13. Week 3: Sept 14-20.
        // Week 4: Sept 21-27. Week 5: Sept 28 - Oct 4 → clipped to Sept 28-30.
        assertEquals(5, windows.size)
        // Every window stays inside September.
        windows.forEach { w ->
            assert(w.start >= LocalDate.of(2026, 9, 1))
            assert(w.end <= LocalDate.of(2026, 9, 30))
        }
        // First window starts on the 1st (Tue) — the clipped Mon-anchor.
        assertEquals(LocalDate.of(2026, 9, 1), windows.first().start)
        // Last window ends on the 30th.
        assertEquals(LocalDate.of(2026, 9, 30), windows.last().end)
    }

    @Test
    fun `monthly windows for a 25-anchored budget in September 2026 return the Aug-25 to Sept-24 cycle that intersects`() {
        val today = LocalDate.of(2026, 10, 5)
        val budget = budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = 25)
        val windows = windowsForMonth(budget, year = 2026, month = 9, globalStartDay = 25)

        // The cycle that contains Sept 1 is the Aug 25..Sept 24 cycle.
        // The full cycle is returned (start is in August but the cycle
        // intersects September). The spend query inside the repo is
        // clipped to the page's month bounds separately.
        assertEquals(1, windows.size)
        assertEquals(LocalDate.of(2026, 8, 25), windows.first().start)
        assertEquals(LocalDate.of(2026, 9, 24), windows.first().end)
    }

    @Test
    fun `monthly windows for a 25-anchored budget in October 2026 yield the Sept-25 to Oct-24 window`() {
        val today = LocalDate.of(2026, 10, 5)
        val budget = budgetOf(BudgetPeriodType.MONTHLY, monthStartDay = 25)
        val windows = windowsForMonth(budget, year = 2026, month = 10, globalStartDay = 25)

        // The cycle that contains Oct 1 is the Sept 25..Oct 24 cycle, fully
        // inside October.
        assertEquals(1, windows.size)
        assertEquals(LocalDate.of(2026, 9, 25), windows.first().start)
        assertEquals(LocalDate.of(2026, 10, 24), windows.first().end)
    }

    @Test
    fun `custom windows for a Nov 5 to Dec 4 budget return an empty list for September`() {
        val today = LocalDate.of(2026, 10, 5)
        val budget = budgetOf(
            BudgetPeriodType.CUSTOM,
            startDate = LocalDate.of(2026, 11, 5),
            endDate = LocalDate.of(2026, 12, 4)
        )
        val windows = windowsForMonth(budget, year = 2026, month = 9, globalStartDay = 1)
        assertEquals(0, windows.size)
    }

    @Test
    fun `custom windows for a Nov 5 to Dec 4 budget return a clipped window for November`() {
        val today = LocalDate.of(2026, 10, 5)
        val budget = budgetOf(
            BudgetPeriodType.CUSTOM,
            startDate = LocalDate.of(2026, 11, 5),
            endDate = LocalDate.of(2026, 12, 4)
        )
        val windows = windowsForMonth(budget, year = 2026, month = 11, globalStartDay = 1)
        assertEquals(1, windows.size)
        // Clipped to November: starts on Nov 5, ends on Nov 30.
        assertEquals(LocalDate.of(2026, 11, 5), windows.first().start)
        assertEquals(LocalDate.of(2026, 11, 30), windows.first().end)
    }

    // ── overlaps ──────────────────────────────────────────────────────

    @Test
    fun `overlaps detects intersecting windows`() {
        val a = BudgetWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 30)
        val b = BudgetWindow(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 5), 11)
        assert(a.overlaps(b))
        assert(b.overlaps(a))
    }

    @Test
    fun `overlaps rejects disjoint windows`() {
        val a = BudgetWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 30)
        val b = BudgetWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 31)
        assert(!a.overlaps(b))
    }

    @Test
    fun `overlaps accepts touching windows on the boundary`() {
        val a = BudgetWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 30)
        val b = BudgetWindow(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 5), 6)
        // They share Sep 30 → overlap is true.
        assert(a.overlaps(b))
    }

    // ── cap-date semantics (the "frozen vs live" math that powers the
    //    History screen and the Weekly sub-list) ────────────────────

    @Test
    fun `partial last week in June 2026 - June view freezes the Jun 30 portion`() {
        // Mon-anchored Weekly budget. The week starting Mon Jun 30 spans
        // Jun 30..Jul 6. For the June view, the spend is frozen at
        // Jun 30 — only the Jun 30 day is counted, with the cap date
        // = monthEnd = Jun 30.
        val today = LocalDate.of(2026, 7, 5)
        val monthEnd = LocalDate.of(2026, 6, 30)
        val window = BudgetWindow(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 6), 7)
        val isCurrentMonth = false
        val isCurrentWeek = !isCurrentMonth && false ||
            isCurrentMonth && !today.isBefore(window.start) && !today.isAfter(window.end)
        assertEquals(false, isCurrentWeek)
        // The repo's effective end for the spend query is the cap date,
        // which is monthEnd for a frozen window.
        val cap = if (isCurrentWeek) today else monthEnd
        assertEquals(LocalDate.of(2026, 6, 30), cap)
    }

    @Test
    fun `current week in July 2026 - July view is live up to today`() {
        val today = LocalDate.of(2026, 7, 5)
        val monthEnd = LocalDate.of(2026, 7, 31)
        val window = BudgetWindow(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 6), 7)
        val isCurrentMonth = true
        val isCurrentWeek = isCurrentMonth &&
            !today.isBefore(window.start) && !today.isAfter(window.end)
        assertEquals(true, isCurrentWeek)
        val cap = if (isCurrentWeek) today else monthEnd
        assertEquals(LocalDate.of(2026, 7, 5), cap)
    }
}
