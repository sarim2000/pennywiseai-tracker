package com.pennywiseai.shared.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pennywiseai.shared.data.local.entity.SharedAccountBalanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedAccountBalanceDao {
    @Query("SELECT * FROM shared_account_balances ORDER BY timestamp_epoch_millis DESC")
    fun observeAll(): Flow<List<SharedAccountBalanceEntity>>

    @Query("""
        SELECT * FROM shared_account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        ORDER BY timestamp_epoch_millis DESC
        LIMIT 1
    """)
    suspend fun getLatest(bankName: String, accountLast4: String): SharedAccountBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: SharedAccountBalanceEntity): Long

    @Query("DELETE FROM shared_account_balances WHERE id = :id")
    suspend fun deleteById(id: Long)
}
