package com.pennywiseai.tracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Axis values snap to a coarse grid, and typefaces come from a bounded cache. */
class GoogleSansFlexAxesTest {

    @Test
    fun valuesSnapToTheGrid() {
        assertEquals(40, quantizeRoundness(36f))
        assertEquals(100, quantizeRoundness(130f))
        assertEquals(0, quantizeGrade(-20f))
        assertEquals(60, quantizeGrade(57f))
        assertEquals(22, quantizeOpticalSize(21f))
        assertEquals(36, quantizeOpticalSize(40f))
        assertEquals(57, quantizeOpticalSize(200f))
    }

    @Test
    fun smallSizesKeepTheDefaultCut() {
        // Below 18 opsz widens advances, so small text keeps the cut it had.
        listOf(8f, 11f, 12f, 14f, 16f, 18f, 19f).forEach { assertEquals("$it", 18, quantizeOpticalSize(it)) }
    }

    @Test
    fun typeScaleSizesAboveTheDefaultAreGridStops() {
        listOf(22, 24, 28, 32, 36, 45, 57).forEach { assertEquals(it, quantizeOpticalSize(it.toFloat())) }
    }

    @Test
    fun theCacheIsBoundedAndKeepsWhatItHolds() {
        val cache = BoundedCache<Int, Any>(2)
        val one = cache.getOrPut(1) { Any() }
        assertSame(one, cache.getOrPut(1) { Any() })
        cache.getOrPut(2) { Any() }
        cache.getOrPut(3) { Any() } // evicts 1, the least recently used
        assertNotSame(one, cache.getOrPut(1) { Any() })
    }
}
