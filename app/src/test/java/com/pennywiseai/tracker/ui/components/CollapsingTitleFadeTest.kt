package com.pennywiseai.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsingTitleFadeTest {

    private val fractions = (0..100).map { it / 100f }

    @Test
    fun `expanded shows only the large title`() {
        assertEquals(1f, CollapsingTitleFade.expanded(0f))
        assertEquals(0f, CollapsingTitleFade.collapsed(0f))
    }

    @Test
    fun `collapsed shows only the bar title`() {
        assertEquals(0f, CollapsingTitleFade.expanded(1f))
        assertEquals(1f, CollapsingTitleFade.collapsed(1f))
    }

    @Test
    fun `the two titles are never visible at the same time`() {
        fractions.forEach { f ->
            val large = CollapsingTitleFade.expanded(f)
            val small = CollapsingTitleFade.collapsed(f)
            assertTrue("both titles visible at fraction $f ($large, $small)", large == 0f || small == 0f)
        }
    }

    @Test
    fun `each title fades monotonically within 0 to 1`() {
        fractions.zipWithNext().forEach { (a, b) ->
            assertTrue(CollapsingTitleFade.expanded(b) <= CollapsingTitleFade.expanded(a))
            assertTrue(CollapsingTitleFade.collapsed(b) >= CollapsingTitleFade.collapsed(a))
        }
        fractions.forEach { f ->
            assertTrue(CollapsingTitleFade.expanded(f) in 0f..1f)
            assertTrue(CollapsingTitleFade.collapsed(f) in 0f..1f)
        }
    }

    @Test
    fun `out of range fractions are clamped`() {
        assertEquals(1f, CollapsingTitleFade.expanded(-0.2f))
        assertEquals(1f, CollapsingTitleFade.collapsed(1.3f))
    }
}
