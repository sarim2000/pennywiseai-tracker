package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantAliasDao {

    @Query("SELECT alias FROM merchant_aliases WHERE merchant_name = :merchantName")
    suspend fun getAliasForMerchant(merchantName: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAlias(alias: MerchantAliasEntity)

    @Query("DELETE FROM merchant_aliases WHERE merchant_name = :merchantName")
    suspend fun deleteAlias(merchantName: String)

    @Query("SELECT * FROM merchant_aliases ORDER BY merchant_name ASC")
    fun getAllAliases(): Flow<List<MerchantAliasEntity>>

    @Query("SELECT COUNT(*) FROM merchant_aliases")
    suspend fun getAliasCount(): Int

    @Query("DELETE FROM merchant_aliases")
    suspend fun deleteAllAliases()

    @Query("SELECT * FROM merchant_aliases")
    suspend fun getAllAliasesList(): List<MerchantAliasEntity>
}
