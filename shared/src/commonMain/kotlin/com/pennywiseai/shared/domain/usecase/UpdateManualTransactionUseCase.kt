package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.repository.SharedTransactionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class UpdateManualTransactionUseCase(
    private val transactionRepository: SharedTransactionRepository,
    private val accountRepository: SharedAccountRepository? = null
) {
    suspend fun execute(transactionId: Long, input: ManualTransactionInput): TransactionMutationResult {
        validate(input)?.let { return TransactionMutationResult.ValidationError(it) }

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
            ensureAccountExists(bankName, accountLast4 ?: "", currency, currentTimeMillis())
        }

        return TransactionMutationResult.Success(transactionId)
    }

    private suspend fun ensureAccountExists(
        bankName: String,
        accountLast4: String,
        currency: String,
        now: Long
    ) {
        val repo = accountRepository ?: return
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

    private fun validate(input: ManualTransactionInput): String? {
        if (input.amountMinor <= 0) return "Amount must be greater than zero"
        if (input.merchantName.isBlank()) return "Merchant name is required"
        if (input.category.isBlank()) return "Category is required"
        if (input.currency.length != 3) return "Currency must be a 3-letter code"
        return null
    }
}
