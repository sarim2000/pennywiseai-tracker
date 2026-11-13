package com.pennywiseai.tracker.presentation.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Home's mic asks the chat tab to start dictating. [token] grows on each tap.
 * Chat sets [handled] once it has started (or given up because the model is absent),
 * so leaving and reopening the chat does not start the microphone again.
 */
class VoiceAsk {
    var token by mutableIntStateOf(0)
        private set
    var handled by mutableIntStateOf(0)

    fun request() {
        token += 1
    }
}
