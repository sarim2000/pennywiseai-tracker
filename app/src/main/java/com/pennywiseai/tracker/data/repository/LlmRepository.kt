package com.pennywiseai.tracker.data.repository

import android.content.Context
import android.util.Log
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ChatMessageFacts
import com.pennywiseai.tracker.data.model.MonthSummary
import com.pennywiseai.tracker.data.model.PendingChatAction
import com.pennywiseai.tracker.data.model.ReviewRow
import com.pennywiseai.tracker.data.model.SpendingComparison
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.data.model.TransactionFinder
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.domain.service.LlmEvent
import com.pennywiseai.tracker.domain.service.LlmService
import com.pennywiseai.tracker.domain.service.TOOL_CALL_PARSE_FAILURE
import com.pennywiseai.tracker.domain.service.isContextFull
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.TokenUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // The last card the user answered: what it showed, and whether it was saved. Checked against
    // a reply that claims a change, so "did you add it?" after a cancel gets the facts (#1083).
    private var lastCard: AnsweredCard? = null

    /** [mentions]: the merchants and amounts the card showed, to tell a reply about it from one about something else. */
    private class AnsweredCard(val shown: String, val saved: Boolean, val mentions: List<String> = emptyList())

    // One build of the live conversation at a time: the warm-up on open and a send can race.
    private val conversationLock = Mutex()

    // Saved messages a long chat dropped from the model's window, oldest first; the screen keeps
    // showing them. Guarded by conversationLock.
    private val droppedIds = mutableSetOf<String>()

    // Saved messages replayed into the live conversation. A message saved before the warm-up
    // read the chat would otherwise reach the model twice.
    private var replayedIds = emptySet<String>()

    private val _contextTokens = MutableStateFlow(0)
    /** Tokens the model's window holds, measured by LiteRT-LM (developer stats). */
    val contextTokens: StateFlow<Int> = _contextTokens.asStateFlow()

    /** The user changed the rows on the review card. */
    fun updateRows(change: (List<ReviewRow>) -> List<ReviewRow>) {
        val card = _pendingAction.value as? PendingChatAction.Add ?: return
        _pendingAction.value = card.copy(rows = change(card.rows))
    }

    /** The card was cancelled, left or superseded: nothing changed, and the model hears that. */
    fun clearPendingAction() {
        _pendingAction.value?.let {
            cardOutcome = "The user cancelled the last card. Nothing was changed."
            lastCard = AnsweredCard(describe(it), saved = false, mentions = mentionsOf(it))
        }
        _pendingAction.value = null
    }

    /** The user's tap went through: [line] (e.g. "Added ₹120 at Starbucks") joins the chat. */
    suspend fun completePendingAction(line: String) {
        lastCard = AnsweredCard(line, saved = true, mentions = _pendingAction.value?.let(::mentionsOf).orEmpty())
        _pendingAction.value = null
        cardOutcome = "The user confirmed the last card. It is saved."
        // The save already happened. A lost line must not read as a failed save worth repeating.
        runCatching { chatDao.insertMessage(ChatMessage(message = line, isUser = false)) }
            .onFailure { Log.w(TAG, "Couldn't save the line after a card (${it.javaClass.simpleName})") }
    }

    fun getAllMessages(): Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getAllMessagesIncludingSystem(): Flow<List<ChatMessage>> = chatDao.getAllMessagesIncludingSystem()

    fun sendMessageStream(userMessage: String): Flow<String> = flow {
        val existingMessages = chatDao.getAllMessagesForContext()
        val isNewChat = existingMessages.isEmpty()

        // If new chat, add system prompt first. Always rebuilt: a cached one
        // carries a stale date, figures and lists — and, when oversized, used to
        // block every new chat until something refreshed the cache (#959).
        if (isNewChat) {
            val systemPrompt = buildSystemPrompt(aiContextRepository.getMonthSummary())
            userPreferencesRepository.updateSystemPrompt(systemPrompt)

            val systemMessage = ChatMessage(
                message = systemPrompt,
                isUser = false,
                isSystemPrompt = true
            )
            chatDao.insertMessage(systemMessage)
            Log.d(TAG, "System prompt added to new chat")
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

        val prompt = cardOutcome?.let { "($it)\n$userMessage" } ?: userMessage
        val cardJustAnswered = cardOutcome != null

        // Ensure engine is initialized and conversation is active, with room for this turn
        ensureConversation(pending = userChatMessage, incoming = prompt)
        cardOutcome = null

        // Stream response — LiteRT-LM conversation manages history internally.
        // Tool calls are handed to us (never auto-run, #170): a lookup is
        // answered from the DB, a proposed transaction becomes a confirm card.
        val responseBuilder = StringBuilder()
        val toolCalls = mutableListOf<LlmEvent.ToolCall>()

        var unreadableToolCall = false
        var conversationBroken = false
        var shownUpTo = 0
        var retried = false
        while (true) {
            try {
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
                break
            } catch (e: Exception) {
                // The history plus this message overflowed the model's window before it said
                // anything: drop the older half of what it sees, once, and ask again.
                if (retried || e is CancellationException || !e.isContextFull() ||
                    responseBuilder.isNotEmpty() || toolCalls.isNotEmpty()
                ) throw e
                retried = true
                Log.w(TAG, "Context window full; retrying with the older turns dropped")
                ensureConversation(pending = userChatMessage, incoming = prompt, shrink = true)
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
        // A comparison is the code's figures alone: prose beside it could carry the model's own arithmetic.
        if (toolCalls.any { it.isComparison(userMessage) }) {
            // The live conversation still holds that prose; the next turn rebuilds from what was saved.
            if (responseBuilder.isNotEmpty()) conversationBroken = true
            responseBuilder.clear()
            // Off the screen too, before the figures land beside it.
            if (shownUpTo > 0) emit(RESET_REPLY)
        }
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
        // A small model sometimes narrates a change instead of calling the tool. Unless it's about
        // the card just saved, nothing happened: the facts replace the claim, or the user reads
        // "Yes, I added it" above the correction and walks away believing it (#1083).
        val card = lastCard
        val reply = responseBuilder.toString()
        // An earlier card says nothing about a later request.
        val aboutCard = card != null && (cardJustAnswered || mentions("$userMessage\n$reply", card.mentions))
        if (toolCalls.isEmpty() && !(aboutCard && card?.saved == true) &&
            claimsAChange(reply, card?.takeIf { !it.saved }?.mentions.orEmpty())
        ) {
            responseBuilder.clear()
            emit(RESET_REPLY)
            // The live conversation still holds the claim; the next turn rebuilds from the save.
            conversationBroken = true
            addLine(
                if (aboutCard && card?.saved == false) context.getString(R.string.chat_reply_card_cancelled, card.shown)
                else context.getString(R.string.chat_reply_nothing_changed)
            )
        }
        // Its state after a failed turn is unknown; the next message rebuilds it from the saved chat.
        if (conversationBroken) llmService.closeConversation()
        // Last, so the check above doesn't take this line's "Nothing was changed" for a claim.
        // Also when the model said nothing at all, rather than leaving an empty bubble.
        if (unreadableToolCall || responseBuilder.isBlank()) addLine(context.getString(R.string.chat_reply_unreadable))

        llmService.tokenCount()?.let { _contextTokens.value = it }

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

    // The chat screen the model is loaded for. A closed screen's late release must not
    // unload the model under the screen that replaced it.
    @Volatile private var modelOwner: Any? = null

    /** [owner] is the chat on screen now; only its [releaseModel] counts. */
    fun claimModel(owner: Any) { modelOwner = owner }

    /** Frees the model's engine if [owner] still holds it; [ensureConversation] rebuilds it on next use. */
    suspend fun releaseModel(owner: Any) = conversationLock.withLock {
        if (modelOwner === owner) llmService.reset()
    }

    /** Loads the model and prefills the chat when the screen opens, while the user is still typing. */
    suspend fun warmUp() {
        if (modelRepository.modelState.first() != ModelState.READY) return
        // Already warm. The screen warms up on every return, maybe while a reply still streams,
        // and re-checking a long chat's window here (with a reply's worth reserved) closed the
        // conversation under that reply. A send checks its own room.
        conversationLock.withLock { if (llmService.isInitialized() && llmService.hasActiveConversation()) return }
        ensureConversation(pending = null, incoming = "")
    }

    /**
     * A live conversation with room for [incoming] and its reply, built from the saved chat if
     * needed. [pending] is the message about to be sent: saved already, so kept out of the
     * replayed history. When the chat outgrows the model's window, its oldest turns leave the
     * window (not the screen) instead of the chat refusing to go on.
     */
    private suspend fun ensureConversation(pending: ChatMessage?, incoming: String, shrink: Boolean = false) = conversationLock.withLock {
        if (!llmService.isInitialized()) {
            val modelFile = modelRepository.getModelFile()
            if (!modelFile.exists()) {
                throw Exception("Model not downloaded. Please download from Settings.")
            }

            val t0 = System.nanoTime()
            // Integrity gate: never load a model whose bytes don't match the
            // pinned hash (guards against an in-place-swapped or corrupted file).
            if (!modelRepository.verifyModelIntegrity()) {
                modelRepository.deleteModel()
                throw Exception(INTEGRITY_FAILED)
            }
            val t1 = System.nanoTime()

            val initResult = llmService.initialize(modelFile.absolutePath)
            if (initResult.isFailure) {
                throw initResult.exceptionOrNull() ?: Exception("Failed to initialize LLM")
            }
            Log.i(TIMING, "hash=${ms(t0, t1)}ms engine=${ms(t1)}ms")
        }

        var outgrown = shrink
        if (llmService.hasActiveConversation()) {
            val replayed = pending != null && pending.id in replayedIds
            val used = llmService.tokenCount()
            if (!replayed && used != null && TokenUtils.fitsWindow(used, incoming)) {
                _contextTokens.value = used
                return@withLock
            }
            outgrown = !replayed && used != null
            llmService.closeConversation()
        }

        // Always fresh: the row saved when the chat began carries that day's date and figures.
        val systemPrompt = buildSystemPrompt(aiContextRepository.getMonthSummary())
        val saved = chatDao.getAllMessagesForContext().filter { !it.isSystemPrompt && !it.id.startsWith(QUICK_ID) }
        // A trailing message that got no reply isn't replayed: it's usually the one being sent,
        // saved while the warm-up loaded the model, and replaying it would mean building twice.
        var history = saved.filter { it.id != pending?.id && it.id !in droppedIds }.dropLastWhile { it.isUser }
        // Dropped only once a conversation takes the message: a message too long for any window
        // must not cost the model the rest of the chat.
        val dropping = mutableSetOf<String>()
        fun halve() = dropOlderHalf(history).also { kept -> dropping += (history - kept.toSet()).map { it.id } }
        // The live conversation was this history and no longer fits: rebuilt whole, it wouldn't either.
        if (outgrown) history = halve()
        while (true) {
            val t0 = System.nanoTime()
            val result = llmService.createConversation(systemPrompt, history.map { it.message to it.isUser }, withTools = true)
            val failure = result.exceptionOrNull()
            // The preface is prefilled on creation, so a history longer than the window fails here.
            if (failure != null && !failure.isContextFull()) throw failure
            val used = if (failure == null) llmService.tokenCount() ?: 0 else -1
            Log.i(TIMING, "conversation=${ms(t0)}ms history=${history.size} tokens=$used")
            if (failure == null && TokenUtils.fitsWindow(used, incoming)) {
                droppedIds += dropping
                replayedIds = history.mapTo(mutableSetOf()) { it.id }
                _contextTokens.value = used
                return@withLock
            }
            // The system prompt and tools alone leave no room for this message. The next one
            // rebuilds the window as it was, without this one, which the model never saw.
            if (history.isEmpty()) {
                llmService.closeConversation()
                pending?.let { droppedIds += it.id }
                throw Exception(MESSAGE_TOO_LONG)
            }
            history = halve()
        }
    }

    // Under conversationLock: a warm-up mid-build would otherwise replay the cleared chat. That can
    // make the clear wait for the model to load, so it takes only what was there when it was asked.
    suspend fun deleteAllMessages() {
        val asked = System.currentTimeMillis()
        conversationLock.withLock {
            llmService.closeConversation()
            chatDao.deleteMessagesBefore(asked)
            cardOutcome = null
            lastCard = null
            droppedIds.clear()
            replayedIds = emptySet()
            _contextTokens.value = 0
        }
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

    /**
     * A compareSpending call, or a this-month lookup for a message that names last month or week:
     * those lookups can't answer it, and the small model reaches for them in Hinglish.
     */
    private fun LlmEvent.ToolCall.isComparison(userMessage: String) = name == PennyWiseTools.COMPARE_SPENDING ||
        (name in MONTH_LOOKUPS && SpendingComparison.namesThePreviousPeriod(userMessage) &&
            !(name == PennyWiseTools.MONTH_SUMMARY && SpendingComparison.asksAboutIncome(userMessage)))

    private suspend fun handleToolCallInner(call: LlmEvent.ToolCall, userMessage: String): String? = if (call.isComparison(userMessage)) {
        val period = SpendingComparison.period(userMessage, call.arguments["period"])
        val asked = (call.arguments["category"] as? String)?.trim().orEmpty()
        val known = if (asked.isEmpty()) null else matchCategory(asked, categoryRepository.getAllCategories().first())
        val category = known ?: asked.ifEmpty { null }
        if (period == null) context.getString(R.string.chat_reply_compare_unsupported)
        else aiContextRepository.compareSpending(period, category, creditAsExpense()).let { result ->
            // A name neither the list nor any transaction carries: "all" from the model means all
            // spending; anything else is unknown, not a category with nothing spent.
            val unknown = category != null && known == null && result is SpendingComparison.Result.Rows && result.rows.isEmpty()
            when {
                unknown && SpendingComparison.meansAllSpending(asked) -> comparisonText(aiContextRepository.compareSpending(period, null, creditAsExpense()), null)
                unknown -> context.getString(R.string.chat_reply_unknown_category, asked)
                else -> comparisonText(result, category)
            }
        }
    } else when (call.name) {
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
            val m = aiContextRepository.getMonthSummary()
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

    /**
     * M8: a one-tap question answered by code alone, before the model has loaded or without it.
     * Saved for the screen but never shown to the model (see [QUICK_ID]). Per currency, never
     * converted, like the comparison.
     */
    private suspend fun creditAsExpense() = userPreferencesRepository.countCreditCardAsExpense.first()

    suspend fun answerQuickAction(action: QuickAction) {
        val money = { amount: BigDecimal, currency: String -> CurrencyFormatter.formatCurrency(amount, currency, maskIfHidden = false) }
        // The user's own currency on top, the rest by code: the same order in every answer.
        val base = userPreferencesRepository.baseCurrency.first()
        fun <V> Map<String, V>.ordered() = entries.sortedWith(compareBy({ it.key != base }, { it.key })).map { it.key to it.value }
        val answer = when (action) {
            QuickAction.COMPARE_MONTH -> comparisonText(aiContextRepository.compareSpending(SpendingComparison.Period.MONTH, null, creditAsExpense()), null)
            QuickAction.MONTH_SPEND -> {
                val (window, rows) = aiContextRepository.monthSoFar()
                // Forgiven loan balances count as spent, as on Home (#938).
                val spent = with(SpendingComparison) { spend(rows, creditAsExpense = creditAsExpense()).plusAmounts(aiContextRepository.settlementLoss(window)) }
                if (spent.isEmpty()) context.getString(R.string.chat_reply_quick_nothing, range(window))
                else (listOf(context.getString(R.string.chat_reply_month_spend, range(window))) + spent.ordered().map { (c, a) -> money(a, c) }).joinToString("\n")
            }
            QuickAction.TOP_CATEGORIES -> {
                val (window, rows) = aiContextRepository.monthSoFar()
                val top = SpendingComparison.topCategories(rows, creditAsExpense = creditAsExpense())
                if (top.isEmpty()) context.getString(R.string.chat_reply_quick_nothing, range(window))
                // A blank line between currencies: each is its own ranking.
                else context.getString(R.string.chat_reply_top_categories, range(window)) + "\n" +
                    top.ordered().joinToString("\n\n") { (c, cats) -> cats.joinToString("\n") { (name, a) -> "$name: ${money(a, c)}" } }
            }
        }
        val at = System.currentTimeMillis()
        // The DAO orders by timestamp alone: the reply must sort after its question.
        chatDao.insertMessages(listOf(
            ChatMessage(id = QUICK_ID + UUID.randomUUID(), message = context.getString(action.label), isUser = true, timestamp = at),
            ChatMessage(id = QUICK_ID + UUID.randomUUID(), message = answer, isUser = false, timestamp = at + 1)
        ))
    }

    private fun range(w: SpendingComparison.Window): String {
        val (a, b) = w.start.format(DAY_MONTH) to w.end.format(DAY_MONTH)
        return if (a == b) a else "$a–$b"
    }

    /** A comparison as chat lines: a dated header, then one line per currency. Also for M8's one-tap question. */
    fun comparisonText(result: SpendingComparison.Result, category: String?): String {
        if (result is SpendingComparison.Result.NoData) return context.getString(R.string.chat_reply_compare_no_data, range(result.missing))
        val rows = (result as SpendingComparison.Result.Rows).rows
        val now = range(result.current)
        val before = range(result.previous)
        if (rows.isEmpty()) return if (category == null) context.getString(R.string.chat_reply_compare_nothing, now, before)
        else context.getString(R.string.chat_reply_compare_nothing_category, category, now, before)
        val money = { amount: BigDecimal, currency: String -> CurrencyFormatter.formatCurrency(amount, currency, maskIfHidden = false) }
        return buildList {
            add(
                if (category == null) context.getString(R.string.chat_reply_compare_title, now, before)
                else context.getString(R.string.chat_reply_compare_title_category, category, now, before)
            )
            rows.forEach { row ->
                val pair = context.getString(R.string.chat_reply_compare_row, money(row.current, row.currency), money(row.previous, row.currency))
                val change = row.changePercent?.let {
                    when (it.signum()) {
                        1 -> context.getString(R.string.chat_reply_compare_more, it.toPlainString())
                        -1 -> context.getString(R.string.chat_reply_compare_less, it.negate().toPlainString())
                        else -> context.getString(R.string.chat_reply_compare_same)
                    }
                }
                add(if (change == null) pair else "$pair ($change)")
            }
        }.joinToString("\n")
    }

    /** What a card showed, each amount in its own currency. */
    private fun describe(card: PendingChatAction): String = when (card) {
        is PendingChatAction.Add -> card.rows.joinToString(", ") { amountLine(it.draft.amount, it.draft.currency, it.draft.type, it.draft.merchant) }
        is PendingChatAction.Delete -> describe(card.transaction)
        is PendingChatAction.Update -> describe(card.transaction)
    }

    private fun mentionsOf(card: PendingChatAction): List<String> = when (card) {
        is PendingChatAction.Add -> card.rows.flatMap { listOf(it.draft.merchant, plain(it.draft.amount)) }
        is PendingChatAction.Delete -> listOf(card.transaction.merchantName, plain(card.transaction.amount))
        is PendingChatAction.Update -> listOfNotNull(card.transaction.merchantName, card.newMerchant, plain(card.transaction.amount))
    }

    private fun plain(amount: BigDecimal) = amount.stripTrailingZeros().toPlainString()

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
    private suspend fun buildSystemPrompt(monthSummary: MonthSummary): String {
        val currency = userPreferencesRepository.baseCurrency.first()
        val categories = categoryRepository.getVisibleCategories().first()
        val ignored = ignoredAccountsStore.keys()
        val accounts = accountBalanceRepository.getAllLatestBalancesOnce()
            .filter { IgnoredAccountsStore.keyFor(it.bankName, it.accountLast4) !in ignored }
            .map { it.alias ?: "${it.bankName} ${it.accountLast4}" }
        return composeSystemPrompt(
            currentDate = java.time.LocalDate.now().toString(),
            monthLine = "${CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currency, maskIfHidden = false)} spent, " +
                "${CurrencyFormatter.formatCurrency(monthSummary.totalIncome, currency, maskIfHidden = false)} income, ${monthSummary.transactionCount} transactions",
            expenseCategories = categories.filter { !it.isIncome }.map { it.name },
            incomeCategories = categories.filter { it.isIncome }.map { it.name },
            accounts = accounts
        )
    }

    suspend fun updateSystemPrompt() {
        val newPrompt = buildSystemPrompt(aiContextRepository.getMonthSummary())
        userPreferencesRepository.updateSystemPrompt(newPrompt)
        // Close conversation so it gets recreated with updated prompt
        llmService.closeConversation()
        Log.d(TAG, "System prompt updated with latest financial data")
    }

    suspend fun getFormattedContextForDisplay(): String {
        val monthSummary = aiContextRepository.getMonthSummary()
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
         * prompt stays a bounded slice of [TokenUtils.MODEL_CONTEXT_TOKENS] however
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
            When the user asks to compare spending with last month or last week, call compareSpending.
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

        /**
         * Marks a quick-action question and reply. They stay out of the model's window: replayed,
         * they made Qwen2.5 copy their figures as the answer to the next question ("what about last
         * week?" got this month's) on S23. A follow-up is answered from its own words instead:
         * "last month" already routes to the comparison.
         */
        internal const val QUICK_ID = "quick-"

        /** The newer half of [history], from a user turn so the model never starts mid-exchange. */
        internal fun dropOlderHalf(history: List<ChatMessage>): List<ChatMessage> =
            history.drop((history.size + 1) / 2).dropWhile { !it.isUser }

        private fun ms(from: Long, to: Long = System.nanoTime()) = (to - from) / 1_000_000

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
            // Passive too: "the lunch at TasteHouse for ₹220 was added" (#1083). "Changed by the bank" is the bank's doing, not a claim.
            "\\b(I(?:['’]ve| have)?|has been|have been|is now|was|were|got) (added|saved|recorded|logged|created|deleted|removed|updated|changed)\\b(?! by (?:the|your|a|an)\\b)" +
                "|\\b(transaction|expense|income|payment|purchase|amount|it)s? (is|are) (added|saved|recorded|logged|created|deleted|removed|updated|changed)\\b" +
                "|\\b(added|recorded|saved|logged) successfully\\b" +
                "|أضفت|اضفت|سجلت|حذفت|عدلت|غيرت|تمت? (?:إضافة|اضافة|تسجيل|حذف|تعديل|تغيير)" +
                "|जोड़ दिया|जोड़ दी|हटा दिया|हटा दी|बदल दिया|बदल दी|दर्ज कर",
            RegexOption.IGNORE_CASE
        )
        // Earlier in a claim's clause, these deny it ("nothing was added"); "I've added it, but I couldn't find an account" still claims.
        private val NEGATION = Regex("\\b(no|not|nothing|never|cancell?ed)\\b|n['’]t\\b|لم |لا |ليس|नहीं ", RegexOption.IGNORE_CASE)
        private val AFFIRMATIVE = Regex("\\b(yes|yeah|yep|sure|done|added|saved|recorded|logged)\\b|نعم|हाँ|हां", RegexOption.IGNORE_CASE)
        // Not every ".": "₹220.50" is one figure.
        private val SENTENCE_END = Regex("[!?؟।\n]|\\.(?=\\s|$)")
        // ponytail: Arabic "و" is left out: it's a prefix, so it can't be told from a word starting with it.
        private val CLAUSE_BREAK = Regex("[,;،]|\\b(but|and|so|because|however|although|while)\\b|لكن|लेकिन|और", RegexOption.IGNORE_CASE)

        /**
         * Whether [reply] says a change happened. Wording varies without end, so a "yes" in the
         * same sentence as a cancelled card's merchant or amount ([cancelled]) counts too.
         */
        internal fun claimsAChange(reply: String, cancelled: List<String>): Boolean = reply.split(SENTENCE_END).any { s ->
            CLAIMS_A_CHANGE.findAll(s).any { !negated(s, it.range.first) } ||
                (AFFIRMATIVE.findAll(s).any { !negated(s, it.range.first) } && mentions(s, cancelled))
        }

        /** A negation earlier in the claim's own clause: "No transactions from your message were added because…". */
        private fun negated(sentence: String, at: Int): Boolean {
            val before = sentence.substring(0, at)
            val start = CLAUSE_BREAK.findAll(before).lastOrNull()?.range?.last?.plus(1) ?: 0
            return NEGATION.containsMatchIn(before.substring(start) + " ")
        }

        /** Whether [text] names one of [of]: a merchant, or an amount as a whole number. */
        internal fun mentions(text: String, of: List<String>): Boolean {
            // ponytail: "₹1,220" is read as 1220, but "220.50" misses a card of 220.5; the merchant usually catches it.
            val flat = text.replace(",", "")
            return of.any { m ->
                if (m.toBigDecimalOrNull() != null) Regex("(?<![\\d.])${Regex.escape(m)}(?!\\d)").containsMatchIn(flat)
                else m.length >= 3 && flat.contains(m, ignoreCase = true)
            }
        }
        private val DAY_MONTH = java.time.format.DateTimeFormatter.ofPattern("d MMM")
        private val MONTH_LOOKUPS = setOf(PennyWiseTools.MONTH_SUMMARY, PennyWiseTools.SPENDING_BY_CATEGORY)
        private val CARD_TOOLS = setOf(PennyWiseTools.ADD_TRANSACTIONS, PennyWiseTools.DELETE_TRANSACTION, PennyWiseTools.UPDATE_TRANSACTION)
        private const val TOOL_CALL_TAG = "<tool_call>"
        /** Emitted in place of text: drop what was streamed so far. */
        internal const val RESET_REPLY = "\u0000reset"
        // Matched by ChatViewModel.sendErrorText: these exception messages are never shown.
        internal const val MESSAGE_TOO_LONG = "Message too long for the model"
        internal const val INTEGRITY_FAILED = "AI model failed its integrity check and was removed"
        private const val TAG = "LlmRepository"
        // Timings only, for measuring the first reply: adb logcat -s ChatTiming
        internal const val TIMING = "ChatTiming"
    }
}

/** M8's one-tap questions: an action the code answers, never text for the model. */
enum class QuickAction(@androidx.annotation.StringRes val label: Int) {
    MONTH_SPEND(R.string.chat_quick_month_spend),
    TOP_CATEGORIES(R.string.chat_quick_top_categories),
    COMPARE_MONTH(R.string.chat_quick_compare_month)
}
