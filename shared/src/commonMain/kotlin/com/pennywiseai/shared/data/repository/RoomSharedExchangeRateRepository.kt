package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedExchangeRateDao
import com.pennywiseai.shared.data.local.entity.SharedExchangeRateEntity
import kotlinx.coroutines.flow.Flow

class RoomSharedExchangeRateRepository(
    private val dao: SharedExchangeRateDao
) : SharedExchangeRateRepository {
    override fun observeAll(): Flow<List<SharedExchangeRateEntity>> = dao.observeAll()
}
