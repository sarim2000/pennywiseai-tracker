package com.pennywiseai.tracker.ui.components.mascot

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MascotMorphTest {
    private fun circle(c: Offset, r: Float, n: Int = 720) = FloatArray(n * 2) { i ->
        val a = 2 * PI * (i / 2) / n
        if (i % 2 == 0) c.x + (r * cos(a)).toFloat() else c.y + (r * sin(a)).toFloat()
    }

    private fun square(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    @Test
    fun `a circle seen from its centre is the same distance every way`() {
        val p = profileOf(listOf(circle(Offset(50f, 50f), 30f)), Offset(50f, 50f))
        assertEquals(PROFILE_RAYS, p.radii.size)
        p.radii.forEach { assertEquals(30f, it, 0.05f) }
    }

    @Test
    fun `the rays go clockwise from just past twelve o'clock`() {
        val first = rayDirection(0)
        assertTrue(first.y < -0.99f && first.x > 0f)
        val quarter = rayDirection(PROFILE_RAYS / 4)
        assertTrue(quarter.x > 0.99f && quarter.y > 0f)
    }

    @Test
    fun `a square reaches its corners on the diagonals and its sides straight out`() {
        val p = profileOf(listOf(square(20f, 20f, 80f, 80f)), Offset(50f, 50f))
        assertEquals(30f, p.radii[0], 0.01f)
        assertEquals(30f, p.radii[PROFILE_RAYS / 4], 0.01f)
        val corner = 30f * kotlin.math.sqrt(2f)
        assertTrue(p.radii.max() in corner - 1f..corner + 1e-3f)
    }

    @Test
    fun `each ray keeps the farthest edge, so a part apart from the body still shows`() {
        // A body and, straight above it, a separate lid: the up ray reaches the lid's top.
        val p = profileOf(listOf(square(30f, 40f, 70f, 80f), square(40f, 10f, 60f, 20f)), Offset(50f, 60f))
        assertEquals(50f, p.radii[0], 0.01f)
    }

    @Test
    fun `a morph starts on one outline, ends on the other and passes between them`() {
        val a = profileOf(listOf(circle(Offset(50f, 50f), 20f)), Offset(50f, 50f))
        val b = profileOf(listOf(circle(Offset(50f, 60f), 40f)), Offset(50f, 60f))
        val start = blendProfiles(a, b, 0f)
        val end = blendProfiles(a, b, 1f)
        val mid = blendProfiles(a, b, 0.5f)
        assertEquals(a.center, start.center)
        assertEquals(b.center, end.center)
        assertEquals(Offset(50f, 55f), mid.center)
        for (i in 0 until PROFILE_RAYS) {
            assertEquals(a.radii[i], start.radii[i], 1e-4f)
            assertEquals(b.radii[i], end.radii[i], 1e-4f)
            assertEquals(30f, mid.radii[i], 0.1f)
        }
    }

    @Test
    fun `a spring past the end overshoots a little, and no ray turns inside out`() {
        val a = profileOf(listOf(circle(Offset(50f, 50f), 40f)), Offset(50f, 50f))
        val b = profileOf(listOf(circle(Offset(50f, 50f), 2f)), Offset(50f, 50f))
        val past = blendProfiles(a, b, 1.1f)
        past.radii.forEach { assertTrue(it >= 0f) }
        val back = blendProfiles(b, a, 1.1f)
        back.radii.forEach { assertEquals(43.8f, it, 0.1f) }
    }

    @Test
    fun `the eyes narrow through the morph and are open at both ends`() {
        assertEquals(0f, morphSquint(0f), 1e-6f)
        assertEquals(0f, morphSquint(1f), 1e-5f)
        assertTrue(morphSquint(0.5f) > 0.8f)
        // A spring past 1 doesn't squint again.
        assertEquals(0f, morphSquint(1.08f), 1e-5f)
    }

    @Test
    fun `the eyes slide from one shape's place to the other's`() {
        val a = EyeSpec(x = 50f, y = 52f, spacing = 28f, scale = 1f)
        val b = EyeSpec(x = 38f, y = 54f, spacing = 16f, scale = 0.9f)
        assertEquals(a, lerpEyes(a, b, 0f))
        assertEquals(b, lerpEyes(a, b, 1f))
        assertEquals(EyeSpec(44f, 53f, 22f, 0.95f), lerpEyes(a, b, 0.5f))
    }

    @Test
    fun `a transfer's glance goes from line start to line end and gives the eyes back`() {
        assertTrue(walletGlance(0f).x < -0.7f)
        assertTrue(walletGlance(0.6f).x > 0.7f)
        assertEquals(0f, walletAttention(0f), 1e-6f)
        assertEquals(1f, walletAttention(0.4f), 1e-6f)
        assertEquals(0f, walletAttention(1f), 1e-6f)
        val pose = arrived(MascotPose(), MascotCue.Glance, (0.4f * ARRIVAL_MS).toLong())
        assertTrue(pose.gazeX != 0f)
        // Over, it changes nothing.
        assertEquals(MascotPose(), arrived(MascotPose(), MascotCue.Glance, ARRIVAL_MS))
    }

    @Test
    fun `the jar's coin falls into the lid, then the jar dips and stands`() {
        val top = jarCoin(0f)!!
        val lower = jarCoin(0.2f)!!
        assertTrue(lower.y > top.y)
        assertNull(jarCoin(0.5f))
        assertEquals(0f, jarSettle(0.1f), 0f)
        assertTrue(jarSettle(0.36f) > 0f)
        assertEquals(0f, jarSettle(0.9f), 0f)
    }

    @Test
    fun `sparkles twinkle once each, and the receipt's stamp stays once stamped`() {
        for (s in COIN_SPARKLES) {
            assertEquals(0f, sparkleAlpha(s, 0f), 0f)
            assertEquals(0f, sparkleAlpha(s, 1f), 0f)
            assertTrue(sparkleAlpha(s, s.delay + 0.2f) > 0.9f)
            // Outside the coin (centre 50,52, radius 38), so the glyph doesn't cut into it.
            assertTrue((s.at - Offset(50f, 52f)).getDistance() - s.radius > 38f)
        }
        assertEquals(0f, stampAlpha(0f), 0f)
        assertEquals(1f, stampAlpha(1f), 0f)
        assertNull(coinShineX(0.9f))
    }

    @Test
    fun `a bell rings as it arrives`() {
        val ringing = arrived(MascotPose(), MascotCue.Ring, 100)
        assertTrue(ringing.roll != 0f)
    }

    @Test
    fun `a shape change in the jar's dip starts from the dip and lets it go through the morph`() {
        val dipMs = (0.36f * ARRIVAL_MS).toLong()
        val dipping = arrived(MascotPose(), MascotCue.CoinDrop, dipMs)
        assertTrue(dipping.lift > 3f)
        val cut = CutArrival(MascotCue.CoinDrop, dipMs, 1f)
        // No jump at the start, none of it left at the end, and it eases out between.
        assertEquals(dipping.lift, withCutArrival(MascotPose(), cut, 0f).lift, 1e-4f)
        assertEquals(dipping.lift / 2, withCutArrival(MascotPose(), cut, 0.5f).lift, 1e-4f)
        assertEquals(0f, withCutArrival(MascotPose(), cut, 1f).lift, 0f)
        // A spring past the end doesn't bring it back.
        assertEquals(0f, withCutArrival(MascotPose(), cut, 1.1f).lift, 0f)
        assertEquals(MascotPose(lift = -2f), withCutArrival(MascotPose(lift = -2f), null, 0.3f))
    }

    @Test
    fun `a morph cut short hands on only what still showed of the arrival`() {
        val cut = CutArrival(MascotCue.Glance, (0.4f * ARRIVAL_MS).toLong(), 1f)
        val leaning = withCutArrival(MascotPose(), cut, 0f)
        assertTrue(leaning.roll != 0f)
        // Cut again a quarter of the way in: the next morph starts from that quarter-faded lean.
        val handedOn = cut.copy(weight = cutWeight(cut, 0.25f))
        assertEquals(
            withCutArrival(MascotPose(), cut, 0.25f).roll,
            withCutArrival(MascotPose(), handedOn, 0f).roll,
            1e-4f,
        )
    }

    private fun halfDisc(c: Offset, r: Float, fromDeg: Double, n: Int = 360): FloatArray {
        val pts = ArrayList<Float>()
        pts += c.x
        pts += c.y
        for (i in 0..n) {
            val a = Math.toRadians(fromDeg + 180.0 * i / n)
            pts += c.x + (r * cos(a)).toFloat()
            pts += c.y + (r * sin(a)).toFloat()
        }
        return pts.toFloatArray()
    }

    @Test
    fun `a pie with its half pulled out is seen whole from its own centre`() {
        // The rest (left half) at the centre, the slice (right half) pulled 6 to the right:
        // the gap between them runs through the middle of their bounds, but not the centre.
        val c = Offset(50f, 54f)
        val pie = listOf(halfDisc(c, 36f, 90.0), halfDisc(Offset(56f, 54f), 36f, -90.0))
        val p = profileOf(pie, c)
        assertTrue(p.radii.min() > 30f)
    }

    @Test
    fun `each cue's clock stops once it has nothing left to move`() {
        for (cue in MascotCue.entries) {
            val end = arrivalEnd(cue)
            assertTrue("$cue", end > 0f && end <= 1f)
            val at = (end * ARRIVAL_MS).toLong()
            val still = arrived(MascotPose(), cue, at)
            assertEquals("$cue", 0f, still.roll, 0.05f)
            assertEquals("$cue", 0f, still.lift, 1e-4f)
            assertEquals("$cue", 0f, still.gazeX, 1e-4f)
            assertEquals("$cue", 0f, still.gazeY, 1e-4f)
            assertEquals("$cue", 0f, still.blink, 1e-4f)
            // A check, once in, is all the way in by the end, and stays.
            val check = checkAlpha(cue, end)
            assertTrue("$cue", check == 0f || check == 1f)
            assertEquals("$cue", check, checkAlpha(cue, 1f), 0f)
        }
        assertEquals(0f, arrivalEnd(null), 0f)
        assertEquals(1f, stampAlpha(arrivalEnd(MascotCue.Stamp)), 0f)
        COIN_SPARKLES.forEach { assertEquals(0f, sparkleAlpha(it, arrivalEnd(MascotCue.Shine)), 1e-4f) }
        BRIM_SPARKLES.forEach { assertEquals(0f, brimSparkleAlpha(it, arrivalEnd(MascotCue.Brim)), 1e-4f) }
        assertNull(jarCoin(arrivalEnd(MascotCue.CoinDrop)))
        assertEquals(0f, coverAmount(arrivalEnd(MascotCue.CoverEyes)), 0f)
    }

    @Test
    fun `every shape that plays a cue of its own names one with an end`() {
        for (shape in MascotShape.entries) {
            val cue = defaultCue(shape) ?: continue
            assertTrue("$shape", arrivalEnd(cue) > 0f)
        }
        assertNull(defaultCue(MascotShape.Owl))
    }

    @Test
    fun `only a new cue asks for a change, so a dropped one plays out`() {
        val requests = CueRequests(null)
        val owl = requests.idFor(null)
        val hidden = requests.idFor(MascotCue.CoverEyes)
        assertTrue(hidden != owl)
        // The reaction ends: no new request, the wings come down on their own clock.
        assertEquals(hidden, requests.idFor(null))
        // Hidden again, or shown: each a new request.
        val again = requests.idFor(MascotCue.CoverEyes)
        assertTrue(again != hidden)
        assertTrue(requests.idFor(MascotCue.Peek) != again)
        // The same cue asked for again while it stands is no new request.
        val peek = requests.idFor(MascotCue.Peek)
        assertEquals(peek, requests.idFor(MascotCue.Peek))
    }
}
