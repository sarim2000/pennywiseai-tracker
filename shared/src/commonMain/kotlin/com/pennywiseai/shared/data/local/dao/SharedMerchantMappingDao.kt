package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedMerchantMappingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedMerchantMappingDao {
    @Query("SELECT * FROM shared_merchant_mappings ORDER BY merchant_name")
    fun observeAll(): Flow<List<SharedMerchantMappingEntity>>

    @Query("SELECT * FROM shared_merchant_mappings WHERE merchant_name = :merchantName LIMIT 1")
    suspend fun getByMerchant(merchantName: String): SharedMerchantMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: SharedMerchantMappingEntity)

    @Query("DELETE FROM shared_merchant_mappings WHERE merchant_name = :merchantName")
    suspend fun delete(merchantName: String)
}
