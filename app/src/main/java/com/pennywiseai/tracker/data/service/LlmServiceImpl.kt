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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import com.pennywiseai.tracker.domain.service.LlmService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

    // What the engine and the live conversation were built from, so a GPU that fails
    // its first reply can be swapped for the CPU in the middle of that send.
    private var modelPath: String? = null
    private var conversationConfig: ConversationConfig? = null

    // Create / close / reset run on Dispatchers.IO from different callers; without
    // this two closes could race on one Conversation, and LiteRT throws
    // IllegalStateException for a close it can't perform (a 2.21.0 crash).
    private val lifecycle = Mutex()

    // True from a GPU start until its first reply completes; KEY_GPU_FAILED stays armed meanwhile.
    @Volatile private var gpuUnconfirmed = false

    private val backendPrefs by lazy { context.getSharedPreferences("llm_backend", Context.MODE_PRIVATE) }

    override suspend fun initialize(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        lifecycle.withLock { initializeLocked(modelPath) }
    }

    private fun initializeLocked(modelPath: String): Result<Unit> {
        // A caller that queued behind another start (or the CPU retry) must not replace,
        // and leak, the engine the live conversation runs on.
        if (engine != null) return Result.success(Unit)
        this.modelPath = modelPath
        return try {
            // GPU first; devices without a usable OpenCL/GPU delegate throw here, so fall back to CPU.
            // A buggy driver can instead crash natively, which no catch sees: the flag is
            // written before trying and cleared only once the GPU finishes its first reply
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

            val config = chatConversationConfig(systemPrompt, history, withTools)
            conversation = currentEngine.createConversation(config)
            conversationConfig = config
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

        Log.d(TAG, "Sending message (${message.length} chars)")

        // A GPU engine can start fine and only fail once it generates (the emulator's
        // "Can not find OpenCL library"). Rather than fail the user's first message,
        // retry it once on the CPU, if nothing was streamed yet.
        return flow {
            val onUnconfirmedGpu = gpuUnconfirmed
            var streamed = false
            emitAll(
                stream(activeConversation, message)
                    .onEach { streamed = true }
                    .catch { e ->
                        if (e is CancellationException || !onUnconfirmedGpu || streamed) throw e
                        Log.w(TAG, "GPU failed its first reply (${e.javaClass.simpleName}), retrying on CPU")
                        val cpuConversation = restartOnCpu(activeConversation) ?: throw e
                        emitAll(stream(cpuConversation, message))
                    }
            )
        }
    }

    private fun stream(activeConversation: Conversation, message: String): Flow<LlmEvent> =
        activeConversation.sendMessageAsync(message)
            // Re-arm after a cancelled first reply; commit() so a native crash still finds it.
            .onStart { if (gpuUnconfirmed) backendPrefs.edit().putBoolean(KEY_GPU_FAILED, true).commit() }
            .onCompletion { cause ->
                // Finished: the GPU works. Cancelled (chat closed): no verdict, so the next
                // start may try GPU again. A thrown error keeps the flag, so it starts on CPU.
                if (gpuUnconfirmed && (cause == null || cause is CancellationException)) {
                    if (cause == null) gpuUnconfirmed = false
                    backendPrefs.edit().putBoolean(KEY_GPU_FAILED, false).apply()
                }
            }
            .catch { e ->
                // Class only: LiteRT's messages can quote the model's reply or the prompt.
                Log.e(TAG, "Error during streaming response (${e.javaClass.simpleName})")
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

    /**
     * Replaces the GPU engine whose first reply failed with a CPU one and rebuilds the
     * conversation that failed on it. Null if that conversation was closed or replaced
     * meanwhile, or the CPU start failed (the next start then begins on CPU).
     */
    private suspend fun restartOnCpu(failed: Conversation): Conversation? = withContext(Dispatchers.IO) {
        lifecycle.withLock {
            val path = modelPath
            val config = conversationConfig
            if (conversation !== failed || path == null || config == null) return@withLock null
            takeConversation()?.closeQuietly()
            closeEngine()
            try {
                gpuUnconfirmed = false
                val cpu = startEngine(path, Backend.CPU()).also { engine = it }
                Log.i(TAG, "Engine restarted on CPU backend")
                cpu.createConversation(config).also { conversation = it }
            } catch (e: Exception) {
                Log.e(TAG, "CPU restart failed (${e.javaClass.simpleName})")
                null
            }
        }
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
                closeEngine()
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

    private fun closeEngine() {
        engine?.let { e -> runCatching { e.close() }.onFailure { Log.w(TAG, "Ignoring failed engine close", it) } }
        engine = null
    }

    /** Closing is cleanup: a close LiteRT refuses (already closed, mid-generation) must not crash the app. */
    private fun Conversation.closeQuietly() {
        runCatching { close() }.onFailure { Log.w(TAG, "Ignoring failed conversation close", it) }
    }

    override fun isInitialized(): Boolean = engine != null

    companion object {
        private const val TAG = "LlmServiceImpl"
        private const val KEY_GPU_FAILED = "gpu_failed"

        /** The chat's conversation setup; the debug LLM probe builds the same one. */
        internal fun chatConversationConfig(
            systemPrompt: String,
            history: List<Pair<String, Boolean>>,
            withTools: Boolean
        ) = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            initialMessages = history.map { (message, isUser) ->
                if (isUser) Message.user(message) else Message.model(message)
            },
            // Tool calls are surfaced, never auto-executed (#170): every
            // mutation goes through the user first.
            tools = if (withTools) listOf(tool(PennyWiseTools())) else emptyList(),
            automaticToolCalling = false,
            samplerConfig = SamplerConfig(
                topK = 10,
                topP = 0.95,
                temperature = 0.8
            ),
            // The model's built-in template can't read LiteRT-LM >= 0.18's message format.
            chatTemplate = QwenChatTemplate.TEMPLATE
        )
    }
}
