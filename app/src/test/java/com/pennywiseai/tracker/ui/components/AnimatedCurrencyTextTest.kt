package com.pennywiseai.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedCurrencyTextTest {

    @Test
    fun `a much shorter amount still clips the outgoing layout in full`() {
        // `$123,456,789` → `$1`: the old digits sit far past twice the new width.
        val clip = rollClip(newWidth = 28f, oldWidth = 420f, anchor = 0f, pad = 28f)
        assertEquals(-28f, clip.left, 0f)
        assertEquals(448f, clip.right, 0f)
        assertTrue(clip.right >= 420f)
    }

    @Test
    fun `equal widths keep a pad on each side`() {
        val clip = rollClip(newWidth = 100f, oldWidth = 100f, anchor = 0f, pad = 100f)
        assertEquals(-100f, clip.left, 0f)
        assertEquals(200f, clip.right, 0f)
    }

    @Test
    fun `an rtl shrink anchors the old layout and still covers it`() {
        val newWidth = 28f
        val oldWidth = 420f
        val anchor = newWidth - oldWidth
        val clip = rollClip(newWidth, oldWidth, anchor, pad = newWidth)
        assertTrue(clip.left <= anchor)
        assertTrue(clip.right >= newWidth)
        assertTrue(clip.right >= oldWidth + anchor)
    }
}
