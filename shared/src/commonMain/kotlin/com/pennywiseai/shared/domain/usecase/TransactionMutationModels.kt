package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.model.SharedTransactionType

data class ManualTransactionInput(
    val amountMinor: Long,
    val merchantName: String,
    val category: String,
    val note: String? = null,
    val currency: String = "INR",
    val transactionType: SharedTransactionType = SharedTransactionType.EXPENSE,
    val occurredAtEpochMillis: Long? = null,
    val bankName: String? = null,
    val accountLast4: String? = null
)

sealed class TransactionMutationResult {
    data class Success(val transactionId: Long) : TransactionMutationResult()
    data class ValidationError(val message: String) : TransactionMutationResult()
    data class NotFound(val message: String) : TransactionMutationResult()
}
