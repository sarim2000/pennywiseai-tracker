package com.pennywiseai.tracker.presentation.monthlybudget

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.repository.CategorySpendingInfo
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyBudgetScreen(
    viewModel: MonthlyBudgetViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCategory: (category: String, yearMonth: String, currency: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    PennyWiseScaffold(
        title = "Monthly Budget",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (uiState.monthlyLimit != null) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Budget Settings")
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@PennyWiseScaffold
        }

        if (uiState.monthlyLimit == null) {
            BudgetSetupPrompt(
                modifier = Modifier.padding(paddingValues),
                currency = uiState.baseCurrency,
                onSetBudget = { amount -> viewModel.setMonthlyLimit(amount) }
            )
        } else {
            BudgetOverview(
                modifier = Modifier.padding(paddingValues),
                uiState = uiState,
                onPreviousMonth = { viewModel.selectPreviousMonth() },
                onNextMonth = { viewModel.selectNextMonth() },
                onEditBudget = onNavigateToSettings,
                onCategoryClick = { category ->
                    val yearMonth = "%04d-%02d".format(uiState.selectedYear, uiState.selectedMonth)
                    onNavigateToCategory(category, yearMonth, uiState.currency)
                }
            )
        }
    }
}

@Composable
private fun BudgetSetupPrompt(
    modifier: Modifier = Modifier,
    currency: String = "INR",
    onSetBudget: (BigDecimal) -> Unit
) {
    var amountText by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.empty),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Set Your Monthly Budget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "How much do you plan to spend each month?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
                            amountText = value
                        }
                    },
                    label = { Text("Monthly Budget") },
                    prefix = { Text(CurrencyFormatter.getCurrencySymbol(currency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val amount = amountText.toBigDecimalOrNull()
                        if (amount != null && amount > BigDecimal.ZERO) {
                            onSetBudget(amount)
                        }
                    },
                    enabled = amountText.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun BudgetOverview(
    modifier: Modifier = Modifier,
    uiState: MonthlyBudgetUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEditBudget: () -> Unit,
    onCategoryClick: (String) -> Unit = {}
) {
    val spending = uiState.spending ?: return
    val isCurrentMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth) == YearMonth.now()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Month Selector
        item {
            MonthSelector(
                year = uiState.selectedYear,
                month = uiState.selectedMonth,
                isCurrentMonth = isCurrentMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )
        }

        // Income & Savings Card
        if (spending.totalIncome > BigDecimal.ZERO) {
            item {
                IncomeSavingsCard(
                    income = spending.totalIncome,
                    expenses = spending.totalSpent,
                    netSavings = spending.netSavings,
                    savingsRate = spending.savingsRate,
                    savingsDelta = spending.savingsDelta,
                    currency = uiState.currency
                )
            }
        }

        // Total Budget Card
        item {
            TotalBudgetCard(
                spent = spending.totalSpent,
                limit = spending.totalLimit,
                percentageUsed = spending.percentageUsed,
                remaining = spending.remaining,
                daysRemaining = spending.daysRemaining,
                dailyAllowance = spending.dailyAllowance,
                currency = uiState.currency,
                isCurrentMonth = isCurrentMonth,
                onEdit = onEditBudget
            )
        }

        // Category Breakdown Header
        if (spending.categorySpending.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Category Items
        items(
            items = spending.categorySpending,
            key = { it.categoryName }
        ) { categoryInfo ->
            CategorySpendingRow(
                info = categoryInfo,
                currency = uiState.currency,
                onClick = { onCategoryClick(categoryInfo.categoryName) }
            )
        }

        // Unallocated Budget Info
        if (uiState.unallocatedBudget > BigDecimal.ZERO && uiState.categoryLimits.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.Padding.content),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unallocated",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatCurrency(uiState.unallocatedBudget, uiState.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun MonthSelector(
    year: Int,
    month: Int,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val yearMonth = YearMonth.of(year, month)
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }

        Text(
            text = yearMonth.format(formatter),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        IconButton(
            onClick = onNext,
            enabled = !isCurrentMonth
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun TotalBudgetCard(
    spent: BigDecimal,
    limit: BigDecimal,
    percentageUsed: Float,
    remaining: BigDecimal,
    daysRemaining: Int,
    dailyAllowance: BigDecimal,
    currency: String,
    isCurrentMonth: Boolean,
    onEdit: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val progressColor = when {
        percentageUsed < 50f -> if (isDark) budget_safe_dark else budget_safe_light
        percentageUsed < 80f -> if (isDark) budget_warning_dark else budget_warning_light
        else -> if (isDark) budget_danger_dark else budget_danger_light
    }

    PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Budget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit budget",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Amount display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = CurrencyFormatter.formatCurrency(spent, currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
                Text(
                    text = "/ ${CurrencyFormatter.formatCurrency(limit, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { (percentageUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Remaining + Daily allowance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (remaining >= BigDecimal.ZERO) {
                        "${CurrencyFormatter.formatCurrency(remaining, currency)} left"
                    } else {
                        "${CurrencyFormatter.formatCurrency(remaining.abs(), currency)} over"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isCurrentMonth && daysRemaining > 0 && remaining > BigDecimal.ZERO) {
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(dailyAllowance, currency)}/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeSavingsCard(
    income: BigDecimal,
    expenses: BigDecimal,
    netSavings: BigDecimal,
    savingsRate: Float,
    savingsDelta: BigDecimal?,
    currency: String
) {
    val isDark = isSystemInDarkTheme()
    val savingsColor = if (netSavings >= BigDecimal.ZERO) {
        if (isDark) budget_safe_dark else budget_safe_light
    } else {
        if (isDark) budget_danger_dark else budget_danger_light
    }

    PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Income & Savings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Income",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(income, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Expenses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(expenses, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (netSavings >= BigDecimal.ZERO) "Saved" else "Overspent",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(netSavings.abs(), currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = savingsColor
                    )
                    if (savingsRate != 0f) {
                        Text(
                            text = "${String.format("%.0f", kotlin.math.abs(savingsRate))}% of income",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (savingsDelta != null && savingsDelta.compareTo(BigDecimal.ZERO) != 0) {
                        val isPositive = savingsDelta >= BigDecimal.ZERO
                        val deltaColor = if (isPositive) {
                            if (isDark) budget_safe_dark else budget_safe_light
                        } else {
                            if (isDark) budget_danger_dark else budget_danger_light
                        }
                        Text(
                            text = "${if (isPositive) "↑" else "↓"}${CurrencyFormatter.formatCurrency(savingsDelta.abs(), currency)} vs last month",
                            style = MaterialTheme.typography.bodySmall,
                            color = deltaColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySpendingRow(
    info: CategorySpendingInfo,
    currency: String,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val hasLimit = info.limit != null

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Category name + amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = info.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (hasLimit) {
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(info.spent, currency)} / ${CurrencyFormatter.formatCurrency(info.limit!!, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = CurrencyFormatter.formatCurrency(info.spent, currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Progress bar (only if has limit)
            if (hasLimit && info.percentageUsed != null) {
                val progressColor = when {
                    info.percentageUsed < 50f -> if (isDark) budget_safe_dark else budget_safe_light
                    info.percentageUsed < 80f -> if (isDark) budget_warning_dark else budget_warning_light
                    else -> if (isDark) budget_danger_dark else budget_danger_light
                }

                LinearProgressIndicator(
                    progress = { (info.percentageUsed / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Daily info: allowance for limited categories, spend rate for others
            if (info.dailyAllowance != null && info.dailyAllowance > BigDecimal.ZERO) {
                Text(
                    text = "${CurrencyFormatter.formatCurrency(info.dailyAllowance, currency)}/day left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (info.dailySpend > BigDecimal.ZERO) {
                Text(
                    text = "${CurrencyFormatter.formatCurrency(info.dailySpend, currency)}/day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
