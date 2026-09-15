package com.pennywiseai.tracker.data.repository

import android.util.Log
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ChatContext
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.service.LlmService
import com.pennywiseai.shared.domain.mapping.SharedCategoryMapping
import com.pennywiseai.tracker.data.model.TransactionDraft
import com.pennywiseai.tracker.data.service.PennyWiseTools
import com.pennywiseai.tracker.domain.service.LlmEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository @Inject constructor(
    private val llmService: LlmService,
    private val chatDao: ChatDao,
    private val modelRepository: ModelRepository,
    private val aiContextRepository: AiContextRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) {

    // A transaction the model proposed via the add_transaction tool (#170),
    // waiting for the user's confirmation on the chat screen. Never auto-saved.
    private val _pendingTransaction = MutableStateFlow<TransactionDraft?>(null)
    val pendingTransaction: StateFlow<TransactionDraft?> = _pendingTransaction.asStateFlow()

    fun clearPendingTransaction() { _pendingTransaction.value = null }

    /** Adds an assistant line to the history (e.g. "Added ₹120 at Starbucks"). */
    suspend fun appendAssistantMessage(text: String) {
        chatDao.insertMessage(ChatMessage(message = text, isUser = false))
    }

    fun getAllMessages(): Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getAllMessagesIncludingSystem(): Flow<List<ChatMessage>> = chatDao.getAllMessagesIncludingSystem()

    suspend fun sendMessage(userMessage: String): Result<String> {
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true
        )
        chatDao.insertMessage(userChatMessage)

        ensureConversation()

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

        // If new chat, add system prompt first
        if (isNewChat) {
            val storedPrompt = userPreferencesRepository.getSystemPrompt().first()
            val systemPrompt = if (storedPrompt.isNullOrEmpty() || !storedPrompt.startsWith(PROMPT_MARKER)) {
                val chatContext = aiContextRepository.getChatContext()
                val newPrompt = buildSystemPrompt(chatContext)
                userPreferencesRepository.updateSystemPrompt(newPrompt)
                newPrompt
            } else {
                storedPrompt
            }

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
        val totalChars = currentMessages.map { it.message.length }.sum() + userMessage.length
        val estimatedTokens = totalChars / 4

        if (estimatedTokens > 1200) {
            throw Exception("Chat memory is full. Please clear the chat to continue.")
        }

        // Save user message
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true
        )
        Log.d(TAG, "Saving user message: ${userMessage.take(50)}...")
        chatDao.insertMessage(userChatMessage)

        // Check if model is downloading
        val currentModelState = modelRepository.modelState.first()
        if (currentModelState == ModelState.DOWNLOADING) {
            throw Exception("Model is currently downloading. Please wait for download to complete.")
        }

        // Ensure engine is initialized and conversation is active
        ensureConversation()

        Log.d(TAG, "=== SENDING TO LLM ===")
        Log.d(TAG, "Total messages in context: ${currentMessages.size}")
        Log.d(TAG, "Estimated tokens: $estimatedTokens")

        // Stream response — LiteRT-LM conversation manages history internally.
        // Tool calls are handed to us (never auto-run, #170): a lookup is
        // answered from the DB, a proposed transaction becomes a confirm card.
        val responseBuilder = StringBuilder()
        val toolCalls = mutableListOf<LlmEvent.ToolCall>()

        llmService.sendMessageEvents(userMessage)
            .collect { event ->
                when (event) {
                    is LlmEvent.Text -> { responseBuilder.append(event.delta); emit(event.delta) }
                    is LlmEvent.ToolCall -> toolCalls.add(event)
                }
            }
        for (call in toolCalls) {
            val line = handleToolCall(call, userMessage) ?: continue
            if (responseBuilder.isNotEmpty()) { responseBuilder.append("\n"); emit("\n") }
            responseBuilder.append(line); emit(line)
        }

        // Save the complete AI response
        val finalResponse = responseBuilder.toString()
        Log.d(TAG, "Saving AI response: ${finalResponse.take(50)}...")
        val aiMessage = ChatMessage(
            message = finalResponse,
            isUser = false
        )
        chatDao.insertMessage(aiMessage)
        Log.d(TAG, "AI response saved")
    }

    private suspend fun ensureConversation() {
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

            // Extract system prompt
            val systemPrompt = allMessages
                .firstOrNull { it.isSystemPrompt }
                ?.message ?: run {
                    val chatContext = aiContextRepository.getChatContext()
                    buildSystemPrompt(chatContext)
                }

            // Build history (excluding system prompt)
            val history = allMessages
                .filter { !it.isSystemPrompt }
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
    private suspend fun handleToolCall(call: LlmEvent.ToolCall, userMessage: String): String? = when (call.name) {
        PennyWiseTools.ADD_TRANSACTION -> {
            val draft = TransactionDraft.fromToolArgs(
                args = call.arguments,
                sourceText = userMessage,
                categories = categoryRepository.getVisibleCategories().first(),
                accounts = accountBalanceRepository.getAllLatestBalancesOnce(),
                fallbackCategory = { merchant -> SharedCategoryMapping.getCategory(merchant) }
            )
            if (draft == null) {
                "I couldn't work out the amount. Try something like \"coffee 120 at Starbucks\"."
            } else {
                _pendingTransaction.value = draft
                val currency = userPreferencesRepository.baseCurrency.first()
                "Here's what I'll add — confirm below:\n${CurrencyFormatter.formatCurrency(draft.amount, currency)} " +
                    "${if (draft.type == TransactionType.INCOME) "from" else "at"} ${draft.merchant} · ${draft.category} · ${draft.accountLabel}"
            }
        }
        PennyWiseTools.SPENDING_BY_CATEGORY -> {
            val category = (call.arguments["category"] as? String)?.trim().orEmpty()
            val currency = userPreferencesRepository.baseCurrency.first()
            val spend = aiContextRepository.getCategorySpending(category)
            if (spend == null) "Nothing spent on $category this month."
            else "${CurrencyFormatter.formatCurrency(spend.amount, currency)} on ${spend.category} this month across ${spend.transactionCount} transactions."
        }
        else -> null
    }

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
        val expense = categories.filter { !it.isIncome }.joinToString(", ") { it.name }
        val income = categories.filter { it.isIncome }.joinToString(", ") { it.name }
        val accounts = accountBalanceRepository.getAllLatestBalancesOnce()
            .joinToString(", ") { it.alias ?: "${it.bankName} ${it.accountLast4}" }
            .ifEmpty { "none yet" }

        return """
        $PROMPT_MARKER
        You are PennyWise AI, a friendly assistant inside an expense tracker. Today is ${context.currentDate}.
        This month so far: ${CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currency)} spent, ${CurrencyFormatter.formatCurrency(monthSummary.totalIncome, currency)} income, ${monthSummary.transactionCount} transactions.

        When the user tells you about money they spent, paid, bought or received, call addTransaction. Use EXPENSE unless they clearly received money.
        When the user asks how much they spent on something, call spendingByCategory.
        Otherwise answer briefly and helpfully. Never invent transactions or figures.

        Expense categories: $expense
        Income categories: $income
        Accounts: $accounts

        Plain text only: no markdown, asterisks or backticks.
        """.trimIndent()
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

        return """
        Hi! I'm PennyWise AI.

        Tell me what you spent and I'll add it after you confirm — e.g. "coffee 120 at Starbucks" or "got 50000 salary". Ask "how much on groceries this month?" for a total.

        This month so far: ${monthSummary.transactionCount} transactions, ${CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currency)} spent.
        Nothing is saved without your tap.
        """.trimIndent()
    }

    companion object {
        private const val PROMPT_MARKER = "[PennyWise tools v2]"
        private const val TAG = "LlmRepository"
    }
}
