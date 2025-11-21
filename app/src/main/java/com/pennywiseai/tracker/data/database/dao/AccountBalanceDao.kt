package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

@Dao
interface AccountBalanceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBalance(balance: AccountBalanceEntity): Long
    
    @Query("""
        SELECT * FROM account_balances 
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestBalance(bankName: String, accountLast4: String): AccountBalanceEntity?
    
    @Query("""
        SELECT * FROM account_balances 
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    fun getLatestBalanceFlow(bankName: String, accountLast4: String): Flow<AccountBalanceEntity?>
    
    @Query("""
        SELECT DISTINCT 
            ab1.id,
            ab1.bank_name,
            ab1.account_last4,
            ab1.balance,
            ab1.timestamp,
            ab1.transaction_id,
            ab1.created_at,
            ab1.credit_limit,
            ab1.is_credit_card,
            ab1.sms_source,
            ab1.source_type,
            ab1.currency
        FROM account_balances ab1
        INNER JOIN (
            SELECT bank_name, account_last4, MAX(timestamp) as max_timestamp
            FROM account_balances
            GROUP BY bank_name, account_last4
        ) ab2 
        ON ab1.bank_name = ab2.bank_name 
        AND ab1.account_last4 = ab2.account_last4 
        AND ab1.timestamp = ab2.max_timestamp
        ORDER BY ab1.balance DESC
    """)
    fun getAllLatestBalances(): Flow<List<AccountBalanceEntity>>
    
    @Query("SELECT * FROM account_balances ORDER BY timestamp DESC")
    fun getAllBalances(): Flow<List<AccountBalanceEntity>>
    
    @Query("DELETE FROM account_balances")
    suspend fun deleteAllBalances()
    
    @Query("""
        SELECT DISTINCT 
            ab1.id,
            ab1.bank_name,
            ab1.account_last4,
            ab1.balance,
            ab1.timestamp,
            ab1.transaction_id,
            ab1.created_at,
            ab1.credit_limit,
            ab1.is_credit_card,
            ab1.sms_source,
            ab1.source_type,
            ab1.currency
        FROM account_balances ab1
        INNER JOIN (
            SELECT bank_name, account_last4, MAX(timestamp) as max_timestamp
            FROM account_balances
            WHERE strftime('%Y-%m', timestamp/1000, 'unixepoch') = strftime('%Y-%m', 'now')
            GROUP BY bank_name, account_last4
        ) ab2 
        ON ab1.bank_name = ab2.bank_name 
        AND ab1.account_last4 = ab2.account_last4 
        AND ab1.timestamp = ab2.max_timestamp
        ORDER BY ab1.balance DESC
    """)
    fun getCurrentMonthLatestBalances(): Flow<List<AccountBalanceEntity>>
    
    @Query("""
        SELECT SUM(balance) as total FROM (
            SELECT DISTINCT 
                ab1.balance
            FROM account_balances ab1
            INNER JOIN (
                SELECT bank_name, account_last4, MAX(timestamp) as max_timestamp
                FROM account_balances
                GROUP BY bank_name, account_last4
            ) ab2 
            ON ab1.bank_name = ab2.bank_name 
            AND ab1.account_last4 = ab2.account_last4 
            AND ab1.timestamp = ab2.max_timestamp
        )
    """)
    fun getTotalBalance(): Flow<BigDecimal?>
    
    @Query("""
        SELECT * FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        AND timestamp >= :startDate AND timestamp <= :endDate
        ORDER BY timestamp DESC
    """)
    fun getBalanceHistory(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<AccountBalanceEntity>>
    
    @Query("""
        SELECT COUNT(DISTINCT bank_name || account_last4) FROM account_balances
    """)
    fun getAccountCount(): Flow<Int>
    
    @Query("DELETE FROM account_balances WHERE timestamp < :beforeDate")
    suspend fun deleteOldBalances(beforeDate: LocalDateTime): Int
    
    @Update
    suspend fun updateBalance(balance: AccountBalanceEntity)
    
    @Delete
    suspend fun deleteBalance(balance: AccountBalanceEntity)
    
    @Query("""SELECT * FROM account_balances 
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        ORDER BY timestamp DESC""")
    suspend fun getBalanceHistoryForAccount(bankName: String, accountLast4: String): List<AccountBalanceEntity>
    
    @Query("DELETE FROM account_balances WHERE id = :id")
    suspend fun deleteBalanceById(id: Long)
    
    @Query("UPDATE account_balances SET balance = :newBalance WHERE id = :id")
    suspend fun updateBalanceById(id: Long, newBalance: BigDecimal)
    
    @Query("""SELECT COUNT(*) FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4""")
    suspend fun getBalanceCountForAccount(bankName: String, accountLast4: String): Int

    @Query("DELETE FROM account_balances WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun deleteAccount(bankName: String, accountLast4: String): Int

    @Query("UPDATE account_balances SET bank_name = :newBankName WHERE bank_name = :oldBankName AND account_last4 = :accountLast4")
    suspend fun updateAccountBankName(oldBankName: String, accountLast4: String, newBankName: String): Int
}