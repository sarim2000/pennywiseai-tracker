package com.pennywiseai.tracker.ui.screens.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** When the spending bar chart retargets bars in place vs crossfades the whole chart. */
class BarFrameTest {
    private fun day(d: Int) = LocalDateTime.of(2026, 3, d, 0, 0)

    private fun frame(
        vararg bars: Pair<Int, Double>,
        currency: String = "INR",
        granularity: BarGranularity = BarGranularity.DAY
    ) = BarFrame(currency, granularity, bars.map { (d, v) -> Bar(day(d), "$d", v) })

    @Test
    fun `overlapping buckets on a similar scale morph`() {
        assertTrue(frame(1 to 100.0, 2 to 50.0).morphsInto(frame(1 to 120.0, 2 to 50.0, 3 to 80.0)))
    }

    @Test
    fun `a currency change never morphs`() {
        assertFalse(frame(1 to 100.0).morphsInto(frame(1 to 100.0, currency = "USD")))
    }

    @Test
    fun `a bucket size change never morphs`() {
        assertFalse(frame(1 to 100.0).morphsInto(frame(1 to 100.0, granularity = BarGranularity.MONTH)))
    }

    @Test
    fun `disjoint buckets crossfade`() {
        assertFalse(frame(1 to 100.0).morphsInto(frame(2 to 100.0)))
    }

    @Test
    fun `an axis more than 2x away crossfades`() {
        assertFalse(frame(1 to 100.0).morphsInto(frame(1 to 250.0)))
        assertFalse(frame(1 to 100.0).morphsInto(frame(1 to 40.0)))
        assertTrue(frame(1 to 100.0).morphsInto(frame(1 to 200.0)))
    }

    @Test
    fun `transitions to and from an all-zero frame crossfade`() {
        assertFalse(frame(1 to 0.0).morphsInto(frame(1 to 500.0)))
        assertFalse(frame(1 to 500.0).morphsInto(frame(1 to 0.0)))
    }

    @Test
    fun `epochs bump only on incompatible frames and repeat calls are stable`() {
        val epochs = BarEpochs()
        val a = frame(1 to 100.0)
        assertEquals(0, epochs.advance(a).epoch)
        assertEquals(0, epochs.advance(a).epoch)
        assertEquals(0, epochs.advance(frame(1 to 150.0, 2 to 10.0)).epoch)
        assertEquals(1, epochs.advance(frame(1 to 150.0, currency = "USD")).epoch)
        assertEquals(2, epochs.advance(frame(9 to 150.0, currency = "USD")).epoch)
    }
}
