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

    @Test
    fun `a language from both the phone and the keyboard is offered online once`() {
        assertEquals(listOf("ar-JO"), VoiceInput.onlineOnly(listOf("en-US", "ar-JO", "ar"), listOf("en-US")))
    }

    @Test
    fun `regional languages stay apart online`() {
        assertEquals(listOf("ar-JO", "ar-EG"), VoiceInput.onlineOnly(listOf("ar-JO", "ar-EG", "ar"), listOf("en-US")))
    }

    @Test
    fun `a script tag is not a bare language`() {
        assertEquals(listOf("sr-Latn", "sr-RS"), VoiceInput.onlineOnly(listOf("sr-Latn", "sr-RS"), emptyList()))
    }
}
