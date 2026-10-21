package com.pennywiseai.tracker.utils

import kotlin.math.roundToInt

/**
 * Utility functions for estimating token counts for LLM models.
 * Based on the rough approximation that 1 token ≈ 4 characters for English text.
 */
object TokenUtils {
    
    // Average characters per token for English text
    private const val CHARS_PER_TOKEN = 4.0

    /** KV cache of the chat model (Qwen2.5-1.5B-Instruct-q8-ekv4096). */
    const val MODEL_CONTEXT_TOKENS = 4096

    /**
     * Kept free for what the estimate can't see: the tool schemas LiteRT-LM adds
     * to the system turn, chat-template tokens, and the reply itself.
     */
    private const val RESERVED_TOKENS = 1536

    /**
     * Estimated tokens (system prompt + history + next message) a chat may hold
     * before it has to be cleared. Was a hard-coded 1200, which a long category
     * or account list alone could exceed, blocking every new chat (#959).
     */
    const val CHAT_TOKEN_BUDGET = MODEL_CONTEXT_TOKENS - RESERVED_TOKENS
    
    /**
     * Estimates the number of tokens in a given text.
     * This is a rough approximation based on character count.
     * 
     * @param text The input text
     * @return Estimated token count
     */
    fun estimateTokens(text: String): Int {
        return (text.length / CHARS_PER_TOKEN).roundToInt()
    }
    
    /**
     * Estimates tokens for a list of messages.
     * 
     * @param messages List of message texts
     * @return Total estimated token count
     */
    fun estimateTokensForMessages(messages: List<String>): Int {
        return messages.fold(0) { acc, msg -> acc + estimateTokens(msg) }
    }
    
    /** Whether a chat holding [context] (system prompt included) still has room for [nextMessage]. */
    fun fitsChatBudget(context: List<String>, nextMessage: String): Boolean =
        estimateTokens(context.joinToString("") + nextMessage) <= CHAT_TOKEN_BUDGET

    /**
     * Formats a number in a user-friendly way (e.g., 1234 -> "1.2k")
     * 
     * @param number The number to format
     * @return Formatted string
     */
    fun formatNumber(number: Int): String {
        return when {
            number < 1000 -> number.toString()
            number < 10000 -> String.format("%.1fk", number / 1000.0)
            number < 1000000 -> "${number / 1000}k"
            else -> String.format("%.1fM", number / 1000000.0)
        }
    }
    
    /**
     * Calculates the percentage of context used.
     * 
     * @param usedTokens Number of tokens used
     * @param maxTokens Maximum context window size
     * @return Percentage as an integer (0-100)
     */
    fun calculateContextUsagePercent(usedTokens: Int, maxTokens: Int): Int {
        if (maxTokens <= 0) return 0
        return ((usedTokens.toFloat() / maxTokens) * 100).roundToInt().coerceIn(0, 100)
    }
    
    /**
     * Returns a color suggestion based on context usage.
     * Can be used to show warnings when approaching context limits.
     * 
     * @param percentage Usage percentage (0-100)
     * @return Color suggestion as a string
     */
    fun getUsageColorHint(percentage: Int): String {
        return when {
            percentage < 50 -> "safe" // Green
            percentage < 75 -> "warning" // Yellow/Orange
            else -> "critical" // Red
        }
    }
}