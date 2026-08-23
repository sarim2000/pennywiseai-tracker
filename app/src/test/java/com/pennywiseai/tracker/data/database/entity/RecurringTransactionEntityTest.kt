package com.pennywiseai.tracker.data.database.entity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Locks in [RecurringTransactionEntity.nextDueAfter] — specifically that a
 * monthly template anchored to a high day-of-month does NOT drift after a short
 * month (the bug the re-anchoring fix addresses, #706).
 */
class RecurringTransactionEntityTest {

    private fun template(
        frequency: RecurringFrequency,
        dayOfMonth: Int? = null,
        dayOfWeek: Int? = null,
    ) = RecurringTransactionEntity(
        merchantName = "Rent",
        frequency = frequency,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek,
    )

    @Test
    fun `monthly day-31 clamps for a short month but does not drift afterward`() {
        val t = template(RecurringFrequency.MONTHLY, dayOfMonth = 31)

        val feb = t.nextDueAfter(LocalDate.of(2026, 1, 31))
        assertEquals("Jan 31 -> Feb 28 (clamped)", LocalDate.of(2026, 2, 28), feb)

        // Re-anchored to the 31st, NOT Mar 28 (which plain plusMonths would give).
        val mar = t.nextDueAfter(feb)
        assertEquals("Feb 28 -> Mar 31 (re-anchored, no drift)", LocalDate.of(2026, 3, 31), mar)

        val apr = t.nextDueAfter(mar)
        assertEquals("Mar 31 -> Apr 30 (clamped)", LocalDate.of(2026, 4, 30), apr)
    }

    @Test
    fun `weekly preserves the weekday`() {
        val t = template(RecurringFrequency.WEEKLY, dayOfWeek = 3)
        val start = LocalDate.of(2026, 1, 7) // a Wednesday
        val next = t.nextDueAfter(start)
        assertEquals(LocalDate.of(2026, 1, 14), next)
        assertEquals(start.dayOfWeek, next.dayOfWeek)
    }

    @Test
    fun `daily steps by one day`() {
        val t = template(RecurringFrequency.DAILY)
        assertEquals(LocalDate.of(2026, 3, 1), t.nextDueAfter(LocalDate.of(2026, 2, 28)))
    }
}
