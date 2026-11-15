package com.pennywiseai.tracker.ui.components.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MascotPoseTest {
    private val seed = 3.7f

    @Test
    fun `a scheduled blink closes and reopens inside its slot`() {
        for (slot in 0L until 50L) {
            val start = slot * BLINK_SLOT_MS + blinkOffsetMs(seed, slot)
            val double = isDoubleBlink(seed, slot)
            assertEquals(0f, scheduledBlink(seed, start - 1), 0f)
            assertEquals(1f, scheduledBlink(seed, start + BLINK_MS / 2), 0.01f)
            assertEquals(0f, scheduledBlink(seed, start + BLINK_MS), 0.01f)
            assertEquals(0f, scheduledBlink(seed, start + blinkLengthMs(double) + 1), 0f)
            // The whole blink, doubled or not, ends before the next slot begins.
            assertTrue(start + blinkLengthMs(double) < (slot + 1) * BLINK_SLOT_MS)
        }
    }

    @Test
    fun `each slot closes once, or twice for a double blink, and about a fifth are double`() {
        var doubles = 0
        val slots = 400L
        for (slot in 0L until slots) {
            var closures = 0
            var shut = false
            for (t in slot * BLINK_SLOT_MS until (slot + 1) * BLINK_SLOT_MS step 5) {
                val nowShut = scheduledBlink(seed, t) > 0.9f
                if (nowShut && !shut) closures++
                shut = nowShut
            }
            val double = isDoubleBlink(seed, slot)
            if (double) doubles++
            assertEquals(if (double) 2 else 1, closures)
        }
        assertEquals(DOUBLE_BLINK_CHANCE, doubles / slots.toFloat(), 0.08f)
    }

    @Test
    fun `the quiet clock waits 4 to 8 seconds and then blinks for 190ms`() {
        assertEquals(4000L, nextBlinkDelayMillis(0f))
        assertEquals(8000L, nextBlinkDelayMillis(1f))
        assertEquals(0f, blinkAt(0, double = false), 0f)
        assertEquals(1f, blinkAt(BLINK_MS / 2, double = false), 0.01f)
        assertEquals(0f, blinkAt(BLINK_MS + 1, double = false), 0f)
        assertEquals(1f, blinkAt(DOUBLE_BLINK_GAP_MS + BLINK_MS / 2, double = true), 0.01f)
        assertEquals(0f, blinkAt(blinkLengthMs(double = true) + 1, double = true), 0f)
    }

    @Test
    fun `gaze stays inside the eye for every mood and motion`() {
        for (mood in MascotMood.entries) {
            for (motion in MascotMotion.entries) {
                for (t in 0L until 120_000L step 37) {
                    assertInsideEye(moodPose(mood, t, t, seed, motion))
                }
            }
        }
    }

    @Test
    fun `a blend starts from the pose on screen and lands on the target`() {
        val onScreen = moodPose(MascotMood.Thinking, 52_345, 52_345, seed, MascotMotion.Full)
        val target = moodPose(MascotMood.Happy, 52_345, 0, seed, MascotMotion.Full)
        assertPose(onScreen, blendPose(onScreen, target, 0f))
        assertPose(target, blendPose(onScreen, target, 1f))
    }

    @Test
    fun `a change of eye style happens behind shut lids`() {
        val onScreen = moodPose(MascotMood.Calm, 1_000, 1_000, seed, MascotMotion.Still)
        val target = moodPose(MascotMood.Happy, 1_000, 0, seed, MascotMotion.Still)
        assertEquals(EyeStyle.Open, blendPose(onScreen, target, 0.4f).style)
        assertEquals(1f, blendPose(onScreen, target, 0.5f).blink, 0.001f)
        assertEquals(EyeStyle.Arc, blendPose(onScreen, target, 0.6f).style)
    }

    @Test
    fun `reduced motion gives one static pose whatever the time`() {
        for (mood in MascotMood.entries) {
            val still = moodPose(mood, 0, 0, seed, MascotMotion.Still)
            for (t in listOf(95L, 300L, 1_234L, 4_401L, 99_999L)) {
                for (since in listOf(0L, 95L, 460L, 5_000L)) {
                    assertEquals(still, moodPose(mood, t, since, seed, MascotMotion.Still))
                }
            }
            assertEquals(0f, still.blink, 0f)
            assertEquals(0f, still.lift, 0f)
        }
    }

    @Test
    fun `happy hops once, upward, and settles`() {
        val lifts = (0L until 3_000L step 10).map { moodPose(MascotMood.Happy, it, it, seed, MascotMotion.Quiet).lift }
        assertTrue(lifts.all { it <= 0f })
        assertTrue(lifts.min() < -5f)
        assertTrue(lifts.drop(100).all { it == 0f }) // nothing after the first second
    }

    private fun assertInsideEye(pose: MascotPose) {
        val gaze = hypot(pose.gazeX, pose.gazeY)
        assertTrue("gaze $gaze leaves the unit disc", gaze <= 1.0001f)
        assertTrue(gaze * OwlEye.GAZE_TRAVEL + OwlEye.IRIS_R <= OwlEye.DISC_R)
    }

    private fun assertPose(expected: MascotPose, actual: MascotPose) {
        val d = 1e-5f
        assertEquals(expected.gazeX, actual.gazeX, d)
        assertEquals(expected.gazeY, actual.gazeY, d)
        assertEquals(expected.roll, actual.roll, d)
        assertEquals(expected.lift, actual.lift, d)
        assertEquals(expected.squint, actual.squint, d)
        assertEquals(expected.blink, actual.blink, d)
        assertEquals(expected.brows, actual.brows, d)
        assertEquals(expected.style, actual.style)
    }
}
