package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class UpdateManualTransactionUseCase(
    private val transactionRepository: SharedTransactionRepository
) {
    suspend fun execute(transactionId: Long, input: ManualTransactionInput): TransactionMutationResult {
        validate(input)?.let { return TransactionMutationResult.ValidationError(it) }

        val existing = transactionRepository.getById(transactionId)
            ?: return TransactionMutationResult.NotFound("Transaction not found")

        transactionRepository.update(
            existing.copy(
                amountMinor = input.amountMinor,
                merchantName = input.merchantName.trim(),
                category = input.category.trim(),
                transactionType = input.transactionType,
                occurredAtEpochMillis = input.occurredAtEpochMillis ?: existing.occurredAtEpochMillis,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                currency = input.currency.trim().uppercase(),
                bankName = input.bankName?.trim()?.takeIf { it.isNotEmpty() },
                accountLast4 = input.accountLast4?.trim()?.takeIf { it.isNotEmpty() },
                updatedAtEpochMillis = currentTimeMillis()
            )
        )
        return TransactionMutationResult.Success(transactionId)
    }

    private fun validate(input: ManualTransactionInput): String? {
        if (input.amountMinor <= 0) return "Amount must be greater than zero"
        if (input.merchantName.isBlank()) return "Merchant name is required"
        if (input.category.isBlank()) return "Category is required"
        if (input.currency.length != 3) return "Currency must be a 3-letter code"
        return null
    }
}
