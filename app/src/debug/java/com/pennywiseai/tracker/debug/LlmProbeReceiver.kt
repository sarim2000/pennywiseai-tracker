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
import java.io.File

/**
 * Debug-only prompt harness (never in release builds):
 *   adb shell am broadcast -n <pkg>/com.pennywiseai.tracker.debug.LlmProbeReceiver \
 *       -a LLM_PROBE --es system "<system prompt>" --es text "<user text>" --ef temp 0.1
 * Runs the on-device model with a CLEAN system prompt and logs "LlmProbe: …".
 */
class LlmProbeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "LlmProbe"
        @Volatile private var engine: Engine? = null
        @Volatile private var enginePath: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val system = intent.getStringExtra("system") ?: ""
        val text = intent.getStringExtra("text") ?: return
        val temp = intent.getFloatExtra("temp", 0.1f).toDouble()
        // Optional: absolute path of another .litertlm to probe (e.g. a smaller model).
        val modelPath = intent.getStringExtra("model")
            ?: File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Constants.ModelDownload.MODEL_FILE_NAME).absolutePath
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t0 = System.currentTimeMillis()
                val eng = engine?.takeIf { enginePath == modelPath } ?: run {
                    engine?.close(); engine = null
                    Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU(), cacheDir = context.cacheDir.path))
                        .also { it.initialize(); engine = it; enginePath = modelPath }
                }
                val tLoad = System.currentTimeMillis()
                val conv = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(system),
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = temp)
                    )
                )
                val reply = conv.sendMessageAsync(text).toList()
                    .joinToString("") { m -> m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text } }
                conv.close()
                val t1 = System.currentTimeMillis()
                Log.i(TAG, "RESULT model=${File(modelPath).name} load=${tLoad - t0}ms gen=${t1 - tLoad}ms text=${text.replace("\n", " ")}")
                Log.i(TAG, "REPLY " + reply.replace("\n", "\\n"))
            } catch (e: Exception) {
                Log.e(TAG, "FAILED: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }
}
