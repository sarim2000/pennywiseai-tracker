package com.pennywiseai.tracker.data.repository

import android.content.Context
import android.util.Log
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ChatContext
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.service.LlmService
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.data.model.ChatMessageFacts
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.model.TransactionFinder
import com.pennywiseai.tracker.data.model.PendingChatAction
import com.pennywiseai.tracker.data.model.ReviewRow
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.domain.service.LlmEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.TokenUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmService: LlmService,
    private val chatDao: ChatDao,
    private val modelRepository: ModelRepository,
    private val aiContextRepository: AiContextRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val ignoredAccountsStore: IgnoredAccountsStore
) {

    // A transaction the model proposed via the add_transaction tool (#170),
    // waiting for the user's confirmation on the chat screen. Never auto-saved.
    private val _pendingAction = MutableStateFlow<PendingChatAction?>(null)
    val pendingAction: StateFlow<PendingChatAction?> = _pendingAction.asStateFlow()

    // Here, not in the ViewModel: a save outlives the screen, and a new screen must not start a new card under it.
    val isConfirming = MutableStateFlow(false)

    // What became of the last card. The live conversation can't see the app's own
    // writes, so the model hears this in front of the next message; never shown or saved.
    // No amount or merchant in it: told "Added ₹90 at Tea Stall", Qwen2.5 records that again.
    private var cardOutcome: String? = null

    /** The user changed the rows on the review card. */
    fun updateRows(change: (List<ReviewRow>) -> List<ReviewRow>) {
        val card = _pendingAction.value as? PendingChatAction.Add ?: return
        _pendingAction.value = card.copy(rows = change(card.rows))
    }

    /** The card was cancelled, left or superseded: nothing changed, and the model hears that. */
    fun clearPendingAction() {
        if (_pendingAction.value != null) cardOutcome = "The user cancelled the last card. Nothing was changed."
        _pendingAction.value = null
    }

    /** The user's tap went through: [line] (e.g. "Added ₹120 at Starbucks") joins the chat. */
    suspend fun completePendingAction(line: String) {
        _pendingAction.value = null
        cardOutcome = "The user confirmed the last card. It is saved."
        // The save already happened. A lost line must not read as a failed save worth repeating.
        runCatching { chatDao.insertMessage(ChatMessage(message = line, isUser = false)) }
            .onFailure { Log.w(TAG, "Couldn't save the line after a card (${it.javaClass.simpleName})") }
    }

    fun getAllMessages(): Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getAllMessagesIncludingSystem(): Flow<List<ChatMessage>> = chatDao.getAllMessagesIncludingSystem()

    suspend fun sendMessage(userMessage: String): Result<String> {
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true
        )
        chatDao.insertMessage(userChatMessage)

        ensureConversation(pending = userChatMessage)

        // Use synchronous collection for non-streaming path
        val responseBuilder = StringBuilder()
        try {
            llmService.sendMessage(userMessage).collect { partial ->
                responseBuilder.append(partial)
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val response = responseBuilder.toString()
        val aiChatMessage = ChatMessage(
            message = response,
            isUser = false
        )
        chatDao.insertMessage(aiChatMessage)

        return Result.success(response)
    }

    fun sendMessageStream(userMessage: String): Flow<String> = flow {
        val existingMessages = chatDao.getAllMessagesForContext()
        val isNewChat = existingMessages.isEmpty()

        // If new chat, add system prompt first. Always rebuilt: a cached one
        // carries a stale date, figures and lists — and, when oversized, used to
        // block every new chat until something refreshed the cache (#959).
        if (isNewChat) {
            val systemPrompt = buildSystemPrompt(aiContextRepository.getChatContext())
            userPreferencesRepository.updateSystemPrompt(systemPrompt)

            val systemMessage = ChatMessage(
                message = systemPrompt,
                isUser = false,
                isSystemPrompt = true
            )
            chatDao.insertMessage(systemMessage)
            Log.d(TAG, "System prompt added to new chat")
        }

        // Estimate tokens from all messages
        val currentMessages = chatDao.getAllMessagesForContext()
        if (!TokenUtils.fitsChatBudget(currentMessages.map { it.message }, userMessage)) {
            throw Exception("Chat memory is full. Please clear the chat to continue.")
        }

        // Save user message
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true
        )
        Log.d(TAG, "Saving user message (${userMessage.length} chars)")
        chatDao.insertMessage(userChatMessage)

        // Check if model is downloading
        val currentModelState = modelRepository.modelState.first()
        if (currentModelState == ModelState.DOWNLOADING) {
            throw Exception("Model is currently downloading. Please wait for download to complete.")
        }

        // Ensure engine is initialized and conversation is active
        ensureConversation(pending = userChatMessage)

        Log.d(TAG, "=== SENDING TO LLM ===")
        Log.d(TAG, "Total messages in context: ${currentMessages.size}")

        // Stream response — LiteRT-LM conversation manages history internally.
        // Tool calls are handed to us (never auto-run, #170): a lookup is
        // answered from the DB, a proposed transaction becomes a confirm card.
        val responseBuilder = StringBuilder()
        val toolCalls = mutableListOf<LlmEvent.ToolCall>()

        val prompt = cardOutcome?.let { "($it)\n$userMessage" } ?: userMessage
        cardOutcome = null
        var unreadableToolCall = false
        var conversationBroken = false
        var shownUpTo = 0
        llmService.sendMessageEvents(prompt)
            .catch { e ->
                if (e.message?.contains(TOOL_CALL_PARSE_FAILURE, ignoreCase = true) != true) throw e
                Log.w(TAG, "The model wrote a tool call that doesn't parse")
                conversationBroken = true
                TransactionDraft.itemsInBrokenCall(e.message.orEmpty())?.let { toolCalls.add(addCall(it)) } ?: run { unreadableToolCall = true }
            }
            .collect { event ->
                when (event) {
                    is LlmEvent.Text -> {
                        // The model can open with bare newlines before a tool call: no empty lines on top.
                        val text = if (responseBuilder.isEmpty()) event.delta.trimStart() else event.delta
                        if (text.isNotEmpty()) {
                            responseBuilder.append(text)
                            // A tool call the runtime didn't catch: never show its JSON, or the start of its tag.
                            val written = responseBuilder.toString()
                            if (!written.startsWith(TOOL_CALL_TAG) && !TOOL_CALL_TAG.startsWith(written)) {
                                emit(written.substring(shownUpTo))
                                shownUpTo = written.length
                            }
                        }
                    }
                    is LlmEvent.ToolCall -> toolCalls.add(event)
                }
            }
        // A broken wrapper can also come through as plain text. It's no reply, so it isn't saved as one.
        val written = responseBuilder.toString()
        if (written.startsWith(TOOL_CALL_TAG)) {
            TransactionDraft.itemsInBrokenCall(written)?.let { toolCalls.add(addCall(it)) } ?: run {
                unreadableToolCall = true
                conversationBroken = true
            }
            responseBuilder.clear()
        } else if (written.isNotEmpty() && TOOL_CALL_TAG.startsWith(written)) {
            // Text held back in case it became a tag, and it didn't.
            if (toolCalls.isEmpty()) emit(written) else responseBuilder.clear()
        }
        suspend fun addLine(line: String) {
            if (responseBuilder.isNotEmpty() && !responseBuilder.endsWith("\n")) { responseBuilder.append("\n"); emit("\n") }
            responseBuilder.append(line); emit(line)
        }
        // Everything to add in a turn shares one review card. Any other card stays one at a
        // time, since a second proposal would replace the first unseen.
        val adds = toolCalls.filter { it.name == PennyWiseTools.ADD_TRANSACTIONS }
        var addsProposed = false
        var skippedCards = 0
        for (call in toolCalls) {
            if (call.name == PennyWiseTools.ADD_TRANSACTIONS && addsProposed) continue
            if (call.name in CARD_TOOLS && _pendingAction.value != null) { skippedCards++; continue }
            if (call.name == PennyWiseTools.ADD_TRANSACTIONS) {
                addsProposed = true
                addLine(proposeAdds(adds.flatMap { TransactionDraft.itemsOfCall(it.arguments) }, userMessage))
            } else {
                handleToolCall(call, userMessage)?.let { addLine(it) }
            }
        }
        if (skippedCards > 0) addLine(context.getString(R.string.chat_reply_one_card_at_a_time))
        // Its state after a failed turn is unknown; the next message rebuilds it from the saved chat.
        if (conversationBroken) llmService.closeConversation()
        // A small model sometimes narrates a change instead of calling the tool.
        // Nothing happened — say so, or the user walks away believing it did.
        if (toolCalls.isEmpty() && CLAIMS_A_CHANGE.containsMatchIn(responseBuilder)) {
            addLine(context.getString(R.string.chat_reply_nothing_changed))
        }
        // Last, so the check above doesn't take this line's "Nothing was changed" for a claim.
        if (unreadableToolCall) addLine(context.getString(R.string.chat_reply_unreadable))

        // Save the complete AI response
        val finalResponse = responseBuilder.toString()
        Log.d(TAG, "Saving AI response (${finalResponse.length} chars)")
        val aiMessage = ChatMessage(
            message = finalResponse,
            isUser = false
        )
        chatDao.insertMessage(aiMessage)
        Log.d(TAG, "AI response saved")
    }

    /** [pending] is the message about to be sent: saved already, so kept out of the replayed history. */
    private suspend fun ensureConversation(pending: ChatMessage) {
        // Initialize engine if needed
        if (!llmService.isInitialized()) {
            val modelFile = modelRepository.getModelFile()
            if (!modelFile.exists()) {
                throw Exception("Model not downloaded. Please download from Settings.")
            }

            // Integrity gate: never load a model whose bytes don't match the
            // pinned hash (guards against an in-place-swapped or corrupted file).
            if (!modelRepository.verifyModelIntegrity()) {
                modelRepository.deleteModel()
                throw Exception("AI model failed its integrity check and was removed. Please re-download it from Settings.")
            }

            val initResult = llmService.initialize(modelFile.absolutePath)
            if (initResult.isFailure) {
                throw initResult.exceptionOrNull() ?: Exception("Failed to initialize LLM")
            }
        }

        // Create conversation if needed, replaying history from Room DB
        if (!llmService.hasActiveConversation()) {
            val allMessages = chatDao.getAllMessagesForContext()

            // Always fresh: the row saved when the chat began carries that day's date and figures.
            val systemPrompt = buildSystemPrompt(aiContextRepository.getChatContext())

            // Build history (excluding system prompt, and the message being sent —
            // replaying it too would put it in front of the model twice)
            val history = allMessages
                .filter { !it.isSystemPrompt && it.id != pending.id }
                .map { it.message to it.isUser }

            val result = llmService.createConversation(systemPrompt, history, withTools = true)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Failed to create conversation")
            }
            Log.d(TAG, "Conversation created with ${history.size} history messages")
        }
    }

    suspend fun deleteAllMessages() {
        llmService.closeConversation()
        chatDao.deleteAllMessages()
        cardOutcome = null
    }

    suspend fun deleteOldMessages(beforeTimestamp: Long) {
        // Conversation needs to be recreated after history changes
        llmService.closeConversation()
        chatDao.deleteMessagesBefore(beforeTimestamp)
    }

    suspend fun getMessageCount(): Int = chatDao.getMessageCount()

    /**
     * Resolves one tool call into a line for the chat. Returns null when there's
     * nothing to say (the transaction card carries its own text).
     */
    private suspend fun handleToolCall(call: LlmEvent.ToolCall, userMessage: String): String? {
        if (com.pennywiseai.tracker.BuildConfig.DEBUG) Log.d(TAG, "tool call ${call.name} ${call.arguments.keys}")
        return handleToolCallInner(call, userMessage)
    }

    /** LiteRT-LM snake_cases parameter names (daysAgo → days_ago); accept either. */
    private fun Map<String, Any?>.arg(camel: String): Any? =
        this[camel] ?: this[camel.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }]

    /** Category the user named, tolerant of "Food", "food and dining", "FOOD & DINING". */
    private fun matchCategory(wanted: String, categories: List<com.pennywiseai.tracker.data.database.entity.CategoryEntity>): String? {
        val norm = { x: String -> x.lowercase().replace("&", "and").replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim() }
        val w = norm(wanted); if (w.isEmpty()) return null
        return categories.firstOrNull { norm(it.name) == w }?.name
            ?: categories.firstOrNull { norm(it.name).startsWith(w) || w.startsWith(norm(it.name)) }?.name
            ?: categories.firstOrNull { norm(it.name).split(" ").first() == w.split(" ").first() }?.name
    }

    private fun addCall(items: List<Map<String, Any?>>) = LlmEvent.ToolCall(PennyWiseTools.ADD_TRANSACTIONS, mapOf("items" to items))

    /** Turns the items of a turn into one review card, and returns the line that introduces it. */
    private suspend fun proposeAdds(items: List<Map<String, Any?>>, userMessage: String): String {
        val drafts = TransactionDraft.fromToolCalls(
            calls = items,
            sourceText = userMessage,
            categories = categoryRepository.getVisibleCategories().first(),
            accounts = accountBalanceRepository.getAllLatestBalancesOnce(),
            baseCurrency = userPreferencesRepository.baseCurrency.first(),
            noAccountLabel = context.getString(R.string.chat_account_none),
            ambiguousAccountLabel = context.getString(R.string.chat_account_ambiguous),
            fallbackCategory = { merchant ->
                com.pennywiseai.tracker.data.mapper.BuiltinCategoryNames.current(SharedCategoryMapping.getCategory(merchant))
            }
        )
        if (drafts.isEmpty()) return context.getString(R.string.chat_reply_amount_unclear)
        val rows = TransactionDraft.reviewRows(drafts)
        _pendingAction.value = PendingChatAction.Add(rows)
        return buildList {
            val only = rows.singleOrNull()?.draft
            add(
                if (only == null) {
                    context.getString(R.string.chat_reply_add_many)
                } else {
                    val line = amountLine(only.amount, only.currency, only.type, only.merchant)
                    context.getString(R.string.chat_reply_add, "$line · ${only.category} · ${only.accountLabel} · ${only.date.format(DAY_MONTH)}")
                }
            )
            if (drafts.size > rows.size) add(context.getString(R.string.chat_reply_too_many, TransactionDraft.MAX_ROWS))
            if (items.size > drafts.size) add(context.getString(R.string.chat_reply_amount_unclear))
        }.joinToString("\n")
    }

    private suspend fun handleToolCallInner(call: LlmEvent.ToolCall, userMessage: String): String? = when (call.name) {
        PennyWiseTools.DELETE_TRANSACTION, PennyWiseTools.UPDATE_TRANSACTION -> {
            val match = findTransaction(call.arguments, userMessage)
            val target = match?.transaction
            // Say so when the day or amount had to be dropped to find it.
            val closest = if (match?.loosened == true) context.getString(R.string.chat_reply_closest_match) + " " else ""
            if (target == null) {
                context.getString(R.string.chat_reply_not_found)
            } else if (call.name == PennyWiseTools.DELETE_TRANSACTION) {
                _pendingAction.value = PendingChatAction.Delete(target)
                closest + context.getString(R.string.chat_reply_delete, describe(target))
            } else {
                val newCategory = (call.arguments.arg("newCategory") as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { wanted ->
                    matchCategory(wanted, categoryRepository.getVisibleCategories().first())
                }?.takeIf { it != target.category }
                val newMerchant = (call.arguments.arg("newMerchant") as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals(target.merchantName, ignoreCase = true) }
                if (newCategory == null && newMerchant == null) {
                    context.getString(R.string.chat_reply_update_what)
                } else {
                    _pendingAction.value = PendingChatAction.Update(target, newCategory, newMerchant)
                    closest + context.getString(
                        R.string.chat_reply_update,
                        describe(target),
                        listOfNotNull(
                            newMerchant?.let { context.getString(R.string.chat_action_change_merchant, it) },
                            newCategory?.let { context.getString(R.string.chat_action_change_category, it) }
                        ).joinToString(", ")
                    )
                }
            }
        }
        PennyWiseTools.MONTH_SUMMARY -> {
            val m = aiContextRepository.getChatContext().monthSummary
            val currency = userPreferencesRepository.baseCurrency.first()
            context.getString(
                R.string.chat_reply_month_summary,
                CurrencyFormatter.formatCurrency(m.totalExpense, currency, maskIfHidden = false),
                CurrencyFormatter.formatCurrency(m.totalIncome, currency, maskIfHidden = false),
                m.transactionCount
            )
        }
        PennyWiseTools.SPENDING_BY_CATEGORY -> {
            val wanted = (call.arguments["category"] as? String)?.trim().orEmpty()
            // Hidden categories still hold transactions, and a row can name a category that no
            // longer exists: a read-only lookup asks the transactions before saying it's unknown.
            val category = matchCategory(wanted, categoryRepository.getAllCategories().first())
            val currency = userPreferencesRepository.baseCurrency.first()
            val spend = aiContextRepository.getCategorySpending(category ?: wanted)
            when {
                // "Nothing spent" would be a false figure for a category that doesn't exist.
                spend == null && category == null -> context.getString(R.string.chat_reply_unknown_category, wanted)
                spend == null -> context.getString(R.string.chat_reply_nothing_spent, category)
                else -> context.getString(
                    R.string.chat_reply_category_spend,
                    CurrencyFormatter.formatCurrency(spend.amount, currency, maskIfHidden = false),
                    spend.category,
                    spend.transactionCount
                )
            }
        }
        else -> null
    }

    private suspend fun findTransaction(args: Map<String, Any?>, userMessage: String): TransactionFinder.Match? {
        val words = (args["merchant"] as? String).orEmpty()
        val amount = ChatMessageFacts.parseAmount(args["amount"])
        // A day the user stated beats the model's reading of it.
        val dayStated = ChatMessageFacts.statesADay(userMessage)
        val daysAgo = if (dayStated) ChatMessageFacts.daysAgo(userMessage, args.arg("daysAgo"))
            else (args.arg("daysAgo") as? Number)?.toInt()?.takeIf { it >= 0 }
        val now = java.time.LocalDateTime.now()
        val recent = transactionRepository.getTransactionsBetweenDates(now.minusDays(30), now).first()
        return TransactionFinder.findWithFallback(recent, words, amount, daysAgo, dayStated = dayStated)
    }

    /** An existing row is shown in ITS currency, never relabelled with the base one. */
    private fun describe(tx: TransactionEntity): String =
        "${amountLine(tx.amount, tx.currency, tx.transactionType, tx.merchantName)} · ${tx.category} · ${tx.dateTime.format(DAY_MONTH)}"

    /** "₹120 at Starbucks", in the transaction's own currency. Also used for the line after a confirmed card. */
    fun amountLine(amount: BigDecimal, currency: String, type: TransactionType, merchant: String): String = context.getString(
        if (type == TransactionType.INCOME) R.string.chat_line_income else R.string.chat_line_expense,
        CurrencyFormatter.formatCurrency(amount, currency, maskIfHidden = false),
        merchant
    )

    /**
     * Thin system prompt (#170): who the assistant is, the month at a glance,
     * the user's categories and accounts, and how to use the tools. The old
     * prompt embedded recent transactions, which a 1.5B model tends to parrot
     * back into tool arguments.
     */
    private suspend fun buildSystemPrompt(context: ChatContext): String {
        val monthSummary = context.monthSummary
        val currency = userPreferencesRepository.baseCurrency.first()
        val categories = categoryRepository.getVisibleCategories().first()
        val ignored = ignoredAccountsStore.keys()
        val accounts = accountBalanceRepository.getAllLatestBalancesOnce()
            .filter { IgnoredAccountsStore.keyFor(it.bankName, it.accountLast4) !in ignored }
            .map { it.alias ?: "${it.bankName} ${it.accountLast4}" }
        return composeSystemPrompt(
            currentDate = context.currentDate.toString(),
            monthLine = "${CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currency, maskIfHidden = false)} spent, " +
                "${CurrencyFormatter.formatCurrency(monthSummary.totalIncome, currency, maskIfHidden = false)} income, ${monthSummary.transactionCount} transactions",
            expenseCategories = categories.filter { !it.isIncome }.map { it.name },
            incomeCategories = categories.filter { it.isIncome }.map { it.name },
            accounts = accounts
        )
    }

    suspend fun updateSystemPrompt() {
        val chatContext = aiContextRepository.getChatContext()
        val newPrompt = buildSystemPrompt(chatContext)
        userPreferencesRepository.updateSystemPrompt(newPrompt)
        // Close conversation so it gets recreated with updated prompt
        llmService.closeConversation()
        Log.d(TAG, "System prompt updated with latest financial data")
    }

    suspend fun getFormattedContextForDisplay(): String {
        val chatContext = aiContextRepository.getChatContext()
        val monthSummary = chatContext.monthSummary
        val currency = userPreferencesRepository.baseCurrency.first()

        return context.getString(
            R.string.chat_welcome,
            monthSummary.transactionCount,
            CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currency, maskIfHidden = false)
        )
    }

    companion object {
        /**
         * The system prompt from already-gathered data. Each list is capped so the
         * prompt stays a bounded slice of [TokenUtils.CHAT_TOKEN_BUDGET] however
         * many categories or accounts the user has — a fresh chat must always fit
         * (#959). The tools still match any category or account the user names.
         */
        internal fun composeSystemPrompt(
            currentDate: String,
            monthLine: String,
            expenseCategories: List<String>,
            incomeCategories: List<String>,
            accounts: List<String>
        ): String = """
            You are PennyWise AI, a friendly assistant inside an expense tracker. Today is $currentDate.
            This month so far: $monthLine.

            When the user tells you about money they spent, paid, bought or received, call addTransactions. Use EXPENSE unless they clearly received money.
            When the user asks how much they spent on something, call spendingByCategory; for this month's overall totals call monthSummary (the figures below are only a snapshot from when this chat started).
            If the user refers to something already recorded — "the tea from today", "that uber", "change", "should be", "was actually", "delete", "remove" — call updateTransaction or deleteTransaction with the words they used to identify it. Never call addTransactions for those.
            Otherwise answer briefly and helpfully. Never invent transactions or figures, and never say you added, changed or deleted anything yourself — only the tools do that, and the user confirms each one.

            Expense categories: ${promptList(expenseCategories, MAX_EXPENSE_CATEGORY_CHARS)}
            Income categories: ${promptList(incomeCategories, MAX_INCOME_CATEGORY_CHARS)}
            Accounts: ${promptList(accounts, MAX_ACCOUNT_CHARS).ifEmpty { "none yet" }}

            Plain text only: no markdown, asterisks or backticks.
            A message can mention several transactions: call addTransactions once, with one entry in items for each of them.
            Every amount the user states is its own transaction: never add amounts together and never merge items.
            Money received, such as salary, is a transaction too: call addTransactions, don't just acknowledge it.
            Leave account empty unless the user names a bank, card or cash in this message.
            """.trimIndent()

        /** Names joined in order until [maxChars]; the rest are counted, not listed. */
        internal fun promptList(names: List<String>, maxChars: Int): String {
            var used = 0
            val shown = names.takeWhile { used += it.length + 2; used <= maxChars }
            val more = names.size - shown.size
            return (shown + listOfNotNull(if (more > 0) "and $more more" else null)).joinToString(", ")
        }

        // ~2,000 chars (~500 tokens) of lists at most; default categories fit with room to spare.
        private const val MAX_EXPENSE_CATEGORY_CHARS = 1200
        private const val MAX_INCOME_CATEGORY_CHARS = 400
        private const val MAX_ACCOUNT_CHARS = 500
        // The model replies in the user's language, so a false claim comes in it too.
        private val CLAIMS_A_CHANGE = Regex(
            "\\b(I(?:'ve| have)?|has been|have been|is now) (added|updated|changed|deleted|removed|recorded)\\b" +
                "|\\b(transaction|expense|income|payment|purchase|amount|it)s? (is|are|was|were) (added|updated|changed|deleted|removed|recorded)\\b" +
                "|\\b(added|recorded) successfully\\b" +
                "|أضفت|اضفت|سجلت|حذفت|عدلت|غيرت|تمت? (?:إضافة|اضافة|تسجيل|حذف|تعديل|تغيير)" +
                "|जोड़ दिया|जोड़ दी|हटा दिया|हटा दी|बदल दिया|बदल दी|दर्ज कर",
            RegexOption.IGNORE_CASE
        )
        private val DAY_MONTH = java.time.format.DateTimeFormatter.ofPattern("d MMM")
        private val CARD_TOOLS = setOf(PennyWiseTools.ADD_TRANSACTIONS, PennyWiseTools.DELETE_TRANSACTION, PennyWiseTools.UPDATE_TRANSACTION)
        // LiteRT-LM has no error type for it, only this message (0.19.0).
        private const val TOOL_CALL_PARSE_FAILURE = "Failed to parse tool calls"
        private const val TOOL_CALL_TAG = "<tool_call>"
        private const val TAG = "LlmRepository"
    }
}
