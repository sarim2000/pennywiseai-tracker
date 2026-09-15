package com.pennywiseai.tracker.domain.service

import kotlinx.coroutines.flow.Flow

/** One piece of a streamed model reply: text, or a tool the model wants called. */
sealed class LlmEvent {
    data class Text(val delta: String) : LlmEvent()
    data class ToolCall(val name: String, val arguments: Map<String, Any?>) : LlmEvent()
}

interface LlmService {
    suspend fun initialize(modelPath: String): Result<Unit>

    /**
     * @param withTools register the app's tool set (#170). Tool calls are never
     *  executed by the runtime — they surface as [LlmEvent.ToolCall] and the app
     *  decides (mutations always go through user confirmation).
     */
    suspend fun createConversation(
        systemPrompt: String,
        history: List<Pair<String, Boolean>>, // message to isUser
        withTools: Boolean = false
    ): Result<Unit>

    fun sendMessage(message: String): Flow<String>
    fun sendMessageEvents(message: String): Flow<LlmEvent>
    fun hasActiveConversation(): Boolean
    suspend fun closeConversation()
    suspend fun reset()
    fun isInitialized(): Boolean
}
