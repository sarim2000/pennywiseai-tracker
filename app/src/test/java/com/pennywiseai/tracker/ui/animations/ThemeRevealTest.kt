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
