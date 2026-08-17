package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantMappingDao {
    
    // COLLATE NOCASE so free-text manual entry matches regardless of casing
    // (e.g. "amazon" finds a mapping saved as "Amazon"). If case-distinct rows
    // both match, prefer the most recently updated one, breaking exact timestamp
    // ties (possible after a backup restore) by merchant_name — the PK — so the
    // result is always deterministic. (#678)
    @Query(
        "SELECT category FROM merchant_mappings WHERE merchant_name = :merchantName COLLATE NOCASE " +
            "ORDER BY updated_at DESC, merchant_name ASC LIMIT 1"
    )
    suspend fun getCategoryForMerchant(merchantName: String): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMapping(mapping: MerchantMappingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: MerchantMappingEntity)
    
    @Query("DELETE FROM merchant_mappings WHERE merchant_name = :merchantName")
    suspend fun deleteMapping(merchantName: String)
    
    @Query("SELECT * FROM merchant_mappings ORDER BY merchant_name ASC")
    fun getAllMappings(): Flow<List<MerchantMappingEntity>>
    
    @Query("SELECT COUNT(*) FROM merchant_mappings")
    suspend fun getMappingCount(): Int
    
    @Query("DELETE FROM merchant_mappings")
    suspend fun deleteAllMappings()

    @Query("SELECT * FROM merchant_mappings")
    suspend fun getAllMappingsList(): List<MerchantMappingEntity>
}