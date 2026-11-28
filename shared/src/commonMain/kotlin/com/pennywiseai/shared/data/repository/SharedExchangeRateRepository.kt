package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedExchangeRateEntity
import kotlinx.coroutines.flow.Flow

interface SharedExchangeRateRepository {
    fun observeAll(): Flow<List<SharedExchangeRateEntity>>
}
