package com.pennywiseai.parser.core

import java.math.BigDecimal
import java.security.MessageDigest

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
    val transactionHash: String? = null,
    val isFromCard: Boolean = false,
    val currency: String = "INR",
    val fromAccount: String? = null,
    val toAccount: String? = null
) {
    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP)
        // Use SMS body hash for reliable deduplication across different timestamp sources
        // (BroadcastReceiver uses SC timestamp, ContentProvider uses device timestamp)
        val smsBodyHash = MessageDigest.getInstance("MD5")
            .digest(smsBody.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16) // First 16 chars of SMS body hash
        val data = "$sender|$normalizedAmount|$smsBodyHash"
        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}


