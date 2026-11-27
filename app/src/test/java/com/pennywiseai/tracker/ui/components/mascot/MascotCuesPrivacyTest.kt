package com.pennywiseai.tracker.ui.components.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owl covering its eyes and peeking out, and the bubble learning a message. */
class MascotCuesPrivacyTest {
    private fun at(cue: MascotCue, t: Float) = arrived(MascotPose(), cue, (t * ARRIVAL_MS).toLong())

    @Test
    fun `the owl's cues fit inside a toggle's hold, and the message's stays short`() {
        assertTrue(arrivalEnd(MascotCue.CoverEyes) * ARRIVAL_MS <= TOGGLE_HOLD_MS)
        assertTrue(arrivalEnd(MascotCue.Peek) * ARRIVAL_MS <= TOGGLE_HOLD_MS)
        listOf(MascotCue.CoverEyes, MascotCue.Peek, MascotCue.Learned).forEach { assertTrue("$it", arrivalEnd(it) <= 0.75f) }
    }

    @Test
    fun `the wings come up over shut eyes, hold, and go down`() {
        assertEquals(0f, coverAmount(0f), 0f)
        for (t in listOf(0.2f, 0.35f, 0.5f)) {
            assertEquals(1f, coverAmount(t), 0f)
            assertEquals("shut under the wings at $t", 1f, at(MascotCue.CoverEyes, t).blink, 0f)
        }
        assertTrue(coverAmount(0.6f) in 0.01f..0.99f)
        assertEquals(0f, coverAmount(COVER_END), 0f)
        val done = at(MascotCue.CoverEyes, COVER_END)
        assertEquals(0f, done.blink, 0f)
        assertEquals(0f, done.lift, 0f)
    }

    @Test
    fun `peeking, one wing lowers first and its eye is open, then both drop and the owl looks round`() {
        val peeping = 0.3f
        assertEquals("the other wing still covers its eye", 1f, peekWing(peeping, -1f), 0f)
        assertTrue(peekWing(peeping, 1f) < 0.5f && peekWing(peeping, 1f) > 0f)
        assertEquals(0f, at(MascotCue.Peek, peeping).blink, 0f)
        // Before that, both wings are up over shut eyes.
        assertEquals(1f, peekWing(0.12f, -1f), 0f)
        assertEquals(1f, peekWing(0.12f, 1f), 0f)
        assertEquals(1f, peekShut(0.12f), 0f)
        // Then both down, and a look one way and the other.
        assertEquals(0f, peekWing(0.5f, -1f), 0f)
        assertEquals(0f, peekWing(0.5f, 1f), 0f)
        val looks = (50..70).map { at(MascotCue.Peek, it / 100f).gazeX }
        assertTrue(looks.any { it > 0.5f } && looks.any { it < -0.5f })
        val done = at(MascotCue.Peek, PEEK_END)
        assertEquals(0f, peekWing(PEEK_END, -1f), 0f)
        assertEquals(0f, peekWing(PEEK_END, 1f), 0f)
        assertEquals(0f, done.gazeX, 1e-4f)
        assertEquals(0f, done.lift, 1e-4f)
    }

    @Test
    fun `the bubble reads one line then the next, and the check lands once it has`() {
        val first = (6..22).map { learnedGaze(it / 100f) }
        assertTrue(first.first().x < -0.5f && first.last().x > 0.5f)
        val second = (26..40).map { learnedGaze(it / 100f) }
        assertTrue(second.first().x < -0.5f && second.last().x > 0.5f)
        assertTrue("the second line is lower", second.last().y > first.last().y)
        assertEquals(0f, learnedCheck(0.4f), 0f)
        assertEquals(1f, learnedCheck(LEARNED_END), 0f)
        assertEquals(1f, learnedLines(0.3f), 0f)
        assertEquals(0f, learnedLines(LEARNED_END), 0f)
        assertTrue((50..66).any { learnedSpark(it / 100f) > 0.5f })
        assertEquals(0f, learnedSpark(LEARNED_END), 0f)
        assertEquals(0f, learnedReading(LEARNED_END), 0f)
        val done = at(MascotCue.Learned, LEARNED_END)
        assertEquals(0f, done.gazeX, 1e-4f)
        assertEquals(0f, done.lift, 1e-4f)
    }

    @Test
    fun `peeking gives the eyes back once it's over`() {
        val wandering = MascotPose(gazeX = 0.5f, gazeY = -0.3f)
        val end = arrived(wandering, MascotCue.Peek, (arrivalEnd(MascotCue.Peek) * ARRIVAL_MS).toLong())
        assertEquals(0.5f, end.gazeX, 1e-4f)
        assertEquals(-0.3f, end.gazeY, 1e-4f)
    }
}
