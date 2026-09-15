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
import com.google.ai.edge.litertlm.SamplerConfig
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
 *       [--ef temp 0.1] [--es model /abs/path.litertlm] [--ez echo true]
 * Logs "LlmProbe: RESULT …" with timings. The prompt and reply are logged only
 * with --ez echo true — keep real SMS / personal data out of logcat.
 * One probe at a time; a second broadcast waits for the first.
 */
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
        val echo = intent.getBooleanExtra("echo", false)
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
                    val reply = eng.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(system),
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = temp)
                        )
                    ).use { conv ->
                        conv.sendMessageAsync(text).toList()
                            .joinToString("") { m -> m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text } }
                    }
                    val t1 = System.currentTimeMillis()
                    Log.i(TAG, "RESULT model=${File(modelPath).name} load=${tLoad - t0}ms gen=${t1 - tLoad}ms chars=${reply.length}")
                    if (echo) {
                        Log.i(TAG, "TEXT " + text.replace("\n", " "))
                        Log.i(TAG, "REPLY " + reply.replace("\n", "\\n"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
