package com.pennywiseai.tracker.domain.conscience

import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.ENTERTAINMENT
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.FOOD
import com.pennywiseai.tracker.domain.conscience.ConscienceCategory.SHOPPING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

class ConscienceReplayTest {

    private val start = LocalDate.of(2025, 1, 1)
    private val dayMillis = 24 * 60 * 60 * 1000L
    private val weekMillis = 7 * dayMillis
    private val historyDays = 95L
    private val renewalMerchant = "Streaming B"

    private class Year(val purchases: List<ConsciencePurchase>, val activeDates: Set<LocalDate>)

    /**
     * A year of one person's spending: lunch most weekdays and some weekends,
     * the odd evening order, binge evenings and late snacks, a monthly late-night
     * renewal, weekend outings, shopping, two breaks, a lean month, and a trip
     * paid in another currency.
     */
    private fun syntheticYear(): Year {
        val random = Random(42)
        val raw = mutableListOf<ConsciencePurchase>()
        fun add(at: LocalDateTime, amount: Int, category: ConscienceCategory, currency: String = "JOD", merchant: String = "Cafe A") {
            raw += ConsciencePurchase(at, BigDecimal(amount), currency, category, 0, merchant)
        }
        val active = mutableSetOf<LocalDate>()
        for (offset in 0L until 365L) {
            val date = start.plusDays(offset)
            if (random.nextInt(10) < 7) active += date
            // The renewal bills through breaks and trips alike.
            if (date.dayOfMonth == 3) add(date.atTime(23, 45), 10, ENTERTAINMENT, "JOD", renewalMerchant)
            if (offset in 100L..112L || offset in 240L..250L) continue
            val trip = offset in 180L..190L
            val currency = if (trip) "USD" else "JOD"
            val lean = offset in 300L..330L
            val weekend = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY

            val lunchOdds = when {
                lean -> 3
                weekend -> 4
                else -> 7
            }
            if (random.nextInt(10) < lunchOdds) {
                add(date.atTime(random.nextInt(11, 15), random.nextInt(60)), random.nextInt(5, 15), FOOD, currency, "Cafe A")
            }
            if (date.dayOfWeek == DayOfWeek.THURSDAY && random.nextInt(10) < 8) {
                add(date.atTime(19, random.nextInt(60)), random.nextInt(8, 20), FOOD, currency, "Grill C")
            }
            if (random.nextInt(14) == 0) {
                // A binge evening: three orders a couple of hours apart.
                for (hour in listOf(18, 20, 22)) add(date.atTime(hour, random.nextInt(60)), random.nextInt(6, 18), FOOD, currency, "Diner D")
            }
            if (random.nextInt(20) == 0) add(date.atTime(23, random.nextInt(10, 60)), random.nextInt(4, 10), FOOD, currency, "Kiosk E")
            if (weekend && random.nextInt(3) == 0) {
                add(date.atTime(random.nextInt(17, 22), random.nextInt(60)), random.nextInt(8, 25), ENTERTAINMENT, currency, "Venue F")
            }
            if (random.nextInt(6) == 0) {
                val big = random.nextInt(30) == 0
                add(date.atTime(random.nextInt(10, 21), random.nextInt(60)), if (big) random.nextInt(150, 300) else random.nextInt(10, 60), SHOPPING, currency, "Store G")
            }
            if (trip && random.nextInt(2) == 0) {
                add(date.atTime(random.nextInt(15, 21), random.nextInt(60)), random.nextInt(20, 60), SHOPPING, "USD", "Store H")
            }
        }
        val purchases = raw.sortedBy { it.at }.mapIndexed { index, p -> p.copy(transactionId = index + 1L) }
        return Year(purchases, active + purchases.map { it.at.toLocalDate() })
    }

    private fun windowOf(purchases: List<ConsciencePurchase>, index: Int): List<ConsciencePurchase> {
        val current = purchases[index]
        val from = current.at.toLocalDate().minusDays(historyDays).atStartOfDay()
        // The manager reads through tomorrow; the detectors drop what came after.
        val until = current.at.toLocalDate().plusDays(2).atStartOfDay()
        return purchases.filterIndexed { i, p -> i != index && !p.at.isBefore(from) && p.at.isBefore(until) }
    }

