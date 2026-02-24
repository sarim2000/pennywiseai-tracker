package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.database.entity.TransactionWithSplits
import java.math.BigDecimal

data class BudgetCategorySpending(
    val categoryName: String,
    val budgetAmount: BigDecimal,
    val actualAmount: BigDecimal,
    val percentageUsed: Float,
    val dailySpend: BigDecimal
)

data class BudgetGroupSpending(
    val group: BudgetWithCategories,
    val categorySpending: List<BudgetCategorySpending>,
    val totalBudget: BigDecimal,
    val totalActual: BigDecimal,
    val remaining: BigDecimal,
    val percentageUsed: Float,
    val dailyAllowance: BigDecimal,
    val daysRemaining: Int,
    val daysElapsed: Int,
    val isTrackingAllExpenses: Boolean = false
)

data class BudgetOverallSummary(
    val groups: List<BudgetGroupSpending>,
    val totalIncome: BigDecimal,
    val totalLimitBudget: BigDecimal,
    val totalLimitSpent: BigDecimal,
    val totalTargetGoal: BigDecimal,
    val totalTargetActual: BigDecimal,
    val totalExpectedBudget: BigDecimal,
    val totalExpectedActual: BigDecimal,
    val netSavings: BigDecimal,
    val savingsRate: Float,
    val dailyAllowance: BigDecimal,
    val daysRemaining: Int,
    val currency: String
)

data class BudgetGroupSpendingRaw(
    val budgetsWithCategories: List<BudgetWithCategories>,
    val allTransactions: List<TransactionWithSplits>,
    val prevTransactions: List<TransactionWithSplits>,
    val daysElapsed: Int,
    val daysRemaining: Int
)
