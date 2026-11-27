package com.pennywiseai.tracker.ui.components.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MascotCuesRecordsTest {
    private fun at(t: Float) = (t * ARRIVAL_MS).toLong()

    private fun samples(to: Float, step: Float = 0.005f) = generateSequence(0f) { it + step }.takeWhile { it <= to }

    @Test
    fun `rules applied jiggle the receipt both ways, then tap it down once`() {
        val rolls = samples(0.2f).map(::sortedRoll).toList()
        assertTrue(rolls.max() > 2f)
        assertTrue(rolls.min() < -2f)
        // The jiggle is over before the tap is at its deepest, and the tap only goes down.
        assertEquals(0f, sortedRoll(0.24f), 0f)
        assertTrue(sortedTap(0.24f) > 2f)
        samples(SORTED_END).forEach { assertTrue(sortedTap(it) >= 0f) }
        assertEquals(0f, sortedTap(0.1f), 0f)
    }

    @Test
    fun `the receipt's lines shake loose and are back in line before the check`() {
        assertEquals(0f, sortedLoose(0f), 0f)
        assertTrue(sortedLoose(0.09f) > 0.9f)
        assertEquals(0f, sortedLoose(0.24f), 0f)
        assertEquals(0f, sortedCheck(0.24f), 0f)
        assertEquals(1f, sortedCheck(SORTED_END), 0f)
    }

    @Test
    fun `a statement is read line by line, left to right, top to bottom`() {
        // Each line starts at the left and is read across; the last ends at the right.
        val starts = listOf(0.04f, 0.18f, 0.32f).map(::readGaze)
        starts.forEach { assertEquals(-0.55f, it.x, 1e-4f) }
        assertTrue(starts[0].y < starts[1].y && starts[1].y < starts[2].y)
        assertEquals(0.55f, readGaze(0.04f + 0.14f * 0.75f).x, 1e-4f)
        assertEquals(0.55f, readGaze(0.46f).x, 1e-4f)
        assertEquals(0.65f, readGaze(0.46f).y, 1e-4f)
        // Never down and back up: the eyes only go down the page.
        val ys = samples(0.5f).map { readGaze(it).y }.toList()
        ys.zipWithNext().forEach { (a, b) -> assertTrue(b >= a - 1e-5f) }
    }

    @Test
    fun `the reading is over before the statement is checked`() {
        assertEquals(0f, readAttention(0.56f), 0f)
        assertEquals(0f, readCheck(0.46f), 0f)
        assertEquals(1f, readCheck(READ_END), 0f)
        val reading = arrived(MascotPose(), MascotCue.Read, at(0.25f))
        assertTrue(reading.gazeX != 0f && reading.roll != 0f)
    }

    @Test
    fun `the shield stands up as its check lands, then a glint crosses it`() {
        assertEquals(0f, securedRise(0f), 0f)
        assertEquals(3f, securedRise(0.17f), 1e-4f)
        assertEquals(0f, securedRise(0.36f), 0f)
        // Up, never down: a lift is negative.
        assertTrue(arrived(MascotPose(), MascotCue.Secured, at(0.17f)).lift < 0f)
        // The check is in at the top of the rise, before the glint starts.
        assertEquals(1f, securedCheck(0.2f), 0f)
        assertNull(securedGlintX(0.15f))
        val glint = samples(SECURED_END).mapNotNull(::securedGlintX).toList()
        assertTrue(glint.first() < 10f && glint.last() > 90f)
        glint.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
        assertNull(securedGlintX(SECURED_END))
    }

    @Test
    fun `the shield's corner glints once, outside it, and is out by the end`() {
        assertEquals(0f, securedTing(0.3f), 0f)
        assertTrue(securedTing(0.42f) > 0.9f)
        assertEquals(0f, securedTing(SECURED_END), 0f)
        // Clear of the shield's top corner (its edge runs to 85,18).
        assertTrue(SECURED_TING.at.x > 85f && SECURED_TING.at.y < 18f)
    }

    @Test
    fun `a bill paid rings briefly both ways, and is still as its check lands`() {
        val rolls = samples(PAID_END).map(::paidRoll).toList()
        assertTrue(rolls.max() > 5f)
        assertTrue(rolls.min() < -2f)
        // Shorter than a poke's ring: it's done by the end, and the check is in by then.
        assertEquals(0f, paidRoll(PAID_END), 1e-4f)
        assertTrue(abs(paidRoll(0.4f)) < 1f)
        assertEquals(0f, paidCheck(0.2f), 0f)
        assertEquals(1f, paidCheck(PAID_END), 0f)
        // The ring's arcs go out and are gone before it's still.
        assertTrue(paidRingAlpha(0.12f, inner = true) > 0.9f)
        assertTrue(paidRingAlpha(0.18f, inner = false) > 0.9f)
        assertEquals(0f, paidRingAlpha(0.3f, inner = true), 0f)
        assertEquals(0f, paidRingAlpha(0.3f, inner = false), 0f)
    }

    @Test
    fun `the record cues are short`() {
        for (cue in listOf(MascotCue.Sorted, MascotCue.Read, MascotCue.Secured, MascotCue.Paid)) {
            assertTrue("$cue", arrivalEnd(cue) <= 0.75f)
        }
    }
}
