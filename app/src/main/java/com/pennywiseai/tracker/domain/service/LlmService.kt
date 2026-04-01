package com.pennywiseai.tracker.domain.service

import kotlinx.coroutines.flow.Flow

interface LlmService {
    suspend fun initialize(modelPath: String): Result<Unit>
    suspend fun createConversation(
        systemPrompt: String,
        history: List<Pair<String, Boolean>> // message to isUser
    ): Result<Unit>
    fun sendMessage(message: String): Flow<String>
    fun hasActiveConversation(): Boolean
    suspend fun closeConversation()
    suspend fun reset()
    fun isInitialized(): Boolean
}
