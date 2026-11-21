package com.pennywiseai.tracker.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class WeeklyGuessTest {
    private val lastStart = LocalDate.parse("2026-11-09")

    /** One currency's weeks, oldest first, the last being the one asked about. */
    private fun weeks(vararg byCategory: Map<String, String>) = byCategory.mapIndexed { i, categories ->
        val start = lastStart.minusWeeks((byCategory.size - 1 - i).toLong())
        val amounts = categories.mapValues { BigDecimal(it.value) }
        CycleTotal(start, start.plusDays(6), BigDecimal.ZERO, amounts.values.fold(BigDecimal.ZERO, BigDecimal::add), amounts)
    }

    private fun question(
        weeks: Map<String, List<CycleTotal>>,
        rows: Map<String, Int> = emptyMap(),
        preferred: String? = null,
    ) = weeklyGuessQuestion(weeks, rows, food = "Food & Dining", others = setOf("Others"), preferred = preferred)

    @Test
    fun `asks about food in Home's currency when it has some`() {
        val q = question(
            mapOf(
                "JOD" to weeks(mapOf("Food & Dining" to "40", "Shopping" to "300")),
                "USD" to weeks(mapOf("Food & Dining" to "90")),
            ),
            rows = mapOf("USD" to 9, "JOD" to 2),
            preferred = "JOD",
        )!!
        assertEquals("Food & Dining", q.category)
        assertEquals("JOD", q.currency)
        assertEquals(BigDecimal("40"), q.actual)
        assertEquals(lastStart, q.weekStart)
    }

    @Test
    fun `without food in Home's currency, the currency with the most rows`() {
        val q = question(
            mapOf(
                "JOD" to weeks(mapOf("Shopping" to "300")),
                "USD" to weeks(mapOf("Food & Dining" to "90")),
                "EUR" to weeks(mapOf("Food & Dining" to "15")),
            ),
            rows = mapOf("USD" to 2, "EUR" to 5),
            preferred = "JOD",
        )!!
        assertEquals("Food & Dining", q.category)
        assertEquals("EUR", q.currency)
    }

    @Test
    fun `without food, the biggest category that isn't Others`() {
        val q = question(
            mapOf("JOD" to weeks(mapOf("Others" to "900", "Transport" to "20", "Shopping" to "60"))),
            preferred = "JOD",
        )!!
        assertEquals("Shopping", q.category)
        assertEquals(BigDecimal("60"), q.actual)
    }

    @Test
    fun `nothing to ask about without spending outside Others`() {
        assertNull(question(mapOf("JOD" to weeks(mapOf("Others" to "50")))))
        assertNull(question(mapOf("JOD" to weeks(mapOf("Food & Dining" to "0")))))
        assertNull(question(emptyMap()))
    }

    @Test
    fun `food last week only counts the week asked about`() {
        val q = question(
            mapOf("JOD" to weeks(mapOf("Food & Dining" to "70"), mapOf("Shopping" to "30"))),
        )!!
        assertEquals("Shopping", q.category)
    }

    @Test
    fun `slider tops out at twice a usual week, rounded up nicely`() {
        // Four weeks before averaging 80: twice is 160, rounded up to 200.
        val q = question(
            mapOf(
                "USD" to weeks(
                    mapOf("Food & Dining" to "60"),
                    mapOf("Food & Dining" to "100"),
                    mapOf("Food & Dining" to "70"),
                    mapOf("Food & Dining" to "90"),
                    mapOf("Food & Dining" to "50"),
                )
            ),
        )!!
        assertEquals(BigDecimal("200"), q.max)
        assertEquals(BigDecimal("5"), q.step)
        assertEquals(listOf("25", "50", "100", "150").map(::BigDecimal), q.chips)
    }

    @Test
    fun `a tiny week, or one far below a big history, still has a pick that hits`() {
        fun reachable(q: WeeklyGuessQuestion) = sliderStops(q.max, q.step, q.actual).any { isCloseGuess(it, q.actual) }
        val tiny = question(mapOf("USD" to weeks(mapOf("Food & Dining" to "0.40"))))!!
        assertTrue(reachable(tiny))
        assertTrue(tiny.chips.all { it <= tiny.max })
        val quiet = question(
            mapOf("USD" to weeks(mapOf("Food & Dining" to "1000"), mapOf("Food & Dining" to "1000"), mapOf("Food & Dining" to "12"))),
        )!!
        assertTrue(reachable(quiet))
    }

    @Test
    fun `an ordinary week keeps every whole step`() {
        val q = question(
            mapOf(
                "USD" to weeks(
                    mapOf("Food & Dining" to "60"),
                    mapOf("Food & Dining" to "100"),
                    mapOf("Food & Dining" to "70"),
                    mapOf("Food & Dining" to "90"),
                    mapOf("Food & Dining" to "50"),
                )
            ),
        )!!
        val stops = sliderStops(q.max, q.step, q.actual)
        assertEquals((0..40).map { BigDecimal(it * 5) }, stops)
    }

    @Test
    fun `a tiny week after a large history stays within the slider's stop limit and can still hit`() {
        val q = question(
            mapOf(
                "USD" to weeks(
                    mapOf("Food & Dining" to "100000"),
                    mapOf("Food & Dining" to "100000"),
                    mapOf("Food & Dining" to "0.01"),
                )
            ),
        )!!
        val stops = sliderStops(q.max, q.step, q.actual)
        assertTrue(stops.size <= MAX_SLIDER_POSITIONS)
        assertEquals(BigDecimal.ZERO, stops.first())
        assertEquals(q.max, stops.last())
        assertTrue(stops.any { isCloseGuess(it, q.actual) })
        assertTrue(stops.drop(1).dropLast(1).filter { it >= BigDecimal.ONE }.all { it.scale() <= 0 })
    }

    @Test
    fun `without history the slider is twice last week`() {
        assertEquals(BigDecimal("150"), guessMax(BigDecimal("70"), emptyList()))
        // Weeks with no spending in the category don't pull it to nothing.
        assertEquals(BigDecimal("150"), guessMax(BigDecimal("70"), listOf(BigDecimal.ZERO)))
    }

    @Test
    fun `slider always leaves room above the answer`() {
        // A usual week of 10, but 300 this week: the top is 1.5x the answer, not 20.
        assertEquals(BigDecimal("500"), guessMax(BigDecimal("300"), listOf(BigDecimal("10"))))
    }

    @Test
    fun `nice figures`() {
        assertEquals(BigDecimal("200"), niceCeil(BigDecimal("160.00")))
        assertEquals(BigDecimal("1500"), niceCeil(BigDecimal("1234")))
        assertEquals(BigDecimal("1000"), niceCeil(BigDecimal("1000")))
        assertEquals(BigDecimal("8"), niceCeil(BigDecimal("7.5")))
        assertEquals(BigDecimal("50"), guessStep(BigDecimal("1500")))
        assertEquals(BigDecimal("1"), guessStep(BigDecimal("8")))
    }

    @Test
    fun `chips never repeat or show zero on a tiny slider`() {
        val q = question(mapOf("JOD" to weeks(mapOf("Food & Dining" to "1"))))!!
        assertEquals(q.chips, q.chips.distinct())
        assertTrue(q.chips.all { it.signum() > 0 })
    }

    @Test
    fun `within 15 percent is a hit`() {
        val actual = BigDecimal("200")
        assertTrue(isCloseGuess(BigDecimal("230"), actual))
        assertTrue(isCloseGuess(BigDecimal("170"), actual))
        assertFalse(isCloseGuess(BigDecimal("231"), actual))
        assertFalse(isCloseGuess(BigDecimal("100"), actual))
        assertEquals(15, guessGapPercent(BigDecimal("230"), actual))
        assertEquals(50, guessGapPercent(BigDecimal("100"), actual))
    }
}
