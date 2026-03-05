package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.model.SharedTransaction
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class CreateManualTransactionUseCase(
    private val transactionRepository: SharedTransactionRepository
) {
    suspend fun execute(input: ManualTransactionInput): TransactionMutationResult {
        validate(input)?.let { return TransactionMutationResult.ValidationError(it) }

        val now = currentTimeMillis()
        val occurredAt = input.occurredAtEpochMillis ?: now
        val id = transactionRepository.insert(
            SharedTransaction(
                amountMinor = input.amountMinor,
                merchantName = input.merchantName.trim(),
                category = input.category.trim(),
                transactionType = input.transactionType,
                occurredAtEpochMillis = occurredAt,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                currency = input.currency.trim().uppercase(),
                bankName = input.bankName?.trim()?.takeIf { it.isNotEmpty() },
                accountLast4 = input.accountLast4?.trim()?.takeIf { it.isNotEmpty() },
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        return TransactionMutationResult.Success(id)
    }

    private fun validate(input: ManualTransactionInput): String? {
        if (input.amountMinor <= 0) return "Amount must be greater than zero"
        if (input.merchantName.isBlank()) return "Merchant name is required"
        if (input.category.isBlank()) return "Category is required"
        if (input.currency.length != 3) return "Currency must be a 3-letter code"
        return null
    }
}
