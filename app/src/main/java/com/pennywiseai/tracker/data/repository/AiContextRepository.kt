package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for gathering AI chat context from financial data
 */
@Singleton
class AiContextRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val subscriptionDao: SubscriptionDao
) {
    
    /**
     * Gathers all financial context for AI chat in parallel
     */
    suspend fun getChatContext(): ChatContext = coroutineScope {
        val currentDate = LocalDate.now()
        
        // Launch parallel queries
        val monthSummaryDeferred = async { getMonthSummary(currentDate) }
        val recentTransactionsDeferred = async { getRecentTransactions(currentDate) }
        val activeSubscriptionsDeferred = async { getActiveSubscriptions(currentDate) }
        val topCategoriesDeferred = async { getTopCategories(currentDate) }
        val quickStatsDeferred = async { getQuickStats(currentDate) }
        
        ChatContext(
            currentDate = currentDate,
            monthSummary = monthSummaryDeferred.await(),
            recentTransactions = recentTransactionsDeferred.await(),
            activeSubscriptions = activeSubscriptionsDeferred.await(),
            topCategories = topCategoriesDeferred.await(),
            quickStats = quickStatsDeferred.await()
        )
    }
    
    private suspend fun getMonthSummary(currentDate: LocalDate): MonthSummary {
        val yearMonth = YearMonth.from(currentDate)
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()
        
        // Get all transactions for current month
        val transactions = transactionDao.getTransactionsBetweenDatesList(
            startOfMonth.atStartOfDay(),
            endOfMonth.atTime(23, 59, 59)
        )
        
        var totalIncome = BigDecimal.ZERO
        var totalExpense = BigDecimal.ZERO
        val transactionCount = transactions.size
        
        // Process in a single pass
        transactions.forEach { transaction ->
            when (transaction.transactionType) {
                TransactionType.INCOME -> totalIncome = totalIncome.add(transaction.amount)
                TransactionType.EXPENSE -> totalExpense = totalExpense.add(transaction.amount)
                TransactionType.CREDIT -> totalExpense = totalExpense.add(transaction.amount) // Credit counts as expense
                TransactionType.TRANSFER -> {} // Transfers don't affect income/expense totals
                TransactionType.INVESTMENT -> {} // Investments are asset reallocation, not expenses
                TransactionType.BALANCE_UPDATE -> {}
            }
        }
        
        return MonthSummary(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            transactionCount = transactionCount,
            daysInMonth = yearMonth.lengthOfMonth(),
            currentDay = currentDate.dayOfMonth
        )
    }
    
    private suspend fun getRecentTransactions(currentDate: LocalDate, days: Int = 14): List<TransactionSummary> {
        val startDate = currentDate.minusDays(days.toLong())
        
        val transactions = transactionDao.getTransactionsBetweenDatesList(
            startDate.atStartOfDay(),
            currentDate.atTime(23, 59, 59)
        )
        
        return transactions
            .sortedByDescending { it.dateTime } // Most recent first
            .take(20) // Limit to 20 most recent
            .map { transaction ->
                val daysAgo = ChronoUnit.DAYS.between(
                    transaction.dateTime.toLocalDate(),
                    currentDate
                ).toInt()
                
                TransactionSummary(
                    merchantName = transaction.merchantName,
                    amount = transaction.amount,
                    category = transaction.category ?: "Others",
                    daysAgo = daysAgo,
                    dateTime = transaction.dateTime,
                    transactionType = transaction.transactionType
                )
            }
    }
    
    private suspend fun getActiveSubscriptions(currentDate: LocalDate): List<SubscriptionSummary> {
        return subscriptionDao.getSubscriptionsByStateList(SubscriptionState.ACTIVE)
            .map { subscription ->
                val daysUntilPayment = ChronoUnit.DAYS.between(
                    currentDate,
                    subscription.nextPaymentDate
                ).toInt()
                
                SubscriptionSummary(
                    merchantName = subscription.merchantName,
                    amount = subscription.amount,
                    nextPaymentDays = daysUntilPayment
                )
            }
            .sortedBy { it.nextPaymentDays }
            .take(10) // Limit to 10 subscriptions
    }
    
    private suspend fun getTopCategories(currentDate: LocalDate): List<CategorySpending> {
        val yearMonth = YearMonth.from(currentDate)
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()
        
        val transactions = transactionDao.getTransactionsBetweenDatesList(
            startOfMonth.atStartOfDay(),
            endOfMonth.atTime(23, 59, 59)
        )
        
        // Group by category and calculate spending
        val categoryMap = mutableMapOf<String, MutableList<BigDecimal>>()
        var totalExpense = BigDecimal.ZERO
        
        transactions
            .filter { it.transactionType == TransactionType.EXPENSE }
            .forEach { transaction ->
                val category = transaction.category ?: "Others"
                categoryMap.getOrPut(category) { mutableListOf() }.add(transaction.amount)
                totalExpense = totalExpense.add(transaction.amount)
            }
        
        return categoryMap.map { (category, amounts) ->
            val categoryTotal = amounts.reduce { acc, amount -> acc.add(amount) }
            val percentage = if (totalExpense > BigDecimal.ZERO) {
                categoryTotal.divide(totalExpense, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toFloat()
            } else 0f
            
            CategorySpending(
                category = category,
                amount = categoryTotal,
                percentage = percentage,
                transactionCount = amounts.size
            )
        }
            .sortedByDescending { it.amount }
            .take(5) // Top 5 categories
    }
    
    private suspend fun getQuickStats(currentDate: LocalDate): QuickStats {
        val yearMonth = YearMonth.from(currentDate)
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()
        
        val transactions = transactionDao.getTransactionsBetweenDatesList(
            startOfMonth.atStartOfDay(),
            endOfMonth.atTime(23, 59, 59)
        )
        
        val expenses = transactions.filter { it.transactionType == TransactionType.EXPENSE }
        
        // Calculate average daily spending
        val totalExpense = expenses.sumOf { it.amount.toDouble() }.toBigDecimal()
        val daysElapsed = currentDate.dayOfMonth
        val avgDailySpending = if (daysElapsed > 0) {
            totalExpense.divide(BigDecimal(daysElapsed), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        
        // Find largest expense
        val largestExpense = expenses.maxByOrNull { it.amount }?.let { transaction ->
            val daysAgo = ChronoUnit.DAYS.between(
                transaction.dateTime.toLocalDate(),
                currentDate
            ).toInt()
            
            TransactionSummary(
                merchantName = transaction.merchantName,
                amount = transaction.amount,
                category = transaction.category ?: "Others",
                daysAgo = daysAgo
            )
        }
        
        // Find most frequent merchant
        val merchantCounts = expenses.groupingBy { it.merchantName }.eachCount()
        val mostFrequent = merchantCounts.maxByOrNull { it.value }
        
        return QuickStats(
            avgDailySpending = avgDailySpending,
            largestExpenseThisMonth = largestExpense,
            mostFrequentMerchant = mostFrequent?.key,
            mostFrequentMerchantCount = mostFrequent?.value ?: 0
        )
    }
}