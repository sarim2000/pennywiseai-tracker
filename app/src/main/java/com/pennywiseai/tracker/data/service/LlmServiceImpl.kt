package com.pennywiseai.tracker.data.service

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Backend
import com.pennywiseai.tracker.domain.service.LlmService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmService {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    override suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )

            engine = Engine(engineConfig).also { it.initialize() }
            Log.d(TAG, "Engine initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize engine", e)
            Result.failure(e)
        }
    }

    override suspend fun createConversation(
        systemPrompt: String,
        history: List<Pair<String, Boolean>>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val currentEngine = engine ?: return@withContext Result.failure(
                IllegalStateException("Engine not initialized")
            )

            // Close any existing conversation
            conversation?.close()

            val initialMessages = history.map { (message, isUser) ->
                if (isUser) Message.user(message) else Message.model(message)
            }

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = initialMessages,
                samplerConfig = SamplerConfig(
                    topK = 10,
                    topP = 0.95,
                    temperature = 0.8
                )
            )

            conversation = currentEngine.createConversation(conversationConfig)
            Log.d(TAG, "Conversation created with ${history.size} history messages")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create conversation", e)
            Result.failure(e)
        }
    }

    override fun sendMessage(message: String): Flow<String> {
        val activeConversation = conversation
            ?: throw IllegalStateException("No active conversation")

        Log.d(TAG, "Sending message: ${message.take(50)}...")

        return activeConversation.sendMessageAsync(message)
            .catch { e ->
                Log.e(TAG, "Error during streaming response", e)
                throw e
            }
            .map { message ->
                message.contents.contents
                    .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                    .joinToString("") { it.text }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun hasActiveConversation(): Boolean = conversation != null

    override suspend fun closeConversation() {
        withContext(Dispatchers.IO) {
            conversation?.close()
            conversation = null
            Log.d(TAG, "Conversation closed")
        }
    }

    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            Log.d(TAG, "Engine and conversation reset")
        }
    }

    override fun isInitialized(): Boolean = engine != null

    companion object {
        private const val TAG = "LlmServiceImpl"
    }
}
