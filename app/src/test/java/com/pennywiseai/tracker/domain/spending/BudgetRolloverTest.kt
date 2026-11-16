package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetWindow
import com.pennywiseai.tracker.data.repository.resolveBudgetWindow
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BudgetRolloverTest {

    private fun jod(amount: String) = Money(BigDecimal(amount), "JOD")
    private fun carry(base: String, spent: String?) = BudgetRollover.carry(jod(base), spent?.let(::jod))

    private fun budget(
        periodType: BudgetPeriodType = BudgetPeriodType.WEEKLY,
        groupType: BudgetGroupType = BudgetGroupType.LIMIT,
        enabled: Boolean = true,
        since: LocalDate? = LocalDate.of(2026, 11, 4),
        monthStartDay: Int? = null,
    ) = BudgetEntity(
        id = 1, name = "Groceries", limitAmount = BigDecimal("100"), periodType = periodType,
        startDate = LocalDate.of(2026, 11, 2), endDate = LocalDate.of(2026, 11, 8),
        weekStartDay = if (periodType == BudgetPeriodType.WEEKLY) 1 else null,
        monthStartDay = monthStartDay, groupType = groupType,
        rolloverEnabled = enabled, rolloverSince = since
    )

    private fun week(start: String) = LocalDate.parse(start).let { BudgetWindow(it, it.plusDays(6), 7) }
    private fun window(start: String, end: String) = BudgetWindow(
        LocalDate.parse(start), LocalDate.parse(end),
        ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)).toInt() + 1
    )

    // ── carry ────────────────────────────────────────────────────────────

    @Test
    fun `what was left last period carries in`() {
        assertEquals(jod("40"), carry("100", "60"))
    }

    @Test
    fun `an overspend carries in as less to spend`() {
        assertEquals(jod("-30"), carry("100", "130"))
    }

    @Test
    fun `nothing spent carries at most one limit`() {
        assertEquals(jod("100"), carry("100", "0"))
        // Refunds outweighing spending still can't carry more than one limit.
        assertEquals(jod("100"), carry("100", "-20"))
    }

    @Test
    fun `a big overspend takes at most one limit off`() {
        assertEquals(jod("-100"), carry("100", "350"))
    }

    @Test
    fun `a zero limit carries nothing`() {
        assertEquals(jod("0"), carry("0", "40"))
    }

    @Test
    fun `spending that couldn't all be counted carries nothing, never a surplus`() {
        assertEquals(jod("0"), carry("100", null))
    }

    @Test
    fun `the effective limit is the base plus the carry`() {
        assertEquals(jod("70"), BudgetRollover.effectiveLimit(jod("100"), carry("100", "130")))
        assertEquals(jod("140"), BudgetRollover.effectiveLimit(jod("100"), carry("100", "60")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a carry in another currency is refused`() {
        BudgetRollover.effectiveLimit(jod("100"), Money(BigDecimal("10"), "USD"))
    }

    // ── which period carries ─────────────────────────────────────────────

    @Test
    fun `only weekly and monthly spending limits can roll over`() {
        assertTrue(BudgetRollover.isAvailable(BudgetGroupType.LIMIT, BudgetPeriodType.WEEKLY))
        assertTrue(BudgetRollover.isAvailable(BudgetGroupType.LIMIT, BudgetPeriodType.MONTHLY))
        assertFalse(BudgetRollover.isAvailable(BudgetGroupType.LIMIT, BudgetPeriodType.CUSTOM))
        assertFalse(BudgetRollover.isAvailable(BudgetGroupType.TARGET, BudgetPeriodType.WEEKLY))
        assertFalse(BudgetRollover.isAvailable(BudgetGroupType.EXPECTED, BudgetPeriodType.MONTHLY))
    }

    @Test
    fun `a budget with rollover off or not available carries nothing`() {
        val thisWeek = week("2026-11-16")
        assertNull(BudgetRollover.sourceWindow(budget(enabled = false), thisWeek, 1))
        assertNull(BudgetRollover.sourceWindow(budget(since = null), thisWeek, 1))
        assertNull(BudgetRollover.sourceWindow(budget(groupType = BudgetGroupType.TARGET), thisWeek, 1))
        assertNull(BudgetRollover.sourceWindow(budget(groupType = BudgetGroupType.EXPECTED), thisWeek, 1))
        assertNull(BudgetRollover.sourceWindow(budget(periodType = BudgetPeriodType.CUSTOM), thisWeek, 1))
    }

    @Test
    fun `the period rollover was turned on in carries nothing, the next carries from it`() {
        // Turned on Wednesday 4 Nov, in the week of Mon 2 Nov.
        val b = budget(since = LocalDate.of(2026, 11, 4))
        assertNull(BudgetRollover.sourceWindow(b, week("2026-11-02"), 1))
        assertEquals(week("2026-11-02"), BudgetRollover.sourceWindow(b, week("2026-11-09"), 1))
    }

    @Test
    fun `weeks without opening the app still carry from last week only`() {
        val b = budget(since = LocalDate.of(2026, 1, 7))
        assertEquals(week("2026-11-09"), BudgetRollover.sourceWindow(b, week("2026-11-16"), 1))
    }

    @Test
    fun `a week spanning two months carries as a whole week`() {
        val b = budget()
        // Mon 30 Nov – Sun 6 Dec is last week for the week of 7 Dec, all seven days.
        assertEquals(week("2026-11-30"), BudgetRollover.sourceWindow(b, week("2026-12-07"), 1))
        // December's page clips that week to 1–6 Dec; it still carries from the full week before.
        assertEquals(week("2026-11-23"), BudgetRollover.sourceWindow(b, window("2026-12-01", "2026-12-06"), 1))
    }

    @Test
    fun `a monthly budget anchored on the 31st carries across February`() {
        val b = budget(periodType = BudgetPeriodType.MONTHLY, monthStartDay = 31, since = LocalDate.of(2026, 12, 1))
        val march = resolveBudgetWindow(b, LocalDate.of(2027, 3, 15), 1)
        assertEquals(window("2027-02-28", "2027-03-30"), march)
        assertEquals(window("2027-01-31", "2027-02-27"), BudgetRollover.sourceWindow(b, march, 1))
        val april = resolveBudgetWindow(b, LocalDate.of(2027, 4, 2), 1)
        assertEquals(march, BudgetRollover.sourceWindow(b, april, 1))
    }

    @Test
    fun `a leap-year February cycle is carried from whole`() {
        val b = budget(periodType = BudgetPeriodType.MONTHLY, monthStartDay = 31, since = LocalDate.of(2027, 12, 1))
        val march = resolveBudgetWindow(b, LocalDate.of(2028, 3, 31), 1)
        assertEquals(window("2028-02-29", "2028-03-30"), BudgetRollover.sourceWindow(b, march, 1))
    }
}
