package com.pennywiseai.tracker.data.model

import java.math.BigDecimal

/** Figures the AI chat reads (AiContextRepository). */
data class MonthSummary(
    val totalIncome: BigDecimal,
    val totalExpense: BigDecimal,
    val transactionCount: Int,
    val daysInMonth: Int,
    val currentDay: Int
)

data class CategorySpending(
    val category: String,
    val amount: BigDecimal,
    val percentage: Float,
    val transactionCount: Int
)
