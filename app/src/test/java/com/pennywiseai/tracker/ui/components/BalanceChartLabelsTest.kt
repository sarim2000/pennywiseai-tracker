package com.pennywiseai.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceChartLabelsTest {
    @Test
    fun `a short series names every point`() {
        assertEquals(listOf(0, 1, 2), xLabelIndices(3))
        assertEquals(listOf(0, 1, 2, 3, 4), xLabelIndices(5))
    }

    @Test
    fun `a long series names a few points from the first to the last`() {
        // 93 daily points (3 months): the axis ends on the latest day, not the 7th.
        assertEquals(listOf(0, 23, 46, 69, 92), xLabelIndices(93))
        assertEquals(listOf(0, 3, 5, 8, 10), xLabelIndices(11))
    }

    @Test
    fun `a large font names fewer dates, never fewer than the first and last`() {
        // 40px dates with 8px gaps: five take 232px, so a 240px row holds them all and a 220px one four.
        assertEquals(5, xLabelsThatFit(labelWidth = 40f, available = 240f, gap = 8f))
        assertEquals(4, xLabelsThatFit(labelWidth = 40f, available = 220f, gap = 8f))
        // The same dates at 200%: three fit.
        assertEquals(3, xLabelsThatFit(labelWidth = 80f, available = 260f, gap = 8f))
        assertEquals(2, xLabelsThatFit(labelWidth = 200f, available = 260f, gap = 8f))
    }

    @Test
    fun `a large font names fewer values`() {
        // A 100px plot holds five 16px lines, but only three 32px ones.
        assertEquals(5, yTicksThatFit(plotHeight = 100f, lineHeight = 16f))
        assertEquals(3, yTicksThatFit(plotHeight = 100f, lineHeight = 32f))
        // Too short for even two still names the top and the bottom.
        assertEquals(2, yTicksThatFit(plotHeight = 10f, lineHeight = 32f))
    }

    @Test
    fun `the value axis runs from zero or the lowest point to the highest`() {
        assertEquals(0.0 to 500.0, valueRange(listOf(100.0, 500.0)))
        assertEquals(-200.0 to 300.0, valueRange(listOf(-200.0, 300.0)))
        // A steady positive balance still has zero below it.
        assertEquals(0.0 to 100.0, valueRange(listOf(100.0, 100.0)))
    }

    @Test
    fun `a flat line still has a range to draw in`() {
        // The chart divides by max - min: an empty range would put every point at NaN.
        assertEquals(0.0 to 1.0, valueRange(listOf(0.0, 0.0, 0.0)))
        assertEquals(-250.0 to 0.0, valueRange(listOf(-250.0, -250.0)))
        assertEquals(0.0 to 1.0, valueRange(listOf(0.0)))
    }
}
