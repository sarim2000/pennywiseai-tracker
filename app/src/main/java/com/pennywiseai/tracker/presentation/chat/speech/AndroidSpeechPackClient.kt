package com.pennywiseai.tracker.presentation.chat.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

private const val TAG = "PennyWiseSpeech"

/**
 * Asks the on-device recognizer which language packs are installed, pending,
 * or available to download, and starts a download. A separate recognizer from
 * dictation: it is destroyed when the query or the download finishes.
 */
internal class AndroidSpeechPackClient(context: Context) {
    private val appContext = context.applicationContext
    private var queryRecognizer: SpeechRecognizer? = null
    private var downloadRecognizer: SpeechRecognizer? = null
    private var queryGeneration = 0
    private var downloadGeneration = 0

    fun query(onResult: (SpeechPackReport) -> Unit) {
        val gen = ++queryGeneration
        releaseQuery()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !onDeviceSpeechAvailable(appContext)) {
            onResult(SpeechPackReport())
            return
        }
        val created = open() ?: run {
            onResult(SpeechPackReport())
            return
        }
        queryRecognizer = created
        try {
            created.checkRecognitionSupport(
                packIntent(language = null),
                ContextCompat.getMainExecutor(appContext),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (gen != queryGeneration) return
                        val report = support.toReport()
                        // Drop this recognizer before the caller opens another one.
                        releaseQuery()
                        onResult(report)
                    }

                    override fun onError(error: Int) {
                        if (gen != queryGeneration) return
                        Log.i(TAG, "Language support error $error")
                        releaseQuery()
                        onResult(SpeechPackReport())
                    }
                },
            )
        } catch (error: RuntimeException) {
            Log.w(TAG, "checkRecognitionSupport failed", error)
            releaseQuery()
            onResult(SpeechPackReport())
        }
    }

    fun download(tag: String, onFinished: (Boolean) -> Unit) {
        val gen = ++downloadGeneration
        releaseDownload()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onFinished(false)
            return
        }
        val created = open() ?: run {
            onFinished(false)
            return
        }
        downloadRecognizer = created
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                downloadWithListener(created, tag, gen, onFinished)
            } else {
                created.triggerModelDownload(packIntent(tag))
                releaseDownload()
                onFinished(true)
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "triggerModelDownload failed", error)
            releaseDownload()
            onFinished(false)
        }
    }

    fun close() {
        queryGeneration++
        downloadGeneration++
        releaseQuery()
        releaseDownload()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun downloadWithListener(
        created: SpeechRecognizer,
        tag: String,
        gen: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        created.triggerModelDownload(
            packIntent(tag),
            ContextCompat.getMainExecutor(appContext),
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) = Unit

                override fun onSuccess() = finish(true)

                override fun onScheduled() = finish(true)

                override fun onError(error: Int) {
                    Log.i(TAG, "Language download error $error")
                    finish(false)
                }

                private fun finish(ok: Boolean) {
                    if (gen != downloadGeneration) return
                    // Drop this recognizer before the caller starts a fresh query.
                    releaseDownload()
                    onFinished(ok)
                }
            },
        )
    }

    private fun open(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            create(appContext).also { it.setRecognitionListener(QuietRecognitionListener) }
        } catch (error: RuntimeException) {
            Log.w(TAG, "On-device recognizer failed to open", error)
            null
        }
    }

    private fun releaseQuery() {
        val current = queryRecognizer ?: return
        queryRecognizer = null
        destroy(current)
    }

    private fun releaseDownload() {
        val current = downloadRecognizer ?: return
        downloadRecognizer = null
        destroy(current)
    }

    private fun destroy(recognizer: SpeechRecognizer) {
        try {
            recognizer.cancel()
        } catch (error: RuntimeException) {
            Log.w(TAG, "cancel failed", error)
        }
        try {
            recognizer.destroy()
        } catch (error: RuntimeException) {
            Log.w(TAG, "destroy failed", error)
        }
    }
}

/** Satisfies the "set a listener before any command" rule. Pack queries use their own callbacks. */
private object QuietRecognitionListener : RecognitionListener {
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onError(error: Int) = Unit
    override fun onResults(results: Bundle?) = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

private fun packIntent(language: String?): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (!language.isNullOrBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    }

private fun RecognitionSupport.toReport(): SpeechPackReport = SpeechPackReport(
    installed = installedOnDeviceLanguages,
    pending = pendingOnDeviceLanguages,
    downloadable = supportedOnDeviceLanguages,
    known = true,
)
