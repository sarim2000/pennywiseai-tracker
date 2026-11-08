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
     * Kept free for the reply. The largest card (10 rows of tool-call JSON) takes ~700 tokens.
     */
    private const val REPLY_RESERVE_TOKENS = 1024

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
     * Whether a conversation holding [usedTokens] (LiteRT-LM's count) has room for [incoming] and a
     * reply. [incoming] counts a token per character: there is no tokenizer to ask, and chars/4
     * undercounts Hindi more than 4 times. Too high only drops an old turn early.
     */
    fun fitsWindow(usedTokens: Int, incoming: String): Boolean =
        usedTokens + incoming.length + REPLY_RESERVE_TOKENS <= MODEL_CONTEXT_TOKENS

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