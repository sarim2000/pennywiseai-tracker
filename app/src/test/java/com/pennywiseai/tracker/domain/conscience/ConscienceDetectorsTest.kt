package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.ENTERTAINMENT
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.FOOD
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.SHOPPING
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.LateNight
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Positive
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class ConscienceDetectorsTest {

    private val today = LocalDate.of(2026, 6, 15) // a Monday
    private val longLedger = today.minusDays(365)
    private var nextId = 1L

    private fun buy(
        at: LocalDateTime,
        amount: String = "10",
        currency: String = "JOD",
        category: ConscienceCategory = FOOD,
        merchant: String = "Cafe A",
        recurring: Boolean = false,
        id: Long = nextId++,
    ) = ConsciencePurchase(at, BigDecimal(amount), currency, category, id, merchant, recurring)

    private fun on(
        date: LocalDate,
        amount: String = "10",
        hour: Int = 12,
        minute: Int = 0,
        currency: String = "JOD",
        category: ConscienceCategory = FOOD,
        recurring: Boolean = false,
    ) = buy(date.atTime(hour, minute), amount, currency, category, recurring = recurring)

    /** The purchase being judged. Its id sorts after every fixture's, so same-time fixtures count as earlier. */
    private fun now(
        amount: String = "10",
        hour: Int = 12,
        minute: Int = 0,
        date: LocalDate = today,
        currency: String = "JOD",
        category: ConscienceCategory = FOOD,
        merchant: String = "Cafe A",
    ) = buy(date.atTime(hour, minute), amount, currency, category, merchant, id = CURRENT_ID)

    private fun detect(
        history: List<ConsciencePurchase>,
        current: ConsciencePurchase = now(),
        ledgerStart: LocalDate? = longLedger,
        active: Set<LocalDate> = emptySet(),
    ) = ConscienceDetectors.detect(current, history, ledgerStart, active)

    private inline fun <reified T : ConscienceFact> List<ConscienceFact>.find(): T? = filterIsInstance<T>().firstOrNull()

    private fun daysAgo(days: Long): LocalDate = today.minusDays(days)

    // --- Shape of the result ---

    @Test
    fun `no history proves nothing`() {
        assertEquals(emptyList<ConscienceFact>(), detect(emptyList()))
        assertEquals(emptyList<ConscienceFact>(), detect(emptyList(), now(category = SHOPPING), ledgerStart = null))
    }

    @Test
    fun `every fact that holds comes back in priority order`() {
        val history = listOf(on(today, hour = 20), on(today, hour = 21), on(today, hour = 22))
        assertEquals(
            listOf(Repeat(FOOD, 4, 6), LateNight(FOOD)),
            detect(history, now(hour = 23, minute = 30)),
        )
    }

    @Test
    fun `a same-time purchase is earlier only with a lower id, a later one never counts`() {
        val at = today.atTime(20, 0)
        val earlier = listOf(on(today, hour = 15))
        assertNull(detect(earlier + buy(at, id = CURRENT_ID + 1), now(hour = 20)).find<Repeat>())
        assertNull(detect(earlier + on(today, hour = 21), now(hour = 20)).find<Repeat>())
        assertEquals(
            Repeat(FOOD, 3, 6),
            detect(earlier + on(today, hour = 17) + buy(at.minusMinutes(0), id = CURRENT_ID + 1), now(hour = 20)).find<Repeat>(),
        )
    }

    // --- Repeat ---

    @Test
    fun `three distinct purchases in six hours repeat, two do not`() {
        assertEquals(
            Repeat(FOOD, 3, 6),
            detect(listOf(on(today, hour = 15), on(today, hour = 18)), now(hour = 20)).find<Repeat>(),
        )
        assertNull(detect(listOf(on(today, hour = 18)), now(hour = 20)).find<Repeat>())
    }

    @Test
    fun `the six hour window includes its start and no more`() {
        assertEquals(
            Repeat(FOOD, 3, 6),
            detect(listOf(on(today, hour = 14), on(today, hour = 19)), now(hour = 20)).find<Repeat>(),
        )
        // Three in 24 hours is short of the 24 hour bar of four.
        assertNull(detect(listOf(on(today, hour = 13, minute = 59), on(today, hour = 19)), now(hour = 20)).find<Repeat>())
    }

    @Test
    fun `four in twenty-four hours repeat over the day, from the window's start`() {
        val sameDay = listOf(on(today, hour = 8), on(today, hour = 19))
        assertEquals(
            Repeat(FOOD, 4, 24),
            detect(sameDay + on(daysAgo(1), hour = 20), now(hour = 20)).find<Repeat>(),
        )
        assertNull(detect(sameDay + on(daysAgo(1), hour = 19, minute = 59), now(hour = 20)).find<Repeat>())
    }

    @Test
    fun `purchases within half an hour are one purchase alerted twice`() {
        val quick = listOf(on(today, hour = 19, minute = 31), on(today, hour = 19, minute = 45))
        assertNull(detect(quick, now(hour = 20)).find<Repeat>())
        val spread = listOf(on(today, hour = 19), on(today, hour = 19, minute = 30))
        assertEquals(Repeat(FOOD, 3, 6), detect(spread, now(hour = 20)).find<Repeat>())
    }

    @Test
    fun `alerts minutes apart count as one purchase in a run`() {
        val duplicates = listOf(on(today, hour = 18), on(today, hour = 18, minute = 1))
        assertNull(detect(duplicates, now(hour = 19)).find<Repeat>())
        assertEquals(Repeat(FOOD, 3, 6), detect(duplicates + on(today, hour = 17), now(hour = 19)).find<Repeat>())
    }

    @Test
    fun `the repeat window counts elapsed hours across a DST change`() {
        val newYork = java.time.ZoneId.of("America/New_York")
        // Clocks fell back at 02:00 on 1 Nov 2026: 00:30 to 06:30 is seven hours.
        val fallBack = LocalDate.of(2026, 11, 1)
        val history = listOf(on(fallBack, hour = 0, minute = 30), on(fallBack, hour = 4, minute = 30))
        val current = now(hour = 6, minute = 30, date = fallBack)
        assertNull(ConscienceDetectors.detect(current, history, longLedger, zone = newYork).find<Repeat>())
        val inside = listOf(on(fallBack, hour = 1, minute = 30), on(fallBack, hour = 4, minute = 30))
        assertEquals(Repeat(FOOD, 3, 6), ConscienceDetectors.detect(current, inside, longLedger, zone = newYork).find<Repeat>())
    }

    @Test
    fun `split parts of one transaction count once`() {
        val at = today.atTime(18, 0)
        assertNull(detect(listOf(buy(at, id = 500), buy(at, id = 500)), now(hour = 20)).find<Repeat>())
    }

    @Test
    fun `recurring charges and other categories are no repeat, other currencies are`() {
        val recurring = listOf(on(today, hour = 15, recurring = true), on(today, hour = 18, recurring = true))
        assertNull(detect(recurring, now(hour = 20)).find<Repeat>())
        val shopping = listOf(on(today, hour = 15, category = SHOPPING), on(today, hour = 18))
        assertNull(detect(shopping, now(hour = 20)).find<Repeat>())
        val usd = listOf(on(today, hour = 15, currency = "USD"), on(today, hour = 18))
        assertEquals(Repeat(FOOD, 3, 6), detect(usd, now(hour = 20)).find<Repeat>())
    }

    // --- Late night ---

    @Test
    fun `late night runs from 23_00 up to 05_00`() {
        fun lateAt(hour: Int, minute: Int) = detect(emptyList(), now(hour = hour, minute = minute)).find<LateNight>()
        assertNull(lateAt(22, 59))
        assertEquals(LateNight(FOOD), lateAt(23, 0))
        assertEquals(LateNight(FOOD), lateAt(0, 0))
        assertEquals(LateNight(FOOD), lateAt(4, 59))
        assertNull(lateAt(5, 0))
    }

    private val streaming = "Streaming B"

    private fun renewal(date: LocalDate, hour: Int, minute: Int, amount: String = "9.99", merchant: String = streaming, currency: String = "JOD") =
        buy(date.atTime(hour, minute), amount, currency, ENTERTAINMENT, merchant)

    private fun lateRenewal(history: List<ConsciencePurchase>, merchant: String = streaming) =
        detect(history, now("9.99", hour = 23, minute = 30, category = ENTERTAINMENT, merchant = merchant)).find<LateNight>()

    @Test
    fun `a nightly renewal on its cycle is not a late night purchase`() {
        val monthly = listOf(renewal(daysAgo(30), 23, 10), renewal(daysAgo(60), 23, 50))
        assertNull(lateRenewal(monthly))
        assertNull(lateRenewal(listOf(renewal(daysAgo(7), 23, 30), renewal(daysAgo(14), 23, 30))))
        // Merchant names compare by their letters and digits.
        assertNull(lateRenewal(listOf(renewal(daysAgo(30), 23, 30, merchant = "STREAMING-B"), renewal(daysAgo(60), 23, 30))))
    }

    @Test
    fun `a renewal split on earlier cycles still matches the whole charge`() {
        fun split(date: LocalDate) = renewal(date, 23, 30, "6").copy(charge = BigDecimal("9.99"))
        assertNull(lateRenewal(listOf(split(daysAgo(30)), split(daysAgo(60)))))
    }

    @Test
    fun `renewal clock times compare across midnight`() {
        assertNull(lateRenewal(listOf(renewal(daysAgo(29), 0, 20), renewal(daysAgo(59), 0, 10))))
    }

    @Test
    fun `a late purchase that only looks like a renewal still counts`() {
        val expected = LateNight(ENTERTAINMENT)
        assertEquals(expected, lateRenewal(listOf(renewal(daysAgo(30), 23, 30))))
        assertEquals(expected, lateRenewal(listOf(renewal(daysAgo(30), 22, 29), renewal(daysAgo(60), 23, 30))))
        assertEquals(expected, lateRenewal(listOf(renewal(daysAgo(30), 23, 30, "10.60"), renewal(daysAgo(60), 23, 30, "10.60"))))
        assertEquals(expected, lateRenewal(listOf(renewal(daysAgo(30), 23, 30), renewal(daysAgo(37), 23, 30))))
        assertEquals(expected, lateRenewal(listOf(renewal(daysAgo(30), 23, 30, currency = "USD"), renewal(daysAgo(60), 23, 30, currency = "USD"))))
        assertEquals(
            expected,
            lateRenewal(listOf(renewal(daysAgo(30), 23, 30, merchant = ""), renewal(daysAgo(60), 23, 30, merchant = "")), merchant = ""),
        )
    }

    // --- Positive ---

    private val wednesday = today.plusDays(2)

    /** Week [index] before the current one: 0 is last week, 1..4 its baseline. */
    private fun positiveWeekStart(index: Int): LocalDate = today.minusWeeks(index + 1L)

    private fun pWeek(index: Int, vararg amounts: String, currency: String = "JOD") =
        amounts.mapIndexed { day, amount -> on(positiveWeekStart(index).plusDays(2L * day), amount, currency = currency) }

    private val everyDay: Set<LocalDate> =
        generateSequence(positiveWeekStart(4)) { it.plusDays(1) }.takeWhile { !it.isAfter(wednesday) }.toSet()

    private val usualWeeks get() = (1..4).flatMap { pWeek(it, "50", "50") }

    private fun positive(
        history: List<ConsciencePurchase>,
        active: Set<LocalDate> = everyDay,
        ledgerStart: LocalDate = longLedger,
        current: ConsciencePurchase = now(date = wednesday),
    ) = detect(history, current, ledgerStart, active).find<Positive>()

    @Test
    fun `last week 20 percent under the usual week is a win`() {
        assertEquals(Positive(FOOD, 20), positive(usualWeeks + pWeek(0, "80")))
        assertNull(positive(usualWeeks + pWeek(0, "80.01")))
    }

    @Test
    fun `last week means the Monday to Sunday week before this one`() {
        val history = usualWeeks + pWeek(0, "80")
        assertEquals(Positive(FOOD, 20), positive(history, current = now(date = today)))
        val sunday = today.plusDays(6)
        assertEquals(Positive(FOOD, 20), positive(history, everyDay + today.plusDays(3), current = now(date = sunday)))
    }

    @Test
    fun `last week must be under at least three baseline weeks`() {
        val baseline = pWeek(1, "39.5", "39.5") + pWeek(2, "39.5", "39.5") + pWeek(3, "60", "60") + pWeek(4, "60", "60")
        assertNull(positive(baseline + pWeek(0, "79.5")))
        assertEquals(Positive(FOOD, 21), positive(baseline + pWeek(0, "78")))
    }

    @Test
    fun `no baseline week may carry over 40 percent of the baseline`() {
        val lumpy = (1..3).flatMap { pWeek(it, "20", "20") } + pWeek(4, "45", "45")
        assertNull(positive(lumpy + pWeek(0, "30")))
        val atLimit = (1..3).flatMap { pWeek(it, "20", "20") } + pWeek(4, "40", "40")
        assertEquals(Positive(FOOD, 25), positive(atLimit + pWeek(0, "30")))
    }

    @Test
    fun `three baseline weeks need spending on two days`() {
        val oneThin = (1..3).flatMap { pWeek(it, "50", "50") } + pWeek(4, "100")
        assertEquals(Positive(FOOD, 20), positive(oneThin + pWeek(0, "80")))
        val twoThin = (1..2).flatMap { pWeek(it, "50", "50") } + (3..4).flatMap { pWeek(it, "100") }
        assertNull(positive(twoThin + pWeek(0, "80")))
    }

    @Test
    fun `an empty week is no win`() {
        assertNull(positive(usualWeeks))
    }

    @Test
    fun `the ledger must reach the oldest baseline week`() {
        val history = usualWeeks + pWeek(0, "80")
        assertEquals(Positive(FOOD, 20), positive(history, ledgerStart = positiveWeekStart(4)))
        assertNull(positive(history, ledgerStart = positiveWeekStart(4).plusDays(1)))
    }

    @Test
    fun `any second currency in the five weeks is no win`() {
        val history = usualWeeks + pWeek(0, "80")
        assertEquals(Positive(FOOD, 20), positive(history))
        assertNull(positive(history + on(positiveWeekStart(0).plusDays(5), "5", currency = "USD")))
        // The same mix on both sides still can't be called "less on food": the
        // cheaper JOD week could hide a far dearer USD one.
        val mixed = history + on(positiveWeekStart(0).plusDays(5), "5000", currency = "USD") +
            on(positiveWeekStart(2).plusDays(5), "5", currency = "USD")
        assertNull(positive(mixed))
        assertNull(positive(history, current = now(date = wednesday, currency = "USD")))
    }

    @Test
    fun `a quiet week only counts while the ledger kept being fed`() {
        val history = usualWeeks + pWeek(0, "80")
        assertNull(positive(history, active = emptySet()))
        val lastWeek = (0L..6L).map { positiveWeekStart(0).plusDays(it) }
        // Baseline weeks have 7 active days each: last week needs 5.25.
        assertNull(positive(history, everyDay - lastWeek.take(2).toSet()))
        assertEquals(Positive(FOOD, 20), positive(history, everyDay - lastWeek.take(1).toSet()))
        // At least 3 active days whatever the baseline.
        val sparse = (1..4).flatMap { index -> listOf(0L, 2L).map { positiveWeekStart(index).plusDays(it) } }.toSet()
        assertNull(positive(history, sparse + lastWeek.take(2)))
        assertEquals(Positive(FOOD, 20), positive(history, sparse + lastWeek.take(3)))
    }

    @Test
    fun `percent is rounded down`() {
        assertEquals(Positive(FOOD, 66), positive(usualWeeks + pWeek(0, "33.333")))
        assertEquals(Positive(FOOD, 99), positive(usualWeeks + pWeek(0, "0.5")))
    }

    private companion object {
        const val CURRENT_ID = 1_000_000L
    }
}
