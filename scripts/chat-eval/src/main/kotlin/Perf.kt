@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.tool
import com.google.gson.Gson
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.data.service.QwenChatTemplate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Timings for what a chat user waits on, and audio input through LiteRT-LM.
 *   PerfKt perf <model> [backend=cpu|gpu] [template=app|builtin] [thinking=off]
 *   PerfKt audio <model> <audio_dir> <out.jsonl> [prompt=...]
 */
private fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000

private fun rssMb(): Long {
    val p = ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString()).start()
    return p.inputStream.bufferedReader().readText().trim().toLong() / 1024
}

fun main(argv: Array<String>) = runBlocking {
    if (argv[0] == "audio") audio(argv) else perf(argv)
}

private suspend fun perf(argv: Array<String>) {
    val model = argv[1]
    val args = argv.drop(2).associate { it.substringBefore('=') to it.substringAfter('=') }
    val backend = if (args["backend"] == "gpu") Backend.GPU() else Backend.CPU()
    val template = if (args["template"] == "builtin") null else QwenChatTemplate.TEMPLATE
    val thinking = if (args["thinking"] == "off") ThinkingConfig(enableThinking = false) else null
    println("model=${File(model).name} backend=${backend.name} sizeMb=${File(model).length() / 1_000_000}")

    val hashStart = System.nanoTime()
    val digest = MessageDigest.getInstance("SHA-256")
    File(model).inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    println("sha256_ms=${ms(hashStart)}")

    ExperimentalFlags.enableBenchmark = true
    val cacheDir = File(model).parentFile.resolve("cache").also { it.mkdirs() }.path
    val t0 = System.nanoTime()
    val engine = Engine(EngineConfig(modelPath = model, backend = backend, cacheDir = cacheDir)).also { it.initialize() }
    println("engine_init_ms=${ms(t0)} rss_mb=${rssMb()}")

    val system = systemPrompt("INR", "")
    fun config(prefill: Boolean) = ConversationConfig(
        systemInstruction = Contents.of(system),
        tools = listOf(tool(PennyWiseTools())),
        automaticToolCalling = false,
        samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
        chatTemplate = template,
        thinkingConfig = thinking,
        prefillPrefaceOnInit = prefill
    )

    suspend fun send(c: com.google.ai.edge.litertlm.Conversation, text: String, label: String) {
        val t = System.nanoTime()
        var first: Long? = null
        val before = c.getTokenCount()
        runCatching {
            c.sendMessageAsync(text).collect { if (first == null) first = ms(t) }
        }.onFailure { println("$label ERROR ${it.javaClass.simpleName} ${it.message?.take(160)?.replace('\n', ' ')}") }
        println("$label first_ms=$first total_ms=${ms(t)} tokens=$before->${c.getTokenCount()} rss_mb=${rssMb()}")
    }

    engine.createConversation(config(prefill = false)).use { c ->
        val t = System.nanoTime()
        println("preface_chars=${c.renderPrefaceIntoString().length} preface_est_tokens_chars_div_4=${c.renderPrefaceIntoString().length / 4} render_ms=${ms(t)}")
        send(c, "coffee 120 at Starbucks", "lazy_first")
        println("preface_plus_turn_tokens=${c.getTokenCount()}")
        send(c, "lunch 300 at Subway", "lazy_second")
        println(runCatching { c.getBenchmarkInfo().toString() }.getOrElse { "bench_failed ${it.message}" })
    }

    run {
        val t = System.nanoTime()
        engine.createConversation(config(prefill = true)).use { c ->
            println("prefill_create_ms=${ms(t)}")
            send(c, "coffee 120 at Starbucks", "prefilled_first")
        }
    }

    // How the app's chars/4 budget holds up, by script.
    for ((name, sample) in listOf(
        "en" to "I paid 120 for coffee at the corner shop this morning and it was quite expensive",
        "ar" to "دفعت مئة وعشرين ريال على القهوة في المحل القريب هذا الصباح وكانت غالية نوعا ما",
        "hi" to "मैंने आज सुबह पास की दुकान पर कॉफी के लिए एक सौ बीस रुपये दिए और यह काफी महंगी थी"
    )) {
        engine.createConversation(ConversationConfig(systemInstruction = Contents.of("Reply with one word."), chatTemplate = template, thinkingConfig = thinking, samplerConfig = SamplerConfig(1, 1.0, 0.0))).use { c ->
            val single = c.renderMessageIntoString(Message.user(sample)).length
            val base = run { c.sendMessageAsync("a").collect { }; c.getTokenCount() }
            c.sendMessageAsync(sample).collect { }
            val spent = c.getTokenCount() - base
            println("script=$name chars=${sample.length} rendered=$single tokens_turn=$spent chars_per_token=${"%.2f".format(sample.length.toDouble() / spent)}")
        }
    }

    // One long chat in each language: latency growth and what happens at the cache limit.
    for ((name, line) in listOf("en" to "coffee %d at Starbucks", "ar" to "قهوة %d ريال من ستاربكس")) {
        engine.createConversation(config(prefill = false)).use { c ->
            var turn = 1
            var failed = false
            var chars = system.length
            while (turn <= 60 && !failed) {
                val text = line.format(100 + turn)
                val t = System.nanoTime()
                chars += text.length
                runCatching { c.sendMessageAsync(text).collect { } }
                    .onFailure { failed = true; println("long_$name turn=$turn FAILED ${it.javaClass.simpleName} ${it.message?.take(200)?.replace('\n', ' ')}") }
                val tokens = runCatching { c.getTokenCount() }.getOrDefault(-1)
                if (turn == 1 || turn % 5 == 0 || failed) println("long_$name turn=$turn ms=${ms(t)} tokens=$tokens")
                turn++
            }
            println("long_$name stopped_at_turn=${turn - 1} failed=$failed")
        }
    }
    println("peak_end_rss_mb=${rssMb()}")
    engine.close()
}

