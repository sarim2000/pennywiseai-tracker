package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.model.SharedTransactionType
import com.pennywiseai.shared.data.repository.SharedAccountRepository

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

internal fun ManualTransactionInput.validationError(): String? {
    if (amountMinor <= 0) return "Amount must be greater than zero"
    if (merchantName.isBlank()) return "Merchant name is required"
    if (category.isBlank()) return "Category is required"
    if (currency.length != 3) return "Currency must be a 3-letter code"
    return null
}

/** Records a zero-balance account for a manual entry's bank when none exists yet. */
internal suspend fun SharedAccountRepository?.ensureAccountExists(
    bankName: String,
    accountLast4: String,
    currency: String,
    now: Long
) {
    val repo = this ?: return
    val existing = repo.getLatestBalance(bankName, accountLast4)
    if (existing == null) {
        repo.insertBalance(
            SharedAccountBalanceEntity(
                bankName = bankName,
                accountLast4 = accountLast4,
                timestampEpochMillis = now,
                balanceMinor = 0L,
                currency = currency,
                createdAtEpochMillis = now
            )
        )
    }
}
