package com.pennywiseai.tracker.data.repository

import android.util.Log
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.model.ChatContext
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.service.LlmService
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
    private val userPreferencesRepository: UserPreferencesRepository
) {
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
            val systemPrompt = if (storedPrompt.isNullOrEmpty()) {
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

        // Stream response — LiteRT-LM conversation manages history internally
        val responseBuilder = StringBuilder()

        llmService.sendMessage(userMessage)
            .collect { partialResponse ->
                responseBuilder.append(partialResponse)
                emit(partialResponse)
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

            val result = llmService.createConversation(systemPrompt, history)
            if (result.isFailure) {
                throw result.exceptionOrNull() ?: Exception("Failed to create conversation")
            }
            Log.d(TAG, "Conversation created with ${history.size} history messages")
        }
    }

    /**
     * Re-renders the AI system prompt after a change to how money is displayed
     * (currently a base-currency change), **without discarding the conversation**.
     *
     * The prompt is derived state: a snapshot of the user's finances formatted in the
     * base currency. A currency change invalidates the *rendering*, not the chat, so
     * this replaces the rendering and leaves every visible message in place.
     *
     * All three cached copies have to move together, or the stale one wins:
     *  1. the DataStore preference — dropped by the currency setters, rebuilt below;
     *  2. the hidden `isSystemPrompt = true` row in chat history — [ensureConversation]
     *     rebuilds the LLM session from *this* copy, never from DataStore, so an
     *     existing chat would otherwise keep answering in the old currency;
     *  3. the live LiteRT-LM conversation, whose `systemInstruction` is fixed at
     *     construction and so can only be changed by rebuilding it.
     *
     * (3) is rebuilt by replaying the same history into a fresh conversation, so the
     * model keeps full context of what was already said and simply continues under the
     * new instruction. Only the hidden prompt row is deleted; `deleteSystemPromptMessages`
     * cannot touch user-visible messages.
     *
     * No-ops when there is no chat yet — a new chat builds its prompt on first send.
     */
    suspend fun refreshSystemPrompt() {
        val existing = chatDao.getAllMessagesForContext()
        if (existing.isEmpty()) {
            // Nothing to re-render; the next new chat picks up the new currency anyway.
            llmService.closeConversation()
            return
        }

        val newPrompt = buildSystemPrompt(aiContextRepository.getChatContext())
        userPreferencesRepository.updateSystemPrompt(newPrompt)

        // Swap the hidden prompt row, preserving its original position in the timeline
        // so ordering by timestamp still puts it ahead of the conversation.
        val oldTimestamp = existing.firstOrNull { it.isSystemPrompt }?.timestamp
            ?: existing.first().timestamp
        chatDao.deleteSystemPromptMessages()
        chatDao.insertMessage(
            ChatMessage(
                message = newPrompt,
                isUser = false,
                timestamp = oldTimestamp,
                isSystemPrompt = true
            )
        )

        // The live conversation pins the old instruction, so rebuild it around the
        // same visible history. ensureConversation() would also do this lazily on the
        // next send; doing it here keeps the in-memory state honest immediately.
        val history = chatDao.getAllMessagesForContext()
            .filter { !it.isSystemPrompt }
            .map { it.message to it.isUser }
        llmService.closeConversation()
        val result = llmService.createConversation(newPrompt, history)
        if (result.isFailure) {
            // Leave no conversation rather than a stale one: ensureConversation() will
            // recreate it from the (already updated) Room rows on the next message.
            llmService.closeConversation()
            Log.w(TAG, "Could not rebuild conversation after prompt refresh; will rebuild on next send")
        }
        Log.d(TAG, "System prompt re-rendered; ${history.size} messages preserved")
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

    private suspend fun buildSystemPrompt(context: ChatContext): String {
        val monthSummary = context.monthSummary
        val topCategories = context.topCategories
        val activeSubs = context.activeSubscriptions
        val stats = context.quickStats
        val currency = userPreferencesRepository.baseCurrency.first()

        val totalSubAmount = activeSubs.map { it.amount.toDouble() }.sum().toBigDecimal()
        val upcomingPayments = activeSubs.filter { it.nextPaymentDays <= 7 }

        return """
        You are PennyWise AI, a friendly financial assistant helping users track expenses and manage money.

        Current Financial Overview (${context.currentDate}):
        - This month: ${CurrencyFormatter.formatCurrency(monthSummary.totalExpense, currency)} spent, ${CurrencyFormatter.formatCurrency(monthSummary.totalIncome, currency)} income
        - ${monthSummary.transactionCount} transactions (Day ${monthSummary.currentDay}/${monthSummary.daysInMonth})
        - Daily average: ${CurrencyFormatter.formatCurrency(stats.avgDailySpending, currency)}

        Top spending categories:
        ${topCategories.joinToString("\n") { "- ${it.category}: ${CurrencyFormatter.formatCurrency(it.amount, currency)} (${it.percentage.toInt()}%)" }}

        Active subscriptions: ${activeSubs.size} services (${CurrencyFormatter.formatCurrency(totalSubAmount, currency)}/month)
        ${if (upcomingPayments.isNotEmpty()) "${upcomingPayments.size} payments due in next 7 days" else ""}

        Recent Transactions (Last 14 days):
        ${context.recentTransactions.take(10).joinToString("\n") { transaction ->
            val dateStr = transaction.dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
            val typeStr = if (transaction.transactionType == TransactionType.INCOME) "+" else "-"
            "- $dateStr: ${transaction.merchantName} ${typeStr}${CurrencyFormatter.formatCurrency(transaction.amount, currency)} (${transaction.category})"
        }}

        ${if (stats.mostFrequentMerchant != null) "Most visited: ${stats.mostFrequentMerchant} (${stats.mostFrequentMerchantCount} times)" else ""}

        Guidelines:
        - Be helpful and non-judgmental about spending
        - Provide actionable insights when asked
        - Use the appropriate currency symbol for amounts (match the currency shown in the data above)
        - Reference actual data when answering
        - Keep responses concise and relevant
        - Use plain text formatting only - no markdown, no special characters
        - Do not use asterisks, underscores, backticks or other markdown syntax
        - For emphasis, use CAPS or simple quotes
        - For lists, use simple dashes or numbers
        - Keep responses clean and readable without formatting
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
        val recentCount = minOf(chatContext.recentTransactions.size, 10)
        val activeSubs = chatContext.activeSubscriptions

        return """
        Hi! I'm PennyWise AI, your financial assistant.

        I have access to:
        • Your last 2 weeks of transactions ($recentCount recent ones)
        • This month's summary (${monthSummary.transactionCount} total transactions)
        • Monthly income and expenses
        • Top spending categories
        • Active subscriptions (${activeSubs.size} services)
        • Daily spending averages

        I can help you understand your spending, find savings, and answer questions about your recent finances.

        What would you like to know?
        """.trimIndent()
    }

    companion object {
        private const val TAG = "LlmRepository"
    }
}
