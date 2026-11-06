package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.ENTERTAINMENT
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.FOOD
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.SHOPPING
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Comeback
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Hello
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.LateNight
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Positive
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.RecordDay
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Repeat
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Spike
import com.pennywiseai.tracker.domain.conscience.ConscienceFact.Weekly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
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
    fun `no history still says hello, and only hello`() {
        assertEquals(listOf(Hello(FOOD)), detect(emptyList()))
        assertEquals(listOf(Hello(SHOPPING)), detect(emptyList(), now(category = SHOPPING), ledgerStart = null))
    }

    @Test
    fun `every fact that holds comes back in priority order with hello last`() {
        val history = recordBase + listOf(
            on(today, "40", hour = 20),
            on(today, "40", hour = 21),
            on(today, "40", hour = 22),
        )
        assertEquals(
            listOf(Repeat(FOOD, 4, 6), LateNight(FOOD), RecordDay(FOOD), Hello(FOOD)),
            detect(history, now("10", hour = 23, minute = 30)),
        )
    }

    @Test
    fun `a same-time purchase is earlier only with a lower id, a later one never counts`() {
        val at = today.atTime(20, 0)
        val earlier = on(today, hour = 18)
        assertEquals(Repeat(FOOD, 3, 6), detect(listOf(earlier, buy(at, id = CURRENT_ID - 1)), now(hour = 20)).find<Repeat>())
        assertNull(detect(listOf(earlier, buy(at, id = CURRENT_ID + 1)), now(hour = 20)).find<Repeat>())
        assertNull(detect(listOf(earlier, on(today, hour = 21)), now(hour = 20)).find<Repeat>())
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
        val spread = listOf(on(today, hour = 19, minute = 30), on(today, hour = 19, minute = 45))
        assertEquals(Repeat(FOOD, 3, 6), detect(spread, now(hour = 20)).find<Repeat>())
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

    // --- Spike ---

    private fun spikeWeekStart(index: Int): LocalDate = today.minusDays(6L + 7L * index)

    /** Baseline week [index] (1..8), one purchase per day from its first day. */
    private fun week(index: Int, vararg amounts: String, currency: String = "JOD") =
        amounts.mapIndexed { day, amount -> on(spikeWeekStart(index).plusDays(day.toLong()), amount, currency = currency) }

    /** Eight weeks of 100 over 2 days: the bar is 1.8 x 100 = 180. */
    private val steadyWeeks get() = (1..8).flatMap { week(it, "50", "50") }

    private val spikeWeek get() = listOf(on(daysAgo(3), "60"), on(daysAgo(1), "60"))

    private fun spike(history: List<ConsciencePurchase>, amount: String = "61", ledgerStart: LocalDate = longLedger) =
        detect(history, now(amount), ledgerStart).find<Spike>()

    @Test
    fun `the week crossing the bar with this purchase is a spike`() {
        assertEquals(Spike(FOOD), spike(steadyWeeks + spikeWeek))
        assertNull(spike(steadyWeeks + spikeWeek, amount = "60"))
    }

    @Test
    fun `a week already over the bar stays quiet`() {
        val atBar = listOf(on(daysAgo(3), "90"), on(daysAgo(1), "90"))
        assertEquals(Spike(FOOD), spike(steadyWeeks + atBar, amount = "1"))
        val overBar = listOf(on(daysAgo(3), "90"), on(daysAgo(1), "90.01"))
        assertNull(spike(steadyWeeks + overBar, amount = "1"))
    }

    @Test
    fun `the ledger must cover all eight baseline weeks`() {
        assertEquals(Spike(FOOD), spike(steadyWeeks + spikeWeek, ledgerStart = daysAgo(62)))
        assertNull(spike(steadyWeeks + spikeWeek, ledgerStart = daysAgo(61)))
    }

    @Test
    fun `the baseline needs 12 active days`() {
        val twelve = (1..4).flatMap { week(it, "50", "50") } + (5..8).flatMap { week(it, "100") }
        assertEquals(Spike(FOOD), spike(twelve + spikeWeek))
        val eleven = (1..3).flatMap { week(it, "50", "50") } + (4..8).flatMap { week(it, "100") }
        assertNull(spike(eleven + spikeWeek))
    }

    @Test
    fun `the baseline needs 6 of 8 weeks with spending`() {
        assertEquals(Spike(FOOD), spike((1..6).flatMap { week(it, "50", "50") } + spikeWeek))
        // The median would still be 100, yet five weeks are too few.
        assertNull(spike((1..5).flatMap { week(it, "34", "33", "33") } + spikeWeek))
    }

    @Test
    fun `a high baseline week lifts the bar to 1_15 x its total`() {
        val history = (1..7).flatMap { week(it, "50", "50") } + week(8, "100", "100") // bar 230
        assertNull(spike(history + spikeWeek))
        val bigger = listOf(on(daysAgo(3), "80"), on(daysAgo(1), "80"))
        assertNull(spike(history + bigger, amount = "70"))
        assertEquals(Spike(FOOD), spike(history + bigger, amount = "71"))
    }

    @Test
    fun `an uneven baseline lifts the bar by three deviations`() {
        // Median 100, deviation 50: the bar is 250.
        val history = (1..8).flatMap { if (it % 2 == 1) week(it, "75", "75") else week(it, "25", "25") }
        val week0 = listOf(on(daysAgo(3), "100"), on(daysAgo(1), "100"))
        assertNull(spike(history + week0, amount = "50"))
        assertEquals(Spike(FOOD), spike(history + week0, amount = "50.01"))
    }

    @Test
    fun `a spike takes three purchases over two days`() {
        assertNull(spike(steadyWeeks + on(daysAgo(1), "90.5"), amount = "90.5"))
        assertNull(spike(steadyWeeks + on(today, "60", hour = 8) + on(today, "60", hour = 9)))
    }

    @Test
    fun `one purchase over half the week is a big buy, not a spike`() {
        assertNull(spike(steadyWeeks + on(daysAgo(3), "30") + on(daysAgo(2), "30"), amount = "121"))
    }

    @Test
    fun `two high weeks running are a new level, not a spike`() {
        val raised = (1..2).flatMap { week(it, "75", "75") } + (3..8).flatMap { week(it, "50", "50") }
        assertNull(spike(raised + spikeWeek))
        val oneHigh = week(1, "75", "75") + (2..8).flatMap { week(it, "50", "50") }
        assertEquals(Spike(FOOD), spike(oneHigh + spikeWeek))
    }

    @Test
    fun `spike totals never mix currencies or categories`() {
        val noise = week(1, "5000", currency = "USD") + on(daysAgo(2), "500", currency = "USD") +
            on(daysAgo(2), "500", category = SHOPPING)
        assertEquals(Spike(FOOD), spike(steadyWeeks + spikeWeek + noise))
        val usdBaseline = (1..8).flatMap { week(it, "50", "50", currency = "USD") }
        assertNull(spike(usdBaseline + spikeWeek))
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

    private val lastWeekEpochDay = positiveWeekStart(0).toEpochDay()

    @Test
    fun `last week 20 percent under the usual week is a win`() {
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(usualWeeks + pWeek(0, "80")))
        assertNull(positive(usualWeeks + pWeek(0, "80.01")))
    }

    @Test
    fun `last week means the Monday to Sunday week before this one`() {
        val history = usualWeeks + pWeek(0, "80")
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(history, current = now(date = today)))
        val sunday = today.plusDays(6)
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(history, everyDay + today.plusDays(3), current = now(date = sunday)))
    }

    @Test
    fun `last week must be under at least three baseline weeks`() {
        val baseline = pWeek(1, "39.5", "39.5") + pWeek(2, "39.5", "39.5") + pWeek(3, "60", "60") + pWeek(4, "60", "60")
        assertNull(positive(baseline + pWeek(0, "79.5")))
        assertEquals(Positive(FOOD, 21, lastWeekEpochDay), positive(baseline + pWeek(0, "78")))
    }

    @Test
    fun `no baseline week may carry over 40 percent of the baseline`() {
        val lumpy = (1..3).flatMap { pWeek(it, "20", "20") } + pWeek(4, "45", "45")
        assertNull(positive(lumpy + pWeek(0, "30")))
        val atLimit = (1..3).flatMap { pWeek(it, "20", "20") } + pWeek(4, "40", "40")
        assertEquals(Positive(FOOD, 25, lastWeekEpochDay), positive(atLimit + pWeek(0, "30")))
    }

    @Test
    fun `three baseline weeks need spending on two days`() {
        val oneThin = (1..3).flatMap { pWeek(it, "50", "50") } + pWeek(4, "100")
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(oneThin + pWeek(0, "80")))
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
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(history, ledgerStart = positiveWeekStart(4)))
        assertNull(positive(history, ledgerStart = positiveWeekStart(4).plusDays(1)))
    }

    @Test
    fun `a change in the currencies used is no win`() {
        val history = usualWeeks + pWeek(0, "80")
        assertNull(positive(history + on(positiveWeekStart(0).plusDays(5), "5", currency = "USD")))
        // The same currency mix both sides is fine, and the other currency's amounts stay out.
        val mixed = history + on(positiveWeekStart(0).plusDays(5), "500", currency = "USD") +
            on(positiveWeekStart(2).plusDays(5), "5", currency = "USD")
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(mixed))
        assertNull(positive(history, current = now(date = wednesday, currency = "USD")))
    }

    @Test
    fun `a quiet week only counts while the ledger kept being fed`() {
        val history = usualWeeks + pWeek(0, "80")
        assertNull(positive(history, active = emptySet()))
        val lastWeek = (0L..6L).map { positiveWeekStart(0).plusDays(it) }
        // Baseline weeks have 7 active days each: last week needs 5.25.
        assertNull(positive(history, everyDay - lastWeek.take(2).toSet()))
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(history, everyDay - lastWeek.take(1).toSet()))
        // At least 3 active days whatever the baseline.
        val sparse = (1..4).flatMap { index -> listOf(0L, 2L).map { positiveWeekStart(index).plusDays(it) } }.toSet()
        assertNull(positive(history, sparse + lastWeek.take(2)))
        assertEquals(Positive(FOOD, 20, lastWeekEpochDay), positive(history, sparse + lastWeek.take(3)))
    }

    @Test
    fun `percent is rounded down`() {
        assertEquals(Positive(FOOD, 66, lastWeekEpochDay), positive(usualWeeks + pWeek(0, "33.333")))
        assertEquals(Positive(FOOD, 99, lastWeekEpochDay), positive(usualWeeks + pWeek(0, "0.5")))
    }

    // --- Weekly ---

    private fun weeksBack(weeks: Long, day: DayOfWeek): LocalDate = today.minusWeeks(weeks).plusDays(day.value - 1L)

    private fun onDays(day: DayOfWeek, weeks: Iterable<Long>, currency: String = "JOD", recurring: Boolean = false) =
        weeks.map { on(weeksBack(it, day), currency = currency, recurring = recurring) }

    private val mondays4 get() = onDays(DayOfWeek.MONDAY, 1L..4L)

    private fun weekly(history: List<ConsciencePurchase>, current: ConsciencePurchase = now()) =
        detect(history, current).find<Weekly>()

    private val mondayFact = Weekly(FOOD, DayOfWeek.MONDAY)

    @Test
    fun `a weekday active in 4 of the last 6 weeks is a habit`() {
        assertEquals(mondayFact, weekly(mondays4 + onDays(DayOfWeek.WEDNESDAY, 1L..4L)))
        assertNull(weekly(onDays(DayOfWeek.MONDAY, 1L..3L) + onDays(DayOfWeek.WEDNESDAY, 1L..5L)))
    }

    @Test
    fun `a habit needs 8 active days in the six weeks`() {
        assertNull(weekly(mondays4 + onDays(DayOfWeek.WEDNESDAY, 1L..3L)))
    }

    @Test
    fun `the weekday must beat the typical other day by two weeks`() {
        fun others(vararg counts: Pair<DayOfWeek, Int>) = counts.flatMap { (day, n) -> onDays(day, 1L..n.toLong()) }
        val median25 = others(
            DayOfWeek.TUESDAY to 3, DayOfWeek.WEDNESDAY to 3, DayOfWeek.THURSDAY to 3,
            DayOfWeek.FRIDAY to 2, DayOfWeek.SATURDAY to 2, DayOfWeek.SUNDAY to 2,
        )
        assertNull(weekly(mondays4 + median25))
        assertEquals(mondayFact, weekly(onDays(DayOfWeek.MONDAY, 1L..5L) + median25))
        val median2 = others(
            DayOfWeek.TUESDAY to 3, DayOfWeek.WEDNESDAY to 3, DayOfWeek.THURSDAY to 2,
            DayOfWeek.FRIDAY to 2, DayOfWeek.SATURDAY to 2, DayOfWeek.SUNDAY to 2,
        )
        assertEquals(mondayFact, weekly(mondays4 + median2))
        // Every day alike is no weekday habit.
        assertNull(weekly(DayOfWeek.entries.flatMap { onDays(it, 1L..6L) }))
    }

    @Test
    fun `only the six complete weeks before this one count`() {
        val wednesdays = onDays(DayOfWeek.WEDNESDAY, 1L..5L)
        assertNull(weekly(onDays(DayOfWeek.MONDAY, listOf(1L, 2L, 3L, 7L)) + wednesdays))
        assertEquals(mondayFact, weekly(onDays(DayOfWeek.MONDAY, listOf(1L, 2L, 3L, 6L)) + wednesdays))
        // Earlier days of this week are not part of the six.
        val thursday = today.plusDays(3)
        val habit = onDays(DayOfWeek.THURSDAY, 1L..4L) + onDays(DayOfWeek.WEDNESDAY, 1L..3L)
        val thisWeek = (0L..2L).map { on(today.plusDays(it)) }
        assertNull(weekly(habit + thisWeek, now(date = thursday)))
        assertEquals(
            Weekly(FOOD, DayOfWeek.THURSDAY),
            weekly(habit + thisWeek + on(weeksBack(4, DayOfWeek.WEDNESDAY)), now(date = thursday)),
        )
    }

    @Test
    fun `only the first purchase of the day is a habit`() {
        val history = mondays4 + onDays(DayOfWeek.WEDNESDAY, 1L..4L)
        assertNull(weekly(history + on(today, hour = 8)))
        // A recurring charge earlier today doesn't take the first slot.
        assertEquals(mondayFact, weekly(history + on(today, hour = 8, recurring = true)))
    }

    @Test
    fun `weeks count once however many purchases, recurring charges not at all`() {
        val wednesdays = onDays(DayOfWeek.WEDNESDAY, 1L..5L)
        val twice = onDays(DayOfWeek.MONDAY, 1L..3L).flatMap { listOf(it, it.copy(at = it.at.withHour(19), transactionId = nextId++)) }
        assertNull(weekly(twice + wednesdays))
        assertNull(weekly(onDays(DayOfWeek.MONDAY, 1L..4L, recurring = true) + wednesdays))
    }

    @Test
    fun `a weekday habit spans currencies`() {
        val mixed = onDays(DayOfWeek.MONDAY, 1L..2L) + onDays(DayOfWeek.MONDAY, 3L..4L, currency = "USD")
        assertEquals(mondayFact, weekly(mixed + onDays(DayOfWeek.WEDNESDAY, 1L..4L)))
    }

    // --- Comeback ---

    /** Purchases walking backward from [last] by the given day gaps. */
    private fun fromGaps(last: LocalDate, gaps: List<Int>, amount: String = "10") =
        gaps.runningFold(last) { date, gap -> date.minusDays(gap.toLong()) }.map { on(it, amount) }

    private fun everyNDays(last: LocalDate, step: Int, count: Int, amount: String = "10") =
        fromGaps(last, List(count - 1) { step }, amount)

    private fun comeback(
        history: List<ConsciencePurchase>,
        current: ConsciencePurchase = now(),
        ledgerStart: LocalDate? = longLedger,
    ) = detect(history, current, ledgerStart).find<Comeback>()

    @Test
    fun `daily orderer is silent at gap 2 and fires at exactly 3`() {
        assertNull(comeback(everyNDays(daysAgo(2), 1, 30)))
        assertEquals(Comeback(FOOD, 3), comeback(everyNDays(daysAgo(3), 1, 30)))
    }

    @Test
    fun `a Mon-Thu routine is no comeback on Monday, only past its longest gap`() {
        val routine = (1L..56L).map { today.minusDays(it) }
            .filter { it.dayOfWeek.value in 1..4 }
            .map { on(it) }
        assertNull(comeback(routine, now()))

        val lastThursday = routine.maxOf { it.at.toLocalDate() }
        assertNull(comeback(routine, now(date = lastThursday.plusDays(4))))
        assertEquals(Comeback(FOOD, 5), comeback(routine, now(date = lastThursday.plusDays(5))))
    }

    @Test
    fun `median of 3 is silent at 8 and fires at 9`() {
        assertNull(comeback(everyNDays(daysAgo(8), 3, 21)))
        assertEquals(Comeback(FOOD, 9), comeback(everyNDays(daysAgo(9), 3, 21)))
    }

    @Test
    fun `even gap count averages the middle two and rounds the threshold up`() {
        val gaps = List(5) { 1 } + List(5) { 2 } // median 1.5, threshold ceil(4.5) = 5
        assertNull(comeback(fromGaps(daysAgo(4), gaps)))
        assertEquals(Comeback(FOOD, 5), comeback(fromGaps(daysAgo(5), gaps)))
    }

    @Test
    fun `threshold of exactly 30 fires, above 30 stays silent`() {
        val median10 = List(4) { 1 } + List(5) { 10 }
        assertEquals(Comeback(FOOD, 30), comeback(fromGaps(daysAgo(30), median10)))

        val median11 = List(4) { 1 } + List(5) { 11 } // threshold 33, never lowered to the cap
        assertNull(comeback(fromGaps(daysAgo(30), median11)))
    }

    @Test
    fun `gap of 31 is silent`() {
        assertNull(comeback(everyNDays(daysAgo(31), 1, 30)))
    }

    @Test
    fun `needs 8 baseline days`() {
        assertNull(comeback(everyNDays(daysAgo(3), 1, 7)))
        assertEquals(Comeback(FOOD, 3), comeback(everyNDays(daysAgo(3), 1, 8)))
    }

    @Test
    fun `baseline reaches exactly 60 days back from the last active date`() {
        val last = daysAgo(21)
        val seven = listOf(0L, 1, 2, 3, 4, 20, 40).map { on(last.minusDays(it)) }
        assertEquals(Comeback(FOOD, 21), comeback(seven + on(last.minusDays(60))))
        assertNull(comeback(seven + on(last.minusDays(61))))
    }

    @Test
    fun `purchase earlier today means no comeback, one later today is ignored`() {
        val history = everyNDays(daysAgo(3), 1, 30)
        assertNull(comeback(history + on(today, hour = 8)))
        assertEquals(Comeback(FOOD, 3), comeback(history + on(today, hour = 20)))
    }

    @Test
    fun `a break in a category is measured across currencies`() {
        val usd = everyNDays(daysAgo(3), 1, 30).map { it.copy(currency = "USD") }
        assertEquals(Comeback(FOOD, 3), comeback(usd))
        assertNull(comeback(everyNDays(daysAgo(3), 1, 30) + on(daysAgo(1), currency = "USD")))
    }

    @Test
    fun `other category history does not count, a recurring charge does`() {
        val shopping = everyNDays(daysAgo(3), 1, 30).map { it.copy(category = SHOPPING) }
        assertNull(comeback(shopping))
        val food = everyNDays(daysAgo(3), 1, 30)
        assertEquals(Comeback(FOOD, 3), comeback(food + on(daysAgo(1), category = SHOPPING)))
        assertNull(comeback(food + on(daysAgo(1), recurring = true)))
    }

    @Test
    fun `several purchases on one day do not distort the median`() {
        fun triples(last: LocalDate) = everyNDays(last, 3, 21).flatMap { p ->
            listOf(p, p.copy(at = p.at.withHour(9), transactionId = nextId++), p.copy(at = p.at.withHour(20), transactionId = nextId++))
        }
        assertNull(comeback(triples(daysAgo(8))))
        assertEquals(Comeback(FOOD, 9), comeback(triples(daysAgo(9))))
    }

    @Test
    fun `a comeback needs no ledger age, only the active days`() {
        val history = everyNDays(daysAgo(3), 1, 30)
        assertEquals(Comeback(FOOD, 3), comeback(history, ledgerStart = daysAgo(32)))
        assertEquals(Comeback(FOOD, 3), comeback(history, ledgerStart = null))
    }

    // --- Record day ---

    /** Eight active days ending yesterday, the best at 100: the bar is 120. */
    private val recordBase get() = (1L..8L).map { on(daysAgo(it), "50") } + on(daysAgo(2), "50", hour = 18)

    private fun record(
        history: List<ConsciencePurchase>,
        amount: String,
        ledgerStart: LocalDate = longLedger,
    ) = detect(history, now(amount), ledgerStart).find<RecordDay>()

    @Test
    fun `today passing 1_2 x the best day is a record`() {
        val earlier = recordBase + on(today, "60", hour = 9)
        assertEquals(RecordDay(FOOD), record(earlier, "60.01"))
        assertNull(record(earlier, "60"))
    }

    @Test
    fun `sitting exactly on the bar before still fires, past it stays quiet`() {
        val onBar = recordBase + on(today, "60", hour = 8) + on(today, "60", hour = 9)
        assertEquals(RecordDay(FOOD), record(onBar, "1"))
        val pastBar = recordBase + on(today, "60", hour = 8) + on(today, "60.01", hour = 9)
        assertNull(record(pastBar, "1"))
    }

    @Test
    fun `one purchase alone is no record day`() {
        assertNull(record(recordBase, "500"))
    }

    @Test
    fun `today's purchases must spread over half an hour`() {
        assertNull(record(recordBase + on(today, "60", hour = 11, minute = 31), "60.01"))
        assertEquals(RecordDay(FOOD), record(recordBase + on(today, "60", hour = 11, minute = 30), "60.01"))
    }

    @Test
    fun `no purchase may carry over 60 percent of the day`() {
        assertNull(record(recordBase + on(today, "40", hour = 9), "90"))
        assertEquals(RecordDay(FOOD), record(recordBase + on(today, "52", hour = 9), "78"))
    }

    @Test
    fun `split parts of one transaction are one purchase of the day`() {
        val at = today.atTime(9, 0)
        assertNull(record(recordBase + buy(at, "40", id = 500) + buy(at, "40", id = 500), "41"))
        assertEquals(RecordDay(FOOD), record(recordBase + on(today, "40", hour = 9) + on(today, "40", hour = 10), "41"))
    }

    @Test
    fun `needs 8 active days in the window`() {
        val seven = (1L..7L).map { on(daysAgo(it), "50") } + on(daysAgo(2), "50", hour = 18)
        assertNull(record(seven + on(today, "60", hour = 9), "60.01"))
        assertEquals(RecordDay(FOOD), record(seven + on(daysAgo(30), "50") + on(today, "60", hour = 9), "60.01"))
    }

    @Test
    fun `the ledger must cover the 60 days`() {
        val history = recordBase + on(today, "60", hour = 9)
        assertEquals(RecordDay(FOOD), record(history, "60.01", ledgerStart = daysAgo(60)))
        assertNull(record(history, "60.01", ledgerStart = daysAgo(59)))
    }

    @Test
    fun `window covers 60 days back and no further`() {
        val base = (1L..8L).map { on(daysAgo(it), "50") } + on(today, "31", hour = 9) // bar 60
        assertEquals(RecordDay(FOOD), record(base + on(daysAgo(61), "500"), "30"))
        assertNull(record(base + on(daysAgo(60), "500"), "30"))
    }

    @Test
    fun `record totals never mix currencies or categories`() {
        val noise = listOf(
            on(daysAgo(3), "99999", currency = "USD"),
            on(today, "1000", hour = 10, currency = "USD"),
            on(today, "1000", hour = 10, category = SHOPPING),
        )
        assertEquals(RecordDay(FOOD), record(recordBase + on(today, "60", hour = 9) + noise, "60.01"))
        val sevenJodOneUsd = (1L..7L).map { on(daysAgo(it), "50") } + on(daysAgo(8), "50", currency = "USD")
        assertNull(record(sevenJodOneUsd + on(today, "60", hour = 9), "60.01"))
    }

    private companion object {
        const val CURRENT_ID = 1_000_000L
    }
}
