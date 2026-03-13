package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import com.pennywiseai.shared.data.local.entity.SharedCardEntity
import com.pennywiseai.shared.data.repository.SharedAccountRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class ManageAccountUseCase(
    private val repository: SharedAccountRepository
) {
    suspend fun addBalance(bankName: String, accountLast4: String, balanceMinor: Long, currency: String = "INR"): Long {
        val now = currentTimeMillis()
        return repository.insertBalance(
            SharedAccountBalanceEntity(
                bankName = bankName,
                accountLast4 = accountLast4,
                timestampEpochMillis = now,
                balanceMinor = balanceMinor,
                currency = currency,
                createdAtEpochMillis = now
            )
        )
    }

    suspend fun upsertCard(bankName: String, cardLast4: String, cardType: String = "CREDIT"): Long {
        val now = currentTimeMillis()
        return repository.upsertCard(
            SharedCardEntity(
                cardLast4 = cardLast4,
                bankName = bankName,
                cardType = cardType,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }
}
