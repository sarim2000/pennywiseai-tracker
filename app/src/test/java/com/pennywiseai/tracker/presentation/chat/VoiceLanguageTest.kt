package com.pennywiseai.tracker.presentation.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceLanguageTest {
    private fun pick(chosen: String?, usable: List<String>, phone: List<String>) =
        VoiceInput.pickLanguage(chosen, usable, phone)

    @Test
    fun `saved pick wins while it is still usable`() {
        assertEquals("ar-JO", pick("ar-JO", listOf("en-US", "ar-JO"), listOf("en-US", "ar-JO")))
    }

    @Test
    fun `a saved pick that went away falls back to the phone's first language`() {
        assertEquals("en-US", pick("hi-IN", listOf("en-US", "ar-JO"), listOf("en-US", "ar-JO")))
    }

    @Test
    fun `phone language matches another region of the same language`() {
        assertEquals("en-US", pick(null, listOf("fr-FR", "en-US"), listOf("en-GB")))
    }

    @Test
    fun `exact region beats same language`() {
        assertEquals("en-GB", pick(null, listOf("en-US", "en-GB"), listOf("en-GB")))
    }

    @Test
    fun `nothing usable means no language`() {
        assertNull(pick(null, emptyList(), listOf("en-US")))
    }

    @Test
    fun `only phone languages with no on-device model at all go online`() {
        // Arabic has no on-device model; English and Hindi have one (installed or downloadable).
        assertEquals(
            listOf("ar-JO"),
            VoiceInput.onlineOnly(listOf("en-GB", "ar-JO", "hi-IN"), listOf("en-US", "hi-IN"))
        )
    }
}
