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
     * Returns all merchant→category mappings for in-memory lookup. Used by the SMS
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
        .associateTo(java.util.TreeMap(SQLITE_NOCASE)) { it.merchantName to it.category }

// SQLite's NOCASE folds ASCII letters only: "CAFÉ" and "Café" stay distinct
// merchants there, so they must here too (String.CASE_INSENSITIVE_ORDER folds É).
private fun Char.asciiLower(): Char = if (this in 'A'..'Z') this + 32 else this

private val SQLITE_NOCASE = Comparator<String> { a, b ->
    (a zip b).firstOrNull { (x, y) -> x.asciiLower() != y.asciiLower() }
        ?.let { (x, y) -> x.asciiLower().compareTo(y.asciiLower()) }
        ?: a.length.compareTo(b.length)
}