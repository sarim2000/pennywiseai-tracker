package com.pennywiseai.shared.domain.usecase

import com.pennywiseai.shared.data.local.entity.SharedExchangeRateEntity
import com.pennywiseai.shared.data.repository.SharedExchangeRateRepository
import com.pennywiseai.shared.core.SharedTimeConstants
import com.pennywiseai.shared.data.util.currentTimeMillis

class ManageExchangeRateUseCase(
    private val repository: SharedExchangeRateRepository
) {
    suspend fun upsertRate(
        fromCurrency: String,
        toCurrency: String,
        rateMicros: Long,
        ttlMillis: Long = SharedTimeConstants.MILLIS_PER_DAY
    ) {
        val now = currentTimeMillis()
        repository.upsert(
            SharedExchangeRateEntity(
                fromCurrency = fromCurrency.uppercase(),
                toCurrency = toCurrency.uppercase(),
                rateMicros = rateMicros,
                provider = "manual",
                updatedAtEpochMillis = now,
                expiresAtEpochMillis = now + ttlMillis
            )
        )
    }
}
