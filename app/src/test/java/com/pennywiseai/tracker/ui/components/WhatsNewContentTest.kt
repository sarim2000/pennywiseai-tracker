package com.pennywiseai.tracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewContentTest {

    @Test
    fun `play changelog without a title keeps every bullet as an item`() {
        val parsed = WhatsNewContent.parseChangelog("• First change\n• Second change\n")!!
        assertEquals(listOf("First change", "Second change"), parsed.items.map { it.text })
    }

    @Test
    fun `legacy title line is skipped`() {
        val parsed = WhatsNewContent.parseChangelog("What's New in v2.15.44\n\n• Only change")!!
        assertEquals(listOf("Only change"), parsed.items.map { it.text })
    }

    @Test
    fun `no bullets means no dialog`() {
        assertNull(WhatsNewContent.parseChangelog("   \n"))
    }
}
