package com.pennywiseai.tracker.ui.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingEchoesTest {
    @Test
    fun `a late echo doesn't undo newer typing`() {
        val echoes = PendingEchoes()
        echoes.sent("a")
        echoes.sent("ab")
        assertTrue(echoes.isEcho("a", shown = "ab"))
        assertTrue(echoes.isEcho("ab", shown = "ab"))
    }

    @Test
    fun `a value we never sent is an outside change`() {
        val echoes = PendingEchoes()
        echoes.sent("1,0")
        assertFalse(echoes.isEcho("1.0", shown = "1,0"))
    }

    @Test
    fun `an owner that skipped an edit doesn't swallow a later reset to it`() {
        val echoes = PendingEchoes()
        echoes.sent("x")
        echoes.sent("xy")
        echoes.sent("x")
        // The owner only ever renders the last "x".
        assertTrue(echoes.isEcho("x", shown = "x"))
        assertFalse(echoes.isEcho("xy", shown = "x"))
    }
}
