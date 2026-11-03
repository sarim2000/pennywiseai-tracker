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
import kotlinx.coroutines.flow.onEach
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

    // True from a GPU start until its first streamed reply; KEY_GPU_FAILED stays armed meanwhile.
    @Volatile private var gpuUnconfirmed = false

    private val backendPrefs by lazy { context.getSharedPreferences("llm_backend", Context.MODE_PRIVATE) }

    override suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        lifecycle.withLock { initializeLocked(modelPath) }
    }

    private fun initializeLocked(modelPath: String): Result<Unit> {
        return try {
            // GPU first; devices without a usable OpenCL/GPU delegate throw here, so fall back to CPU.
            // A buggy driver can instead crash natively, which no catch sees: the flag is
            // written before trying and cleared only once the GPU streams its first reply
            // (a driver can also die creating the conversation or generating), so a GPU
            // attempt that crashed or threw is never retried and later starts go to CPU.
            val cpu = {
                gpuUnconfirmed = false
                startEngine(modelPath, Backend.CPU()).also { Log.i(TAG, "Engine initialized on CPU backend") }
            }
            engine = if (backendPrefs.getBoolean(KEY_GPU_FAILED, false)) {
                cpu()
            } else try {
                backendPrefs.edit().putBoolean(KEY_GPU_FAILED, true).commit()
                startEngine(modelPath, Backend.GPU()).also {
                    gpuUnconfirmed = true
                    Log.i(TAG, "Engine initialized on GPU backend")
                }
            } catch (e: Throwable) { // incl. UnsatisfiedLinkError when the OpenCL lib is missing
                Log.w(TAG, "GPU backend unavailable (${e.javaClass.simpleName}), falling back to CPU")
                cpu()
            }
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
            .onEach {
                if (gpuUnconfirmed) {
                    gpuUnconfirmed = false
                    backendPrefs.edit().putBoolean(KEY_GPU_FAILED, false).apply()
                }
            }
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

    /** Builds and initializes an engine on [backend], releasing it if initialization fails. */
    private fun startEngine(modelPath: String, backend: Backend): Engine {
        val e = Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = context.cacheDir.path))
        try {
            e.initialize()
        } catch (t: Throwable) {
            runCatching { e.close() }
            throw t
        }
        return e
    }

    private fun takeConversation(): Conversation? = conversation.also { conversation = null }

    /** Closing is cleanup: a close LiteRT refuses (already closed, mid-generation) must not crash the app. */
    private fun Conversation.closeQuietly() {
        runCatching { close() }.onFailure { Log.w(TAG, "Ignoring failed conversation close", it) }
    }

    override fun isInitialized(): Boolean = engine != null

    companion object {
        private const val TAG = "LlmServiceImpl"
        private const val KEY_GPU_FAILED = "gpu_failed"
    }
}
