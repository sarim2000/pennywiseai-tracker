package com.pennywiseai.shared.data.repository

import com.pennywiseai.shared.data.local.dao.SharedMerchantMappingDao
import com.pennywiseai.shared.data.local.entity.SharedMerchantMappingEntity

class RoomSharedMerchantMappingRepository(
    private val dao: SharedMerchantMappingDao
) : SharedMerchantMappingRepository {
    override suspend fun getByMerchant(merchantName: String): SharedMerchantMappingEntity? = dao.getByMerchant(merchantName)
}
