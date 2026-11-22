package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.entity.SharedMerchantMappingEntity

interface SharedMerchantMappingRepository {
    suspend fun getByMerchant(merchantName: String): SharedMerchantMappingEntity?
}
