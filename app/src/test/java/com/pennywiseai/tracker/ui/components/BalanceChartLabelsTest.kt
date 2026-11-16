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
}
