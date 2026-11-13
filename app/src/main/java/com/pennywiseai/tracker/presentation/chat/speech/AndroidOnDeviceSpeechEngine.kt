package com.pennywiseai.tracker.presentation.chat.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.Locale

private const val TAG = "PennyWiseSpeech"

/**
 * Dictation for the on-device assistant. Audio is recognized by the system's
 * on-device speech service and is never sent to PennyWise or to a cloud recognizer.
 * A downloaded Whisper/Qwen model would also stay on device, but it is a second
 * large download beside the chat model; the system recognizer is already on an S23.
 * The language passed to [start] is the dictation language, not the app language.
 * Below Android 12, or when that service is missing, dictation is unavailable
 * rather than falling back to a network recognizer.
 */
class AndroidOnDeviceSpeechEngine(context: Context) : SpeechEngine {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    override fun available(): Boolean = onDeviceSpeechAvailable(appContext)

    override fun start(languageTag: String, callbacks: SpeechCallbacks) {
        cancel()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !available()) {
            callbacks.onError(SpeechErrorCode.UNAVAILABLE)
            return
        }
        val gen = ++generation
        val created = try {
            create(appContext)
        } catch (error: RuntimeException) {
            Log.w(TAG, "On-device recognizer failed to start", error)
            callbacks.onError(SpeechErrorCode.UNAVAILABLE)
            return
        }
        recognizer = created
        created.setRecognitionListener(listener(gen, callbacks))
        val tag = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
        Log.i(TAG, "Listening, language $tag")
        try {
            created.startListening(listenIntent(languageTag))
        } catch (error: RuntimeException) {
            Log.w(TAG, "startListening failed", error)
            cancel()
            callbacks.onError(SpeechErrorCode.UNAVAILABLE)
        }
    }

    override fun stopListening(): Boolean {
        val current = recognizer ?: return false
        return try {
            // Leave the recognizer alive. onResults or onError destroys it.
            Log.i(TAG, "Stopping, waiting for the final result")
            current.stopListening()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "stopListening failed", error)
            false
        }
    }

    override fun cancel() {
        generation++
        val current = recognizer ?: return
        recognizer = null
        try {
            current.cancel()
        } catch (error: RuntimeException) {
            Log.w(TAG, "cancel failed", error)
        }
        try {
            current.destroy()
        } catch (error: RuntimeException) {
            Log.w(TAG, "destroy failed", error)
        }
    }

    private fun listener(gen: Int, callbacks: SpeechCallbacks) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (gen != generation) return
            val text = hypothesis(partialResults)
            if (text.isNotEmpty()) callbacks.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (gen != generation) return
            finish()
            callbacks.onFinal(hypothesis(results))
        }

        override fun onError(error: Int) {
            if (gen != generation) return
            Log.i(TAG, "Recognizer error $error")
            finish()
            callbacks.onError(error)
        }
    }

    private fun finish() {
        // destroy() can deliver another error on this listener. Retire it first.
        generation++
        val current = recognizer ?: return
        recognizer = null
        try {
            current.destroy()
        } catch (error: RuntimeException) {
            Log.w(TAG, "destroy failed", error)
        }
    }
}

internal fun onDeviceSpeechAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
}

@RequiresApi(Build.VERSION_CODES.S)
internal fun create(context: Context): SpeechRecognizer =
    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

private fun listenIntent(languageTag: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        val tag = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        // A short money phrase should not be cut off, and a pause should end the phrase.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_400)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200)
        // Google's on-device recognizer formats "twenty" as "20" when it honors this.
        putExtra("android.speech.extra.ENABLE_FORMATTING", "quality")
    }

/** Best transcript in the bundle. Partials sometimes arrive only as unstable text. */
internal fun hypothesis(bundle: Bundle?): String {
    if (bundle == null) return ""
    val stable = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val unstable = bundle.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
    return (stable.best() ?: unstable.best()).orEmpty()
}

private fun ArrayList<String>?.best(): String? =
    this?.firstOrNull { it.isNotBlank() }?.trim()
