package com.pennywiseai.tracker.data.parser

import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Represents a parsed transaction from an SMS message.
 */
data class ParsedTransaction(
    val amount: BigDecimal,
    val type: TransactionType,
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: BigDecimal?,
    val creditLimit: BigDecimal? = null,
    val smsBody: String,
    val sender: String,
    val timestamp: Long,
    val bankName: String,
    val transactionHash: String? = null
) {
    /**
     * Generates a unique transaction ID based on sender, amount, and timestamp.
     * This helps in duplicate detection.
     */
    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(Constants.Parsing.AMOUNT_SCALE, java.math.RoundingMode.HALF_UP)
        val data = "$sender|$normalizedAmount|$timestamp"
        
        val hash = MessageDigest.getInstance(Constants.Parsing.MD5_ALGORITHM)
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
        
        android.util.Log.d("ParsedTransaction", """
            Hash Generation Debug:
            Sender: $sender
            Original Amount: $amount
            Normalized Amount: $normalizedAmount
            Timestamp: $timestamp
            Hash Input: $data
            Generated Hash: $hash
        """.trimIndent())
        
        return hash
    }
    
    /**
     * Converts this parsed transaction to a database entity.
     */
    fun toEntity(): TransactionEntity {
        val dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )
        
        // Normalize merchant name to proper case
        val normalizedMerchant = merchant?.let { normalizeMerchantName(it) }
        
        // Determine which hash to use
        val finalHash = transactionHash?.takeIf { it.isNotBlank() } ?: generateTransactionId()
        
        android.util.Log.d("ParsedTransaction", """
            ToEntity Hash Selection:
            Custom Hash Provided: ${transactionHash != null}
            Custom Hash Value: $transactionHash
            Final Hash Used: $finalHash
            Bank: $bankName
            Merchant: $normalizedMerchant
        """.trimIndent())
        
        return TransactionEntity(
            id = 0, // Auto-generated
            amount = amount,
            merchantName = normalizedMerchant ?: "Unknown Merchant",
            category = determineCategory(),
            transactionType = type,
            dateTime = dateTime,
            description = null,
            smsBody = smsBody,
            bankName = bankName,
            smsSender = sender,
            accountNumber = accountLast4,
            balanceAfter = balance,
            transactionHash = finalHash,
            isRecurring = false, // Will be determined later
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * Normalizes merchant name to consistent format.
     * Converts all-caps to proper case, preserves already mixed case.
     */
    private fun normalizeMerchantName(name: String): String {
        val trimmed = name.trim()
        
        // If it's all uppercase, convert to proper case
        return if (trimmed == trimmed.uppercase()) {
            trimmed.lowercase().split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        } else {
            // Already has mixed case, keep as is
            trimmed
        }
    }
    
    /**
     * Determines the category based on merchant name and transaction type.
     */
    private fun determineCategory(): String {
        val merchantName = merchant ?: return "Others"
        
        // Special handling for income transactions
        if (type == TransactionType.INCOME) {
            val merchantLower = merchantName.lowercase()
            return when {
                merchantLower.contains("salary") -> "Salary"
                merchantLower.contains("refund") -> "Refunds"
                merchantLower.contains("cashback") -> "Cashback"
                merchantLower.contains("interest") -> "Interest"
                merchantLower.contains("dividend") -> "Dividends"
                else -> "Income"
            }
        }
        
        // Use unified category mapping for expenses
        return CategoryMapping.getCategory(merchantName)
    }
}