private suspend fun audio(argv: Array<String>) {
    val model = argv[1]
    val dir = File(argv[2])
    val out = File(argv[3])
    val args = argv.drop(4).associate { it.substringBefore('=') to it.substringAfter('=') }
    val prompt = args["prompt"] ?: "Transcribe this audio exactly, in the language spoken. Write amounts as digits. Output only the transcript."
    val cases = Gson().fromJson(File("stt_cases.json").readText(), SttFile::class.java).cases
    val peak = AtomicLong()
    val t0 = System.nanoTime()
    val engine = Engine(EngineConfig(modelPath = model, backend = Backend.CPU(), audioBackend = Backend.CPU(), cacheDir = File(model).parentFile.resolve("cache").also { it.mkdirs() }.path)).also { it.initialize() }
    println("engine_init_ms=${ms(t0)} rss_mb=${rssMb()}")
    val gson = Gson()
    for (c in cases) for (noise in listOf("clean", "noisy")) {
        val clip = File(dir, "${c.id}.$noise.wav")
        val t = System.nanoTime()
        val text = StringBuilder()
        var error: String? = null
        runCatching {
            engine.createConversation(ConversationConfig(samplerConfig = SamplerConfig(1, 1.0, 0.0))).use { conv ->
                conv.sendMessageAsync(Contents.of(Content.AudioFile(clip.absolutePath), Content.Text(prompt))).collect { m ->
                    text.append(m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text })
                }
            }
        }.onFailure { error = "${it.javaClass.simpleName}: ${it.message?.take(200)}" }
        peak.updateAndGet { maxOf(it, rssMb()) }
        val row = mapOf("id" to c.id, "noise" to noise, "engine" to "litert", "model" to File(model).name, "transcript" to text.toString().trim(), "audio_s" to clip.length() / 32000.0, "proc_s" to ms(t) / 1000.0, "error" to error, "rss_mb" to peak.get())
        out.appendText(gson.toJson(row) + "\n")
        println("${c.id} $noise ${ms(t)}ms ${text.toString().trim().take(100)} ${error ?: ""}")
    }
    engine.close()
}

private data class SttCase(val id: String)
private data class SttFile(val cases: List<SttCase>)
