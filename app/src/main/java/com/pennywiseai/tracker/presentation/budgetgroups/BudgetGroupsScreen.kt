package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetGroupsScreen(
    viewModel: BudgetGroupsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToGroupEdit: (Long) -> Unit = {},
    onNavigateToCategory: (category: String, yearMonth: String, currency: String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    PennyWiseScaffold(
        title = "Budget Groups",
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        floatingActionButton = {
            if (uiState.hasGroups) {
                FloatingActionButton(onClick = { onNavigateToGroupEdit(-1L) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Group")
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

        // Migration prompt
        if (uiState.needsMigration) {
            MigrationPrompt(
                modifier = Modifier.padding(paddingValues),
                onMigrate = { viewModel.migrateOldBudget() },
                onSkip = {
                    viewModel.runSmartDefaults()
                }
            )
            return@PennyWiseScaffold
        }

        if (!uiState.hasGroups) {
            EmptyBudgetState(
                modifier = Modifier.padding(paddingValues),
                onSmartDefaults = { viewModel.runSmartDefaults() },
                onCreateNew = { onNavigateToGroupEdit(-1L) }
            )
        } else {
            BudgetGroupsContent(
                modifier = Modifier.padding(paddingValues),
                uiState = uiState,
                onPreviousMonth = { viewModel.selectPreviousMonth() },
                onNextMonth = { viewModel.selectNextMonth() },
                onGroupClick = { groupId -> onNavigateToGroupEdit(groupId) },
                onDeleteGroup = { groupId -> viewModel.deleteGroup(groupId) },
                onMoveUp = { groupId -> viewModel.moveGroupUp(groupId) },
                onMoveDown = { groupId -> viewModel.moveGroupDown(groupId) },
                onCategoryClick = { category ->
                    val yearMonth = "%04d-%02d".format(uiState.selectedYear, uiState.selectedMonth)
                    onNavigateToCategory(category, yearMonth, uiState.currency)
                }
            )
        }
    }
}

@Composable
private fun MigrationPrompt(
    modifier: Modifier = Modifier,
    onMigrate: () -> Unit,
    onSkip: () -> Unit
) {
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
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Upgrade to Budget Groups",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "We found your existing budget. Would you like to migrate it to the new budget groups system?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onMigrate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Migrate Existing Budget")
                }

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Fresh with Smart Defaults")
                }
            }
        }
    }
}

@Composable
private fun EmptyBudgetState(
    modifier: Modifier = Modifier,
    onSmartDefaults: () -> Unit,
    onCreateNew: () -> Unit
) {
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
                    text = "Set Up Your Budget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Organize your spending into groups like Spending, Fixed Costs, and Investments.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onSmartDefaults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Use Smart Defaults")
                }

                OutlinedButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Custom Group")
                }
            }
        }
    }
}

