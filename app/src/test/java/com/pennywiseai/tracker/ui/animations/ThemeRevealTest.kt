package com.pennywiseai.tracker.ui.animations

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRevealTest {
    @Test
    fun `the circle reaches the corner farthest from the tap`() {
        val screen = Size(300f, 400f)
        // From the top-left corner, the bottom-right one is 500 away (3-4-5).
        assertEquals(500f, farthestCorner(Offset.Zero, screen), 0.01f)
        // From the centre, every corner is equally far.
        assertEquals(250f, farthestCorner(Offset(150f, 200f), screen), 0.01f)
        // Near the bottom-right, it's the top-left that has to be covered.
        assertEquals(500f, farthestCorner(Offset(300f, 400f), screen), 0.01f)
    }

    @Test
    fun `each bar is covered once the circle passes its own corners`() {
        val screen = Size(300f, 400f)
        // A toggle near the top left: the top corners are close, the bottom ones far.
        val tap = Offset(60f, 80f)
        // Top-right corner: 240 across, 80 down.
        assertEquals(252.98f, edgeCover(tap, screen, top = true), 0.01f)
        // Bottom-right corner: 240 across, 320 up (3-4-5), the whole window's far corner.
        assertEquals(400f, edgeCover(tap, screen, top = false), 0.01f)
        assertEquals(farthestCorner(tap, screen), edgeCover(tap, screen, top = false), 0.01f)
        // From the centre, both bars are passed together.
        val centre = Offset(150f, 200f)
        assertEquals(edgeCover(centre, screen, top = true), edgeCover(centre, screen, top = false), 0.01f)
    }

    @Test
    fun `the circle starts at a fresh touch, else at the centre`() {
        val screen = Size(300f, 400f)
        val tap = Offset(40f, 80f)
        val centre = Offset(150f, 200f)
        assertEquals(tap, revealOrigin(tap, touchedAt = 5_000L, now = 5_200L, size = screen))
        // A change long after the last touch (a backup restore) didn't come from it.
        assertEquals(centre, revealOrigin(tap, touchedAt = 5_000L, now = 5_000L + TOUCH_CAUSE_MS + 1, size = screen))
        // No touch yet.
        assertEquals(centre, revealOrigin(Offset.Unspecified, touchedAt = 0L, now = 5_000L, size = screen))
    }
}
