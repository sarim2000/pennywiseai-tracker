package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {

    /**
     * Feeds a sample every 20 ms (SENSOR_DELAY_GAME) for [untilMs]; [magnitudeAt] gives
     * the acceleration (m/s², gravity included) at each millisecond. Returns the shake count.
     */
    private fun shakes(untilMs: Long = 6_000, magnitudeAt: (Long) -> Float): Int {
        val detector = ShakeDetector()
        val start = 5_000_000_000_000L // a realistic boot-clock timestamp, not 0
        return (0..untilMs step 20).count { t ->
            detector.onSample(0f, 0f, magnitudeAt(t), start + t * 1_000_000L)
        }
    }

    private val rest = 9.81f

    /** A real shake: hard back-and-forth readings, one easing off now and then. */
    private fun shaking(t: Long) = if ((t / 20) % 5 == 4L) 11f else 22f

    @Test
    fun `resting is not a shake`() {
        assertEquals(0, shakes { rest })
    }

    @Test
    fun `a knock or the phone set down is not a shake`() {
        assertEquals(0, shakes { t -> if (t in 1_000L..1_060L) 30f else rest })
    }

    @Test
    fun `walking is not a shake`() {
        // Footfalls: ~1.5 g for 100 ms, twice a second.
        assertEquals(0, shakes { t -> if (t % 500 < 100) 15f else 8f })
    }

    @Test
    fun `half a second of hard shaking is one shake`() {
        assertEquals(1, shakes { t -> if (t in 1_000L..1_500L) shaking(t) else rest })
    }

    @Test
    fun `shaking hard for three seconds toggles once`() {
        assertEquals(1, shakes { t -> if (t in 1_000L..4_000L) 25f else rest })
    }

    @Test
    fun `shaking again after a pause toggles again`() {
        assertEquals(2, shakes { t -> if (t in 1_000L..1_500L || t in 3_000L..3_500L) shaking(t) else rest })
    }
}
