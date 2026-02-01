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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
        // Save user message
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true
        )
        chatDao.insertMessage(userChatMessage)
        
        // Initialize LLM if needed
        if (!llmService.isInitialized()) {
            val modelFile = modelRepository.getModelFile()
            if (!modelFile.exists()) {
                return Result.failure(Exception("Model not downloaded"))
            }
            
            val initResult = llmService.initialize(modelFile.absolutePath)
            if (initResult.isFailure) {
                return Result.failure(initResult.exceptionOrNull() ?: Exception("Failed to initialize LLM"))
            }
        }
        
        // Generate response
        val responseResult = llmService.generateResponse(userMessage)
        
        return if (responseResult.isSuccess) {
            val response = responseResult.getOrNull() ?: ""
            
            // Save AI response
            val aiChatMessage = ChatMessage(
                message = response,
                isUser = false
            )
            chatDao.insertMessage(aiChatMessage)
            
            Result.success(response)
        } else {
            Result.failure(responseResult.exceptionOrNull() ?: Exception("Failed to generate response"))
        }
    }
    
    fun sendMessageStream(userMessage: String): Flow<String> = flow {
        // Check if this is the first message (no existing messages)
        val existingMessages = chatDao.getAllMessagesForContext()
        val isNewChat = existingMessages.isEmpty()
        
        // If new chat, add system prompt first
        if (isNewChat) {
            // Try to get stored system prompt first
            val storedPrompt = userPreferencesRepository.getSystemPrompt().first()
            val systemPrompt = if (storedPrompt.isNullOrEmpty()) {
                // Generate new prompt if none exists
                val chatContext = aiContextRepository.getChatContext()
                val newPrompt = buildSystemPrompt(chatContext)
                // Save for future use
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
            Log.d("LlmRepository", "System prompt added to new chat")
        }
        
        // Check token limit before processing
        val currentMessages = chatDao.getAllMessagesForContext()
        val conversationText = buildConversationContext(currentMessages, userMessage)
        val estimatedTokens = conversationText.length / 4 // Rough estimation
        
        if (estimatedTokens > 1200) { // Leave some buffer (1200 out of 1280)
            // Don't process, throw error to inform user
            throw Exception("Chat memory is full. Please clear the chat to continue.")
        }
        
        // Save user message
        val userChatMessage = ChatMessage(
            message = userMessage,
            isUser = true
        )
        Log.d("LlmRepository", "Saving user message: ${userMessage.take(50)}...")
        chatDao.insertMessage(userChatMessage)
        Log.d("LlmRepository", "User message saved")
        
        // Check if model is downloading
        val currentModelState = modelRepository.modelState.first()
        if (currentModelState == ModelState.DOWNLOADING) {
            throw Exception("Model is currently downloading. Please wait for download to complete.")
        }
        
        // Initialize LLM if needed
        if (!llmService.isInitialized()) {
            val modelFile = modelRepository.getModelFile()
            if (!modelFile.exists()) {
                throw Exception("Model not downloaded. Please download from Settings.")
            }
            
            val initResult = llmService.initialize(modelFile.absolutePath)
            if (initResult.isFailure) {
                throw initResult.exceptionOrNull() ?: Exception("Failed to initialize LLM")
            }
        }
        
        // Get ALL conversation history (including system prompt)
        val allMessages = chatDao.getAllMessagesForContext()
        val conversationContext = buildConversationContext(allMessages, userMessage)
        
        // Log the final message being sent to LLM
        Log.d("LlmRepository", "=== SENDING TO LLM ===")
        Log.d("LlmRepository", "Total messages in context: ${allMessages.size}")
        Log.d("LlmRepository", "Context length: ${conversationContext.length} characters")
        Log.d("LlmRepository", "Estimated tokens: ${conversationContext.length / 4}")
        Log.d("LlmRepository", "=== FULL CONTEXT ===")
        Log.d("LlmRepository", conversationContext)
        Log.d("LlmRepository", "=== END CONTEXT ===")
        
        // Stream response and accumulate for saving
        val responseBuilder = StringBuilder()
        var messageInserted = false
        
        llmService.generateResponseStream(conversationContext)
            .collect { partialResponse ->
                responseBuilder.append(partialResponse)
                emit(partialResponse)
            }
        
        // Save the complete AI response at the end
        val finalResponse = responseBuilder.toString()
        Log.d("LlmRepository", "Saving AI response: ${finalResponse.take(50)}...")
        val aiMessage = ChatMessage(
            message = finalResponse,
            isUser = false
        )
        chatDao.insertMessage(aiMessage)
        Log.d("LlmRepository", "AI response saved")
    }
    
    suspend fun deleteAllMessages() {
        chatDao.deleteAllMessages()
    }
    
    suspend fun deleteOldMessages(beforeTimestamp: Long) {
        chatDao.deleteMessagesBefore(beforeTimestamp)
    }
    
    suspend fun getMessageCount(): Int = chatDao.getMessageCount()
    
    private suspend fun buildConversationContext(
        history: List<ChatMessage>, 
        currentMessage: String
    ): String {
        val contextBuilder = StringBuilder()
        var systemPromptCount = 0
        var userMessageCount = 0
        var aiMessageCount = 0
        
        // Add all conversation history (already in ASC order)
        history.forEach { msg ->
            when {
                msg.isSystemPrompt -> {
                    systemPromptCount++
                    // System prompt is already formatted
                    contextBuilder.append(msg.message)
                    contextBuilder.append("\n\n")
                }
                msg.isUser -> {
                    userMessageCount++
                    contextBuilder.append("User: ${msg.message}\n")
                }
                else -> {
                    aiMessageCount++
                    contextBuilder.append("Assistant: ${msg.message}\n")
                }
            }
        }
        
        // Add current message (not yet in history)
        contextBuilder.append("User: $currentMessage\n")
        contextBuilder.append("Assistant:")
        
        Log.d("LlmRepository", "Context composition: $systemPromptCount system prompts, $userMessageCount user messages, $aiMessageCount AI messages")
        
        return contextBuilder.toString()
    }
    
    private suspend fun buildSystemPrompt(context: ChatContext): String {
        val monthSummary = context.monthSummary
        val topCategories = context.topCategories
        val activeSubs = context.activeSubscriptions
        val stats = context.quickStats
        val currency = userPreferencesRepository.baseCurrency.first()

        val totalSubAmount = activeSubs.sumOf { it.amount.toDouble() }.toBigDecimal()
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
        ${if (upcomingPayments.isNotEmpty()) "⚠️ ${upcomingPayments.size} payments due in next 7 days" else ""}

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
        Log.d("LlmRepository", "System prompt updated with latest financial data")
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
}