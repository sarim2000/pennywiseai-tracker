package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetCategorySpending
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.GradientMeshCard
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Budget Groups",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                hazeState = hazeState
            )
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
                    .hazeSource(hazeState)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Migration prompt
        if (uiState.needsMigration) {
            MigrationPrompt(
                modifier = Modifier.hazeSource(hazeState).padding(paddingValues),
                onMigrate = { viewModel.migrateOldBudget() },
                onSkip = {
                    viewModel.runSmartDefaults()
                }
            )
            return@Scaffold
        }

        if (!uiState.hasGroups) {
            EmptyBudgetState(
                modifier = Modifier.hazeSource(hazeState).padding(paddingValues),
                onSmartDefaults = { viewModel.runSmartDefaults() },
                onCreateNew = { onNavigateToGroupEdit(-1L) }
            )
        } else {
            BudgetGroupsContent(
                modifier = Modifier.hazeSource(hazeState).padding(paddingValues),
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
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            contentPadding = Dimensions.Padding.empty
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            contentPadding = Dimensions.Padding.empty
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
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

    val lazyListState = rememberLazyListState()
    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
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
                onCategoryClick = onCategoryClick,
                modifier = Modifier.animateItem()
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
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "Previous month",
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = yearMonth.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        FilledTonalIconButton(
            onClick = onNext,
            enabled = !isCurrentMonth,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Next month",
                modifier = Modifier.size(20.dp)
            )
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

    GradientMeshCard(
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
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
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val budget = groupSpending.group.budget
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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

    PennyWiseCardV2(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        contentPadding = Spacing.md
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(groupColor)
                    )
                    Text(
                        text = budget.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(Spacing.xs),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = typeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                        )
                    }
                    if (groupSpending.isTrackingAllExpenses) {
                        Surface(
                            shape = RoundedCornerShape(Spacing.xs),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "All expenses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Overflow menu for actions
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        if (onMoveUp != null) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                onClick = {
                                    showMenu = false
                                    onMoveUp()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                        if (onMoveDown != null) {
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                onClick = {
                                    showMenu = false
                                    onMoveDown()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
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
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
            } else if (groupSpending.isTrackingAllExpenses) {
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
            AnimatedVisibility(
                visible = expanded && groupSpending.categorySpending.isNotEmpty(),
                enter = fadeIn() + expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs)
                    )

                    // Pie chart for category distribution (only when 2+ categories have spending)
                    val categoriesWithSpending = groupSpending.categorySpending.filter {
                        it.actualAmount > BigDecimal.ZERO
                    }
                    if (categoriesWithSpending.size >= 2) {
                        BudgetCategoryPieChart(
                            categorySpending = categoriesWithSpending,
                            currency = currency,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                    groupSpending.categorySpending.forEach { catSpending ->
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

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Spacing.sm))
                                .clickable { onCategoryClick(catSpending.categoryName) }
                                .padding(vertical = Spacing.xs, horizontal = Spacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(catProgressColor)
                                    )
                                    Text(
                                        text = catSpending.categoryName,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
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
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                LinearProgressIndicator(
                                    progress = { catPctUsed.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = catProgressColor,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetCategoryPieChart(
    categorySpending: List<BudgetCategorySpending>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val totalSpent = remember(categorySpending) {
        categorySpending.fold(BigDecimal.ZERO) { acc, cat -> acc + cat.actualAmount }
    }

    if (totalSpent <= BigDecimal.ZERO) return

    val percentages = remember(categorySpending, totalSpent) {
        categorySpending.map { cat ->
            cat.actualAmount.toFloat() / totalSpent.toFloat() * 100f
        }
    }

    val categoryColors = remember(categorySpending) {
        categorySpending.map { cat ->
            val info = CategoryMapping.categories[cat.categoryName]
                ?: CategoryMapping.categories["Others"]
            info?.color ?: Color(0xFF9E9E9E)
        }
    }

    // Animate sweep from 0 to 1 on appear
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationStarted = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "budget_pie_animation"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val strokeWidth = with(LocalDensity.current) { 24.dp.toPx() }
        val gapDegrees = 2f

        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = strokeWidth / 2
                val arcSize = Size(
                    size.width - strokeWidth,
                    size.height - strokeWidth
                )
                val topLeft = Offset(padding, padding)

                var startAngle = -90f

                categorySpending.forEachIndexed { index, _ ->
                    val sweepAngle = (percentages[index] / 100f * 360f - gapDegrees)
                        .coerceAtLeast(0f) * animationProgress

                    drawArc(
                        color = categoryColors[index],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    )

                    startAngle += (percentages[index] / 100f * 360f) * animationProgress
                }
            }

            // Center text: total spent
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(totalSpent, currency),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Compact legend
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            categorySpending.forEachIndexed { index, cat ->
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Canvas(modifier = Modifier.size(6.dp)) {
                        drawCircle(color = categoryColors[index])
                    }
                    Text(
                        text = "${cat.categoryName} ${percentages[index].toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