    private fun pool(kind: ConscienceKind) = List(8) { ConscienceLine("${kind.name}_$it", 0) }

    @Test
    fun `a year of spending gives sound facts and a sensible number of lines`() {
        val year = syntheticYear()
        val purchases = year.purchases
        val detected = mutableMapOf<ConscienceKind, Int>()
        val fired = mutableMapOf<ConscienceKind, Int>()
        val fireTimes = mutableListOf<Long>()
        var log = ConscienceLog()
        val random = Random(7)

        purchases.indices.forEach { index ->
            val current = purchases[index]
            val today = current.at.toLocalDate()
            val history = windowOf(purchases, index)
            val active = year.activeDates.filter { !it.isAfter(today) }.toSet()
            val facts = ConscienceDetectors.detect(current, history, start, active)

            assertEquals(ConscienceFact.Hello(current.category), facts.last())
            assertEquals(facts.sortedBy { it.kind.ordinal }, facts)
            assertEquals(facts.size, facts.map { it.kind }.distinct().size)
            assertTrue(facts.all { it.category == current.category })

            // Scaling every other currency's amounts by 1000 must change nothing:
            // no fact may depend on money in a currency other than the purchase's.
            val skewed = history.map { if (it.currency == current.currency) it else it.copy(amount = it.amount * BigDecimal(1000)) }
            assertEquals("${current.at}", facts, ConscienceDetectors.detect(current, skewed, start, active))

            if (current.merchant == renewalMerchant && today.isAfter(start.plusDays(70))) {
                assertTrue("renewal on $today read as late night", facts.none { it is ConscienceFact.LateNight })
            }

            facts.forEach { detected.merge(it.kind, 1, Int::plus) }
            val nowMillis = current.at.toInstant(ZoneOffset.UTC).toEpochMilli()
            val choice = ConscienceCadence.choose(
                facts.map { ConscienceCandidate(it, pool(it.kind)) },
                log,
                ConscienceFeedback(),
                ConscienceFrequency.NORMAL,
                "en",
                nowMillis,
                today.toEpochDay(),
                random,
            )
            if (choice != null) {
                log = ConscienceCadence.record(log, choice, nowMillis, today.toEpochDay())
                fired.merge(choice.fact.kind, 1, Int::plus)
                fireTimes += nowMillis
            }
        }

        val detectedFacts = detected.filterKeys { it != ConscienceKind.HELLO }
        val firedFacts = fired.filterKeys { it != ConscienceKind.HELLO }.values.sum()
        println(
            "Conscience replay: ${purchases.size} purchases, detected ${detected.toSortedMap()}, " +
                "fired ${fireTimes.size} ${fired.toSortedMap()}",
        )

        for (fire in fireTimes) {
            val inWeek = fireTimes.count { it >= fire && it - fire < weekMillis }
            assertTrue("$inWeek fires in the week from $fire", inWeek <= 3)
            assertTrue(fireTimes.count { it >= fire && it - fire < dayMillis } <= 1)
        }
        // Every kind but the rarest shows up, and facts speak about once a week.
        assertTrue("only ${detectedFacts.keys} detected", detectedFacts.keys.size >= 6)
        assertTrue("$firedFacts fact lines fired", firedFacts in 30..100)
    }

    @Test
    fun `a year of a fixed Mon-Thu routine never reads as a comeback`() {
        val purchases = (0L until 365L).map { start.plusDays(it) }
            .filter { it.dayOfWeek.value in 1..4 }
            .mapIndexed { index, date ->
                ConsciencePurchase(date.atTime(13, 0), BigDecimal(10), "JOD", FOOD, index + 1L, "Cafe A")
            }
        val comebacks = purchases.indices.count { index ->
            ConscienceDetectors.detect(purchases[index], windowOf(purchases, index), start)
                .any { it is ConscienceFact.Comeback }
        }
        assertEquals(0, comebacks)
    }
}
