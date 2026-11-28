package com.pennywiseai.tracker.data.trip

import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionGroupEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.GroupSummary
import com.pennywiseai.tracker.data.repository.groupTotalsOf
import com.pennywiseai.tracker.utils.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class TripSummaryTest {
    private val start = LocalDateTime.of(2026, 12, 1, 9, 30)
    private val end = LocalDateTime.of(2026, 12, 5, 14, 0)
    private val trip = TransactionGroupEntity(id = 7, name = "Synthetic trip", tripStart = start, tripEnd = end)

    private fun tx(
        amount: String,
        category: String = "Food",
        currency: String = "INR",
        type: TransactionType = TransactionType.EXPENSE,
        at: LocalDateTime = start.plusDays(1),
        hash: String = "h$amount$category$currency"
    ) = TransactionEntity(
        amount = BigDecimal(amount), merchantName = "Synthetic shop", category = category,
        transactionType = type, dateTime = at, transactionHash = hash, currency = currency
    )

    @Test
    fun daysCountBothEndsInclusive() {
        assertEquals(5, tripTotalDays(trip))
        assertEquals(1, tripTotalDays(trip.copy(tripEnd = start.plusHours(1))))
        assertNull(tripTotalDays(trip.copy(tripEnd = null)))
    }

    @Test
    fun runningTripCountsUpToToday() {
        assertEquals(1, tripDaysSoFar(trip, start))
        assertEquals(3, tripDaysSoFar(trip, LocalDateTime.of(2026, 12, 3, 0, 5)))
        // Open-ended: keeps counting.
        assertEquals(10, tripDaysSoFar(trip.copy(tripEnd = null), LocalDateTime.of(2026, 12, 10, 23, 0)))
    }

    @Test
    fun endedTripStopsAtItsEndDay() {
        assertEquals(5, tripDaysSoFar(trip, LocalDateTime.of(2027, 3, 1, 0, 0)))
        // Stopped mid-day: that day counts, later days don't.
        val stopped = trip.copy(tripEnd = LocalDateTime.of(2026, 12, 2, 18, 0))
        assertEquals(2, tripDaysSoFar(stopped, LocalDateTime.of(2026, 12, 20, 0, 0)))
    }

    @Test
    fun scheduledAndPlainGroupsHaveNoDays() {
        assertEquals(0, tripDaysSoFar(trip, start.minusNanos(1)))
        assertEquals(0, tripDaysSoFar(TransactionGroupEntity(name = "plain"), start))
    }

    @Test
    fun averageIsPerCurrencyAndGuardsZeroDays() {
        val totals = mapOf("INR" to Money(BigDecimal("1000"), "INR"), "USD" to Money(BigDecimal("10"), "USD"))
        val avg = averagePerDay(totals, 3)
        assertEquals(BigDecimal("333.33"), avg.getValue("INR").amount)
        assertEquals(BigDecimal("3.33"), avg.getValue("USD").amount)
        assertEquals("USD", avg.getValue("USD").currency)
        assertTrue(averagePerDay(totals, 0).isEmpty())
    }

    @Test
    fun summaryKeepsCurrenciesApartAndLeavesIncomeOut() {
        val rows = listOf(
            tx("300", "Food"), tx("100", "Food"), tx("50", "Travel"),
            tx("20", "Food", currency = "USD"),
            tx("999", "Salary", type = TransactionType.INCOME)
        )
        val s = tripSummaryOf(trip, rows, LocalDateTime.of(2026, 12, 4, 12, 0))
        assertEquals(setOf("INR", "USD"), s.totalByCurrency.keys)
        assertEquals(BigDecimal("450"), s.totalByCurrency.getValue("INR").amount)
        assertEquals(BigDecimal("20"), s.totalByCurrency.getValue("USD").amount)
        assertEquals(4, s.daysSoFar)
        assertEquals(5, s.totalDays)
        assertEquals(BigDecimal("112.50"), s.averagePerDay.getValue("INR").amount)
        assertEquals(listOf("Food", "Travel"), s.topCategories.getValue("INR").map { it.category })
        assertEquals(BigDecimal("400"), s.topCategories.getValue("INR").first().amount.amount)
        assertEquals(listOf("Food"), s.topCategories.getValue("USD").map { it.category })
        assertEquals(groupTotalsOf(rows).expense, s.totalByCurrency)
    }

    @Test
    fun topCategoriesAreCapped() {
        val rows = listOf("A", "B", "C", "D").mapIndexed { i, c -> tx("${10 * (i + 1)}", c) }
        assertEquals(listOf("D", "C", "B"), topCategoriesByCurrency(rows).getValue("INR").map { it.category })
        assertEquals(setOf("INR"), topCategoriesByCurrency(rows).getValue("INR").map { it.amount.currency }.toSet())
    }

    @Test
    fun noSpendingGivesNoTotals() {
        assertTrue(tripSummaryOf(trip, emptyList(), start.plusDays(1)).totalByCurrency.isEmpty())
    }

    private fun summary(group: TransactionGroupEntity) = GroupSummary(group, 0, emptyMap(), emptyMap())

    @Test
    fun chipOnlyWhileRunning() {
        val now = LocalDateTime.of(2026, 12, 3, 12, 0)
        val plain = summary(TransactionGroupEntity(id = 1, name = "plain"))
        val ended = summary(TransactionGroupEntity(id = 2, name = "ended", tripStart = start.minusDays(30), tripEnd = start.minusDays(20)))
        val scheduled = summary(TransactionGroupEntity(id = 3, name = "later", tripStart = now.plusDays(2)))
        val running = summary(trip)
        assertNull(runningTripOf(listOf(plain, ended, scheduled), now))
        assertEquals(7L, runningTripOf(listOf(plain, ended, running, scheduled), now)?.group?.id)
        // It appears and disappears with time, not with data.
        assertNull(runningTripOf(listOf(running), start.minusNanos(1)))
        assertEquals(7L, runningTripOf(listOf(running), end)?.group?.id)
        assertNull(runningTripOf(listOf(running), end.plusNanos(1)))
    }

    @Test
    fun chipTotalIsPerCurrency() {
        val rows = listOf(tx("300"), tx("20", currency = "USD"))
        val totals = groupTotalsOf(rows)
        val chip = GroupSummary(trip, rows.size, totals.expense, totals.income)
        assertEquals(setOf("INR", "USD"), chip.expenseByCurrency.keys)
    }

    @Test
    fun backfillTakesOnlyWhatWouldHaveJoined() {
        val inside = tx("10", hash = "in")
        val rows = listOf(
            inside,
            tx("11", hash = "grouped").copy(groupId = 3),
            tx("12", hash = "loan").copy(loanId = 4),
            tx("13", hash = "recurring").copy(isRecurring = true),
            tx("14", hash = "income", type = TransactionType.INCOME),
            tx("15", hash = "card", type = TransactionType.CREDIT),
            tx("16", hash = "before", at = start.minusNanos(1)),
            tx("17", hash = "after", at = end.plusNanos(1)),
            tx("18", hash = "removed")
        )
        val picked = backfillRowsFor(rows, trip, excluded = setOf("removed")).map { it.transactionHash }
        assertEquals(listOf("in", "card"), picked)
    }

    @Test
    fun backfillIsOfferedForNewTripsAndEarlierStartsOnly() {
        assertTrue(tripStartMovedEarlier(null, trip))
        assertTrue(tripStartMovedEarlier(TransactionGroupEntity(name = "plain"), trip))
        assertTrue(tripStartMovedEarlier(trip, trip.copy(tripStart = start.minusDays(2))))
        assertEquals(false, tripStartMovedEarlier(trip, trip))
        assertEquals(false, tripStartMovedEarlier(trip, trip.copy(tripStart = start.plusDays(1))))
        assertEquals(false, tripStartMovedEarlier(trip, trip.copy(tripStart = null, tripEnd = null)))
    }
}
