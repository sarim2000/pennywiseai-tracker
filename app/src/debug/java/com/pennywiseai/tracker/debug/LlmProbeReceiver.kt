package com.pennywiseai.tracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import com.pennywiseai.tracker.core.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Debug-only prompt harness (never in release builds). Guarded by
 * android.permission.DUMP, which adb shell holds and third-party apps can't:
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.LlmProbeReceiver \
 *       -a LLM_PROBE --es system "<system prompt>" --es text "<user text>" \
 *       [--ef temp 0.1] [--es model /abs/path.litertlm] [--es mode text|tools|json] [--es schema '<json schema>']
 * mode=tools registers PennyTools (addTransaction / spendingByCategory) with
 * automaticToolCalling=false and records the model's tool calls; mode=json uses
 * constrained decoding (ResponseFormat.json) with the given schema.
 * Logs "LlmProbe: RESULT …" with timings only. The reply is written to the
 * app's private cache (llm_probe_reply.txt — read it with `run-as <pkg> cat
 * cache/llm_probe_reply.txt`), never to logcat, so prompt contents stay off
 * the shared log. One probe at a time; a second broadcast waits for the first.
 */
/** Tools the probe offers the model. Nothing here touches app data. */
@Suppress("unused")
class PennyTools : ToolSet {
    @Tool(description = "Record a new transaction the user is telling you about, e.g. money they spent or received.")
    fun addTransaction(
        @ToolParam(description = "Amount of money, as a number") amount: Double,
        @ToolParam(description = "Shop, service or person the money went to or came from") merchant: String,
        @ToolParam(description = "One of the known category names") category: String,
        @ToolParam(description = "EXPENSE or INCOME") type: String,
        @ToolParam(description = "Bank, card or cash the user mentioned, or empty") account: String
    ): String = "recorded"

    @Tool(description = "Total the user spent in a category this month")
    fun spendingByCategory(@ToolParam(description = "Category name") category: String): String = "1234"
}

class LlmProbeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "LlmProbe"
        private val lock = Mutex()
        private var engine: Engine? = null
        private var enginePath: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val system = intent.getStringExtra("system") ?: ""
        val text = intent.getStringExtra("text") ?: return
        val temp = intent.getFloatExtra("temp", 0.1f).toDouble()
        val mode = intent.getStringExtra("mode") ?: "text"
        val schema = intent.getStringExtra("schema")
        // Optional: absolute path of another .litertlm to probe (e.g. a smaller model).
        val modelPath = intent.getStringExtra("model")
            ?: File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Constants.ModelDownload.MODEL_FILE_NAME).absolutePath
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                lock.withLock {
                    val t0 = System.currentTimeMillis()
                    val eng = engine?.takeIf { enginePath == modelPath } ?: run {
                        engine?.close(); engine = null; enginePath = null
                        Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = context.cacheDir.path))
                            .also { it.initialize(); engine = it; enginePath = modelPath }
                    }
                    val tLoad = System.currentTimeMillis()
                    val sampler = SamplerConfig(topK = 1, topP = 1.0, temperature = temp)
                    val reply = when (mode) {
                        "tools" -> eng.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(system),
                                tools = listOf(tool(PennyTools())),
                                automaticToolCalling = false,
                                samplerConfig = sampler
                            )
                        ).use { conv ->
                            // Two turns in ONE conversation, to see whether the tool-schema
                            // prefill is paid once (the real chat keeps its conversation).
                            fun turn(t: String): String {
                                val s0 = System.currentTimeMillis()
                                val msg = conv.sendMessage(t)
                                val calls = msg.toolCalls.joinToString("; ") { "${it.name}(${it.arguments})" }
                                val txt = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                                return "[${System.currentTimeMillis() - s0}ms] TOOL_CALLS=[$calls] TEXT=$txt"
                            }
                            val second = intent.getStringExtra("text2")
                            turn(text) + (second?.let { "\n" + turn(it) } ?: "")
                        }
                        "json" -> eng.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(system),
                                samplerConfig = sampler,
                                enableResponseFormat = true
                            )
                        ).use { conv ->
                            val msg = conv.sendMessage(text, responseFormat = ResponseFormat.json(schema ?: "{\"type\":\"object\"}"))
                            msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                        }
                        else -> eng.createConversation(
                            ConversationConfig(systemInstruction = Contents.of(system), samplerConfig = sampler)
                        ).use { conv ->
                            conv.sendMessageAsync(text).toList()
                                .joinToString("") { m -> m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text } }
                        }
                    }
                    val t1 = System.currentTimeMillis()
                    File(context.cacheDir, "llm_probe_reply.txt").writeText(reply)
                    Log.i(TAG, "RESULT model=${File(modelPath).name} load=${tLoad - t0}ms gen=${t1 - tLoad}ms chars=${reply.length}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
