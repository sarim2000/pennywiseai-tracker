package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.MerchantMappingDao
import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantMappingRepository @Inject constructor(
    private val merchantMappingDao: MerchantMappingDao
) {
    
    suspend fun getCategoryForMerchant(merchantName: String): String? {
        return merchantMappingDao.getCategoryForMerchant(merchantName)
    }
    
    suspend fun setMapping(merchantName: String, category: String) {
        merchantMappingDao.insertOrUpdateMapping(
            MerchantMappingEntity(
                merchantName = merchantName,
                category = category,
                updatedAt = LocalDateTime.now()
            )
        )
    }
    
    suspend fun removeMapping(merchantName: String) {
        merchantMappingDao.deleteMapping(merchantName)
    }
    
    fun getAllMappings(): Flow<List<MerchantMappingEntity>> {
        return merchantMappingDao.getAllMappings()
    }
    
    suspend fun getMappingCount(): Int {
        return merchantMappingDao.getMappingCount()
    }

    /**
     * Returns all merchant→category mappings as a plain Map for O(1) lookup.
     * Used by the SMS worker to pre-load the cache once per scan instead of
     * hitting the DB once per transaction.
     *
     * Requires adding this query to MerchantMappingDao:
     *   @Query("SELECT * FROM merchant_mappings")
     *   suspend fun getAllMappingsList(): List<MerchantMappingEntity>
     */
    suspend fun getAllMappingsAsMap(): Map<String, String> {
        return merchantMappingDao.getAllMappingsList().associate { it.merchantName to it.category }
    }
}