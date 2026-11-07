package com.pennywiseai.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAxisLabelsTest {
    @Test
    fun `short lists pass through`() {
        assertEquals(listOf("a", "b"), chartAxisLabels(listOf("a", "b"), 4))
    }

    @Test
    fun `labels are taken where they land on the axis, first and last included`() {
        val days = (1..31).map { "$it Oct" }
        assertEquals(listOf("1 Oct", "11 Oct", "21 Oct", "31 Oct"), chartAxisLabels(days, 4))
    }
}
