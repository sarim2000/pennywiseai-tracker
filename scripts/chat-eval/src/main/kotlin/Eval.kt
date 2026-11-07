@file:OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.pennywiseai.tracker.data.service.AddTransactionsTool
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.data.service.QwenChatTemplate
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Runs chat cases through LiteRT-LM on the desktop with the app's tool schema and template.
 *
 *   ./run.sh <model> <shape> [key=value...]
 * Shapes: app (the app's tools and prompt as they are now), prod, prod_hint, ext_hint, ext_rules, ext_few, array, array_rules, array_few, json.
 * "prod" and "prod_hint" compile the app's PennyWiseTools, so they only mean the old tools on a checkout from before them.
 * Output is one JSON object per run. A case with "turns" sends them in one conversation; its calls and text cover all turns.
 * Keys: cases, out, backend (cpu|gpu), threads, temp, topk, topp, reps, template (app|builtin), only, prefix (put in front of each message), perf.
 */
private const val TODAY = "2026-11-04"
private val EXPENSE = listOf(
    "Food & Dining", "Groceries", "Transportation", "Shopping", "Bills & Utilities", "Entertainment", "Healthcare",
    "Investments", "Banking", "Personal Care", "Education", "Mobile", "Fitness", "Insurance", "Travel", "Others"
)
private val INCOME = listOf("Salary", "Income")

/** Same text as LlmRepository.composeSystemPrompt, with synthetic figures. */
internal fun systemPrompt(base: String, hint: String): String {
    // CurrencyFormatter's symbols: the prompt shows "SR 18,450", never naming the ISO code.
    val symbol = mapOf("INR" to "₹", "SAR" to "SR ", "EGP" to "E£", "JOD" to "JD ")[base] ?: "$base "
    return """
    You are PennyWise AI, a friendly assistant inside an expense tracker. Today is $TODAY.
    This month so far: ${symbol}18,450 spent, ${symbol}52,000 income, 37 transactions.

    When the user tells you about money they spent, paid, bought or received, call addTransaction. Use EXPENSE unless they clearly received money.
    When the user asks how much they spent on something, call spendingByCategory; for this month's overall totals call monthSummary (the figures below are only a snapshot from when this chat started).
    If the user refers to something already recorded — "the tea from today", "that uber", "change", "should be", "was actually", "delete", "remove" — call updateTransaction or deleteTransaction with the words they used to identify it. Never call addTransaction for those.
    Otherwise answer briefly and helpfully. Never invent transactions or figures, and never say you added, changed or deleted anything yourself — only the tools do that, and the user confirms each one.

    Expense categories: ${EXPENSE.joinToString(", ")}
    Income categories: ${INCOME.joinToString(", ")}
    Accounts: HDFC 1234, Cash wallet

    Plain text only: no markdown, asterisks or backticks.
""".trimIndent() + hint
}

/** The prompt the app sends today: LlmRepository.composeSystemPrompt names the array tool and adds these rules. */
internal fun appSystemPrompt(base: String) =
    systemPrompt(base, ONE_CALL_ITEMS + RULES).replace("call addTransaction.", "call addTransactions.").replace("Never call addTransaction for", "Never call addTransactions for")

private const val ONE_CALL_EACH = "\nIf a message mentions several transactions, call addTransaction once for each of them."
private const val RULES = """
Every amount the user states is its own transaction: never add amounts together and never merge items.
Money received, such as salary, is a transaction too: call addTransactions, don't just acknowledge it.
Leave account empty unless the user names a bank, card or cash in this message."""

private const val EXAMPLES = RULES + """
Examples:
"coffee 120 and lunch 300": two transactions, 120 at coffee and 300 at lunch. Never one of 420.
"قهوة 15 وغدا 40": two transactions, 15 at قهوة and 40 at غدا. Never one of 55.
"got 5000 salary and paid 1000 rent": 5000 INCOME from salary and 1000 EXPENSE at rent.
"paid ${'$'}5 for tea yesterday": 5 at tea, currency USD, days_ago 1."""

private const val ONE_CALL_ITEMS =
    "\nA message can mention several transactions: call addTransactions once, with one entry in items for each of them."

/** addTransaction with currency and date, for the ext_* shapes. */
@Suppress("unused")
class ExtendedTools : ToolSet {
    @Tool(description = "Record a transaction the user is telling you about: money they spent, paid, bought, or received.")
    fun addTransaction(
        @ToolParam(description = "Amount of money as a number, without currency symbol") amount: Double,
        @ToolParam(description = "The shop, service or person the money went to or came from. Never a bank or card.") merchant: String,
        @ToolParam(description = "One of the known category names") category: String,
        @ToolParam(description = "EXPENSE for money spent or paid (the usual case), INCOME only for money received such as salary, refund or cashback") type: String,
        @ToolParam(description = "The bank, card or cash the user mentioned, or empty if not mentioned") account: String,
        @ToolParam(description = "ISO currency code if the user named a currency, e.g. USD, or empty if not mentioned") currency: String,
        @ToolParam(description = "How many days ago: 0 for today or if not mentioned, 1 for yesterday") daysAgo: Int
    ): String = ""
}

@Suppress("unused")
class OtherTools : ToolSet {
    @Tool(description = "Delete a transaction the user recorded earlier, e.g. 'delete the starbucks coffee from yesterday'.")
    fun deleteTransaction(
        @ToolParam(description = "Merchant, shop or description words the user used to identify it") merchant: String,
        @ToolParam(description = "Amount if the user mentioned one, else 0") amount: Double,
        @ToolParam(description = "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned") daysAgo: Int
    ): String = ""

    @Tool(description = "Change the category or merchant name of a transaction the user recorded earlier, e.g. 'the uber yesterday was actually Transportation'.")
    fun updateTransaction(
        @ToolParam(description = "Merchant, shop or description words the user used to identify it") merchant: String,
        @ToolParam(description = "Amount if the user mentioned one, else 0") amount: Double,
        @ToolParam(description = "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned") daysAgo: Int,
        @ToolParam(description = "The new category, or empty to keep it") newCategory: String,
        @ToolParam(description = "The new merchant name, or empty to keep it") newMerchant: String
    ): String = ""

    @Tool(description = "Look up this month's totals: money spent, money received and number of transactions.")
    fun monthSummary(): String = ""

    @Tool(description = "Look up how much the user has spent in a category this month.")
    fun spendingByCategory(
        @ToolParam(description = "One of the known category names") category: String
    ): String = ""
}

private val ITEM_PROPERTIES = """
    "amount":{"type":"number","description":"Amount of money as a number, without currency symbol"},
    "merchant":{"type":"string","description":"The shop, service or person the money went to or came from. Never a bank or card."},
    "category":{"type":"string","description":"One of the known category names"},
    "type":{"type":"string","description":"EXPENSE for money spent or paid (the usual case), INCOME only for money received such as salary, refund or cashback"},
    "account":{"type":"string","description":"The bank, card or cash the user mentioned, or empty if not mentioned"},
    "currency":{"type":"string","description":"ISO currency code if the user named a currency, e.g. USD, or empty if not mentioned"},
    "days_ago":{"type":"integer","description":"How many days ago: 0 for today or if not mentioned, 1 for yesterday"}
""".trimIndent()

private val ITEMS_SCHEMA =
    """{"type":"array","items":{"type":"object","properties":{$ITEM_PROPERTIES},"required":["amount","merchant","category","type","account","currency","days_ago"]}}"""

private class ArrayTool : OpenApiTool {
    override fun getToolDescriptionJsonString() =
        """{"name":"add_transactions","description":"Record the transactions the user is telling you about: money they spent, paid, bought, or received.","parameters":{"type":"object","properties":{"items":$ITEMS_SCHEMA},"required":["items"]}}"""
    override fun execute(paramsJsonString: String) = ""
}

private val JSON_SCHEMA = """{"type":"object","properties":{"items":$ITEMS_SCHEMA},"required":["items"]}"""

private fun extractionPrompt(base: String) = """
    You extract money transactions from a user's message in an expense tracker. Today is $TODAY. The user's default currency is $base.
    Return JSON with an items array containing one entry for each amount of money the user says they spent or received.
    Expense categories: ${EXPENSE.joinToString(", ")}
    Income categories: ${INCOME.joinToString(", ")}
""".trimIndent()

// Gson skips Kotlin defaults, so a missing base arrives as null.
private data class Case(val id: String, val text: String, private val base: String?, val turns: List<String>?) {
    val currency get() = base ?: "INR"
}
private data class CaseFile(val cases: List<Case>)

private fun arg(args: Map<String, String>, key: String, default: String) = args[key] ?: default

/** Peak resident memory of this process (model weights live natively, off the JVM heap). */
private fun startRssSampler(peak: AtomicLong) = thread(isDaemon = true) {
    val pid = ProcessHandle.current().pid().toString()
    while (true) {
        runCatching {
            val p = ProcessBuilder("ps", "-o", "rss=", "-p", pid).start()
            val kb = p.inputStream.bufferedReader().readText().trim().toLong()
            peak.updateAndGet { maxOf(it, kb) }
        }
        Thread.sleep(250)
    }
}

fun main(argv: Array<String>) = runBlocking {
    val modelPath = argv[0]
    val shape = argv[1]
    val args = argv.drop(2).associate { it.substringBefore('=') to it.substringAfter('=') }
    val temp = arg(args, "temp", "0.8").toDouble()
    val topK = arg(args, "topk", "10").toInt()
    val topP = arg(args, "topp", "0.95").toDouble()
    val reps = arg(args, "reps", "1").toInt()
    val useAppTemplate = arg(args, "template", "app") == "app"
    val thinkingOff = arg(args, "thinking", "default") == "off"
    val backend = if (arg(args, "backend", "cpu") == "gpu") Backend.GPU() else Backend.CPU(threadCount = args["threads"]?.toInt())
    val gson = Gson()
    val cases = gson.fromJson(File(arg(args, "cases", "cases.json")).readText(), CaseFile::class.java).cases
        .filter { c -> args["only"]?.let { c.id.startsWith(it) } ?: true }
    val out = File(arg(args, "out", "results.jsonl")).also { it.parentFile?.mkdirs() }

    val rssPeak = AtomicLong()
    startRssSampler(rssPeak)
    val cacheDir = File(modelPath).parentFile.resolve("cache").also { it.mkdirs() }.path
    val t0 = System.nanoTime()
    val engine = Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cacheDir)).also { it.initialize() }
    val loadMs = (System.nanoTime() - t0) / 1_000_000
    println("loaded in ${loadMs}ms rssPeakMb=${rssPeak.get() / 1024} cases=${cases.size} reps=$reps")

    val template = if (useAppTemplate) QwenChatTemplate.TEMPLATE else null
    val thinking = if (thinkingOff) ThinkingConfig(enableThinking = false) else null

    fun config(c: Case, seed: Int): ConversationConfig {
        val sampler = SamplerConfig(topK = topK, topP = topP, temperature = temp, seed = seed)
        val tools: List<ToolProvider>
        val system: String
        when (shape) {
            "app" -> {
                tools = listOf(tool(AddTransactionsTool()), tool(PennyWiseTools()))
                system = appSystemPrompt(c.currency)
            }
            "prod" -> { tools = listOf(tool(PennyWiseTools())); system = systemPrompt(c.currency, "") }
            "prod_hint" -> { tools = listOf(tool(PennyWiseTools())); system = systemPrompt(c.currency, ONE_CALL_EACH) }
            "ext_hint" -> { tools = listOf(tool(ExtendedTools()), tool(OtherTools())); system = systemPrompt(c.currency, ONE_CALL_EACH) }
            "ext_few" -> { tools = listOf(tool(ExtendedTools()), tool(OtherTools())); system = systemPrompt(c.currency, ONE_CALL_EACH + EXAMPLES) }
            "ext_rules" -> { tools = listOf(tool(ExtendedTools()), tool(OtherTools())); system = systemPrompt(c.currency, ONE_CALL_EACH + RULES) }
            "array", "array_few", "array_rules" -> {
                tools = listOf(tool(ArrayTool()), tool(OtherTools()))
                val hint = ONE_CALL_ITEMS + when (shape) { "array_few" -> EXAMPLES; "array_rules" -> RULES; else -> "" }
                system = systemPrompt(c.currency, hint).replace("call addTransaction.", "call addTransactions.").replace("Never call addTransaction for", "Never call addTransactions for")
            }
            "json" -> { tools = emptyList(); system = extractionPrompt(c.currency) }
            else -> error("unknown shape $shape")
        }
        return ConversationConfig(
            systemInstruction = Contents.of(system),
            tools = tools,
            automaticToolCalling = false,
            samplerConfig = sampler,
            chatTemplate = template,
            thinkingConfig = thinking,
            enableResponseFormat = shape == "json"
        )
    }

    for (c in cases) for (rep in 1..reps) {
        val row = linkedMapOf<String, Any?>("id" to c.id, "rep" to rep, "shape" to shape, "temp" to temp, "topk" to topK, "topp" to topP, "model" to File(modelPath).name, "backend" to backend.name, "load_ms" to loadMs)
        val started = System.nanoTime()
        val text = StringBuilder()
        val calls = mutableListOf<Map<String, Any?>>()
        var firstMs: Long? = null
        var convo: Conversation? = null
        try {
            convo = engine.createConversation(config(c, seed = rep))
            row["create_ms"] = (System.nanoTime() - started) / 1_000_000
            for (turn in c.turns ?: listOf(c.text)) {
                val message = args["prefix"].orEmpty().replace("\\n", "\n") + turn
                val flow = if (shape == "json") convo.sendMessageAsync(message, responseFormat = ResponseFormat.json(JSON_SCHEMA)) else convo.sendMessageAsync(message)
                flow.collect { m ->
                    if (firstMs == null) firstMs = (System.nanoTime() - started) / 1_000_000
                    text.append(m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text })
                    m.toolCalls.forEach { calls += mapOf("name" to it.name, "args" to it.arguments) }
                }
                if (c.turns != null) text.append("\n--- ")
            }
            if (shape == "json") {
                val parsed = runCatching { JsonParser.parseString(text.toString()).asJsonObject.getAsJsonArray("items") }.getOrNull()
                parsed?.forEach { calls += mapOf("name" to "add_transaction", "args" to gson.fromJson(it, Map::class.java)) }
                if (parsed == null) row["error"] = "json_parse"
            }
            runCatching {
                val b = convo.getBenchmarkInfo()
                row["bench"] = b.toString()
            }
        } catch (e: Throwable) {
            row["error"] = "${e.javaClass.simpleName}: ${e.message?.take(3000)}"
        } finally {
            runCatching { convo?.close() }
        }
        row["first_ms"] = firstMs
        row["total_ms"] = (System.nanoTime() - started) / 1_000_000
        row["text"] = text.toString()
        row["calls"] = calls
        row["rss_peak_mb"] = rssPeak.get() / 1024
        out.appendText(gson.toJson(row) + "\n")
        println("${c.id} r$rep ${row["total_ms"]}ms calls=${calls.size}${row["error"]?.let { " ERROR=$it" } ?: ""}")
    }
    engine.close()
    Unit
}
