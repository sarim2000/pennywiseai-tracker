package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedSubscriptionEntity
import kotlinx.coroutines.flow.Flow

interface SharedSubscriptionRepository {
    fun observeAll(): Flow<List<SharedSubscriptionEntity>>
    suspend fun upsert(subscription: SharedSubscriptionEntity): Long
    suspend fun deleteById(id: Long)
}
