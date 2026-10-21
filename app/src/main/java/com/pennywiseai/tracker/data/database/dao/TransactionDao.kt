package com.pennywiseai.tracker.data.database.dao

import androidx.room.*
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDateTime

@Dao
interface TransactionDao {

    // ── Split-bill shares (#346): transactions.lent_shares_total mirrors loan_shares ──

    @Query("SELECT amount FROM loan_shares WHERE transaction_id = :transactionId")
    suspend fun getShareAmounts(transactionId: Long): List<BigDecimal>

    @Query("UPDATE transactions SET lent_shares_total = :total, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE id = :transactionId")
    suspend fun setLentSharesTotal(transactionId: Long, total: BigDecimal?)

    @Query("UPDATE loan_shares SET transaction_id = :toId WHERE transaction_id = :fromId")
    suspend fun repointShares(fromId: Long, toId: Long)

    /**
     * Re-derives `lent_shares_total` from the transaction's `loan_shares` rows,
     * the one source of truth; a no-op (no `updated_at` bump) when it already agrees.
     */
    @androidx.room.Transaction
    suspend fun syncLentSharesTotal(transactionId: Long) {
        val current = getTransactionById(transactionId) ?: return
        val amounts = getShareAmounts(transactionId)
        val total = if (amounts.isEmpty()) null else amounts.fold(BigDecimal.ZERO, BigDecimal::add)
        val same = if (total == null || current.lentSharesTotal == null) total == current.lentSharesTotal
        else total.compareTo(current.lentSharesTotal) == 0
        if (!same) setLentSharesTotal(transactionId, total)
    }

