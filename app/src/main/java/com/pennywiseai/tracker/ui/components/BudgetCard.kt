package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.data.database.entity.BudgetEntity
import com.pennywiseai.tracker.data.repository.BudgetSpending
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun BudgetCard(
    budget: BudgetEntity,
    spending: BudgetSpending,
    dailyAllowance: BigDecimal,
    daysRemaining: Int,
    onClick: () -> Unit,
    onViewTransactions: () -> Unit,
    modifier: Modifier = Modifier,
    showCategoryBreakdown: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }

    val progressColor = getProgressColor(spending.percentageUsed, isDark)
    val budgetColor = try {
        Color(android.graphics.Color.parseColor(budget.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    PennyWiseCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header: Budget name and remaining amount
            BudgetCardHeader(
                name = budget.name,
                remaining = spending.remaining,
                limit = budget.limitAmount,
                currency = budget.currency,
                budgetColor = budgetColor
            )

            // Progress bar with percentage
            BudgetProgressBar(
                percentageUsed = spending.percentageUsed,
                progressColor = progressColor
            )

            // Timeline: Start date → Today → End date
            BudgetTimeline(
                startDate = budget.startDate,
                endDate = budget.endDate,
                progressColor = progressColor
            )

            // Smart insight
            if (daysRemaining > 0 && spending.remaining > BigDecimal.ZERO) {
                BudgetInsight(
                    dailyAllowance = dailyAllowance,
                    daysRemaining = daysRemaining,
                    currency = budget.currency
                )
            } else if (spending.remaining <= BigDecimal.ZERO) {
                Text(
                    text = "Budget exceeded",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) budget_danger_dark else budget_danger_light
                )
            }

            // Category breakdown (expandable)
            if (showCategoryBreakdown && spending.categoryBreakdown.isNotEmpty()) {
                Divider(
                    modifier = Modifier.padding(vertical = Spacing.xs),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isExpanded) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        spending.categoryBreakdown.entries.take(5).forEach { (category, amount) ->
                            CategoryBreakdownItem(
                                categoryName = category,
                                amount = amount,
                                totalSpent = spending.totalSpent,
                                currency = budget.currency
                            )
                        }
                        if (spending.categoryBreakdown.size > 5) {
                            Text(
                                text = "+${spending.categoryBreakdown.size - 5} more categories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // View Transactions button
            if (spending.transactionCount > 0) {
                TextButton(
                    onClick = onViewTransactions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "View ${spending.transactionCount} Transaction${if (spending.transactionCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCardHeader(
    name: String,
    remaining: BigDecimal,
    limit: BigDecimal,
    currency: String,
    budgetColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(budgetColor)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = CurrencyFormatter.formatCurrency(remaining, currency),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (remaining >= BigDecimal.ZERO) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    if (isSystemInDarkTheme()) budget_danger_dark else budget_danger_light
                }
            )
            Text(
                text = "left of ${CurrencyFormatter.formatCurrency(limit, currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetProgressBar(
    percentageUsed: Float,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (percentageUsed / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(progressColor)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${percentageUsed.toInt()}% spent",
                style = MaterialTheme.typography.labelSmall,
                color = progressColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BudgetTimeline(
    startDate: LocalDate,
    endDate: LocalDate,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    val daysPassed = ChronoUnit.DAYS.between(startDate, today).toInt()
    val timeProgress = (daysPassed.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)

    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Today marker
            if (timeProgress in 0f..1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = timeProgress)
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(progressColor)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = startDate.format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (today in startDate..endDate) {
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = progressColor,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = endDate.format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetInsight(
    dailyAllowance: BigDecimal,
    daysRemaining: Int,
    currency: String
) {
    Text(
        text = "You can spend ${CurrencyFormatter.formatCurrency(dailyAllowance, currency)}/day for $daysRemaining more days",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CategoryBreakdownItem(
    categoryName: String,
    amount: BigDecimal,
    totalSpent: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier
) {
    val percentage = if (totalSpent > BigDecimal.ZERO) {
        (amount.toFloat() / totalSpent.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = CurrencyFormatter.formatCurrency(amount, currency),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun getProgressColor(percentageUsed: Float, isDark: Boolean): Color {
    return when {
        percentageUsed < 50f -> if (isDark) budget_safe_dark else budget_safe_light
        percentageUsed < 80f -> if (isDark) budget_warning_dark else budget_warning_light
        else -> if (isDark) budget_danger_dark else budget_danger_light
    }
}

@Composable
fun BudgetCardCompact(
    budget: BudgetEntity,
    spending: BudgetSpending,
    daysRemaining: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val progressColor = getProgressColor(spending.percentageUsed, isDark)
    val budgetColor = try {
        Color(android.graphics.Color.parseColor(budget.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    PennyWiseCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(budgetColor)
                    )
                    Text(
                        text = budget.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = CurrencyFormatter.formatCurrency(budget.limitAmount, budget.currency),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${CurrencyFormatter.formatCurrency(spending.remaining, budget.currency)} left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (spending.remaining >= BigDecimal.ZERO) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        if (isDark) budget_danger_dark else budget_danger_light
                    }
                )
                Text(
                    text = "$daysRemaining days left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (spending.percentageUsed / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(progressColor)
                )
            }
        }
    }
}
