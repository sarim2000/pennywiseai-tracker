package com.pennywiseai.tracker.domain.spending

import com.pennywiseai.tracker.domain.review.CycleTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CycleAveragesTest {
    private fun d(v: String) = BigDecimal(v)

    private fun cycle(month: Int, income: String, spent: String) = CycleTotal(
        start = LocalDate.of(2026, month, 1),
        end = LocalDate.of(2026, month, 1).plusMonths(1).minusDays(1),
        income = d(income),
        spent = d(spent),
        byCategory = emptyMap(),
    )

    @Test
    fun `mean needs every one of the three cycles`() {
        assertNull(CycleAverages.mean(emptyList()))
        assertNull(CycleAverages.mean(listOf(d("1"), d("2"))))
        assertNull(CycleAverages.mean(listOf(d("1"), null, d("2"))))
        assertEquals(d("2"), CycleAverages.mean(listOf(d("1"), d("2"), d("3"))))
        // Only the three most recent count.
        assertEquals(d("2"), CycleAverages.mean(listOf(d("1"), d("2"), d("3"), null)))
    }

    @Test
    fun `mean rounds half up at the asked scale`() {
        assertEquals(d("2"), CycleAverages.mean(listOf(d("1"), d("2"), d("2"))))
        assertEquals(d("1.67"), CycleAverages.mean(listOf(d("1"), d("2"), d("2")), scale = 2))
    }

    /** Home's forecast reads the same "usual" it did before the averaging moved here. */
    @Test
    fun `home forecast usual is unchanged`() {
        val start = LocalDate.of(2026, 11, 1)
        val end = LocalDate.of(2026, 11, 30)
        val today = LocalDate.of(2026, 11, 10)
        fun usual(vararg previous: String?) =
            SpendForecast.of(d("5000"), "INR", start, end, today, previous.map { it?.let(::d) })!!.usual

        assertEquals(d("30000"), usual("28000", "30000", "32000"))
        assertEquals(d("10001"), usual("10000", "10001", "10001.50"))
        assertEquals(d("2"), usual("1", "2", "2"))
        assertNull(usual("28000", null, "32000"))
        assertNull(usual("28000", "30000"))
        assertEquals(SpendForecast.USUAL_PERIODS, CycleAverages.CYCLES)
    }

    @Test
    fun `averages skip the current cycle and need three active ones before it`() {
        val averages = CycleAverages.of(
            mapOf(
                "INR" to listOf(
                    cycle(7, "0", "999"),
                    cycle(8, "50000", "30000"),
                    cycle(9, "50000", "36000"),
                    cycle(10, "56000", "33000"),
                    // The cycle under way: half a month mustn't drag the average.
                    cycle(11, "0", "1000"),
                ),
                // Only two cycles of history.
                "USD" to listOf(cycle(9, "0", "0"), cycle(10, "100", "40"), cycle(11, "100", "40")),
            )
        )
        val inr = averages.getValue("INR")
        assertEquals(d("52000.00"), inr.income)
        assertEquals(d("33000.00"), inr.spent)
        assertEquals(d("19000.00"), inr.surplus)
        assertTrue("USD" !in averages)
    }

    @Test
    fun `a quiet cycle in between means no usual yet`() {
        val averages = CycleAverages.of(
            mapOf("INR" to listOf(cycle(8, "100", "50"), cycle(9, "0", "0"), cycle(10, "100", "50"), cycle(11, "0", "0")))
        )
        assertTrue(averages.isEmpty())
    }

    @Test
    fun `spending more than earning gives a negative surplus`() {
        val inr = CycleAverages.of(
            mapOf("INR" to listOf(cycle(8, "100", "150"), cycle(9, "100", "150"), cycle(10, "100", "150"), cycle(11, "0", "0")))
        ).getValue("INR")
        assertEquals(d("-50.00"), inr.surplus)
    }
}
