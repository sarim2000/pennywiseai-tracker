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
        assertTrue(menu.catalog.isEmpty())
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
        assertTrue(menu.catalog.isEmpty())
    }

    @Test
    fun catalogOffersLanguagesThePersonDoesNotAlreadyUse() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US"),
            pending = emptyList(),
            downloadable = listOf("en-GB", "fr-CA", "fr-FR", "de-AT", "de-DE", "ar-EG", "ar-SA"),
            userTags = listOf("en-US", "ar-SA"),
            saved = null,
        )
        assertEquals(listOf("ar-SA"), menu.suggestions.map { it.tag })
        assertEquals(listOf("de-DE", "fr-FR"), menu.catalog.map { it.tag })
        assertFalse(menu.catalog.any { it.pending })
    }

    @Test
    fun catalogPrefersTheMainArabicRegion() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US"),
            pending = emptyList(),
            downloadable = listOf("ar-EG", "ar-SA"),
            userTags = listOf("en-US"),
            saved = null,
        )
        assertEquals(listOf("ar-SA"), menu.catalog.map { it.tag })
    }

    @Test
    fun catalogMarksAPendingPackAndKeepsChineseScriptsApart() {
        val menu = speechLanguageMenu(
            installed = listOf("en-US"),
            pending = listOf("ja-JP"),
            downloadable = listOf("ja-JP", "cmn-Hans-CN", "cmn-Hant-TW"),
            userTags = listOf("en-US"),
            saved = null,
        )
        val japanese = menu.catalog.single { packGroup(it.tag) == packGroup("ja-JP") }
        assertEquals("ja-JP", japanese.tag)
        assertTrue(japanese.pending)
        val scripts = menu.catalog.map { packGroup(it.tag) }.filter { it.startsWith("zh") || it.startsWith("cmn") }
        assertEquals(scripts.toSet().size, scripts.size)
        assertTrue(scripts.size >= 1)
    }

    @Test
    fun unknownPacksKeepTheSavedLanguage() {
        val menu = speechMenuFor(
            report = SpeechPackReport(),
            userTags = listOf("en-US"),
            saved = "fr-FR",
        )
        assertEquals("fr-FR", menu.selected)
        assertTrue(menu.ready.map { it.tag }.contains("fr-FR"))
        assertTrue(menu.catalog.isEmpty())
        assertEquals("fr-FR", listeningLanguage(menu, "fr-FR", "en-US"))
    }

    @Test
    fun unknownPacksWithoutASavedChoiceUseThePhoneLanguage() {
        val menu = speechMenuFor(
            report = SpeechPackReport(),
            userTags = listOf("ar-JO", "en-US"),
            saved = null,
        )
        assertEquals("ar-JO", menu.selected)
    }

    @Test
    fun watchKeepsAPendingPackAndSelectsItOnceInstalled() {
        val group = packGroup("fr-FR")
        val pending = watchDownload(
            report = SpeechPackReport(
                installed = listOf("en-US"),
                pending = listOf("fr-FR"),
                downloadable = listOf("fr-FR"),
                known = true,
            ),
            group = group,
            elapsedMs = 60_000,
        )
        assertNull(pending.installedTag)
        assertTrue(pending.keepWaiting)

        val ready = watchDownload(
            report = SpeechPackReport(installed = listOf("en-US", "fr-FR"), known = true),
            group = group,
            elapsedMs = 60_000,
        )
        assertEquals("fr-FR", ready.installedTag)
        assertFalse(ready.keepWaiting)
    }

    @Test
    fun watchGivesUpWhenThePackNeverStarts() {
        val waiting = watchDownload(
            report = SpeechPackReport(installed = listOf("en-US"), downloadable = listOf("de-DE"), known = true),
            group = packGroup("de-DE"),
            elapsedMs = 1_000,
        )
        assertTrue(waiting.keepWaiting)
        val givenUp = watchDownload(
            report = SpeechPackReport(installed = listOf("en-US"), downloadable = listOf("de-DE"), known = true),
            group = packGroup("de-DE"),
            elapsedMs = DOWNLOAD_GRACE_MS,
        )
        assertNull(givenUp.installedTag)
        assertFalse(givenUp.keepWaiting)
    }

    @Test
    fun chineseScriptsDoNotShareADetail() {
        val hans = speechPackDetail("cmn-Hans-CN", Locale.ENGLISH)
        val hant = speechPackDetail("cmn-Hant-TW", Locale.ENGLISH)
        assertFalse(hans.isNullOrBlank())
        assertFalse(hant.isNullOrBlank())
        assertTrue(hans != hant)
    }

    @Test
    fun watchDoesNotTreatAnotherScriptAsTheRequestedPack() {
        val hans = packGroup("cmn-Hans-CN")
        val hant = packGroup("cmn-Hant-TW")
        if (hans == hant) return
        val decision = watchDownload(
            report = SpeechPackReport(installed = listOf("cmn-Hans-CN"), known = true),
            group = hant,
            elapsedMs = DOWNLOAD_GRACE_MS,
        )
        assertNull(decision.installedTag)
        assertFalse(decision.keepWaiting)
    }
}
