package com.pennywiseai.tracker.presentation.chat.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechInputTest {
    @Test
    fun partialsReplaceThePhraseInsteadOfAppending() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("rent")
        val callbacks = engine.callbacks!!

        callbacks.onPartial("five")
        callbacks.onPartial("five thousand")

        assertEquals("rent five thousand", speech.draft.display())
        assertTrue(speech.listening)
        assertEquals(2, speech.revision)
    }

    @Test
    fun finalCommitsThePhraseAndStops() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("")
        engine.callbacks!!.onPartial("coffee 20")
        engine.callbacks!!.onFinal("coffee 20 rupees")

        assertFalse(speech.listening)
        assertEquals("coffee 20 rupees", speech.draft.display())
        assertEquals("", speech.draft.heard)
        assertNull(speech.failure)
    }

    @Test
    fun emptyFinalKeepsTheLastPartial() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("")
        engine.callbacks!!.onPartial("uber 90")
        engine.callbacks!!.onFinal("")

        assertEquals("uber 90", speech.draft.display())
        assertFalse(speech.listening)
    }

    @Test
    fun timeoutAfterWordsIsNotAnError() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("lunch")
        engine.callbacks!!.onPartial("three hundred")
        engine.callbacks!!.onError(SpeechErrorCode.TIMEOUT)

        assertEquals("lunch three hundred", speech.draft.display())
        assertNull(speech.failure)
        assertFalse(speech.listening)
    }

    @Test
    fun noMatchWithNothingHeardIsShown() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("keep me")
        engine.callbacks!!.onError(SpeechErrorCode.NO_MATCH)

        assertEquals("keep me", speech.draft.display())
        assertEquals(SpeechFailure.NO_MATCH, speech.failure)
    }

    @Test
    fun languageAndNetworkErrorsAskForTheOnDeviceLanguage() {
        assertEquals(SpeechFailure.LANGUAGE, speechFailure(SpeechErrorCode.LANGUAGE_UNAVAILABLE))
        assertEquals(SpeechFailure.LANGUAGE, speechFailure(SpeechErrorCode.NETWORK))
        assertEquals(SpeechFailure.PERMISSION, speechFailure(SpeechErrorCode.PERMISSIONS))
        assertEquals(SpeechFailure.UNKNOWN, speechFailure(SpeechErrorCode.CLIENT))
    }

    @Test
    fun unavailableDoesNotStart() {
        val engine = FakeEngine(available = false)
        val speech = SpeechInput(engine)
        speech.start("hello")

        assertEquals(SpeechFailure.UNAVAILABLE, speech.failure)
        assertEquals(0, engine.starts)
        assertFalse(speech.listening)
    }

    @Test
    fun aCallbackAfterTheFinalResultDoesNotChangeThePhrase() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("")
        val callbacks = engine.callbacks!!
        callbacks.onFinal("coffee 20")
        val revision = speech.revision
        callbacks.onPartial("coffee 20")
        callbacks.onError(SpeechErrorCode.CLIENT)

        assertEquals("coffee 20", speech.draft.display())
        assertEquals("", speech.draft.heard)
        assertNull(speech.failure)
        assertFalse(speech.listening)
        assertEquals(revision, speech.revision)
    }

    @Test
    fun stopKeepsThePartialAndIgnoresALateCallback() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("")
        val callbacks = engine.callbacks!!
        callbacks.onPartial("fifty")
        speech.stop()
        callbacks.onPartial("fifty thousand")
        callbacks.onFinal("fifty thousand")

        assertEquals("fifty", speech.draft.display())
        assertFalse(speech.listening)
        assertEquals(1, engine.cancels)
    }

    @Test
    fun typingWhileListeningWinsOverLaterPartials() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("hello")
        val callbacks = engine.callbacks!!
        callbacks.onPartial("there")
        speech.keepTyped("hello th")
        callbacks.onPartial("there now")

        assertEquals("hello th", speech.draft.display())
        assertFalse(speech.listening)
        assertEquals(1, speech.revision)
    }

    @Test
    fun discardDropsThePhraseAndIgnoresALateResult() {
        val engine = FakeEngine()
        val speech = SpeechInput(engine)
        speech.start("")
        val callbacks = engine.callbacks!!
        callbacks.onPartial("coffee 20")
        val revision = speech.revision
        speech.discard()
        callbacks.onFinal("coffee 20")

        assertEquals("", speech.draft.display())
        assertFalse(speech.listening)
        assertEquals(revision, speech.revision)
    }

    @Test
    fun sendUsesTheFieldOnceListeningHasStopped() {
        assertEquals("five dollar coffee", textToSend("five dollar coffee", "five dollar pizza", listening = false))
        assertEquals("", textToSend("", "five dollar pizza", listening = false))
    }

    @Test
    fun sendUsesTheDraftWhileStillListening() {
        assertEquals("five dollar pizza", textToSend("five", "five dollar pizza", listening = true))
    }

    @Test
    fun blankPartialDoesNotWipeTheBase() {
        val draft = SpeechDraft("chai 15", "")
        assertEquals("chai 15", draft.withHeard("   ").display())
        assertEquals("chai 15 rupees", draft.withHeard("  rupees ").display())
    }
}

private class FakeEngine(private val available: Boolean = true) : SpeechEngine {
    var callbacks: SpeechCallbacks? = null
    var starts = 0
    var cancels = 0

    override fun available(): Boolean = available

    override fun start(callbacks: SpeechCallbacks) {
        starts++
        this.callbacks = callbacks
    }

    override fun cancel() {
        cancels++
    }
}
