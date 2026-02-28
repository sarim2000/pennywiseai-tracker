package com.pennywiseai.tracker.data.model

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Data models for AI chat context
 */
data class ChatContext(
    val currentDate: LocalDate,
    val monthSummary: MonthSummary,
    val recentTransactions: List<TransactionSummary>,
    val activeSubscriptions: List<SubscriptionSummary>,
    val topCategories: List<CategorySpending>,
    val quickStats: QuickStats
)

data class MonthSummary(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val transactionCount: Int,
    val daysInMonth: Int,
    val currentDay: Int
)

data class TransactionSummary(
    val merchantName: String,
    val amount: BigDecimal,
    val category: String,
    val daysAgo: Int,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val transactionType: TransactionType = TransactionType.EXPENSE
)

data class SubscriptionSummary(
    val merchantName: String,
    val amount: BigDecimal,
    val nextPaymentDays: Int
)

data class CategorySpending(
    val category: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)

data class QuickStats(
    val avgDailySpending: BigDecimal,
    val largestExpenseThisMonth: TransactionSummary?,
    val mostFrequentMerchant: String?,
    val mostFrequentMerchantCount: Int = 0
)