package com.pennywiseai.tracker.presentation.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The period switch must not change height while its buttons resize (the Insights card jumped). */
class InsightsPeriodSelectorTest {

    @Test
    fun `settled only once every button rests at its target`() {
        assertTrue(isSelectionSettled(listOf(1f, 0f, 0f), checkedIndex = 0))
        assertTrue(isSelectionSettled(listOf(0f, 0f, 1f), checkedIndex = 2))
        assertTrue(isSelectionSettled(listOf(0f, 0f, 0f), checkedIndex = -1))
        // Mid-switch from This month to Last month, and the spring's overshoot on either side.
        assertFalse(isSelectionSettled(listOf(0.4f, 0.6f, 0f), checkedIndex = 1))
        assertFalse(isSelectionSettled(listOf(-0.05f, 1.05f, 0f), checkedIndex = 1))
        assertFalse(isSelectionSettled(listOf(0f, 1f, 0f), checkedIndex = 0))
    }

    @Test
    fun `the check icon opens with the width and never past its slot`() {
        assertEquals(0f, checkIconFraction(0f), 0f)
        assertEquals(0.5f, checkIconFraction(0.5f), 0f)
        assertEquals(1f, checkIconFraction(1f), 0f)
        // Overshoot: the icon neither goes negative nor grows beyond its size.
        assertEquals(0f, checkIconFraction(-0.05f), 0f)
        assertEquals(1f, checkIconFraction(1.08f), 0f)
    }

    @Test
    fun `weight moves from rest to checked with the same progress`() {
        assertEquals(1f, selectedWeight(0f), 0f)
        assertEquals(1.45f, selectedWeight(1f), 1e-6f)
        // Two buttons swapping selection keep the row's total weight, so the third holds still.
        listOf(0f, 0.3f, 0.7f, 1f, 1.08f).forEach { p ->
            assertEquals(2.45f, selectedWeight(p) + selectedWeight(1f - p), 1e-5f)
        }
    }
}
