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
     * Returns all merchant→category mappings for O(1) lookup. Used by the SMS
     * worker to pre-load the cache once per scan instead of hitting the DB once
     * per transaction. Keys match case-insensitively, like [getCategoryForMerchant].
     */
    suspend fun getAllMappingsAsMap(): Map<String, String> =
        merchantMappingDao.getAllMappingsList().toCategoryLookup()
}

/**
 * Case-insensitive merchant→category lookup with the same winner as
 * [MerchantMappingDao.getCategoryForMerchant]: newest `updatedAt`, then the
 * smallest merchant name. An exact-case map let the background scan miss a
 * mapping the live SMS path applied, so a rescan reverted the category (#911).
 */
internal fun List<MerchantMappingEntity>.toCategoryLookup(): Map<String, String> =
    sortedWith(compareBy<MerchantMappingEntity> { it.updatedAt }.thenByDescending { it.merchantName })
        .associateTo(java.util.TreeMap(String.CASE_INSENSITIVE_ORDER)) { it.merchantName to it.category }