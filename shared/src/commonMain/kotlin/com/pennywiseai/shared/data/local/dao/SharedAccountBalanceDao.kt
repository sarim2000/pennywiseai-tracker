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

    @Query("""
        SELECT b.* FROM shared_account_balances b
        INNER JOIN (
            SELECT bank_name, account_last4, MAX(timestamp_epoch_millis) AS max_ts
            FROM shared_account_balances
            GROUP BY bank_name, account_last4
        ) latest ON b.bank_name = latest.bank_name
            AND b.account_last4 = latest.account_last4
            AND b.timestamp_epoch_millis = latest.max_ts
        ORDER BY b.bank_name
    """)
    suspend fun getDistinctAccounts(): List<SharedAccountBalanceEntity>

    @Query("DELETE FROM shared_account_balances WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun deleteByAccount(bankName: String, accountLast4: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: SharedAccountBalanceEntity): Long

    @Query("DELETE FROM shared_account_balances WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE shared_account_balances SET bank_name = :newBankName WHERE bank_name = :oldBankName AND account_last4 = :accountLast4")
    suspend fun renameAccount(oldBankName: String, newBankName: String, accountLast4: String)
}
