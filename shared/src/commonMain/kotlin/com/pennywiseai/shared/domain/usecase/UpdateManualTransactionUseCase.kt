package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class UpdateManualTransactionUseCase(
    private val transactionRepository: SharedTransactionRepository,
    private val accountRepository: SharedAccountRepository? = null
) {
    suspend fun execute(transactionId: Long, input: ManualTransactionInput): TransactionMutationResult {
        input.validationError()?.let { return TransactionMutationResult.ValidationError(it) }

        val existing = transactionRepository.getById(transactionId)
            ?: return TransactionMutationResult.NotFound("Transaction not found")

        val bankName = input.bankName?.trim()?.takeIf { it.isNotEmpty() }
        val accountLast4 = input.accountLast4?.trim()?.takeIf { it.isNotEmpty() }
        val currency = input.currency.trim().uppercase()

        transactionRepository.update(
            existing.copy(
                amountMinor = input.amountMinor,
                merchantName = input.merchantName.trim(),
                category = input.category.trim(),
                transactionType = input.transactionType,
                occurredAtEpochMillis = input.occurredAtEpochMillis ?: existing.occurredAtEpochMillis,
                note = input.note?.trim()?.takeIf { it.isNotEmpty() },
                currency = currency,
                bankName = bankName,
                accountLast4 = accountLast4,
                updatedAtEpochMillis = currentTimeMillis()
            )
        )

        if (bankName != null) {
            accountRepository.ensureAccountExists(bankName, accountLast4 ?: "", currency, currentTimeMillis())
        }

        return TransactionMutationResult.Success(transactionId)
    }
}
