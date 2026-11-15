package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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
                for (sweep in listOf(false, true)) {
                    for (t in 0L until 120_000L step 37) {
                        assertInsideEye(moodPose(mood, t, t, seed, motion, sweep))
                    }
                }
            }
        }
    }

    @Test
    fun `a reading bubble sweeps its eyes both ways, and holds still without motion`() {
        val gazes = (0L until 4_000L step 20).map { moodPose(MascotMood.Thinking, it, it, seed, MascotMotion.Quiet, sweep = true).gazeX }
        assertTrue(gazes.min() < -0.7f)
        assertTrue(gazes.max() > 0.7f)
        val still = moodPose(MascotMood.Thinking, 1_234, 1_234, seed, MascotMotion.Still, sweep = true)
        assertEquals(0f, still.gazeX, 0f)
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
                    val stillSweep = moodPose(mood, 0, 0, seed, MascotMotion.Still, sweep = true)
                    assertEquals(stillSweep, moodPose(mood, t, since, seed, MascotMotion.Still, sweep = true))
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

    @Test
    fun `a poke blinks once and wobbles within six degrees, then settles`() {
        val calm = MascotPose()
        val rolls = (0L until POKE_MS step 5).map { poked(calm, it).roll }
        assertTrue(rolls.all { abs(it) <= POKE_ROLL })
        assertTrue(rolls.max() > 3f && rolls.min() < -1f) // it wobbles both ways
        assertEquals(1f, poked(calm, BLINK_MS / 2).blink, 0.01f)
        assertEquals(0f, poked(calm, BLINK_MS + 1).blink, 0f)
        assertEquals(calm, poked(calm, POKE_MS))
        assertEquals(calm, poked(calm, -1))
    }

    @Test
    fun `a drag looks toward the finger and never leaves the eye`() {
        val right = dragGaze(0.3f, 0f)
        assertTrue(right.x > 0.3f && right.y == 0f)
        val upLeft = dragGaze(-0.2f, -0.2f)
        assertTrue(upLeft.x < 0f && upLeft.y < 0f)
        for (d in listOf(0.5f, 2f, 10f, 1_000f)) {
            for ((x, y) in listOf(d to 0f, 0f to -d, -d to d, d to d)) {
                val gaze = dragGaze(x, y)
                assertInsideEye(MascotPose(gazeX = gaze.x, gazeY = gaze.y))
            }
        }
        assertEquals(Offset.Zero, dragGaze(0f, 0f))
    }

    @Test
    fun `let go, the eyes are back to their own gaze within a second`() {
        var attention = 1f
        for (frame in 0 until 60) attention = ease(attention, 0f, 16, ATTENTION_MS) // ~1s at 60fps
        assertTrue(attention < 0.05f)
        val own = MascotPose(gazeX = 0.2f)
        assertEquals(0.2f, lookAt(own, Offset(-1f, 0f), attention).gazeX, 0.07f)
        assertEquals(-1f, lookAt(own, Offset(-1f, 0f), 1f).gazeX, 0f)
    }

    /** Frames of [frameMs] from [start] until [needsFrames] says stop; returns the frames run and where the eyes ended. */
    private fun runUntilSettled(
        start: Follow,
        target: Offset?,
        shape: MascotShape = MascotShape.Owl,
        sincePokeMs: Long = Long.MAX_VALUE / 2,
        frameMs: Long = 16,
    ): Pair<Int, Follow> {
        var follow = start
        var frames = 0
        while (needsFrames(false, sincePokeMs + frames * frameMs, shape, follow, target)) {
            follow = follow.step(target, frameMs)
            frames++
            check(frames < 10_000) { "never settled" }
        }
        return frames to follow.settle(target)
    }

    @Test
    fun `a settled mascot asks for no frames`() {
        assertFalse(needsFrames(false, Long.MAX_VALUE / 2, MascotShape.Owl, Follow(), null))
        // A look the eyes have reached holds without frames too.
        val look = Offset(0.3f, 0.8f)
        assertFalse(needsFrames(false, Long.MAX_VALUE / 2, MascotShape.Owl, Follow(1f, look), look))
    }

    @Test
    fun `a finger on the mascot keeps frames coming, and let go they stop after a short settle`() {
        val gaze = Offset(0.8f, -0.4f)
        assertTrue(needsFrames(true, Long.MAX_VALUE / 2, MascotShape.Owl, Follow(1f, gaze), gaze))
        val (frames, landed) = runUntilSettled(Follow(1f, gaze), target = null)
        assertTrue("settled in ${frames * 16}ms", frames * 16 in 300..1_500)
        assertEquals(Follow(0f, gaze), landed)
    }

    @Test
    fun `a new look plays out and then holds still`() {
        val look = typingGaze(12)
        val (frames, landed) = runUntilSettled(Follow(), look)
        assertTrue("settled in ${frames * 16}ms", frames * 16 in 100..1_500)
        assertEquals(Follow(1f, look), landed)
    }

    @Test
    fun `a poke runs frames for its wobble only, and a bell for its whole swing`() {
        val (wobble, _) = runUntilSettled(Follow(), null, MascotShape.Owl, sincePokeMs = 0)
        assertEquals(POKE_MS.toFloat(), wobble * 16f, 16f)
        val (swing, _) = runUntilSettled(Follow(), null, MascotShape.Bell, sincePokeMs = 0)
        assertEquals(RING_MS.toFloat(), swing * 16f, 16f)
    }

    @Test
    fun `only open eyes follow a look`() {
        val happy = MascotPose(style = EyeStyle.Arc)
        assertEquals(happy, lookAt(happy, Offset(1f, 0f), 1f))
        val asleep = MascotPose(style = EyeStyle.Closed)
        assertEquals(asleep, lookAt(asleep, Offset(1f, 0f), 1f))
    }

    @Test
    fun `typing has the eyes down on the input, moving along the line as it fills`() {
        val start = typingGaze(1)
        val later = typingGaze(20)
        val full = typingGaze(500)
        assertTrue(start.y > 0.5f)
        assertTrue(start.x < later.x && later.x < full.x)
        assertEquals(full, typingGaze(5_000))
        for (n in 0..200) assertInsideEye(MascotPose(gazeX = typingGaze(n).x, gazeY = typingGaze(n).y))
    }

    @Test
    fun `asleep, the hero breathes and the small mascot stays still`() {
        val lifts = (0L until 4_000L step 20).map { moodPose(MascotMood.Asleep, it, it, seed, MascotMotion.Full).lift }
        assertTrue(lifts.max() > 1f && lifts.min() < -1f)
        assertEquals(0f, moodPose(MascotMood.Asleep, 1_234, 1_234, seed, MascotMotion.Quiet).lift, 0f)
    }

    private fun assertInsideEye(pose: MascotPose) {
        val gaze = hypot(pose.gazeX, pose.gazeY)
        assertTrue("gaze $gaze leaves the unit disc", gaze <= 1.0001f)
        assertTrue(gaze * OwlEye.GAZE_TRAVEL + OwlEye.IRIS_R <= OwlEye.DISC_R)
    }

    @Test
    fun `bars look up when spending rose, down when it fell, ahead when it held`() {
        assertTrue(trendGaze(1f).y < 0f)
        assertTrue(trendGaze(-1f).y > 0f)
        assertEquals(0f, trendGaze(0f).y, 0f)
        // Only up or down, and never out of the eye however big the change.
        assertEquals(0f, trendGaze(5f).x, 0f)
        assertTrue(hypot(trendGaze(5f).x, trendGaze(5f).y) <= 1f)
    }

    @Test
    fun `a rung bell swings both ways within fourteen degrees, dies down and stops`() {
        val calm = MascotPose()
        val rolls = (0L until RING_MS step 5).map { rung(calm, it).roll }
        assertTrue(rolls.all { abs(it) <= RING_ROLL })
        assertTrue(rolls.max() > 8f && rolls.min() < -5f)
        // Each swing is smaller than the one before: the last half second is barely a sway.
        assertTrue(rolls.drop(rolls.size * 2 / 3).all { abs(it) < 3f })
        assertEquals(calm, rung(calm, RING_MS))
        assertEquals(calm, rung(calm, -1))
        assertEquals(0f, rung(calm, 0).blink, 0f) // the swing is the bell's; the blink stays the poke's
    }

    @Test
    fun `a gauge needle points left with nothing used, up at half, right when all is used`() {
        val none = gaugeNeedleTip(0f)
        val half = gaugeNeedleTip(0.5f)
        val all = gaugeNeedleTip(1f)
        assertTrue(none.x < 50f && abs(none.y - 78f) < 0.01f)
        assertTrue(abs(half.x - 50f) < 0.01f && half.y < 78f)
        assertTrue(all.x > 50f && abs(all.y - 78f) < 0.01f)
        // Over budget still pins at the end of the dial rather than wrapping round.
        assertEquals(all, gaugeNeedleTip(1.6f))
        assertEquals(none, gaugeNeedleTip(-0.2f))
    }

    @Test
    fun `a jar fills from its bottom, and never past its shoulders or below its floor`() {
        assertEquals(91f, jarFillTop(0f), 0f)
        assertTrue(jarFillTop(0.25f) < jarFillTop(0f) && jarFillTop(0.75f) < jarFillTop(0.25f))
        assertEquals(25f, jarFillTop(1f), 0.01f)
        assertEquals(jarFillTop(1f), jarFillTop(3f), 0f)
        assertEquals(jarFillTop(0f), jarFillTop(-1f), 0f)
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
