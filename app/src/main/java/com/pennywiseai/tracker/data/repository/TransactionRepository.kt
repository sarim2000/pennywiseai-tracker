package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.currentNameFor
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.dao.TransactionSplitDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionSplitEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.statement.StatementTransactionEnricher
import com.pennywiseai.tracker.data.manager.TransactionDeduplication
import com.pennywiseai.tracker.domain.model.BudgetCycle
import com.pennywiseai.tracker.utils.forTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
open class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionSplitDao: TransactionSplitDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryDao: com.pennywiseai.tracker.data.database.dao.CategoryDao,
    private val goalRepository: GoalRepository
) {
    suspend fun getWebhookChanges(start: LocalDateTime, end: LocalDateTime, currency: String, profileId: String) =
        transactionDao.getWebhookChanges(start, end, currency, profileId)

    suspend fun getWebhookCurrencyRemovals(currency: String, profileId: String) =
        transactionDao.getWebhookCurrencyRemovals(currency, profileId)

    fun getWebhookTransactions(start: LocalDateTime, end: LocalDateTime, currency: String) =
        transactionDao.getTransactionsFiltered(start, end, currency, null)

    fun getAllTransactions(): Flow<List<TransactionEntity>> = 
        transactionDao.getAllTransactions()
    
    open suspend fun getTransactionById(id: Long): TransactionEntity? = 
        transactionDao.getTransactionById(id)
    
    fun getTransactionsBetweenDates(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsBetweenDates(startDate, endDate)
    
    fun getTransactionsBetweenDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsBetweenDates(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        )

    /**
     * Gets transactions filtered at the database level for better performance.
     * Combines date range, currency, and transaction type filters to reduce memory usage.
     *
     * @param startDate Start of the date range (inclusive)
     * @param endDate End of the date range (inclusive)
     * @param currency Currency code to filter by (e.g., "INR", "USD")
     * @param transactionType Optional transaction type filter (null means all types)
     * @return Flow of filtered transactions
     */
    fun getTransactionsFiltered(
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        transactionType: TransactionType? = null
    ): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsFiltered(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59),
            currency,
            transactionType
        )
    
    fun getTransactionsByType(type: TransactionType): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsByType(type)
    
    fun getTransactionsByCategory(category: String): Flow<List<TransactionEntity>> = 
        transactionDao.getTransactionsByCategory(category)
    
    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        transactionDao.searchTransactions(query)

    fun getAllCurrencies(): Flow<List<String>> =
        transactionDao.getAllCurrencies()

    fun getCurrenciesForPeriod(startDate: LocalDateTime, endDate: LocalDateTime): Flow<List<String>> =
        transactionDao.getCurrenciesForPeriod(startDate, endDate)
    
    fun getAllCategories(): Flow<List<String>> =
        transactionDao.getAllCategories()

    /**
     * Gets the top N categories by usage count (number of transactions).
     * Useful for showing user's most frequently used categories in notifications.
     *
     * @param limit Maximum number of categories to return (default: 3)
     * @return List of category names ordered by usage count (most used first)
     */
    suspend fun getTopCategoriesByUsage(limit: Int = 3): List<String> =
        transactionDao.getTopCategoriesByUsage(limit)

    fun getAllMerchants(): Flow<List<String>> =
        transactionDao.getAllMerchants()
    
    suspend fun getTotalAmountByTypeAndPeriod(
        type: TransactionType,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double? = transactionDao.getTotalAmountByTypeAndPeriod(type, startDate, endDate)
    
    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insertTransaction(withCurrentCategoryNames(listOf(transaction)).single())

    suspend fun insertTransactions(transactions: List<TransactionEntity>) =
        transactionDao.insertTransactions(withCurrentCategoryNames(transactions))

    /**
     * Auto-categorization speaks in built-in names ("Food & Dining"); if the user
     * renamed that category (#823), new rows get its current name instead.
     */
    private suspend fun withCurrentCategoryNames(transactions: List<TransactionEntity>): List<TransactionEntity> {
        val categories = categoryDao.getAllCategoriesList()
        if (categories.none { it.systemName != null && it.systemName != it.name }) return transactions
        return transactions.map { tx ->
            tx.copy(
                category = categories.currentNameFor(tx.category),
                budgetCategory = tx.budgetCategory?.let { categories.currentNameFor(it) }
            )
        }
    }
    
    /**
     * Every edit goes through here, so a lowered amount also caps the goal
     * contributions taken from this transaction (#914).
     */
    suspend fun updateTransaction(transaction: TransactionEntity) = goalRepository.writeAndCap(transaction.id) {
        writeKeepingSplit(transaction)
    }

    /**
     * Writes [transaction] so a split bill (#346) survives a stale copy of the
     * row: whether it's split comes from its loan_shares rows, not the stored
     * total; it stays an expense in its currency, at least its shares; and
     * `lent_shares_total` is written from those same rows.
     */
    private suspend fun writeKeepingSplit(transaction: TransactionEntity) {
        val amounts = transactionDao.getShareAmounts(transaction.id)
        val shares = if (amounts.isEmpty()) null else amounts.fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
        val persisted = if (shares == null) null else transactionDao.getTransactionById(transaction.id)
        val safe = com.pennywiseai.tracker.domain.loan.SplitRules.keepSplitBill(persisted, transaction, shares)
        transactionDao.updateTransaction(safe.copy(lentSharesTotal = shares, updatedAt = LocalDateTime.now()))
    }
    
    open suspend fun deleteTransaction(transaction: TransactionEntity, hardDelete: Boolean = false) {
        if (hardDelete) {
            transactionDao.deleteTransaction(transaction)
        } else {
            transactionDao.softDeleteTransaction(transaction.id)
        }
    }

    suspend fun deleteTransactionById(id: Long, hardDelete: Boolean = false) {
        if (hardDelete) {
            transactionDao.deleteTransactionById(id)
        } else {
            transactionDao.softDeleteTransaction(id)
        }
    }

    /** @return the number of rows actually removed. */
    suspend fun deleteAllTransactions(): Int =
        transactionDao.deleteAllTransactions()

    /** See [TransactionDao.observeAllTransactionCount]. */
    fun observeAllTransactionCount(): Flow<Int> =
        transactionDao.observeAllTransactionCount()

    /** See [TransactionDao.observeActiveTransactionCount]. */
    fun observeActiveTransactionCount(): Flow<Int> =
        transactionDao.observeActiveTransactionCount()

    /** See [TransactionDao.countAllTransactions]. */
    suspend fun countAllTransactions(): Int =
        transactionDao.countAllTransactions()

    /** See [TransactionDao.deleteRebuildableSmsTransactions]. */
    suspend fun deleteRebuildableSmsTransactions(from: LocalDateTime) =
        transactionDao.deleteRebuildableSmsTransactions(
            com.pennywiseai.tracker.receiver.BankNotificationConfig.notificationAliases.toList(),
            from
        )

    /** See [TransactionDao.getTransactionByAmountAndDateIncludingDeleted]. */
    suspend fun getTransactionByAmountAndDateIncludingDeleted(
        amount: BigDecimal,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity> =
        transactionDao.getTransactionByAmountAndDateIncludingDeleted(amount, dateStart, dateEnd)

    // Helper method to check if transaction exists by hash
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
        transactionDao.getTransactionByHash(transactionHash)

    /** A soft-deleted transaction from the same raw SMS, if any (#703). */
    suspend fun getDeletedBySms(smsBody: String, smsSender: String?): TransactionEntity? =
        transactionDao.getDeletedBySms(smsBody, smsSender)

    /** See [TransactionDao.getAnyBySms]. */
    suspend fun getAnyBySms(smsBody: String, smsSender: String?): TransactionEntity? =
        transactionDao.getAnyBySms(smsBody, smsSender)

    /** See [TransactionDao.getActiveSmsDuplicateRows]. */
    suspend fun getActiveSmsDuplicateRows(): List<TransactionEntity> =
        transactionDao.getActiveSmsDuplicateRows()

    /**
     * Rewrites a stored row's amount and currency with a fresh read of the same
     * SMS — only for amounts [TransactionDeduplication.isParseArtifact] proves
     * were an older parser's mistake. Only those two columns change, and only if
     * the row still holds the bad amount, so nothing the user saved is lost.
     *
     * Left for the user to review instead:
     *  - split transactions — the splits were sized to the old amount
     *  - a loan-linked row whose currency would change — loan totals sum their
     *    linked rows as one currency (the manual edit path forbids this too)
     *
     * The hash is left alone (it's unique; a soft-deleted duplicate may hold the
     * new one), and account balances aren't shifted: these are historical rows,
     * since re-anchored by the bank's own figures, so a delta now would
     * double-count.
     *
     * @return whether the row was repaired.
     */
    suspend fun correctParsedAmount(stored: TransactionEntity, fresh: TransactionEntity): Boolean =
        // The split, loan and goal guards live in the UPDATE itself, so they see
        // the row as it is at write time, not as it was when [stored] was read.
        goalRepository.writeAndCap(stored.id) {
            transactionDao.repairParsedAmount(
                id = stored.id,
                amount = fresh.amount,
                currency = fresh.currency,
                now = LocalDateTime.now(),
                expectedAmount = stored.amount,
                expectedCurrency = stored.currency
            ) > 0
        }

    /**
     * The subset of [hashes] already present. Chunked under SQLite's 999
     * bind-variable limit (Room does not auto-chunk `IN (:list)`), so a large
     * CSV import can't crash with "too many SQL variables".
     */
    suspend fun getExistingHashes(hashes: List<String>): List<String> =
        hashes.chunked(900).flatMap { transactionDao.getExistingHashes(it) }

    /** See [TransactionDao.findRecentExpensesByMerchantAndAmount]. */
    suspend fun findRecentExpensesByMerchantAndAmount(
        merchant: String,
        amount: java.math.BigDecimal,
        since: java.time.LocalDateTime,
        limit: Int = 5,
    ): List<TransactionEntity> =
        transactionDao.findRecentExpensesByMerchantAndAmount(merchant, amount, since, limit)

    suspend fun getTransactionByReference(reference: String): TransactionEntity? =
        transactionDao.getTransactionByReference(reference)

    /** Transactions (deleted included) with any of [hashes], in chunks under SQLite's bound-variable limit. */
    suspend fun getTransactionsByHashes(hashes: List<String>): List<TransactionEntity> =
        hashes.chunked(500).flatMap { transactionDao.getTransactionsByHashes(it) }

    suspend fun getTransactionsBetweenDatesList(startDate: LocalDateTime, endDate: LocalDateTime): List<TransactionEntity> =
        transactionDao.getTransactionsBetweenDatesList(startDate, endDate)

    suspend fun findStatementMergeCandidate(transaction: TransactionEntity): TransactionEntity? {
        val reference = transaction.reference?.takeIf { it.isNotBlank() } ?: return null
        return transactionDao.getTransactionsByReference(reference)
            .firstOrNull { candidate ->
                StatementTransactionEnricher.isStatementMatch(candidate, transaction)
            }
    }

    suspend fun findPotentialDuplicates(transaction: TransactionEntity): List<TransactionEntity> {
        if (!TransactionDeduplication.hasUpiReference(transaction)) return emptyList()

        return transactionDao.findPotentialDuplicatesByReference(
            reference = transaction.reference.orEmpty(),
            amount = transaction.amount,
            transactionType = transaction.transactionType,
            currency = transaction.currency,
            accountNumber = transaction.accountNumber,
            startDate = transaction.dateTime.minus(TransactionDeduplication.UPI_DUPLICATE_WINDOW),
            endDate = transaction.dateTime.plus(TransactionDeduplication.UPI_DUPLICATE_WINDOW)
        ).filter { candidate ->
            candidate.id != transaction.id &&
                    TransactionDeduplication.isSameUpiTransaction(candidate, transaction)
        }
    }

    suspend fun getTransactionByAmountAndDate(
        amount: BigDecimal,
        dateStart: LocalDateTime,
        dateEnd: LocalDateTime
    ): List<TransactionEntity> =
        transactionDao.getTransactionByAmountAndDate(amount, dateStart, dateEnd)

    /** Duplicate id → the id of the row that stays. */
    suspend fun findGPayDuplicatesForCleanup(): Map<Long, Long> {
        val candidates = transactionDao.findPotentialDuplicatesByReference()
        return TransactionDeduplication.duplicatesToKeepers(candidates)
    }

    /**
     * Soft-deletes an automatically found duplicate, first moving any goal
     * contributions on it to [keeperId] so they aren't lost with it (#914).
     * A duplicate whose split shares (#346) can't move to [keeperId] is kept:
     * deleting it would hide the bill those debts came from.
     * @return whether the duplicate was deleted.
     */
    suspend fun deleteDuplicate(duplicateId: Long, keeperId: Long): Boolean {
        if (transactionDao.getShareAmounts(duplicateId).isNotEmpty() &&
            !transactionDao.moveSharesToKeeper(duplicateId, keeperId)
        ) return false
        goalRepository.moveContributions(duplicateId, keeperId)
        deleteTransactionById(duplicateId)
        return true
    }

    /** Restores a deleted row from its snapshot, through the split-bill guard (#346). */
    open suspend fun undoDeleteTransaction(transaction: TransactionEntity) {
        writeKeepingSplit(transaction.copy(isDeleted = false))
    }
    
    suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String) {
        transactionDao.updateCategoryForMerchant(merchantName, newCategory)
    }

    suspend fun updateCategory(transactionId: Long, category: String) {
        transactionDao.updateCategoryById(transactionId, category, LocalDateTime.now())
    }
    
    suspend fun getOtherTransactionCountForMerchant(merchantName: String, excludeId: Long): Int {
        return transactionDao.getTransactionCountForMerchant(merchantName, excludeId)
    }

    suspend fun countExplicitProfileMismatchForAccount(
        bankName: String,
        accountLast4: String,
        profileId: Long
    ): Int {
        return transactionDao.countExplicitProfileMismatchForAccount(bankName, accountLast4, profileId)
    }

    suspend fun setProfileForAccountTransactions(
        bankName: String,
        accountLast4: String,
        profileId: Long
    ): Int {
        return transactionDao.setProfileForAccountTransactions(
            bankName,
            accountLast4,
            profileId,
            LocalDateTime.now()
        )
    }
    
    // Additional methods for Home screen
    data class MonthlyBreakdown(
        val total: BigDecimal,
        val income: BigDecimal,
        val expenses: BigDecimal
    )
    
    fun getCurrentMonthBreakdown(): Flow<MonthlyBreakdown> {
        return getTransactionsForCurrentMonth()
            .map { transactions ->
                transactions.toMonthlyBreakdown()
            }
    }
    
    fun getCurrentMonthTotal(): Flow<BigDecimal> {
        return getCurrentMonthBreakdown().map { it.total }
    }
    
    fun getLastMonthBreakdown(): Flow<MonthlyBreakdown> {
        return getTransactionsForComparableLastMonth()
            .map { transactions ->
                transactions.toMonthlyBreakdown()
            }
    }
    
    fun getLastMonthTotal(): Flow<BigDecimal> {
        return getLastMonthBreakdown().map { it.total }
    }

    // Currency-grouped breakdown methods
    fun getCurrentMonthBreakdownByCurrency(): Flow<Map<String, MonthlyBreakdown>> {
        return getTransactionsForCurrentMonth()
            .map { transactions ->
                transactions.toMonthlyBreakdownByCurrency()
            }
    }

    fun getLastMonthBreakdownByCurrency(): Flow<Map<String, MonthlyBreakdown>> {
        return getTransactionsForComparableLastMonth()
            .map { transactions ->
                transactions.toMonthlyBreakdownByCurrency()
            }
    }

    private fun getTransactionsForCurrentMonth(): Flow<List<TransactionEntity>> {
        val now = LocalDate.now()
        return userPreferencesRepository.budgetCycleStartDay.flatMapLatest { startDay ->
            val (cycleStart, cycleEnd) = BudgetCycle.currentCycle(now, startDay)
            val startDate = cycleStart.atStartOfDay()
            val endDate = cycleEnd.atTime(LocalTime.MAX)
            transactionDao.getTransactionsBetweenDates(startDate, endDate)
                // Monthly spending summary ignores analytics-excluded and loan-linked transactions (#451, #800).
                .map { txns -> txns.forTotals() }
        }
    }

    private fun getTransactionsForComparableLastMonth(): Flow<List<TransactionEntity>> {
        val now = LocalDate.now()
        return userPreferencesRepository.budgetCycleStartDay.flatMapLatest { startDay ->
            val current = BudgetCycle.currentCycle(now, startDay)
            val (prevStart, prevEnd) = BudgetCycle.previousCycle(current, startDay)
            val startDate = prevStart.atStartOfDay()
            val endDate = prevEnd.atTime(LocalTime.MAX)
            transactionDao.getTransactionsBetweenDates(startDate, endDate)
                // Monthly spending summary ignores analytics-excluded and loan-linked transactions (#451, #800).
                .map { txns -> txns.forTotals() }
        }
    }

    private fun List<TransactionEntity>.toMonthlyBreakdown(): MonthlyBreakdown {
        val income = sumTransactionType(TransactionType.INCOME)
        val expenses = sumTransactionType(TransactionType.EXPENSE)
        return MonthlyBreakdown(
            total = income - expenses,
            income = income,
            expenses = expenses
        )
    }

    private fun List<TransactionEntity>.toMonthlyBreakdownByCurrency(): Map<String, MonthlyBreakdown> {
        return forTotals()
            .groupBy { it.currency }
            .mapValues { (_, transactions) -> transactions.toMonthlyBreakdown() }
    }

    private fun List<TransactionEntity>.sumTransactionType(type: TransactionType): BigDecimal {
        return forTotals().filter { it.transactionType == type }
            .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
    }
    
    fun getRecentTransactions(limit: Int = 5): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
            .map { transactions ->
                transactions.take(limit)
            }
    }

    fun getTransactionsByAccount(bankName: String, accountLast4: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccount(bankName, accountLast4)
    }

    suspend fun countByAccount(bankName: String, accountLast4: String): Int =
        transactionDao.countByAccount(bankName, accountLast4)

    /**
     * Bulk re-target every transaction on the source account to the target
     * account. Used by the account-merge feature (#368). Returns the row
     * count actually updated. Caller cleans up the source account's balance
     * rows afterwards via [AccountBalanceRepository.deleteAccount].
     */
    suspend fun mergeAccountTransactions(
        sourceBankName: String,
        sourceAccountLast4: String,
        targetBankName: String,
        targetAccountLast4: String
    ): Int = transactionDao.mergeAccountTransactions(
        sourceBankName = sourceBankName,
        sourceAccountLast4 = sourceAccountLast4,
        targetBankName = targetBankName,
        targetAccountLast4 = targetAccountLast4,
        updatedAt = LocalDateTime.now()
    )

    /** Re-target TRANSFER from/to-account refs after an account merge (#368). */
    suspend fun retargetTransferLegRefs(
        sourceBankName: String,
        sourceAccountLast4: String,
        targetAccountLast4: String,
        targetBankName: String = sourceBankName
    ): Int {
        val ids = transactionDao.getAccountTransferLegRefIds(sourceBankName, sourceAccountLast4)
        return ids.chunked(500).sumOf { batch ->
            transactionDao.retargetTransferLegRefs(
                transactionIds = batch,
                sourceBankName = sourceBankName,
                targetBankName = targetBankName,
                sourceAccountLast4 = sourceAccountLast4,
                targetAccountLast4 = targetAccountLast4,
                updatedAt = LocalDateTime.now()
            )
        }
    }

    fun getTransactionsByAccountAndDateRange(
        bankName: String,
        accountLast4: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccountAndDateRange(bankName, accountLast4, startDate, endDate)
    }

    // Methods for batch rule application
    suspend fun getAllTransactionsList(): List<TransactionEntity> {
        // Get all non-deleted transactions as a list (not Flow) for batch processing
        // Use a large date range to get all transactions
        val startDate = LocalDateTime.of(2000, 1, 1, 0, 0)
        val endDate = LocalDateTime.now().plusYears(10)
        return transactionDao.getTransactionsBetweenDatesList(startDate, endDate)
    }

    suspend fun getUncategorizedTransactions(): List<TransactionEntity> {
        // Get all transactions without a category or with "Others" category
        return getAllTransactionsList().filter { transaction ->
            transaction.category.isNullOrBlank() || transaction.category == "Others"
        }
    }

    // ========== Transaction Split Methods ==========

    /**
     * Gets a transaction with its splits.
     */
    fun getTransactionWithSplits(transactionId: Long): Flow<TransactionWithSplits?> =
        transactionSplitDao.getTransactionWithSplits(transactionId)

    /**
     * Gets transactions with their splits for a date range and currency.
     * Useful for analytics that need to consider split amounts by category.
     */
    fun getTransactionsWithSplitsFiltered(
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String
    ): Flow<List<TransactionWithSplits>> =
        transactionSplitDao.getTransactionsWithSplitsFiltered(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59),
            currency
        )

    /**
     * Gets transactions with their splits for a date range across all currencies.
     * Used for unified currency mode where all currencies are loaded and converted.
     */
    fun getTransactionsWithSplitsFiltered(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionWithSplits>> =
        transactionSplitDao.getTransactionsWithSplitsAllCurrencies(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        )

    /**
     * Gets a transaction with its splits synchronously.
     */
    suspend fun getTransactionWithSplitsSync(transactionId: Long): TransactionWithSplits? =
        transactionSplitDao.getTransactionWithSplitsSync(transactionId)

    /**
     * Gets splits for a specific transaction.
     */
    fun getSplitsForTransaction(transactionId: Long): Flow<List<TransactionSplitEntity>> =
        transactionSplitDao.getSplitsForTransaction(transactionId)

    /**
     * Checks if a transaction has splits.
     */
    suspend fun hasSplits(transactionId: Long): Boolean =
        transactionSplitDao.hasSplits(transactionId)

    /**
     * Saves splits for a transaction, replacing any existing splits.
     */
    suspend fun saveSplits(transactionId: Long, splits: List<TransactionSplitEntity>) {
        // Delete existing splits
        transactionSplitDao.deleteSplitsForTransaction(transactionId)
        // Insert new splits
        if (splits.isNotEmpty()) {
            transactionSplitDao.insertSplits(splits.map { it.copy(transactionId = transactionId) })
        }
    }

    /**
     * Removes all splits from a transaction.
     */
    suspend fun removeSplits(transactionId: Long) {
        transactionSplitDao.deleteSplitsForTransaction(transactionId)
    }

    /**
     * Inserts a single split.
     */
    suspend fun insertSplit(split: TransactionSplitEntity): Long =
        transactionSplitDao.insertSplit(split)

    /**
     * Updates a split.
     */
    suspend fun updateSplit(split: TransactionSplitEntity) =
        transactionSplitDao.updateSplit(split)

    /**
     * Deletes a single split.
     */
    suspend fun deleteSplit(split: TransactionSplitEntity) =
        transactionSplitDao.deleteSplit(split)
}
