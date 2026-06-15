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

    /**
     * Resolves an account when only the last-4 digits are known (e.g. the
     * `from_account` / `to_account` columns on TRANSFER transactions only store
     * `accountLast4`, not the bank). Picks the most-recent balance row across
     * banks; ambiguous in the rare case the user has two accounts with the same
     * last4, which matches the existing dropdown's resolution.
     */
    @Query("""
        SELECT * FROM account_balances
        WHERE account_last4 = :accountLast4
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestBalanceByLast4(accountLast4: String): AccountBalanceEntity?
    
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
            ab1.currency,
            ab1.profile_id,
            ab1.alias
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

    /**
     * Resync companion to [TransactionDao.deleteUncuratedTransactions].
     * Wipes balance entries the SMS re-parse will regenerate, while
     * preserving:
     *
     *  - Entries linked to a transaction that survived the resync
     *    (loan-linked / grouped) — their `transaction_id` still points
     *    at an existing row. Without this, balance time-series develops
     *    gaps because the re-parse short-circuits on hash collision and
     *    never calls processBalanceUpdate() for surviving rows.
     *  - User-curated entries with `source_type` MANUAL or CARD_LINK
     *    (added from the Manage Accounts screen). These have no SMS to
     *    re-derive from, so a wipe would lose them permanently.
     *
     * What it does delete:
     *  - Orphans: `transaction_id` pointed at a transaction wiped this
     *    resync.
     *  - Balance-only SMS notifications (`transaction_id IS NULL` AND
     *    `source_type IS NULL`) — these will be re-inserted by the
     *    re-parse and REPLACE on the (bank, account, timestamp) unique
     *    index, so the wipe-then-rebuild round-trip is a no-op.
     */
    @Query("""
        DELETE FROM account_balances
        WHERE
            (transaction_id IS NOT NULL
             AND transaction_id NOT IN (SELECT id FROM transactions))
            OR
            (transaction_id IS NULL AND source_type IS NULL)
    """)
    suspend fun deleteRebuildableBalances()
    
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
            ab1.currency,
            ab1.profile_id,
            ab1.alias
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
    
    // Never prune OPENING anchors — they carry an intentionally early timestamp (so
    // they never win "latest"), which would otherwise make them the prime target of a
    // timestamp-cutoff delete and silently break the derived manual-balance model.
    @Query("DELETE FROM account_balances WHERE timestamp < :beforeDate AND (source_type IS NULL OR source_type != 'OPENING')")
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

    @Query("DELETE FROM account_balances WHERE transaction_id = :transactionId")
    suspend fun deleteBalancesForTransaction(transactionId: Long): Int
    
    @Query("UPDATE account_balances SET balance = :newBalance WHERE id = :id")
    suspend fun updateBalanceById(id: Long, newBalance: BigDecimal)
    
    @Query("""SELECT COUNT(*) FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4""")
    suspend fun getBalanceCountForAccount(bankName: String, accountLast4: String): Int

    @Query("""
        SELECT * FROM account_balances
        WHERE timestamp >= :startDate
        ORDER BY timestamp ASC
    """)
    fun getBalancesFromDate(startDate: LocalDateTime): Flow<List<AccountBalanceEntity>>

    @Query("DELETE FROM account_balances WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun deleteAccount(bankName: String, accountLast4: String): Int

    @Query("UPDATE account_balances SET bank_name = :newBankName WHERE bank_name = :oldBankName AND account_last4 = :accountLast4")
    suspend fun updateAccountBankName(oldBankName: String, accountLast4: String, newBankName: String): Int

    @Query("UPDATE account_balances SET statement_day = :statementDay WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun updateStatementDay(bankName: String, accountLast4: String, statementDay: Int?): Int

    @Query("UPDATE account_balances SET profile_id = :profileId WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun setAccountProfile(bankName: String, accountLast4: String, profileId: Long): Int

    @Query("UPDATE account_balances SET alias = :alias WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun setAccountAlias(bankName: String, accountLast4: String, alias: String?): Int

    // ─────────────────────────────────────────────────────────────────────────
    // Manual/cash account balance recompute support.
    //
    // A manual account's displayed balance = opening + Σ(its transactions). The
    // opening is stored in a single OPENING row at an early timestamp (so it never
    // wins "latest by timestamp"); the displayed value lives in a single MANUAL row
    // that recompute refreshes. See AccountBalanceRepository.recomputeManualBalance.
    // ─────────────────────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        AND source_type = 'OPENING'
        ORDER BY timestamp ASC
        LIMIT 1
    """)
    suspend fun getOpeningRow(bankName: String, accountLast4: String): AccountBalanceEntity?

    @Query("""
        SELECT * FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        AND source_type = 'MANUAL'
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getManualCurrentRow(bankName: String, accountLast4: String): AccountBalanceEntity?

    @Query("""
        SELECT MIN(timestamp) FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
    """)
    suspend fun getEarliestBalanceTimestamp(bankName: String, accountLast4: String): LocalDateTime?

    @Query("UPDATE account_balances SET balance = :balance, timestamp = :timestamp WHERE id = :id")
    suspend fun updateBalanceAndTimestampById(id: Long, balance: BigDecimal, timestamp: LocalDateTime)

    @Query("UPDATE account_balances SET currency = :currency WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun updateAccountCurrency(bankName: String, accountLast4: String, currency: String): Int

    /**
     * Count of SMS-sourced balance rows for an account. Real bank SMS balances always
     * carry a non-null `sms_source`, so a positive count means the account is
     * SMS-tracked — used to keep the manual-balance recompute away from SMS accounts
     * that merely had a one-off "Update balance" override.
     */
    @Query("""
        SELECT COUNT(*) FROM account_balances
        WHERE bank_name = :bankName AND account_last4 = :accountLast4
        AND sms_source IS NOT NULL
    """)
    suspend fun countSmsSourcedBalances(bankName: String, accountLast4: String): Int
}
