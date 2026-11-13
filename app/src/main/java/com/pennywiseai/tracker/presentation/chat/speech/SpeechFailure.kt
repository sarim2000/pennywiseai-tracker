package com.pennywiseai.tracker.presentation.chat.speech

import com.pennywiseai.tracker.R

/**
 * Why dictation stopped, in words the chat can show.
 * [opensSettings] is a dead end the user can only fix in system settings.
 */
enum class SpeechFailure {
    UNAVAILABLE,
    PERMISSION,
    BLOCKED,
    NO_MATCH,
    TIMEOUT,
    AUDIO,
    BUSY,
    LANGUAGE,
    UNKNOWN,
    ;

    val opensSettings: Boolean
        get() = this == UNAVAILABLE || this == LANGUAGE || this == BLOCKED || this == PERMISSION

    fun messageRes(): Int = when (this) {
        UNAVAILABLE -> R.string.chat_voice_unavailable
        PERMISSION, BLOCKED -> R.string.chat_voice_blocked
        NO_MATCH -> R.string.chat_voice_no_match
        TIMEOUT -> R.string.chat_voice_timeout
        AUDIO -> R.string.chat_voice_audio
        BUSY -> R.string.chat_voice_busy
        LANGUAGE -> R.string.chat_voice_language
        UNKNOWN -> R.string.chat_voice_error
    }
}

/**
 * Framework [android.speech.SpeechRecognizer] error constants. Duplicated so the
 * mapping can be tested without the Android speech classes. The values are the
 * public API and have not changed.
 */
internal object SpeechErrorCode {
    const val UNAVAILABLE = -1
    const val NETWORK_TIMEOUT = 1
    const val NETWORK = 2
    const val AUDIO = 3
    const val SERVER = 4
    const val CLIENT = 5
    const val TIMEOUT = 6
    const val NO_MATCH = 7
    const val BUSY = 8
    const val PERMISSIONS = 9
    const val SERVER_DISCONNECTED = 11
    const val LANGUAGE_UNSUPPORTED = 12
    const val LANGUAGE_UNAVAILABLE = 13
}

fun speechFailure(code: Int): SpeechFailure = when (code) {
    SpeechErrorCode.UNAVAILABLE -> SpeechFailure.UNAVAILABLE
    SpeechErrorCode.AUDIO -> SpeechFailure.AUDIO
    SpeechErrorCode.TIMEOUT -> SpeechFailure.TIMEOUT
    SpeechErrorCode.NO_MATCH -> SpeechFailure.NO_MATCH
    SpeechErrorCode.BUSY -> SpeechFailure.BUSY
    SpeechErrorCode.PERMISSIONS -> SpeechFailure.PERMISSION
    SpeechErrorCode.NETWORK_TIMEOUT,
    SpeechErrorCode.NETWORK,
    SpeechErrorCode.SERVER,
    SpeechErrorCode.SERVER_DISCONNECTED,
    SpeechErrorCode.LANGUAGE_UNSUPPORTED,
    SpeechErrorCode.LANGUAGE_UNAVAILABLE,
    -> SpeechFailure.LANGUAGE
    else -> SpeechFailure.UNKNOWN
}