    /**
     * Moves a duplicate's split shares to the row that survives it, when that
     * row can take them: an expense in the same currency, not marked as a loan,
     * not split itself, and at least as large. Returns whether they moved.
     */
    @androidx.room.Transaction
    suspend fun moveSharesToKeeper(fromId: Long, toId: Long): Boolean {
        if (fromId == toId) return false
        val moving = getShareAmounts(fromId)
        if (moving.isEmpty()) return false
        val from = getTransactionById(fromId) ?: return false
        val keeper = getTransactionById(toId) ?: return false
        val fits = keeper.transactionType == TransactionType.EXPENSE && keeper.loanId == null &&
            getShareAmounts(toId).isEmpty() && keeper.currency.equals(from.currency, ignoreCase = true) &&
            moving.fold(BigDecimal.ZERO, BigDecimal::add) <= keeper.amount
        if (!fits) return false
        repointShares(fromId, toId)
        syncLentSharesTotal(fromId)
        syncLentSharesTotal(toId)
        return true
    }
    @Query("""
        SELECT * FROM transactions
        WHERE (currency = :currency AND updated_at >= :start AND updated_at <= :end)
        OR (currency != :currency AND id IN (
            SELECT transaction_id FROM webhook_delivered_transactions WHERE profile_id = :profileId
        ))
        ORDER BY updated_at, id
    """)
    suspend fun getWebhookChanges(start: LocalDateTime, end: LocalDateTime, currency: String, profileId: String = ""): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions WHERE currency != :currency AND id IN (
            SELECT transaction_id FROM webhook_delivered_transactions WHERE profile_id = :profileId
        ) ORDER BY updated_at, id
    """)
    suspend fun getWebhookCurrencyRemovals(currency: String, profileId: String): List<TransactionEntity>

    
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
    
    /** @return the number of rows actually removed. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions(): Int

    /**
     * Every row in the table, soft-deleted ones included, observed so the
     * "delete all" confirmation keeps showing what it is actually about to
     * remove — a background SMS scan writing a row while the dialog is open
     * moves the number the user is consenting to, rather than leaving them
     * looking at a stale one.
     */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeAllTransactionCount(): Flow<Int>

    /** Live count of transactions the user can see (soft-deleted rows excluded). */
    @Query("SELECT COUNT(*) FROM transactions WHERE is_deleted = 0")
    fun observeActiveTransactionCount(): Flow<Int>

    /**
     * One-shot row count, read *inside* the delete-all transaction to check that
     * the table still holds exactly what the user confirmed before anything is
     * removed.
     */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAllTransactions(): Int


    /**
     * The "rebuild from scratch" resync: deletes only rows the SMS inbox can
     * rebuild — active, parsed from an SMS, and not loan-linked, grouped
     * (#401), split with people (#346) or put toward a goal (#914: the hard delete would cascade the
     * contribution away). Everything else is kept, since nothing would bring it back:
     * manual / chat / CSV / recurring rows (no sender), PDF imports ("… PDF"
     * sender), bank-app notification rows ([notificationAliases]), and rows
     * the user deleted (kept so the rescan doesn't resurrect them, #703).
     * Only rows dated at/after [from] go: the rebuild re-reads the inbox from
     * there, so an older SMS row would never come back.
     *
     * Re-parse uses `transaction_hash` UNIQUE + `OnConflictStrategy.IGNORE`,
     * so the surviving rows are not duplicated when SMS is re-read.
     */
    @Query("""
        DELETE FROM transactions
        WHERE loan_id IS NULL AND group_id IS NULL AND is_deleted = 0
          AND id NOT IN (SELECT transaction_id FROM goal_contributions WHERE transaction_id IS NOT NULL)
          AND id NOT IN (SELECT transaction_id FROM loan_shares)
          AND sms_sender IS NOT NULL
          AND sms_sender NOT LIKE '% PDF'
          AND sms_sender NOT IN (:notificationAliases)
          AND date_time >= :from
    """)
    suspend fun deleteRebuildableSmsTransactions(notificationAliases: List<String>, from: LocalDateTime)
    
    @Query("UPDATE transactions SET category = :newCategory, updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime') WHERE merchant_name = :merchantName COLLATE NOCASE")
    suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String)

    @Query("UPDATE transactions SET category = :category, updated_at = :updatedAt WHERE id = :transactionId")
    suspend fun updateCategoryById(transactionId: Long, category: String, updatedAt: LocalDateTime)

    @Query("SELECT COUNT(*) FROM transactions WHERE merchant_name = :merchantName COLLATE NOCASE AND id != :excludeId")
    suspend fun getTransactionCountForMerchant(merchantName: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE bank_name = :bankName AND account_number = :accountLast4 AND is_deleted = 0 AND profile_id IS NOT NULL AND profile_id != :profileId")
    suspend fun countExplicitProfileMismatchForAccount(bankName: String, accountLast4: String, profileId: Long): Int

    // Non-deleted transactions still attributed to a bank + account. Used to detect a
    // phantom account left behind after GPay dedup — one with balance rows but no
    // surviving transactions (#456).
    @Query("SELECT COUNT(*) FROM transactions WHERE bank_name = :bankName AND account_number = :accountLast4 AND is_deleted = 0")
    suspend fun countActiveTransactionsForAccount(bankName: String, accountLast4: String): Int

    @Query("UPDATE transactions SET profile_id = :profileId, updated_at = :updatedAt WHERE bank_name = :bankName AND account_number = :accountLast4 AND is_deleted = 0 AND profile_id IS NOT NULL AND profile_id != :profileId")
    suspend fun setProfileForAccountTransactions(bankName: String, accountLast4: String, profileId: Long, updatedAt: LocalDateTime): Int

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 ORDER BY currency")
    fun getAllCurrencies(): Flow<List<String>>

    @Query("SELECT DISTINCT currency FROM transactions WHERE is_deleted = 0 AND date_time BETWEEN :startDate AND :endDate ORDER BY currency")
    fun getCurrenciesForPeriod(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>>

    // Soft delete methods - also clear hash so it doesn't block new inserts with same details
    @Query("UPDATE transactions SET updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime'), is_deleted = 1, transaction_hash = 'DELETED_' || id || '_' || transaction_hash WHERE id = :transactionId")
    suspend fun softDeleteTransaction(transactionId: Long)

    @Query("UPDATE transactions SET updated_at = strftime('%Y-%m-%dT%H:%M:%f', 'now', 'localtime'), is_deleted = 1, transaction_hash = 'DELETED_' || id || '_' || transaction_hash WHERE transaction_hash = :transactionHash")
    suspend fun softDeleteByHash(transactionHash: String)

    // Method to check if transaction exists by hash (including deleted)
    @Query("SELECT * FROM transactions WHERE transaction_hash = :transactionHash LIMIT 1")
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transaction_hash IN (:transactionHashes)")
    suspend fun getTransactionsByHashes(transactionHashes: List<String>): List<TransactionEntity>

    /**
     * A SOFT-DELETED transaction from the exact same SMS (raw sender + body).
     * Used to keep deletion durable across app updates: the transaction_hash
     * includes the parsed amount, so a parser change can shift it and let a
     * re-scan re-insert a previously-deleted row. The raw SMS never changes, so
     * matching on it lets the scanner skip resurrecting a deleted txn even when
     * its hash no longer matches. (#703)
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE is_deleted = 1 AND sms_body = :smsBody
          AND (sms_sender = :smsSender OR (:smsSender IS NULL AND sms_sender IS NULL))
        LIMIT 1
        """
    )
    suspend fun getDeletedBySms(smsBody: String, smsSender: String?): TransactionEntity?

    /**
     * Any stored row for this exact SMS — an active one first, else a
     * soft-deleted one. The raw SMS is the stable identity of a transaction: the
     * hash includes the parsed amount, so it shifts whenever a parser fix changes
     * that amount, and a rescan must still recognise the SMS. (#886)
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE sms_body = :smsBody
          AND (sms_sender = :smsSender OR (:smsSender IS NULL AND sms_sender IS NULL))
        ORDER BY is_deleted ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun getAnyBySms(smsBody: String, smsSender: String?): TransactionEntity?

    /**
     * Active rows that share their exact SMS with another active row — duplicates
     * left by earlier rescans (#886). Grouped and resolved by the caller.
     */
    @Query(
        """
        SELECT t.* FROM transactions t
        INNER JOIN (
            SELECT sms_body AS body, IFNULL(sms_sender, '') AS sender FROM transactions
            WHERE is_deleted = 0 AND sms_body IS NOT NULL AND sms_body != ''
            GROUP BY sms_body, IFNULL(sms_sender, '')
            HAVING COUNT(*) > 1
        ) d ON t.sms_body = d.body AND IFNULL(t.sms_sender, '') = d.sender
        WHERE t.is_deleted = 0
        ORDER BY t.sms_body, t.id
        """
    )
    suspend fun getActiveSmsDuplicateRows(): List<TransactionEntity>

    /**
     * Rewrites only amount and currency, and only while the row is still safe to
     * repair — checked here, at write time, not from an earlier read (#886):
     *  - it still holds the amount being repaired (anything the user saved since
     *    — category, note — is untouched, and a changed amount isn't overwritten)
     *  - it has no category splits or split-bill shares (#346) (they were sized to the old amount)
     *  - it isn't linked to a loan, unless the currency stays the same (loan
     *    totals sum their linked rows as one currency)
     *  - it holds no goal contributions, unless the currency stays the same (their
     *    shares are in the transaction's currency, #914)
     * @return rows changed: 0 when any of these no longer holds.
     */
    @Query(
        """
        UPDATE transactions
        SET amount = :amount, currency = :currency, updated_at = :now
        WHERE id = :id AND amount = :expectedAmount AND currency = :expectedCurrency
          AND NOT EXISTS (SELECT 1 FROM transaction_splits WHERE transaction_id = :id)
          AND NOT EXISTS (SELECT 1 FROM loan_shares WHERE transaction_id = :id)
          AND (loan_id IS NULL OR currency = :currency)
          AND (currency = :currency OR NOT EXISTS (SELECT 1 FROM goal_contributions WHERE transaction_id = :id))
        """
    )
    suspend fun repairParsedAmount(
        id: Long,
        amount: BigDecimal,
        currency: String,
        now: LocalDateTime,
        expectedAmount: BigDecimal,
        expectedCurrency: String
    ): Int

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

    /** Select bank-owned legs, including manual destinations and paired legacy SMS rows. */
    @Query("""
        WITH owned AS (
            SELECT * FROM transactions
            WHERE (bank_name = :bankName AND account_number = :sourceAccountLast4)
               OR id IN (SELECT transaction_id FROM account_balances
                         WHERE bank_name = :bankName AND account_last4 = :sourceAccountLast4)
        )
        SELECT DISTINCT candidate.id FROM transactions candidate
        WHERE (candidate.from_account = :sourceAccountLast4 OR candidate.to_account = :sourceAccountLast4)
          AND ((candidate.from_account = :sourceAccountLast4 AND candidate.from_bank_name = :bankName)
               OR (candidate.to_account = :sourceAccountLast4 AND candidate.to_bank_name = :bankName)
               OR candidate.id IN (SELECT id FROM owned)
               OR EXISTS (
                   SELECT 1 FROM owned seed
                   WHERE seed.transaction_type = 'TRANSFER' AND candidate.transaction_type = 'TRANSFER'
                     AND seed.reference IS NOT NULL AND TRIM(seed.reference) != ''
                     AND candidate.reference = seed.reference
                     AND candidate.currency = seed.currency AND candidate.amount = seed.amount
                     AND candidate.from_account IS seed.from_account AND candidate.to_account IS seed.to_account
                     AND julianday(candidate.date_time) BETWEEN julianday(seed.date_time, '-5 minutes') AND julianday(seed.date_time, '+5 minutes')
               ))
    """)
    suspend fun getAccountTransferLegRefIds(bankName: String, sourceAccountLast4: String): List<Long>

    @Query("""
        UPDATE transactions
        SET from_account = CASE WHEN from_account = :sourceAccountLast4 AND (from_bank_name IS NULL OR from_bank_name = :sourceBankName) THEN :targetAccountLast4 ELSE from_account END,
            to_account = CASE WHEN to_account = :sourceAccountLast4 AND (to_bank_name IS NULL OR to_bank_name = :sourceBankName) THEN :targetAccountLast4 ELSE to_account END,
            from_bank_name = CASE WHEN from_account = :sourceAccountLast4 AND (from_bank_name IS NULL OR from_bank_name = :sourceBankName) THEN :targetBankName ELSE from_bank_name END,
            to_bank_name = CASE WHEN to_account = :sourceAccountLast4 AND (to_bank_name IS NULL OR to_bank_name = :sourceBankName) THEN :targetBankName ELSE to_bank_name END,
            updated_at   = :updatedAt
        WHERE (from_account = :sourceAccountLast4 OR to_account = :sourceAccountLast4)
          AND id IN (:transactionIds)
    """)
    suspend fun retargetTransferLegRefs(
        transactionIds: List<Long>,
        sourceBankName: String,
        targetBankName: String,
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

    /**
     * [getTransactionByAmountAndDate] including rows the user deleted: a charge
     * deleted after a notification booked it must still block the matching SMS
     * on a rescan, or it comes back.
     */
    @Query("""
        SELECT * FROM transactions
        WHERE amount = :amount
        AND date_time BETWEEN :dateStart AND :dateEnd
    """)
    suspend fun getTransactionByAmountAndDateIncludingDeleted(
        amount: BigDecimal,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity>

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
