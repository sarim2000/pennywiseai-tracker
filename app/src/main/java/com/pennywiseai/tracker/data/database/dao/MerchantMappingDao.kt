package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.MerchantMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantMappingDao {
    
    @Query("SELECT category FROM merchant_mappings WHERE merchant_name = :merchantName")
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