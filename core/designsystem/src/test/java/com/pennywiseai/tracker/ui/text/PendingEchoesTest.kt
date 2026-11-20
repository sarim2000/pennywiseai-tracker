package com.pennywiseai.tracker.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingEchoesTest {
    @Test
    fun `a trailing echo is not an outside change`() {
        val echoes = PendingEchoes()
        echoes.sent("a")
        echoes.sent("ab")
        assertTrue(echoes.isEcho("a"))
        assertEquals("ab", echoes.last("a"))
        assertTrue(echoes.isEcho("ab"))
        assertEquals("ab", echoes.last("ab"))
    }

    @Test
    fun `a value we never sent is an outside change and clears the queue`() {
        val echoes = PendingEchoes()
        echoes.sent("1,0")
        assertFalse(echoes.isEcho("1.0"))
        assertEquals("1.0", echoes.last("1.0"))
    }
}
