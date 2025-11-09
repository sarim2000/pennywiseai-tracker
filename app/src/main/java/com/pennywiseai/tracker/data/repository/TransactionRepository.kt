package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAllTransactions(): Flow<List<TransactionEntity>> = 
        transactionDao.getAllTransactions()
    
    suspend fun getTransactionById(id: Long): TransactionEntity? = 
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
    
    fun getAllMerchants(): Flow<List<String>> = 
        transactionDao.getAllMerchants()
    
    suspend fun getTotalAmountByTypeAndPeriod(
        type: TransactionType,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Double? = transactionDao.getTotalAmountByTypeAndPeriod(type, startDate, endDate)
    
    suspend fun insertTransaction(transaction: TransactionEntity): Long = 
        transactionDao.insertTransaction(transaction)
    
    suspend fun insertTransactions(transactions: List<TransactionEntity>) = 
        transactionDao.insertTransactions(transactions)
    
    suspend fun updateTransaction(transaction: TransactionEntity) = 
        transactionDao.updateTransaction(transaction)
    
    suspend fun deleteTransaction(transaction: TransactionEntity, hardDelete: Boolean = false) {
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

    suspend fun deleteAllTransactions() =
        transactionDao.deleteAllTransactions()

    // Helper method to check if transaction exists by hash
    suspend fun getTransactionByHash(transactionHash: String): TransactionEntity? =
        transactionDao.getTransactionByHash(transactionHash)
    
    suspend fun undoDeleteTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction.copy(isDeleted = false))
    }
    
    suspend fun updateCategoryForMerchant(merchantName: String, newCategory: String) {
        transactionDao.updateCategoryForMerchant(merchantName, newCategory)
    }
    
    suspend fun getOtherTransactionCountForMerchant(merchantName: String, excludeId: Long): Int {
        return transactionDao.getTransactionCountForMerchant(merchantName, excludeId)
    }
    
    // Additional methods for Home screen
    data class MonthlyBreakdown(
        val total: BigDecimal,
        val income: BigDecimal,
        val expenses: BigDecimal
    )
    
    fun getCurrentMonthBreakdown(): Flow<MonthlyBreakdown> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1).atStartOfDay()
        val endDate = now.atTime(23, 59, 59)
        
        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                val income = transactions
                    .filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                val expenses = transactions
                    .filter { it.transactionType == TransactionType.EXPENSE }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                MonthlyBreakdown(
                    total = income - expenses,
                    income = income,
                    expenses = expenses
                )
            }
    }
    
    fun getCurrentMonthTotal(): Flow<BigDecimal> {
        return getCurrentMonthBreakdown().map { it.total }
    }
    
    fun getLastMonthBreakdown(): Flow<MonthlyBreakdown> {
        val now = LocalDate.now()
        val dayOfMonth = now.dayOfMonth
        val lastMonth = now.minusMonths(1)
        
        // Compare same period: if today is 10th, compare 1st-10th of last month
        val startDate = lastMonth.withDayOfMonth(1).atStartOfDay()
        val lastMonthMaxDay = min(dayOfMonth, lastMonth.lengthOfMonth())
        val endDate = lastMonth.withDayOfMonth(lastMonthMaxDay).atTime(23, 59, 59)
        
        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                val income = transactions
                    .filter { it.transactionType == TransactionType.INCOME }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                val expenses = transactions
                    .filter { it.transactionType == TransactionType.EXPENSE }
                    .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                MonthlyBreakdown(
                    total = income - expenses,
                    income = income,
                    expenses = expenses
                )
            }
    }
    
    fun getLastMonthTotal(): Flow<BigDecimal> {
        return getLastMonthBreakdown().map { it.total }
    }

    // Currency-grouped breakdown methods
    fun getCurrentMonthBreakdownByCurrency(): Flow<Map<String, MonthlyBreakdown>> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1).atStartOfDay()
        val endDate = now.atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                transactions.groupBy { it.currency }.mapValues { (_, currencyTransactions) ->
                    val income = currencyTransactions
                        .filter { it.transactionType == TransactionType.INCOME }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    val expenses = currencyTransactions
                        .filter { it.transactionType == TransactionType.EXPENSE }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    MonthlyBreakdown(
                        total = income - expenses,
                        income = income,
                        expenses = expenses
                    )
                }
            }
    }

    fun getLastMonthBreakdownByCurrency(): Flow<Map<String, MonthlyBreakdown>> {
        val now = LocalDate.now()
        val dayOfMonth = now.dayOfMonth
        val lastMonth = now.minusMonths(1)

        // Compare same period: if today is 10th, compare 1st-10th of last month
        val startDate = lastMonth.withDayOfMonth(1).atStartOfDay()
        val lastMonthMaxDay = min(dayOfMonth, lastMonth.lengthOfMonth())
        val endDate = lastMonth.withDayOfMonth(lastMonthMaxDay).atTime(23, 59, 59)

        return transactionDao.getTransactionsBetweenDates(startDate, endDate)
            .map { transactions ->
                transactions.groupBy { it.currency }.mapValues { (_, currencyTransactions) ->
                    val income = currencyTransactions
                        .filter { it.transactionType == TransactionType.INCOME }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    val expenses = currencyTransactions
                        .filter { it.transactionType == TransactionType.EXPENSE }
                        .fold(BigDecimal.ZERO) { acc, transaction -> acc + transaction.amount }
                    MonthlyBreakdown(
                        total = income - expenses,
                        income = income,
                        expenses = expenses
                    )
                }
            }
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
}