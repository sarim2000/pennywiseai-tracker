package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.delay
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
                title = "Budgets",
                hasBackButton = true,
                hasActionButton = true,
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
                    Icon(Icons.Default.Add, contentDescription = "Add Budget")
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (!uiState.hasGroups) {
            EmptyBudgetState(
                modifier = Modifier.hazeSource(hazeState).background(MaterialTheme.colorScheme.background).padding(paddingValues),
                onSmartDefaults = { viewModel.runSmartDefaults() },
                onCreateNew = { onNavigateToGroupEdit(-1L) }
            )
        } else {
            BudgetGroupsContent(
                modifier = Modifier.hazeSource(hazeState).background(MaterialTheme.colorScheme.background),
                topPadding = paddingValues.calculateTopPadding(),
                uiState = uiState,
                onPreviousMonth = { viewModel.selectPreviousMonth() },
                onNextMonth = { viewModel.selectNextMonth() },
                onGroupClick = { groupId -> onNavigateToGroupEdit(groupId) },
                onDeleteGroup = { groupId -> viewModel.deleteGroup(groupId) },
                onCategoryClick = { category ->
                    val yearMonth = "%04d-%02d".format(uiState.selectedYear, uiState.selectedMonth)
                    onNavigateToCategory(category, yearMonth, uiState.currency)
                }
            )
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
                    text = "Organize your spending into budgets to track where your money goes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onSmartDefaults,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Use Smart Defaults")
                }

                OutlinedButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Custom Budget")
                }
            }
        }
    }
}

@Composable
private fun BudgetGroupsContent(
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    uiState: BudgetGroupsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val summary = uiState.summary ?: return
    val isCurrentMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth) == YearMonth.now()
    var deleteGroupId by remember { mutableStateOf<Long?>(null) }
    var deleteGroupName by remember { mutableStateOf("") }

    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(350)
            hasAnimated = true
        }
    }

    val lazyListState = rememberLazyListState()
    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            top = Dimensions.Padding.content + topPadding,
            bottom = Dimensions.Component.bottomBarHeight + Spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
    ) {
        // Month Selector
        item {
            val visible = remember { mutableStateOf(hasAnimated) }
            LaunchedEffect(Unit) {
                if (!hasAnimated) { delay(0); visible.value = true }
            }
            AnimatedVisibility(
                visible = visible.value,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { slideOffsetPx },
                    animationSpec = tween(300)
                )
            ) {
                MonthSelector(
                    year = uiState.selectedYear,
                    month = uiState.selectedMonth,
                    isCurrentMonth = isCurrentMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth
                )
            }
        }

        // Budget Cards
        itemsIndexed(
            items = summary.groups,
            key = { _, group -> group.group.budget.id }
        ) { index, groupSpending ->
            val visible = remember { mutableStateOf(hasAnimated) }
            LaunchedEffect(Unit) {
                if (!hasAnimated) { delay((index + 1) * 50L); visible.value = true }
            }
            AnimatedVisibility(
                visible = visible.value,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { slideOffsetPx },
                    animationSpec = tween(300)
                )
            ) {
                BudgetCard(
                    groupSpending = groupSpending,
                    currency = uiState.currency,
                    onClick = { onGroupClick(groupSpending.group.budget.id) },
                    onDelete = {
                        deleteGroupId = groupSpending.group.budget.id
                        deleteGroupName = groupSpending.group.budget.name
                    },
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    // Delete confirmation dialog
    if (deleteGroupId != null) {
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text("Delete Budget") },
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
                modifier = Modifier.size(Dimensions.Icon.medium)
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
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}

@Composable
private fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val budget = groupSpending.group.budget
    var expanded by remember { mutableStateOf(false) }

    val pctUsed = groupSpending.percentageUsed
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO

    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        animatedProgress = (pctUsed / 100f).coerceIn(0f, 1f)
    }

    val statusColor: Color = when {
        pctUsed >= 90f -> MaterialTheme.colorScheme.error
        pctUsed >= 70f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    PennyWiseCardV2(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            // Row 1: Budget name + percentage pill + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = budget.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (groupSpending.totalBudget > BigDecimal.ZERO) {
                        Text(
                            text = "${pctUsed.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(
                                    color = statusColor,
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        )
                    }
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit budget",
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete budget",
                            modifier = Modifier.size(Dimensions.Icon.small),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                Spacer(modifier = Modifier.height(Spacing.sm))

                // Row 2: Custom rounded progress bar
                val barShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(barShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = animatedProgressState)
                            .fillMaxHeight()
                            .clip(barShape)
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Row 3: Remaining amount (hero)
                val remainingAbs = groupSpending.remaining.abs()
                Text(
                    text = if (isOverBudget) {
                        "${CurrencyFormatter.formatCurrency(remainingAbs, currency)} over budget"
                    } else {
                        "${CurrencyFormatter.formatCurrency(groupSpending.remaining.coerceAtLeast(BigDecimal.ZERO), currency)} remaining"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Row 4: Contextual subtitle
                val subtitleText = when {
                    groupSpending.daysRemaining == 0 -> "Period ended"
                    isOverBudget -> "Over by ${CurrencyFormatter.formatCurrency(remainingAbs, currency)}"
                    else -> "${CurrencyFormatter.formatCurrency(groupSpending.dailyAllowance, currency)}/day \u00B7 ${groupSpending.daysRemaining} days left"
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimensions.Alpha.subtitle)
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Row 5: Spent X of Y
                Text(
                    text = "Spent ${CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)} of ${CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else if (groupSpending.isTrackingAllExpenses) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "Spent ${CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Tracking all expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = Dimensions.Alpha.subtitle)
                )
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
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))

                    groupSpending.categorySpending.forEach { catSpending ->
                        val catPctUsed = catSpending.percentageUsed
                        val catStatusColor: Color = when {
                            catPctUsed >= 90f -> MaterialTheme.colorScheme.error
                            catPctUsed >= 70f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        val categoryInfo = CategoryMapping.categories[catSpending.categoryName]
                            ?: CategoryMapping.categories["Others"]!!

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Spacing.sm))
                                .clickable { onCategoryClick(catSpending.categoryName) }
                                .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Category icon in colored circle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(categoryInfo.color.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryIcon(
                                    category = catSpending.categoryName,
                                    size = 18.dp,
                                    tint = categoryInfo.color
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = catSpending.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (catSpending.budgetAmount > BigDecimal.ZERO) {
                                        Text(
                                            text = "${catPctUsed.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = catStatusColor
                                        )
                                    }
                                }

                                if (catSpending.budgetAmount > BigDecimal.ZERO) {
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    val catBarShape = RoundedCornerShape(50)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(catBarShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = (catPctUsed / 100f).coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .clip(catBarShape)
                                                .background(catStatusColor)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = "${CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency)} of ${CurrencyFormatter.formatCurrency(catSpending.budgetAmount, currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                } else {
                                    Text(
                                        text = CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
