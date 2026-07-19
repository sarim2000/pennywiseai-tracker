package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

@Dao
interface TransactionDao {
    
    @Query("SELECT * FROM transactions WHERE is_deleted = 0 ORDER BY date_time DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :transactionId")
    suspend fun getTransactionById(transactionId: Long): TransactionEntity?
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """)
    fun getTransactionsBetweenDates(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    /**
     * Optimized query that filters transactions at the database level.
     * Combines date range, currency, and transaction type filters to reduce memory usage.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @param currency Currency code to filter by (e.g., "INR", "USD")
     * @param transactionType Optional transaction type filter (null means all types)
     * @return Flow of filtered transactions ordered by date descending
     */
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND date_time BETWEEN :startDate AND :endDate
        AND currency = :currency
        AND (:transactionType IS NULL OR transaction_type = :transactionType)
        ORDER BY date_time DESC
    """)
    fun getTransactionsFiltered(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        currency: String,
        transactionType: TransactionType?
    ): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND transaction_type = :type 
        ORDER BY date_time DESC
    """)
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND category = :category 
        ORDER BY date_time DESC
    """)
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND (merchant_name LIKE '%' || :searchQuery || '%' 
        OR description LIKE '%' || :searchQuery || '%'
        OR sms_body LIKE '%' || :searchQuery || '%') 
        ORDER BY date_time DESC
    """)
    fun searchTransactions(searchQuery: String): Flow<List<TransactionEntity>>
    
    @Query("SELECT DISTINCT category FROM transactions WHERE is_deleted = 0 ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("""
        SELECT category FROM transactions
        WHERE is_deleted = 0
        GROUP BY category
        ORDER BY COUNT(*) DESC
        LIMIT :limit
    """)
    suspend fun getTopCategoriesByUsage(limit: Int = 3): List<String>

    @Query("SELECT DISTINCT merchant_name FROM transactions WHERE is_deleted = 0 ORDER BY merchant_name ASC")
    fun getAllMerchants(): Flow<List<String>>
    
    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE is_deleted = 0 
        AND transaction_type = :type 
        AND date_time BETWEEN :startDate AND :endDate
    """)
    suspend fun getTotalAmountByTypeAndPeriod(
        type: TransactionType,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)
    
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
    
    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
    
    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransactionById(transactionId: Long)
    
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    /**
     * Deletes only transactions that don't carry user-curated annotations
     * (no loan link, no group membership). Used by the full-rescan path so
     * rebuilding the SMS-derived view doesn't blow away loans + their
     * linked history or grouped transactions. Fixes #401.
     *
     * Re-parse uses `transaction_hash` UNIQUE + `OnConflictStrategy.IGNORE`,
     * so the surviving rows are not duplicated when SMS is re-read.
     */
    @Query("DELETE FROM transactions WHERE loan_id IS NULL AND group_id IS NULL")
    suspend fun deleteUncuratedTransactions()
    
    @Query("UPDATE transactions SET category = :newCategory WHERE merchant_name = :merchantName")
    suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String)

    @Query("UPDATE transactions SET category = :category, updated_at = :updatedAt WHERE id = :transactionId")
    suspend fun updateCategoryById(transactionId: Long, category: String, updatedAt: LocalDateTime)

    @Query("SELECT COUNT(*) FROM transactions WHERE merchant_name = :merchantName AND id != :excludeId")
    suspend fun getTransactionCountForMerchant(merchantName: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE bank_name = :bankName AND account_number = :accountLast4 AND is_deleted = 0 AND profile_id IS NOT NULL AND profile_id != :profileId")
    suspend fun countExplicitProfileMismatchForAccount(bankName: String, accountLast4: String, profileId: Long): Int

    @Query("UPDATE transactions SET profile_id = :profileId, updated_at = :updatedAt WHERE bank_name = :bankName AND account_number = :accountLast4 AND is_deleted = 0 AND profile_id IS NOT NULL AND profile_id != :profileId")
    suspend fun setProfileForAccountTransactions(bankName: String, accountLast4: String, profileId: Long, updatedAt: LocalDateTime): Int

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 ORDER BY currency")
    fun getAllCurrencies(): Flow<List<String>>

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 AND date_time BETWEEN :startDate AND :endDate ORDER BY currency")
    fun getCurrenciesForPeriod(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>>

    // Soft delete methods - also clear hash so it doesn't block new inserts with same details
    @Query("UPDATE transactions SET is_deleted = 1, transaction_hash = 'DELETED_' || id || '_' || transaction_hash WHERE id = :transactionId")
    suspend fun softDeleteTransaction(transactionId: Long)

    @Query("UPDATE transactions SET is_deleted = 1, transaction_hash = 'DELETED_' || id || '_' || transaction_hash WHERE transaction_hash = :transactionHash")
    suspend fun softDeleteByHash(transactionHash: String)

    // Method to check if transaction exists by hash (including deleted)
    @Query("SELECT * FROM transactions WHERE transaction_hash = :transactionHash LIMIT 1")
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity?

    /**
     * The subset of [hashes] that already exist (including soft-deleted rows).
     * One query for the whole batch — used by CSV import to dedup a whole file
     * without issuing a per-row lookup.
     */
    @Query("SELECT transaction_hash FROM transactions WHERE transaction_hash IN (:hashes)")
    suspend fun getExistingHashes(hashes: List<String>): List<String>

    /**
     * Find recent EXPENSE transactions whose merchant AND amount match —
     * used to surface candidate auto-pay charges when the user opens the
     * mark-as-paid sheet for a subscription (#412). Both filters matter:
     * a one-off ₹789 Netflix purchase shouldn't show up as a candidate
     * for a ₹499 monthly sub.
     *
     * Skips phantom rows we created ourselves (`subpay-...`, `autopay-...`
     * transaction hashes) so the user only sees real bank-derived
     * payments to link against.
     *
     * Amount comparison: `CAST(amount AS REAL)` handles the BigDecimal
     * text representation drift ("499" vs "499.00"). Tiny epsilon
     * (±₹0.01) absorbs any float-conversion rounding without matching
     * meaningfully different amounts.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE LOWER(merchant_name) = LOWER(:merchant)
          AND CAST(amount AS REAL) BETWEEN CAST(:amount AS REAL) - 0.01
                                       AND CAST(:amount AS REAL) + 0.01
          AND transaction_type = 'EXPENSE'
          AND is_deleted = 0
          AND date_time >= :since
          AND (transaction_hash NOT LIKE 'subpay-%' AND transaction_hash NOT LIKE 'autopay-%')
        ORDER BY date_time DESC
        LIMIT :limit
    """)
    suspend fun findRecentExpensesByMerchantAndAmount(
        merchant: String,
        amount: java.math.BigDecimal,
        since: LocalDateTime,
        limit: Int = 5,
    ): List<TransactionEntity>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        AND date_time BETWEEN :startDate AND :endDate 
        ORDER BY date_time DESC
    """)
    suspend fun getTransactionsBetweenDatesList(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity>
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND bank_name = :bankName
        AND (account_number = :accountLast4 OR account_number IS NULL)
        ORDER BY date_time DESC
    """)
    fun getTransactionsByAccount(
        bankName: String,
        accountLast4: String
    ): Flow<List<TransactionEntity>>

    /**
     * One-shot, strict-match transactions for an account — only rows explicitly
     * assigned to (bankName, accountLast4), excluding the `account_number IS NULL`
     * "any account at this bank" rows. Used to recompute a manual/cash account's
     * balance as opening + Σ(transactions).
     */
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND bank_name = :bankName
        AND account_number = :accountLast4
    """)
    suspend fun getTransactionsForAccountStrict(
        bankName: String,
        accountLast4: String
    ): List<TransactionEntity>

    /**
     * TRANSFER transactions touching an account on either side. Transfers are stored
     * once with `from_account` / `to_account` (last-4 only, matching how the transfer
     * balance shift resolves accounts), so a manual account's recompute must pull them
     * by those columns — they don't carry the account in `account_number`.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND transaction_type = 'TRANSFER'
        AND (from_account = :accountLast4 OR to_account = :accountLast4)
    """)
    suspend fun getTransfersForAccount(accountLast4: String): List<TransactionEntity>
    
    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND bank_name = :bankName
        AND account_number = :accountLast4
        AND date_time BETWEEN :startDate AND :endDate
        ORDER BY date_time DESC
    """)
    fun getTransactionsByAccountAndDateRange(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>>

    /**
     * Total transactions on an account, including soft-deleted ones. Used by
     * the account-merge confirmation dialog (#368) — must match
     * [mergeAccountTransactions]'s WHERE clause exactly, otherwise the dialog
     * would under-report the actual number of rows about to be moved for an
     * irreversible operation.
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE bank_name = :bankName
          AND account_number = :accountLast4
    """)
    suspend fun countByAccount(bankName: String, accountLast4: String): Int

    /**
     * Bulk re-target every transaction on [sourceBankName]/[sourceAccountLast4]
     * to [targetBankName]/[targetAccountLast4]. Used by the account-merge
     * feature (#368). Returns the row count actually updated.
     *
     * Includes soft-deleted rows on purpose: if the source account is
     * about to be removed, any trashed transactions on it would otherwise
     * orphan-point at an account that no longer exists, breaking a
     * future "restore from trash" flow.
     */
    @Query("""
        UPDATE transactions
        SET bank_name = :targetBankName,
            account_number = :targetAccountLast4,
            updated_at = :updatedAt
        WHERE bank_name = :sourceBankName
          AND account_number = :sourceAccountLast4
    """)
    suspend fun mergeAccountTransactions(
        sourceBankName: String,
        sourceAccountLast4: String,
        targetBankName: String,
        targetAccountLast4: String,
        updatedAt: LocalDateTime
    ): Int

    /**
     * Re-target any TRANSFER row that referenced [sourceAccountLast4] in its
     * `from_account` / `to_account` columns so the detail screen's From → To
     * flow keeps pointing at a live account after a merge (#368). Note:
     * these columns don't carry a bank — match is by account last-4 only.
     */
    @Query("""
        UPDATE transactions
        SET from_account = CASE WHEN from_account = :sourceAccountLast4 THEN :targetAccountLast4 ELSE from_account END,
            to_account   = CASE WHEN to_account   = :sourceAccountLast4 THEN :targetAccountLast4 ELSE to_account   END,
            updated_at   = :updatedAt
        WHERE from_account = :sourceAccountLast4 OR to_account = :sourceAccountLast4
    """)
    suspend fun retargetTransferLegRefs(
        sourceAccountLast4: String,
        targetAccountLast4: String,
        updatedAt: LocalDateTime
    ): Int

    @Query("SELECT * FROM transactions WHERE reference = :reference AND is_deleted = 0 LIMIT 1")
    suspend fun getTransactionByReference(reference: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE reference = :reference
        AND is_deleted = 0
        ORDER BY date_time ASC
    """)
    suspend fun getTransactionsByReference(reference: String): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND reference = :reference
        AND amount = :amount
        AND transaction_type = :transactionType
        AND currency = :currency
        AND date_time BETWEEN :startDate AND :endDate
        AND (:accountNumber IS NULL OR account_number = :accountNumber OR account_number IS NULL)
        ORDER BY date_time ASC
    """)
    suspend fun findPotentialDuplicatesByReference(
        reference: String,
        amount: BigDecimal,
        transactionType: TransactionType,
        currency: String,
        accountNumber: String?,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND reference GLOB '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'
        ORDER BY reference ASC, date_time ASC
    """)
    suspend fun findPotentialDuplicatesByReference(): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE is_deleted = 0
        AND amount = :amount
        AND date_time BETWEEN :dateStart AND :dateEnd
    """)
    suspend fun getTransactionByAmountAndDate(
        amount: BigDecimal,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity>
}
