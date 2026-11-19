package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.domain.spending.budgetPace
import com.pennywiseai.tracker.presentation.home.heroBudget
import com.pennywiseai.tracker.presentation.home.rememberToday
import com.pennywiseai.tracker.presentation.plan.PlanMeter
import com.pennywiseai.tracker.presentation.plan.PlanSwitcher
import com.pennywiseai.tracker.presentation.plan.PlanTabHost
import com.pennywiseai.tracker.presentation.plan.planOverline
import com.pennywiseai.tracker.ui.animations.StaggeredEntrance
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.animations.rememberEntranceDone
import com.pennywiseai.tracker.ui.animations.staggerDelayMs
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.DockNavAction
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.components.cards.BudgetStatusContent
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RolloverLine
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.needsExchangeRate
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.*
import java.math.BigDecimal
import java.time.YearMonth

/** Gap between the blocks of a Plan section (switch, hero, list); rows inside a block sit 2dp apart. */
private val BlockGap = Spacing.smd

/** Budget status thresholds (% used), the same Home's budget card turns at. */
private const val WARN_PERCENT = 70f
private const val DANGER_PERCENT = 90f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BudgetGroupsScreen(
    viewModel: BudgetGroupsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToGroupEdit: (Long) -> Unit = {},
    onNavigateToHistory: (Long, Int, Int) -> Unit = { _, _, _ -> },
    onNavigateToCategory: (category: String, yearMonth: String, currency: String) -> Unit = { _, _, _ -> },
    onNavigateToGoal: (Long) -> Unit = {},
    onNavigateToGoalEdit: (Long) -> Unit = {},
    onNavigateToExchangeRates: () -> Unit = {},
    createGoalOnOpen: Boolean = false,
    /** Set when drawn as the Plan tab's Budgets section; Goals then has its own section. */
    planTab: PlanTabHost? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // In the Plan tab the switch takes the goals' slot at the top of the list.
    val goalsSection: @Composable () -> Unit = if (planTab != null) {
        { PlanSwitcher(planTab) }
    } else {
        {
            com.pennywiseai.tracker.presentation.goals.GoalsSection(
                onOpenGoal = onNavigateToGoal,
                onCreateGoal = { onNavigateToGoalEdit(-1L) },
                createOnOpen = createGoalOnOpen,
                // With no budgets either, the budget card below is the screen's one mascot.
                mascot = uiState.hasGroups
            )
        }
    }
    // With the floating nav bar, the Plan tab docks "Add budget" beside it instead of a FAB.
    // That's the section's one add action: an "+ New budget" button at the end of the
    // list as well would be a second control for the same thing.
    val dockAdd = planTab != null && LocalNavDock.current != null
    if (dockAdd && uiState.hasGroups) {
        DockNavAction(
            NavDockAction(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.budgets_add),
                onClick = { onNavigateToGroupEdit(-1L) },
            )
        )
    }

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(if (planTab != null) R.string.nav_plan else R.string.budgets_title),
                overline = if (planTab != null) planOverline() else null,
                hasBackButton = planTab == null,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.budgets_back))
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            if (uiState.hasGroups && !dockAdd) {
                FloatingActionButton(
                    onClick = { onNavigateToGroupEdit(-1L) },
                    // Above the nav bar laid over the Plan tab.
                    modifier = Modifier.padding(bottom = if (planTab != null) Dimensions.Component.bottomBarHeight else Spacing.none),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.budgets_add))
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
                LoadingIndicator()
            }
            return@PennyWiseScaffold
        }

        val selectedMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth)
        if (!uiState.hasGroups && uiState.summaryMonth == selectedMonth) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    // Clear of the floating nav bar laid over the Plan tab.
                    .padding(bottom = if (planTab != null) Dimensions.Component.bottomBarHeight else Spacing.none)
            ) {
                Box(
                    Modifier.padding(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Spacing.sm,
                        bottom = BlockGap
                    )
                ) { goalsSection() }
                MonthSelector(
                    year = uiState.selectedYear,
                    month = uiState.selectedMonth,
                    isCurrentMonth = uiState.selectedYear == java.time.LocalDate.now().year &&
                        uiState.selectedMonth == java.time.LocalDate.now().monthValue,
                    onPrevious = { viewModel.selectPreviousMonth() },
                    onNext = { viewModel.selectNextMonth() }
                )
                EmptyBudgetState(
                    modifier = Modifier.fillMaxSize(),
                    onSmartDefaults = { viewModel.runSmartDefaults() },
                    onCreateNew = { onNavigateToGroupEdit(-1L) }
                )
            }
        } else {
            BudgetGroupsContent(
                modifier = Modifier.hazeSource(hazeState).background(MaterialTheme.colorScheme.background),
                topPadding = paddingValues.calculateTopPadding(),
                uiState = uiState,
                goalsSection = goalsSection,
                onPreviousMonth = { viewModel.selectPreviousMonth() },
                onNextMonth = { viewModel.selectNextMonth() },
                onGroupClick = { groupId -> onNavigateToGroupEdit(groupId) },
                onDeleteGroup = { groupId -> viewModel.deleteGroup(groupId) },
                onMoveGroupUp = { groupId -> viewModel.moveGroupUp(groupId) },
                onMoveGroupDown = { groupId -> viewModel.moveGroupDown(groupId) },
                onNavigateToHistory = { groupId ->
                    val month = uiState.summaryMonth ?: selectedMonth
                    onNavigateToHistory(groupId, month.year, month.monthValue)
                },
                onCategoryClick = { category ->
                    val month = uiState.summaryMonth ?: selectedMonth
                    onNavigateToCategory(category, month.toString(), uiState.summary?.currency ?: uiState.currency)
                },
                onAddRate = onNavigateToExchangeRates
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
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = Dimensions.Padding.empty
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Nothing budgeted yet, so the needle rests at zero.
                Mascot(size = Dimensions.Icon.extraLarge, shape = MascotShape.Gauge, interactive = true)

                Text(
                    text = stringResource(R.string.budgets_setup_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.budgets_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onSmartDefaults,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.budgets_setup_smart_defaults))
                }

                OutlinedButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.budgets_setup_custom))
                }
            }
        }
    }
}

