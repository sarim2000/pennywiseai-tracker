package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MascotCuesGoalsTest {
    private val brimEnd = arrivalEnd(MascotCue.Brim)

    @Test
    fun `a goal reached plays calmly and short`() {
        assertTrue(brimEnd <= 0.75f)
    }

    @Test
    fun `the last coin falls onto the lid, then it's in`() {
        val start = brimCoin(0f)!!
        val late = brimCoin(0.19f)!!
        assertTrue(late.y > start.y)
        // It ends on the lid (y 9..19), never inside the jar.
        assertTrue(late.y in 9f..19f)
        assertNull(brimCoin(0.2f))
        assertEquals(1f, brimCoinAlpha(0.1f), 0f)
    }

    @Test
    fun `the jar dips under the coin, then stands taller, then is at rest`() {
        assertEquals(0f, brimLift(0.1f), 0f)
        assertTrue(brimLift(0.26f) > 0f)
        assertTrue(brimLift(0.45f) < 0f)
        assertEquals(0f, brimLift(brimEnd), 0f)
        assertEquals(MascotPose(), brimPose(MascotPose(), brimEnd))
    }

    @Test
    fun `the ripple spreads as the coin lands and is gone before the glint ends`() {
        assertEquals(0f, brimRipple(0.15f), 0f)
        assertTrue(brimRipple(0.3f) > 0f)
        assertTrue(brimRippleSpread(0.4f) > brimRippleSpread(0.25f))
        assertEquals(0f, brimRipple(0.5f), 0f)
    }

    @Test
    fun `the glint crosses the jar after the coin is in, left to right`() {
        assertNull(brimGlintX(0.2f))
        val a = brimGlintX(0.35f)
        val b = brimGlintX(0.5f)
        assertNotNull(a)
        assertTrue(b!! > a!!)
        assertNull(brimGlintX(brimEnd))
    }

    @Test
    fun `the rim sparkles twinkle one after another, off the jar, and are out by the end`() {
        val delays = BRIM_SPARKLES.map { it.delay }
        assertEquals(delays.sorted(), delays)
        for (s in BRIM_SPARKLES) {
            assertEquals(0f, brimSparkleAlpha(s, s.delay - 0.01f), 0f)
            assertTrue(brimSparkleAlpha(s, s.delay + 0.12f) > 0.9f)
            assertEquals(0f, brimSparkleAlpha(s, brimEnd), 1e-4f)
            assertTrue("$s", clearOfJar(s.at, s.radius))
        }
    }

    // The jar's body: a 60x66 box from (20, 25) with corners of 17, and the lid and neck above it.
    private fun clearOfJar(c: Offset, r: Float): Boolean {
        val nearX = c.x.coerceIn(37f, 63f)
        val nearY = c.y.coerceIn(42f, 74f)
        val body = hypot(c.x - nearX, c.y - nearY) > 17f + r
        val lid = c.x + r < 33f || c.x - r > 67f || c.y + r < 9f
        return body && lid
    }
}
