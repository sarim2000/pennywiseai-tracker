package com.pennywiseai.parser.core

import java.math.BigDecimal

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
    val toAccount: String? = null,
    // Mobile-money wallet (e.g. eMola, M-Pesa Mozambique): the SMS carries a
    // running balance but no per-account number, because the whole wallet IS the
    // account. When true, the app derives a single service-level account row from
    // the balance instead of requiring an accountLast4.
    val isMobileWallet: Boolean = false
) {
    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP)
        // Use SMS body hash for reliable deduplication across different timestamp sources
        // (BroadcastReceiver uses SC timestamp, ContentProvider uses device timestamp)
        val smsBodyHash = md5Hex(smsBody)
            .take(16) // First 16 chars of SMS body hash
        val data = "$sender|$normalizedAmount|$smsBodyHash"
        return md5Hex(data)
    }
}


