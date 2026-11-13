package com.pennywiseai.tracker.presentation.chat.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SpeechLanguageTest {
    @Test
    fun phoneLanguageWinsWhenNothingWasSaved() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US", "ar-SA"),
            pending = emptyList(),
            downloadable = emptyList(),
            userTags = listOf("ar-SA", "en-US"),
            saved = null,
        )
        assertEquals("ar-SA", menu.selected)
        assertEquals(listOf("ar-SA", "en-US"), menu.ready.map { it.tag })
        assertTrue(menu.suggestions.isEmpty())
    }

    @Test
    fun savedLanguageWinsWhenItsPackIsInstalled() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US", "ar-SA"),
            pending = emptyList(),
            downloadable = emptyList(),
            userTags = listOf("en-US"),
            saved = "ar",
        )
        assertEquals("ar-SA", menu.selected)
    }

    @Test
    fun undownloadedUserLanguageIsASuggestion() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US"),
            pending = emptyList(),
            downloadable = listOf("ar-SA", "ar-EG", "hi-IN"),
            userTags = listOf("en-US", "ar"),
            saved = null,
        )
        assertEquals("en-US", menu.selected)
        assertEquals(listOf("ar-SA"), menu.suggestions.map { it.tag })
        assertFalse(menu.suggestions.single().pending)
    }

    @Test
    fun regionalPackBeatsAnotherVariant() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US"),
            pending = emptyList(),
            downloadable = listOf("ar-SA", "ar-EG"),
            userTags = listOf("en-US", "ar-EG"),
            saved = null,
        )
        assertEquals("ar-EG", menu.suggestions.single().tag)
    }

    @Test
    fun pendingPackStaysASuggestion() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US"),
            pending = listOf("ar-SA"),
            downloadable = listOf("ar-SA"),
            userTags = listOf("en-US", "ar-SA"),
            saved = null,
        )
        assertTrue(menu.suggestions.single().pending)
        assertEquals("ar-SA", menu.suggestions.single().tag)
    }

    @Test
    fun installedLanguageIsNotSuggestedAgain() {
        val menu = speechLanguageMenu(
            installed = listOf("ar-SA"),
            pending = emptyList(),
            downloadable = listOf("ar-SA", "ar-EG"),
            userTags = listOf("ar-EG"),
            saved = null,
        )
        assertEquals("ar-SA", menu.selected)
        assertTrue(menu.suggestions.isEmpty())
    }

    @Test
    fun userTagsKeepPhoneThenAppThenKeyboard() {
        assertEquals(
            listOf("ar-SA", "en-US", "hi-IN"),
            userLanguageTags(
                system = listOf("ar_SA", "en-US"),
                app = listOf("en-US"),
                keyboards = listOf("hi-IN", "", "zz", "ar"),
            ),
        )
    }

    @Test
    fun listeningUsesTheMenuThenTheSavedTagThenTheFallback() {
        val menu = SpeechLanguageMenu("ar-SA", emptyList(), emptyList())
        assertEquals("ar-SA", listeningLanguage(menu, "en-US", "fr-FR"))
        assertEquals("en-US", listeningLanguage(null, "en-US", "fr-FR"))
        assertEquals("fr-FR", listeningLanguage(null, null, "fr-FR"))
        assertEquals("en-US", listeningLanguage(SpeechLanguageMenu(null, emptyList(), emptyList()), "en-US", "fr-FR"))
    }

    @Test
    fun namesStayInTheLanguageItself() {
        assertEquals("EN", speechLanguageBadge("en-US"))
        assertEquals("English", speechLanguageUiName("en-US", Locale.ENGLISH))
        val arabic = speechLanguageName("ar-SA")
        assertTrue(arabic.isNotBlank())
        assertEquals(arabic.take(1), speechLanguageBadge("ar-SA"))
        assertFalse(speechLanguageBadge("ar-SA").any { it in 'A'..'Z' })
    }

    @Test
    fun blankMenuSelectsNothing() {
        val menu = speechLanguageMenu(
            installed = emptyList(),
            pending = emptyList(),
            downloadable = emptyList(),
            userTags = listOf("ar-SA"),
            saved = "ar-SA",
        )
        assertNull(menu.selected)
        assertTrue(menu.ready.isEmpty())
    }
}
