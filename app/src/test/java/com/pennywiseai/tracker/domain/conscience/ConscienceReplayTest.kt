package com.pennywiseai.tracker.domain.conscience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class ConscienceReplayTest {

    private val start = LocalDate.of(2025, 1, 1)
    private val weekMillis = 7 * 24 * 60 * 60 * 1000L

    /** About 4 order days a week for a year, with two breaks and occasional spikes. */
    private fun syntheticYear(): List<ConsciencePurchase> {
        val random = Random(42)
        val purchases = mutableListOf<ConsciencePurchase>()
        for (offset in 0L until 365L) {
            if (offset in 100L..112L || offset in 240L..250L) continue
            if (random.nextInt(7) >= 4) continue
            val date = start.plusDays(offset)
            val spike = random.nextInt(25) == 0
            val amount = if (spike) random.nextInt(40, 80) else random.nextInt(5, 15)
            purchases += ConsciencePurchase(
                date.atTime(random.nextInt(11, 15), 0),
                BigDecimal(amount),
                "JOD",
                ConscienceCategory.FOOD,
            )
            if (random.nextInt(12) == 0) {
                purchases += ConsciencePurchase(date.atTime(20, 0), BigDecimal(8), "JOD", ConscienceCategory.FOOD)
            }
        }
        return purchases
    }

    @Test
    fun `a year of ordering fires sometimes and stays within the weekly cap`() {
        val purchases = syntheticYear()
        var log = ConscienceLog()
        val fires = mutableListOf<Long>()
        val factTypes = mutableMapOf<String, Int>()
        purchases.forEachIndexed { index, current ->
            val nowMillis = current.at.toInstant(ZoneOffset.UTC).toEpochMilli()
            val fact = ConscienceDetectors.detect(current, purchases.take(index), start)
            if (fact != null && ConscienceCadence.allows(log, ConscienceFrequency.NORMAL, nowMillis)) {
                log = ConscienceCadence.record(log, "line", nowMillis, current.at.toLocalDate().toEpochDay())
                fires += nowMillis
                factTypes.merge(fact::class.simpleName.orEmpty(), 1, Int::plus)
            }
        }

        println("Conscience replay: ${fires.size} fires over 365 days, ${purchases.size} purchases, $factTypes")
        assertTrue(fires.isNotEmpty())
        for (fire in fires) {
            val inWeek = fires.count { it >= fire && it - fire < weekMillis }
            assertTrue("$inWeek fires in the week from $fire", inWeek <= 3)
        }
    }

    @Test
    fun `a year of a fixed Mon-Thu routine never reads as a comeback`() {
        val purchases = (0L until 365L).map { start.plusDays(it) }
            .filter { it.dayOfWeek.value in 1..4 }
            .map { ConsciencePurchase(it.atTime(13, 0), BigDecimal(10), "JOD", ConscienceCategory.FOOD) }
        val comebacks = purchases.indices.count { index ->
            ConscienceDetectors.detect(purchases[index], purchases.take(index), start) is ConscienceFact.Comeback
        }
        assertEquals(0, comebacks)
    }
}
