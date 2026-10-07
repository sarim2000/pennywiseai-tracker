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
import com.google.ai.edge.litertlm.tool
import com.pennywiseai.tracker.domain.service.LlmEvent
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
import com.pennywiseai.tracker.domain.service.LlmService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmService {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    // Create / close / reset run on Dispatchers.IO from different callers; without
    // this two closes could race on one Conversation, and LiteRT throws
    // IllegalStateException for a close it can't perform (a 2.21.0 crash).
    private val lifecycle = Mutex()

    override suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        lifecycle.withLock { initializeLocked(modelPath) }
    }

    private fun initializeLocked(modelPath: String): Result<Unit> {
        return try {
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
        history: List<Pair<String, Boolean>>,
        withTools: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) { lifecycle.withLock {
        try {
            val currentEngine = engine ?: return@withLock Result.failure(
                IllegalStateException("Engine not initialized")
            )

            // Close any existing conversation. Taken first, so a failed close can't
            // leave a dead conversation behind for the next caller to close again.
            takeConversation()?.closeQuietly()

            val initialMessages = history.map { (message, isUser) ->
                if (isUser) Message.user(message) else Message.model(message)
            }

            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = initialMessages,
                // Tool calls are surfaced, never auto-executed (#170): every
                // mutation goes through the user first.
                tools = if (withTools) listOf(tool(PennyWiseTools())) else emptyList(),
                automaticToolCalling = false,
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
    } }

    override fun sendMessage(message: String): Flow<String> =
        sendMessageEvents(message).mapNotNull { (it as? LlmEvent.Text)?.delta }

    override fun sendMessageEvents(message: String): Flow<LlmEvent> {
        val activeConversation = conversation
            ?: throw IllegalStateException("No active conversation")

        Log.d(TAG, "Sending message: ${message.take(50)}...")

        return activeConversation.sendMessageAsync(message)
            .catch { e ->
                Log.e(TAG, "Error during streaming response", e)
                throw e
            }
            .transform { chunk ->
                val text = chunk.contents.contents
                    .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                    .joinToString("") { it.text }
                if (text.isNotEmpty()) emit(LlmEvent.Text(text))
                chunk.toolCalls.forEach { emit(LlmEvent.ToolCall(it.name, it.arguments)) }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun hasActiveConversation(): Boolean = conversation != null

    override suspend fun closeConversation() {
        withContext(Dispatchers.IO) {
            lifecycle.withLock { takeConversation()?.closeQuietly() }
            Log.d(TAG, "Conversation closed")
        }
    }

    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            lifecycle.withLock {
                takeConversation()?.closeQuietly()
                engine?.let { e -> runCatching { e.close() }.onFailure { Log.w(TAG, "Ignoring failed engine close", it) } }
                engine = null
            }
            Log.d(TAG, "Engine and conversation reset")
        }
    }

    private fun takeConversation(): Conversation? = conversation.also { conversation = null }

    /** Closing is cleanup: a close LiteRT refuses (already closed, mid-generation) must not crash the app. */
    private fun Conversation.closeQuietly() {
        runCatching { close() }.onFailure { Log.w(TAG, "Ignoring failed conversation close", it) }
    }

    override fun isInitialized(): Boolean = engine != null

    companion object {
        private const val TAG = "LlmServiceImpl"
    }
}
