package com.pennywiseai.tracker.widget

import java.math.BigDecimal

data class BudgetWidgetData(
    val totalSpent: BigDecimal = BigDecimal.ZERO,
    val totalLimit: BigDecimal = BigDecimal.ZERO,
    val remaining: BigDecimal = BigDecimal.ZERO,
    val percentageUsed: Float = 0f,
    val dailyAllowance: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val netSavings: BigDecimal = BigDecimal.ZERO,
    val savingsRate: Float = 0f,
    val savingsDelta: BigDecimal? = null,
    val currency: String = "INR"
)
