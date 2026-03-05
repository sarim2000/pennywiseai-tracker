package com.pennywiseai.shared.data.model

data class SharedTransaction(
    val id: Long = 0L,
    val amountMinor: Long,
    val merchantName: String,
    val category: String,
    val transactionType: SharedTransactionType,
    val occurredAtEpochMillis: Long,
    val note: String? = null,
    val currency: String = "INR",
    val transactionHash: String? = null,
    val reference: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
    val balanceAfterMinor: Long? = null,
    val isDeleted: Boolean = false,
    val createdAtEpochMillis: Long = occurredAtEpochMillis,
    val updatedAtEpochMillis: Long = occurredAtEpochMillis
)
