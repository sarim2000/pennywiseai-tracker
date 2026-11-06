package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Comeback
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.RecordDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ConscienceDetectorsTest {

    private val today = LocalDate.of(2026, 6, 15)
    private val longLedger = today.minusDays(365)

    private fun purchase(
        date: LocalDate,
        amount: String = "10",
        currency: String = "JOD",
        category: ConscienceCategory = ConscienceCategory.FOOD,
        hour: Int = 12,
    ) = ConsciencePurchase(date.atTime(hour, 0), BigDecimal(amount), currency, category)

    /** Purchases walking backward from [last] by the given day gaps. */
    private fun fromGaps(last: LocalDate, gaps: List<Int>, amount: String = "10") =
        gaps.runningFold(last) { date, gap -> date.minusDays(gap.toLong()) }.map { purchase(it, amount) }

    private fun everyNDays(last: LocalDate, step: Int, count: Int, amount: String = "10") =
        fromGaps(last, List(count - 1) { step }, amount)

    private fun detect(
        history: List<ConsciencePurchase>,
        current: ConsciencePurchase = purchase(today),
        ledgerStart: LocalDate? = longLedger,
    ) = ConscienceDetectors.detect(current, history, ledgerStart)

    private fun daysAgo(days: Long): LocalDate = today.minusDays(days)

    // --- Comeback ---

    @Test
    fun `daily orderer is silent at gap 2 and fires at exactly 3`() {
        assertNull(detect(everyNDays(daysAgo(2), 1, 30)))
        assertEquals(Comeback(ConscienceCategory.FOOD, 3), detect(everyNDays(daysAgo(3), 1, 30)))
    }

    @Test
    fun `a Mon-Thu routine is no comeback on Monday, only past its longest gap`() {
        val monday = LocalDate.of(2026, 6, 15)
        val routine = (1L..56L).map { monday.minusDays(it) }
            .filter { it.dayOfWeek.value in 1..4 }
            .map { purchase(it) }
        assertNull(detect(routine, purchase(monday)))

        val lastThursday = routine.maxOf { it.at.toLocalDate() }
        assertNull(detect(routine, purchase(lastThursday.plusDays(4))))
        assertEquals(
            Comeback(ConscienceCategory.FOOD, 5),
            detect(routine, purchase(lastThursday.plusDays(5))),
        )
    }

    @Test
    fun `median of 3 is silent at 8 and fires at 9`() {
        assertNull(detect(everyNDays(daysAgo(8), 3, 21)))
        assertEquals(Comeback(ConscienceCategory.FOOD, 9), detect(everyNDays(daysAgo(9), 3, 21)))
    }

    @Test
    fun `even gap count averages the middle two and rounds the threshold up`() {
        val gaps = List(5) { 1 } + List(5) { 2 } // median 1_5, threshold ceil(4_5) = 5
        assertNull(detect(fromGaps(daysAgo(4), gaps)))
        assertEquals(Comeback(ConscienceCategory.FOOD, 5), detect(fromGaps(daysAgo(5), gaps)))
    }

    @Test
    fun `threshold of exactly 30 fires, above 30 stays silent`() {
        val median10 = List(4) { 1 } + List(5) { 10 }
        assertEquals(Comeback(ConscienceCategory.FOOD, 30), detect(fromGaps(daysAgo(30), median10)))

        val median11 = List(4) { 1 } + List(5) { 11 } // threshold 33, never lowered to the cap
        assertNull(detect(fromGaps(daysAgo(30), median11)))
    }

    @Test
    fun `gap of 31 is silent`() {
        assertNull(detect(everyNDays(daysAgo(31), 1, 30)))
    }

    @Test
    fun `needs 10 baseline days`() {
        assertNull(detect(everyNDays(daysAgo(3), 1, 9)))
        assertEquals(Comeback(ConscienceCategory.FOOD, 3), detect(everyNDays(daysAgo(3), 1, 10)))
    }

    @Test
    fun `baseline reaches exactly 60 days back from the last active date`() {
        val last = daysAgo(21)
        val nine = listOf(0L, 1, 2, 3, 4, 5, 20, 40, 50).map { purchase(last.minusDays(it)) }
        assertEquals(Comeback(ConscienceCategory.FOOD, 21), detect(nine + purchase(last.minusDays(60))))
        assertNull(detect(nine + purchase(last.minusDays(61))))
    }

    @Test
    fun `purchase earlier today means no comeback, one later today is ignored`() {
        val history = everyNDays(daysAgo(3), 1, 30)
        // Small enough that today's total stays under the max, so record day is silent too.
        assertNull(detect(history + purchase(today, hour = 8, amount = "1"), purchase(today, amount = "5")))
        assertEquals(Comeback(ConscienceCategory.FOOD, 3), detect(history + purchase(today, hour = 20)))
    }

    @Test
    fun `a break in a category is measured across currencies`() {
        val usd = everyNDays(daysAgo(3), 1, 30).map { it.copy(currency = "USD") }
        assertEquals(Comeback(ConscienceCategory.FOOD, 3), detect(usd))
        // A recent purchase in another currency means there was no break at all.
        val jod = everyNDays(daysAgo(3), 1, 30)
        assertNull(detect(jod + purchase(daysAgo(1), currency = "USD")))
    }

    @Test
    fun `other category history does not count`() {
        val shopping = everyNDays(daysAgo(3), 1, 30).map { it.copy(category = ConscienceCategory.SHOPPING) }
        assertNull(detect(shopping))
        val food = everyNDays(daysAgo(3), 1, 30)
        assertEquals(
            Comeback(ConscienceCategory.FOOD, 3),
            detect(food + purchase(daysAgo(1), category = ConscienceCategory.SHOPPING)),
        )
    }

    @Test
    fun `several purchases on one day do not distort the median`() {
        // Counted per purchase the median gap would be 0 and the threshold 3.
        fun triples(last: LocalDate) = everyNDays(last, 3, 21).flatMap { p ->
            listOf(p, p.copy(at = p.at.withHour(9)), p.copy(at = p.at.withHour(20)))
        }
        assertNull(detect(triples(daysAgo(8))))
        assertEquals(Comeback(ConscienceCategory.FOOD, 9), detect(triples(daysAgo(9))))
    }

    @Test
    fun `ledger younger than 60 days or unknown is silent`() {
        val history = everyNDays(daysAgo(3), 1, 30)
        assertNull(detect(history, ledgerStart = daysAgo(59)))
        assertNull(detect(history, ledgerStart = null))
        assertEquals(Comeback(ConscienceCategory.FOOD, 3), detect(history, ledgerStart = daysAgo(60)))
    }

    // --- Record day ---

    /** Ten active days ending yesterday, peaking at 100 two days ago. */
    private val tenDays = everyNDays(daysAgo(1), 1, 10, amount = "50") + purchase(daysAgo(2), "50")

    private fun today(amount: String, hour: Int = 12) = purchase(today, amount, hour = hour)

    @Test
    fun `equal to the max is silent, whatever the scale`() {
        assertNull(detect(tenDays, today("100.00")))
    }

    @Test
    fun `just above the max fires`() {
        assertEquals(RecordDay(ConscienceCategory.FOOD), detect(tenDays, today("100.01")))
    }

    @Test
    fun `second purchase after crossing is silent the same day`() {
        assertNull(detect(tenDays + today("100.01", hour = 9), today("5")))
    }

    @Test
    fun `crossing only through a same-day sum fires`() {
        assertEquals(RecordDay(ConscienceCategory.FOOD), detect(tenDays + today("60", hour = 9), today("50")))
    }

    @Test
    fun `sitting exactly on the max before this purchase still fires`() {
        assertEquals(RecordDay(ConscienceCategory.FOOD), detect(tenDays + today("100", hour = 9), today("1")))
    }

    @Test
    fun `needs 10 active days in the window`() {
        val nine = everyNDays(daysAgo(1), 1, 9, amount = "50")
        assertNull(detect(nine, today("1000")))
        assertEquals(RecordDay(ConscienceCategory.FOOD), detect(nine + purchase(daysAgo(10), "50"), today("1000")))
    }

    @Test
    fun `window covers 60 days back and no further`() {
        val base = everyNDays(daysAgo(1), 1, 10, amount = "50")
        assertEquals(RecordDay(ConscienceCategory.FOOD), detect(base + purchase(daysAgo(61), "500"), today("100")))
        assertNull(detect(base + purchase(daysAgo(60), "500"), today("100")))
    }

    @Test
    fun `another currency's huge day does not raise the bar or count as active`() {
        assertEquals(
            RecordDay(ConscienceCategory.FOOD),
            detect(tenDays + purchase(daysAgo(3), "99999", currency = "USD"), today("101")),
        )
        val nineJodOneUsd = everyNDays(daysAgo(1), 1, 9, amount = "50") + purchase(daysAgo(10), currency = "USD")
        assertNull(detect(nineJodOneUsd, today("1000")))
    }

    @Test
    fun `comeback wins when both qualify`() {
        val history = everyNDays(daysAgo(3), 1, 30)
        assertEquals(Comeback(ConscienceCategory.FOOD, 3), detect(history, today("1000")))
        // A big spend short of the comeback threshold is still a record.
        assertEquals(RecordDay(ConscienceCategory.FOOD), detect(everyNDays(daysAgo(2), 1, 30), today("1000")))
    }

    @Test
    fun `young ledger silences record day too`() {
        assertNull(detect(tenDays, today("1000"), ledgerStart = daysAgo(59)))
    }
}
