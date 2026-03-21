package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedMerchantMappingEntity
import kotlinx.coroutines.flow.Flow

interface SharedMerchantMappingRepository {
    fun observeAll(): Flow<List<SharedMerchantMappingEntity>>
    suspend fun getByMerchant(merchantName: String): SharedMerchantMappingEntity?
    suspend fun upsert(mapping: SharedMerchantMappingEntity)
}