@Composable
private fun BudgetGroupsContent(
    modifier: Modifier = Modifier,
    topPadding: Dp = Spacing.none,
    uiState: BudgetGroupsUiState,
    goalsSection: @Composable () -> Unit = {},
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onMoveGroupUp: (Long) -> Unit,
    onMoveGroupDown: (Long) -> Unit,
    onNavigateToHistory: (Long) -> Unit,
    onCategoryClick: (String) -> Unit,
    onAddRate: () -> Unit = {}
) {
    val summary = uiState.summary ?: return
    val today = rememberToday()
    val selectedMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth)
    val isCurrentMonth = selectedMonth == YearMonth.from(today)
    val summaryReady = uiState.summaryMonth == selectedMonth
    val heroId = if (isCurrentMonth && summaryReady) {
        summary.heroBudget(today)?.budgetId
    } else null
    val hero = summary.groups.firstOrNull { it.group.budget.id == heroId }
    val otherGroups = if (summaryReady) summary.groups.filterNot { it.group.budget.id == heroId } else emptyList()
    var deleteGroupId by remember { mutableStateOf<Long?>(null) }
    var deleteGroupName by remember { mutableStateOf("") }

    val hasAnimated = rememberEntranceDone()

    val lazyListState = rememberLazyListState()
    // Rows of one block sit a grouped-list gutter apart; blocks add [BlockGap] on top.
    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize().overScrollVertical(),
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            top = Spacing.sm + topPadding,
            bottom = Dimensions.Component.bottomBarHeight + Spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
        flingBehavior = rememberOverscrollFlingBehavior { lazyListState }
    ) {
        // Goals (#914) sit above the spending budgets; they aren't month-based.
        item(key = "goals") { goalsSection() }

        item(key = "month") {
            Box(Modifier.padding(top = BlockGap)) {
                MonthSelector(
                    year = uiState.selectedYear,
                    month = uiState.selectedMonth,
                    isCurrentMonth = isCurrentMonth,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth
                )
            }
        }

        if (!summaryReady) {
            item(key = "loading-budgets") {
                Box(Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }

        val renderBudget: @Composable (BudgetGroupSpending, ListItemPosition, Boolean) -> Unit = { spending, position, isHero ->
            val budget = spending.group.budget
            val index = summary.groups.indexOfFirst { it.group.budget.id == budget.id }
            BudgetCard(
                groupSpending = spending,
                currency = summary.currency,
                position = position,
                hero = isHero,
                isFirst = index == 0,
                isLast = index == summary.groups.lastIndex,
                onEdit = { onGroupClick(budget.id) },
                onDelete = {
                    deleteGroupId = budget.id
                    deleteGroupName = budget.name
                },
                onMoveUp = { onMoveGroupUp(budget.id) },
                onMoveDown = { onMoveGroupDown(budget.id) },
                onViewHistory = { onNavigateToHistory(budget.id) },
                onCategoryClick = onCategoryClick,
                onAddRate = onAddRate
            )
        }
        if (hero != null) {
            item(key = hero.group.budget.id) {
                Box(Modifier.padding(top = BlockGap)) {
                    renderBudget(hero, ListItemPosition.Single, true)
                }
            }
        }
        itemsIndexed(
            items = otherGroups,
            key = { _, group -> group.group.budget.id }
        ) { index, groupSpending ->
            StaggeredEntrance(
                delayMs = staggerDelayMs(index),
                skip = hasAnimated,
                modifier = Modifier
                    .animateItem()
                    .padding(top = if (index == 0) BlockGap else Spacing.none)
            ) {
                renderBudget(groupSpending, ListItemPosition.from(index, otherGroups.size), false)
            }
        }
    }

    // Delete confirmation dialog
    if (deleteGroupId != null) {
        AlertDialog(
            onDismissRequest = { deleteGroupId = null },
            title = { Text(stringResource(R.string.budgets_delete_title)) },
            text = { Text(stringResource(R.string.budgets_delete_message, deleteGroupName)) },
            confirmButton = {
                Button(
                    onClick = {
                        deleteGroupId?.let { onDeleteGroup(it) }
                        deleteGroupId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.budgets_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupId = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.budgets_action_cancel))
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
    val formatter = AppDates.pattern("MMMM yyyy")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onPrevious,
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.budgets_previous_month),
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        Surface(
            modifier = Modifier.weight(1f, fill = false),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = yearMonth.format(formatter),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.md))

        FilledTonalIconButton(
            onClick = onNext,
            enabled = !isCurrentMonth,
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.budgets_next_month),
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}

@Composable
private fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    position: ListItemPosition,
    hero: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onViewHistory: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onAddRate: () -> Unit
) {
    var expanded by rememberSaveable(groupSpending.group.budget.id) { mutableStateOf(false) }
    val detailAction = stringResource(
        if (expanded) R.string.plan_budget_hide_details else R.string.plan_budget_show_details
    )
    val needsRate = groupSpending.needsExchangeRate(currency)
    val color = if (hero) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    GroupedList {
        GroupedRow(
            position = if (expanded || needsRate) {
                if (position == ListItemPosition.Top || position == ListItemPosition.Single) ListItemPosition.Top
                else ListItemPosition.Middle
            } else position,
            containerColor = color,
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = detailAction }
        ) {
            BudgetStatusContent(groupSpending, currency, hero = hero, dense = !hero) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val endPosition = if (position == ListItemPosition.Single || position == ListItemPosition.Bottom) {
            ListItemPosition.Bottom
        } else ListItemPosition.Middle
        if (needsRate) {
            GroupedRow(
                position = if (expanded) ListItemPosition.Middle else endPosition,
                onClick = onAddRate
            ) {
                RowLabels(title = stringResource(R.string.budget_rollover_add_rate), titleMaxLines = Int.MAX_VALUE)
            }
        }
        AnimatedVisibility(visible = expanded, enter = pwExpand(), exit = pwCollapse()) {
            GroupedColumn(position = endPosition) {
                BudgetDetails(
                    groupSpending = groupSpending,
                    currency = currency,
                    isFirst = isFirst,
                    isLast = isLast,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onViewHistory = onViewHistory,
                    onCategoryClick = onCategoryClick
                )
            }
        }
    }
}

/** Category drill-down, pace and calculation before the budget’s management actions. */
@Composable
private fun BudgetDetails(
    groupSpending: BudgetGroupSpending,
    currency: String,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onViewHistory: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val budget = groupSpending.group.budget
    val isLimit = budget.groupType == BudgetGroupType.LIMIT
    val categories = groupSpending.categorySpending
    if (categories.isNotEmpty()) {
        GroupedList {
            categories.forEachIndexed { index, category ->
                val categoryColor = CategoryMapping.colorFor(category.categoryName)
                GroupedRow(
                    position = ListItemPosition.from(index, categories.size),
                    onClick = { onCategoryClick(category.categoryName) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Box(
                        Modifier.size(Dimensions.Icon.avatar).clip(CircleShape)
                            .background(categoryColor.copy(alpha = Dimensions.Alpha.categoryTint)),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryIcon(category = category.categoryName, size = Dimensions.Icon.inline, tint = categoryColor)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(categoryLabel(category.categoryName), style = PennyWiseText.rowTitle)
                        val actual = CurrencyFormatter.formatCurrency(category.actualAmount, currency)
                        val hasLimit = category.budgetAmount.signum() > 0
                        Text(
                            if (hasLimit) stringResource(
                                R.string.budgets_amount_of,
                                actual,
                                CurrencyFormatter.formatCurrency(category.budgetAmount, currency)
                            ) else actual,
                            style = PennyWiseText.rowSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasLimit && !AmountPrivacy.hidden) {
                            PlanMeter(
                                fraction = category.percentageUsed / 100f,
                                color = when {
                                    isLimit && category.percentageUsed >= DANGER_PERCENT -> MaterialTheme.colorScheme.error
                                    isLimit && category.percentageUsed >= WARN_PERCENT -> MaterialTheme.colorScheme.warning
                                    else -> categoryColor
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                dense = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (isLimit && !AmountPrivacy.hidden &&
        groupSpending.dailyCumulativeSpending.size >= 2 && groupSpending.dailyBudgetPace.isNotEmpty()
    ) {
        SpendingPaceChart(groupSpending, currency)
    }

    var showCalculation by rememberSaveable { mutableStateOf(false) }
    if (isLimit && (groupSpending.hasLimit || budget.limitAmount.signum() > 0)) {
        GroupedRow(position = ListItemPosition.Single, onClick = { showCalculation = !showCalculation }) {
            RowLabels(title = stringResource(R.string.plan_budget_calculation), titleMaxLines = Int.MAX_VALUE)
            Icon(
                if (showCalculation) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = stringResource(
                    if (showCalculation) R.string.subscriptions_collapse else R.string.subscriptions_expand
                )
            )
        }
        if (showCalculation) {
            val base = CurrencyFormatter.formatCurrency(groupSpending.baseBudget, currency)
            if (groupSpending.carry.signum() == 0) {
                Text(stringResource(R.string.plan_budget_base, base), style = MaterialTheme.typography.bodyMedium)
            }
            RolloverLine(groupSpending, currency)
            if (!currency.equals(budget.currency, ignoreCase = true) && budget.limitAmount.signum() > 0 &&
                groupSpending.hasLimit
            ) {
                Text(
                    stringResource(
                        R.string.plan_budget_converted_limit,
                        CurrencyFormatter.formatCurrency(budget.limitAmount, budget.currency),
                        currency
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        TextButton(onClick = onViewHistory, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.budgets_view_history))
        }
        FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.budgets_edit))
        }
        Box {
            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.budgets_more_options))
            }
            DropdownMenu(
                shape = MaterialTheme.shapes.large,
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.budgets_move_up)) },
                    onClick = { showMenu = false; onMoveUp() },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                    enabled = !isFirst
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.budgets_move_down)) },
                    onClick = { showMenu = false; onMoveDown() },
                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    enabled = !isLast
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.budgets_delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

@Composable
private fun SpendingPaceChart(
    spending: BudgetGroupSpending,
    currency: String
) {
    val cumulativeSpending = spending.dailyCumulativeSpending
    val paceValues = spending.dailyBudgetPace
    val themeColors = MaterialTheme.colorScheme
    val isOverPace = cumulativeSpending.lastOrNull()?.let { actual ->
        paceValues.getOrNull(cumulativeSpending.lastIndex.coerceAtMost(paceValues.lastIndex))?.let { pace -> actual > pace }
    } ?: false
    val spendingColor = if (isOverPace) themeColors.error else themeColors.primary
    val today = rememberToday()
    val pace = if (today in spending.windowStart..spending.windowEnd) {
        budgetPace(spending.totalActual, spending.totalBudget, spending.daysElapsed, spending.windowDays)
    } else null

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LineChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimensions.Component.budgetPaceChartHeight),
            data = listOf(
                Line(
                    label = stringResource(R.string.budgets_chart_actual),
                    values = cumulativeSpending,
                    color = SolidColor(spendingColor),
                    firstGradientFillColor = spendingColor.copy(alpha = 0.2f),
                    secondGradientFillColor = Color.Transparent,
                    strokeAnimationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    gradientAnimationDelay = 600,
                    drawStyle = DrawStyle.Stroke(width = Dimensions.Component.paceTickWidth),
                    curvedEdges = true,
                    dotProperties = DotProperties(
                        enabled = false
                    )
                ),
                Line(
                    label = stringResource(R.string.budgets_chart_pace),
                    values = paceValues,
                    color = SolidColor(themeColors.onSurfaceVariant.copy(alpha = 0.4f)),
                    drawStyle = DrawStyle.Stroke(width = Dimensions.Component.dividerThicknessEmphasis),
                    strokeAnimationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    curvedEdges = false,
                    dotProperties = DotProperties(enabled = false)
                )
            ),
            dividerProperties = DividerProperties(enabled = false),
            indicatorProperties = HorizontalIndicatorProperties(
                enabled = true,
                textStyle = PennyWiseText.chartLabel.copy(
                    color = themeColors.onSurfaceVariant
                ),
                contentBuilder = { value ->
                    CurrencyFormatter.formatAbbreviated(value, currency)
                }
            ),
            labelHelperProperties = LabelHelperProperties(enabled = false),
            labelProperties = LabelProperties(enabled = false),
            gridProperties = GridProperties(
                enabled = true,
                xAxisProperties = GridProperties.AxisProperties(
                    enabled = false
                ),
                yAxisProperties = GridProperties.AxisProperties(
                    enabled = true,
                    style = StrokeStyle.Dashed(),
                    color = SolidColor(themeColors.onSurface.copy(alpha = 0.08f))
                )
            ),
            animationMode = AnimationMode.Together(delayBuilder = { it * 100L }),
        )

        Text(
            stringResource(
                R.string.plan_budget_pace_summary,
                CurrencyFormatter.formatCurrency(BigDecimal.valueOf(cumulativeSpending.last()), currency),
                CurrencyFormatter.formatCurrency(
                    BigDecimal.valueOf(paceValues[cumulativeSpending.lastIndex.coerceAtMost(paceValues.lastIndex)]),
                    currency
                )
            ),
            style = MaterialTheme.typography.bodySmall,
            color = themeColors.onSurfaceVariant
        )
        if (pace != null) {
            WavyMeter(
                fraction = spending.percentageUsed / 100f,
                color = spendingColor,
                trackColor = themeColors.surfaceContainerHighest,
                dense = true,
                modifier = Modifier.heightIn(min = Dimensions.Component.paceTickHeight).drawWithContent {
                    drawContent()
                    val fraction = if (layoutDirection == LayoutDirection.Rtl) 1f - pace.todayFraction else pace.todayFraction
                    val width = Dimensions.Component.paceTickWidth.toPx()
                    val x = (size.width * fraction).coerceIn(width / 2, size.width - width / 2)
                    drawLine(themeColors.onSurface, Offset(x, 0f), Offset(x, size.height), strokeWidth = width, cap = StrokeCap.Round)
                }
            )
            Text(
                when {
                    pace.daysOver > 0 -> pluralStringResource(R.plurals.budget_card_days_over_pace, pace.daysOver, pace.daysOver)
                    pace.daysOver < 0 -> (-pace.daysOver).let {
                        pluralStringResource(R.plurals.budget_card_days_under_pace, it, it)
                    }
                    else -> stringResource(R.string.budget_card_on_pace)
                },
                style = MaterialTheme.typography.bodySmall,
                color = themeColors.onSurfaceVariant
            )
        }
        Text(
            stringResource(R.string.budget_pace_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = themeColors.onSurfaceVariant
        )
    }
}