@Composable
private fun BudgetGroupsContent(
    modifier: Modifier = Modifier,
    uiState: BudgetGroupsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val summary = uiState.summary ?: return
    val isCurrentMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth) == YearMonth.now()
    var deleteGroupId by remember { mutableStateOf<Long?>(null) }
    var deleteGroupName by remember { mutableStateOf("") }
    val groupCount = summary.groups.size

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

        // Overall Summary Card (LIMIT groups aggregate)
        if (summary.totalLimitBudget > BigDecimal.ZERO) {
            item {
                OverallSummaryCard(
                    summary = summary,
                    currency = uiState.currency,
                    isCurrentMonth = isCurrentMonth
                )
            }
        }

        // Group Cards
        itemsIndexed(
            items = summary.groups,
            key = { _, group -> group.group.budget.id }
        ) { index, groupSpending ->
            BudgetGroupCard(
                groupSpending = groupSpending,
                currency = uiState.currency,
                onClick = { onGroupClick(groupSpending.group.budget.id) },
                onDelete = {
                    deleteGroupId = groupSpending.group.budget.id
                    deleteGroupName = groupSpending.group.budget.name
                },
                onMoveUp = if (index > 0) {{ onMoveUp(groupSpending.group.budget.id) }} else null,
                onMoveDown = if (index < groupCount - 1) {{ onMoveDown(groupSpending.group.budget.id) }} else null,
                onCategoryClick = onCategoryClick
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // Delete confirmation dialog
    if (deleteGroupId != null) {
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text("Delete Group") },
            text = { Text("Are you sure you want to delete \"$deleteGroupName\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteGroupId?.let { onDeleteGroup(it) }
                        deleteGroupId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupId = null }) {
                    Text("Cancel")
                }
            }
        )
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
private fun OverallSummaryCard(
    summary: BudgetOverallSummary,
    currency: String,
    isCurrentMonth: Boolean
) {
    val isDark = isSystemInDarkTheme()
    val limitRemaining = summary.totalLimitBudget - summary.totalLimitSpent
    val pctUsed = if (summary.totalLimitBudget > BigDecimal.ZERO) {
        (summary.totalLimitSpent.toFloat() / summary.totalLimitBudget.toFloat() * 100f)
    } else 0f

    val progressColor = when {
        pctUsed < 50f -> if (isDark) budget_safe_dark else budget_safe_light
        pctUsed < 80f -> if (isDark) budget_warning_dark else budget_warning_light
        else -> if (isDark) budget_danger_dark else budget_danger_light
    }

    PennyWiseCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Spending Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = CurrencyFormatter.formatCurrency(summary.totalLimitSpent, currency),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
                Text(
                    text = "/ ${CurrencyFormatter.formatCurrency(summary.totalLimitBudget, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { (pctUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (limitRemaining >= BigDecimal.ZERO) {
                        "${CurrencyFormatter.formatCurrency(limitRemaining, currency)} remaining"
                    } else {
                        "${CurrencyFormatter.formatCurrency(limitRemaining.abs(), currency)} over budget"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrentMonth && summary.daysRemaining > 0 && summary.dailyAllowance > BigDecimal.ZERO) {
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(summary.dailyAllowance, currency)}/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Income & Savings
            if (summary.totalIncome > BigDecimal.ZERO) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatCurrency(summary.totalIncome, currency),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                val savingsColor = if (summary.netSavings >= BigDecimal.ZERO) {
                    if (isDark) budget_safe_dark else budget_safe_light
                } else {
                    if (isDark) budget_danger_dark else budget_danger_light
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (summary.netSavings >= BigDecimal.ZERO) "Saved" else "Overspent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(summary.netSavings.abs(), currency)} (${String.format("%.0f", kotlin.math.abs(summary.savingsRate))}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = savingsColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetGroupCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onCategoryClick: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val budget = groupSpending.group.budget
    var expanded by remember { mutableStateOf(false) }

    val groupColor = try {
        Color(android.graphics.Color.parseColor(budget.color))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val progressColor = when (budget.groupType) {
        BudgetGroupType.LIMIT -> when {
            groupSpending.percentageUsed < 50f -> if (isDark) budget_safe_dark else budget_safe_light
            groupSpending.percentageUsed < 80f -> if (isDark) budget_warning_dark else budget_warning_light
            else -> if (isDark) budget_danger_dark else budget_danger_light
        }
        BudgetGroupType.TARGET -> when {
            groupSpending.percentageUsed >= 100f -> if (isDark) budget_safe_dark else budget_safe_light
            groupSpending.percentageUsed >= 50f -> if (isDark) budget_warning_dark else budget_warning_light
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        BudgetGroupType.EXPECTED -> {
            val deviation = kotlin.math.abs(groupSpending.percentageUsed - 100f)
            when {
                deviation < 10f -> if (isDark) budget_safe_dark else budget_safe_light
                deviation < 25f -> if (isDark) budget_warning_dark else budget_warning_light
                else -> if (isDark) budget_danger_dark else budget_danger_light
            }
        }
    }

    val statusText = if (groupSpending.isTrackingAllExpenses && groupSpending.totalBudget == BigDecimal.ZERO) {
        // Tracking all expenses without a budget set
        "Tracking all expenses"
    } else when (budget.groupType) {
        BudgetGroupType.LIMIT -> {
            if (groupSpending.remaining >= BigDecimal.ZERO) {
                "${CurrencyFormatter.formatCurrency(groupSpending.remaining, currency)} remaining"
            } else {
                "${CurrencyFormatter.formatCurrency(groupSpending.remaining.abs(), currency)} over budget"
            }
        }
        BudgetGroupType.TARGET -> {
            if (groupSpending.totalActual >= groupSpending.totalBudget) {
                "Goal reached!"
            } else {
                "${CurrencyFormatter.formatCurrency(groupSpending.remaining, currency)} more to reach goal"
            }
        }
        BudgetGroupType.EXPECTED -> {
            val deviation = groupSpending.totalActual - groupSpending.totalBudget
            when {
                deviation.abs() < BigDecimal.ONE -> "On track"
                deviation > BigDecimal.ZERO -> "${CurrencyFormatter.formatCurrency(deviation, currency)} over expected"
                else -> "${CurrencyFormatter.formatCurrency(deviation.abs(), currency)} under expected"
            }
        }
    }

    val typeBadge = when (budget.groupType) {
        BudgetGroupType.LIMIT -> "Limit"
        BudgetGroupType.TARGET -> "Target"
        BudgetGroupType.EXPECTED -> "Expected"
    }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Header: name + type badge + color dot
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
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(groupColor)
                    )
                    Text(
                        text = budget.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = typeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    // Show "All expenses" badge when tracking all categories
                    if (groupSpending.isTrackingAllExpenses) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "All expenses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row {
                    if (onMoveUp != null) {
                        IconButton(
                            onClick = onMoveUp,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(
                            onClick = onMoveDown,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete group",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit group",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Amounts + progress
            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                    Text(
                        text = "/ ${CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = { (groupSpending.percentageUsed / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            } else if (groupSpending.isTrackingAllExpenses) {
                // Show spending without budget limit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = progressColor
                )
                if (budget.groupType == BudgetGroupType.LIMIT && groupSpending.daysRemaining > 0 && groupSpending.dailyAllowance > BigDecimal.ZERO) {
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(groupSpending.dailyAllowance, currency)}/day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expandable category list
            if (expanded && groupSpending.categorySpending.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                groupSpending.categorySpending.forEach { catSpending ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick(catSpending.categoryName) }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = catSpending.categoryName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (catSpending.budgetAmount > BigDecimal.ZERO) {
                                Text(
                                    text = "${CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency)} / ${CurrencyFormatter.formatCurrency(catSpending.budgetAmount, currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Category progress bar
                        if (catSpending.budgetAmount > BigDecimal.ZERO) {
                            val catPctUsed = catSpending.percentageUsed / 100f
                            val catProgressColor = when (budget.groupType) {
                                BudgetGroupType.LIMIT -> when {
                                    catSpending.percentageUsed < 50f -> if (isDark) budget_safe_dark else budget_safe_light
                                    catSpending.percentageUsed < 80f -> if (isDark) budget_warning_dark else budget_warning_light
                                    else -> if (isDark) budget_danger_dark else budget_danger_light
                                }
                                BudgetGroupType.TARGET -> when {
                                    catSpending.percentageUsed >= 100f -> if (isDark) budget_safe_dark else budget_safe_light
                                    catSpending.percentageUsed >= 50f -> if (isDark) budget_warning_dark else budget_warning_light
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                BudgetGroupType.EXPECTED -> {
                                    val deviation = kotlin.math.abs(catSpending.percentageUsed - 100f)
                                    when {
                                        deviation < 10f -> if (isDark) budget_safe_dark else budget_safe_light
                                        deviation < 25f -> if (isDark) budget_warning_dark else budget_warning_light
                                        else -> if (isDark) budget_danger_dark else budget_danger_light
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { catPctUsed.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = catProgressColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
