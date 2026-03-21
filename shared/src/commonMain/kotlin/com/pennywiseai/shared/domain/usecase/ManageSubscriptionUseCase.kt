package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedSubscriptionEntity
import com.pennywiseai.shared.data.repository.SharedSubscriptionRepository
import com.pennywiseai.shared.data.util.currentTimeMillis

class ManageSubscriptionUseCase(
    private val repository: SharedSubscriptionRepository
) {
    suspend fun upsert(
        id: Long = 0L,
        merchantName: String,
        amountMinor: Long,
        category: String?,
        currency: String = "INR"
    ): Long {
        val now = currentTimeMillis()
        return repository.upsert(
            SharedSubscriptionEntity(
                id = id,
                merchantName = merchantName.trim(),
                amountMinor = amountMinor,
                category = category,
                currency = currency,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
    }
}
