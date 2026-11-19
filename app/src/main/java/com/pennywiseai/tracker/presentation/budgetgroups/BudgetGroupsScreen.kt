package com.pennywiseai.tracker.presentation.budgetgroups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.database.entity.BudgetWithCategories
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.presentation.home.heroBudget
import com.pennywiseai.tracker.presentation.plan.PlanHeroAmount
import com.pennywiseai.tracker.presentation.plan.PlanHeroCard
import com.pennywiseai.tracker.presentation.plan.PlanIconBadge
import com.pennywiseai.tracker.presentation.plan.PlanMeter
import com.pennywiseai.tracker.presentation.plan.PlanShapeBadge
import com.pennywiseai.tracker.presentation.plan.PlanStatusChip
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
import com.pennywiseai.tracker.ui.components.cards.HomeHeroBudget
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PaceInfo
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RolloverLine
import com.pennywiseai.tracker.ui.components.cards.paceInfoToggle
import com.pennywiseai.tracker.ui.components.cards.toShape
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.format.TextStyle

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
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
                FloatingActionButton(onClick = { onNavigateToGroupEdit(-1L) }) {
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
            return@Scaffold
        }

        if (!uiState.hasGroups) {
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
                    onNavigateToHistory(groupId, uiState.selectedYear, uiState.selectedMonth)
                },
                onCategoryClick = { category ->
                    val yearMonth = "%04d-%02d".format(uiState.selectedYear, uiState.selectedMonth)
                    onNavigateToCategory(category, yearMonth, uiState.currency)
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
    topPadding: Dp = 0.dp,
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
    val isCurrentMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth) == YearMonth.now()
    val groupCount = summary.groups.size
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

        // The same budget Home's hero leads with: an all-expenses spending limit
        // running today, monthly preferred. Summing every limit would count a
        // "Food" limit twice when an all-expenses one already covers it. None
        // (or a past month) → just the month picker.
        item(key = "hero") {
            val hero = remember(summary) { summary.heroBudget(java.time.LocalDate.now()) }
            StaggeredEntrance(delayMs = 0L, skip = hasAnimated, modifier = Modifier.padding(top = BlockGap)) {
                if (hero != null && hero.limit.signum() > 0) {
                    BudgetHeroCard(
                        hero = hero,
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        isCurrentMonth = isCurrentMonth,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth
                    )
                } else {
                    MonthSelector(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        isCurrentMonth = isCurrentMonth,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth
                    )
                }
            }
        }

        // Budget rows: one segmented block.
        itemsIndexed(
            items = summary.groups,
            key = { _, group -> group.group.budget.id }
        ) { index, groupSpending ->
            StaggeredEntrance(
                delayMs = staggerDelayMs(index),
                skip = hasAnimated,
                modifier = Modifier
                    .animateItem()
                    .padding(top = if (index == 0) BlockGap else Spacing.none)
            ) {
                BudgetCard(
                    groupSpending = groupSpending,
                    currency = uiState.currency,
                    index = index,
                    position = ListItemPosition.from(index, groupCount),
                    isFirst = index == 0,
                    isLast = index == groupCount - 1,
                    onEdit = { onGroupClick(groupSpending.group.budget.id) },
                    onDelete = {
                        deleteGroupId = groupSpending.group.budget.id
                        deleteGroupName = groupSpending.group.budget.name
                    },
                    onMoveUp = { onMoveGroupUp(groupSpending.group.budget.id) },
                    onMoveDown = { onMoveGroupDown(groupSpending.group.budget.id) },
                    onViewHistory = { onNavigateToHistory(groupSpending.group.budget.id) },
                    onCategoryClick = onCategoryClick,
                    onAddRate = onAddRate
                )
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

/** "November" this year, "November 2025" for another year. */
@Composable
private fun monthLabel(year: Int, month: Int): String {
    val yearMonth = YearMonth.of(year, month)
    val pattern = if (year == YearMonth.now().year) "MMMM" else "MMMM yyyy"
    return yearMonth.format(AppDates.pattern(pattern))
}

/**
 * [hero] (Home's hero budget) as one figure: what's left of it, a meter and how much
 * that leaves per day. Carries the month picker, since the figure is the month's.
 */
@Composable
private fun BudgetHeroCard(
    hero: HomeHeroBudget,
    year: Int,
    month: Int,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val currency = hero.currency
    val limit = hero.limit
    val spent = hero.spent
    val remaining = hero.remaining
    val isOver = remaining.signum() < 0
    val usedTarget = spent.divide(limit, 4, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(usedTarget) { target = usedTarget }
    val used by animateFloatAsState(target, MaterialTheme.motionScheme.slowEffectsSpec(), label = "heroMeter")

    val onHero = MaterialTheme.colorScheme.onPrimary
    val subtle = onHero.copy(alpha = Dimensions.Alpha.subtitle)

    PlanHeroCard(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = onHero
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    // A monthly budget is named by the month, any other by its own name (as on Home).
                    text = hero.name ?: stringResource(R.string.plan_budget_month, monthLabel(year, month)),
                    style = MaterialTheme.typography.titleMedium,
                    color = subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                PlanHeroAmount(CurrencyFormatter.formatCurrency(remaining.abs(), currency))
                Text(
                    text = stringResource(
                        if (isOver) R.string.plan_budget_over_of else R.string.plan_budget_left_of,
                        CurrencyFormatter.formatCurrency(limit, currency)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = subtle
                )
            }
            // The gauge owl reads how much of the month's limit is used.
            Mascot(
                size = Dimensions.Component.planHeroMascot,
                shape = MascotShape.Gauge,
                value = used,
                mood = if (isOver) MascotMood.Worried else MascotMood.Calm,
                interactive = true
            )
        }

        PlanMeter(
            fraction = used,
            color = onHero,
            trackColor = onHero.copy(alpha = Dimensions.Alpha.disabled)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val days = hero.daysLeft
            Text(
                text = when {
                    isCurrentMonth && days > 0 && !isOver && hero.perDay.signum() > 0 ->
                        pluralStringResource(
                            R.plurals.plan_budget_days_left_per_day,
                            days,
                            days,
                            CurrencyFormatter.formatCurrency(hero.perDay, currency)
                        )
                    isCurrentMonth && days > 0 -> pluralStringResource(R.plurals.plan_budget_days_left, days, days)
                    else -> stringResource(
                        R.string.budgets_spent_of,
                        CurrencyFormatter.formatCurrency(spent, currency),
                        CurrencyFormatter.formatCurrency(limit, currency)
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = subtle,
                modifier = Modifier.weight(1f)
            )
            val navColors = IconButtonDefaults.iconButtonColors(
                contentColor = onHero,
                disabledContentColor = onHero.copy(alpha = Dimensions.Alpha.disabled)
            )
            IconButton(onClick = onPrevious, colors = navColors, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.budgets_previous_month)
                )
            }
            IconButton(
                onClick = onNext,
                enabled = !isCurrentMonth,
                colors = navColors,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.budgets_next_month)
                )
            }
        }
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
            shape = CircleShape,
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

/** A budget's leading glyph: its one category's own icon, else a glyph for its kind in the budget's colour. */
@Composable
private fun BudgetBadge(group: BudgetWithCategories, index: Int) {
    val single = group.categories.singleOrNull()?.takeIf { !group.budget.includeAllCategories }
    if (single != null) {
        val color = CategoryMapping.colorFor(single.categoryName)
        PlanShapeBadge(color = color, index = index) {
            CategoryIcon(category = single.categoryName, size = Dimensions.Icon.medium, tint = color)
        }
    } else {
        val icon: ImageVector = when (group.budget.groupType) {
            BudgetGroupType.LIMIT -> Icons.Default.AccountBalanceWallet
            BudgetGroupType.TARGET -> Icons.Default.Savings
            BudgetGroupType.EXPECTED -> Icons.Default.EventRepeat
        }
        PlanIconBadge(icon = icon, color = group.budget.color.toColorOr(MaterialTheme.colorScheme.primary), index = index)
    }
}

/** One budget: glyph, name and status, what's left, a meter and the spend. Tap for the breakdown and actions. */
@Composable
private fun BudgetCard(
    groupSpending: BudgetGroupSpending,
    currency: String,
    index: Int,
    position: ListItemPosition,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onViewHistory: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onAddRate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val budget = groupSpending.group.budget
    var expanded by rememberSaveable(budget.id) { mutableStateOf(false) }

    val pctUsed = groupSpending.percentageUsed
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO
    val isLimit = budget.groupType == BudgetGroupType.LIMIT

    var targetFraction by remember { mutableFloatStateOf(0f) }
    val usedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "progressAnimation"
    )
    LaunchedEffect(pctUsed) {
        targetFraction = (pctUsed / 100f).coerceIn(0f, 1f)
    }
    // Status reads the percent the bar is showing, so the colour turns as the bar
    // crosses 70/90% rather than before it gets there (as on Home's budget card).
    val shownPercent = if (usedFraction >= 1f) pctUsed else usedFraction * 100f
    val budgetColor = budget.color.toColorOr(MaterialTheme.colorScheme.primary)
    val scheme = MaterialTheme.colorScheme
    val (statusTarget, statusText) = when {
        isOverBudget -> scheme.error to stringResource(R.string.plan_budget_status_over)
        shownPercent >= DANGER_PERCENT -> scheme.error to stringResource(R.string.plan_budget_status_almost)
        shownPercent >= WARN_PERCENT -> scheme.warning to stringResource(R.string.plan_budget_status_close)
        else -> scheme.success to stringResource(R.string.plan_budget_status_on_track)
    }
    val colorSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val statusColor by animateColorAsState(statusTarget, colorSpec, label = "budgetStatusColor")
    // The bar wears the budget's own colour while it's healthy; the pill stays semantic.
    val barColor by animateColorAsState(
        if (shownPercent >= WARN_PERCENT || isOverBudget) statusTarget else budgetColor,
        colorSpec,
        label = "budgetBarColor"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = position.toShape(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(
                        if (expanded) R.string.plan_budget_hide_details else R.string.plan_budget_show_details
                    ),
                    onClick = { expanded = !expanded }
                )
                .animateContentSize(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
                .padding(Dimensions.Padding.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            // Glyph · name + status · amount left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
            ) {
                BudgetBadge(groupSpending.group, index)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = budget.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (groupSpending.hasLimit) {
                        // A spending limit gets a verdict; a savings target or expected
                        // bills just their progress, since "almost used up" means nothing there.
                        PlanStatusChip(
                            text = if (isLimit) statusText else stringResource(R.string.budgets_percent, pctUsed.toInt()),
                            color = if (isLimit) statusColor else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (groupSpending.hasLimit) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Text(
                            text = CurrencyFormatter.formatCurrency(
                                if (isOverBudget) groupSpending.remaining.abs() else groupSpending.remaining,
                                currency
                            ),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).money(),
                            color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(if (isOverBudget) R.string.plan_budget_over else R.string.plan_budget_left),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (groupSpending.hasLimit) {
                PlanMeter(
                    fraction = usedFraction,
                    color = barColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = stringResource(
                            R.string.plan_budget_spent_of,
                            CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                            CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BudgetWindowLine(groupSpending, currency, isLimit)
                    RolloverLine(groupSpending, currency, onAddRate = onAddRate)
                }
            } else if (groupSpending.isTrackingAllExpenses) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        text = stringResource(R.string.budgets_spent, CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold).money(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.budgets_tracking_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Breakdown: pace chart, categories, and the budget's actions.
            AnimatedVisibility(
                visible = expanded,
                enter = pwExpand(),
                exit = pwCollapse()
            ) {
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

/**
 * When the budget's own window renews ("Resets in 5 days · day 1"), plus what's
 * left to spend per day for a spending limit. Each budget keeps its own cadence,
 * so a weekly budget's countdown differs from the month's.
 */
@Composable
private fun BudgetWindowLine(groupSpending: BudgetGroupSpending, currency: String, isLimit: Boolean) {
    val isOverBudget = groupSpending.remaining < BigDecimal.ZERO
    val remainingAbs = groupSpending.remaining.abs()
    val dateFormatter = AppDates.dayMonthFormatter()
    val locale = LocalConfiguration.current.locales[0]
    val subtitleText = when {
        groupSpending.daysRemaining == 0 && groupSpending.daysElapsed >= groupSpending.windowDays -> stringResource(R.string.budgets_finished)
        isOverBudget -> stringResource(R.string.budgets_over_by, CurrencyFormatter.formatCurrency(remainingAbs, currency))
        groupSpending.periodType == BudgetPeriodType.WEEKLY -> {
            val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
            val weekdayName = java.time.DayOfWeek.of((groupSpending.group.budget.weekStartDay ?: 1).coerceIn(1, 7))
                .getDisplayName(TextStyle.FULL, locale)
            if (renewalIn == 0) {
                stringResource(R.string.budgets_resets_today_weekly, weekdayName)
            } else {
                pluralStringResource(R.plurals.budgets_resets_in_weekly, renewalIn, renewalIn, weekdayName)
            }
        }
        groupSpending.periodType == BudgetPeriodType.MONTHLY -> {
            val startDay = groupSpending.group.budget.monthStartDay
                ?: groupSpending.windowStart.dayOfMonth
            val renewalIn = (groupSpending.daysRemaining - 1).coerceAtLeast(0)
            if (renewalIn == 0) {
                stringResource(R.string.budgets_resets_today_monthly, startDay)
            } else {
                pluralStringResource(R.plurals.budgets_resets_in_monthly, renewalIn, renewalIn, startDay)
            }
        }
        groupSpending.periodType == BudgetPeriodType.CUSTOM -> {
            val range = stringResource(
                R.string.budgets_date_range,
                groupSpending.windowStart.format(dateFormatter),
                groupSpending.windowEnd.format(dateFormatter)
            )
            if (groupSpending.daysRemaining >= 1) {
                // >1 counts the days after today; ==1 reads as "1 day" (unchanged behaviour).
                val left = (groupSpending.daysRemaining - 1).coerceAtLeast(1)
                pluralStringResource(R.plurals.budgets_runs_days_remaining, left, range, left)
            } else {
                stringResource(R.string.budgets_runs_finished, range)
            }
        }
        else -> pluralStringResource(
            R.plurals.budgets_days_remaining,
            groupSpending.daysRemaining,
            groupSpending.daysRemaining
        )
    }
    // Per day only for a spending limit: it means nothing for a savings target or expected bills.
    val showDaily = isLimit && groupSpending.dailyAllowance > BigDecimal.ZERO
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showDaily) {
            Text(
                text = stringResource(R.string.budget_card_per_day, CurrencyFormatter.formatCurrency(groupSpending.dailyAllowance, currency)),
                style = PennyWiseText.amountSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** The expanded part of a budget: pace chart, per-category spend and the budget's actions. */
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Per-budget spending pace chart
        if (groupSpending.dailyCumulativeSpending.size >= 2 && groupSpending.dailyBudgetPace.isNotEmpty()) {
            SpendingPaceChart(
                cumulativeSpending = groupSpending.dailyCumulativeSpending,
                budgetPace = groupSpending.dailyBudgetPace,
                currency = currency
            )
        }

        if (groupSpending.categorySpending.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            groupSpending.categorySpending.forEach { catSpending ->
                CategorySpendRow(
                    categoryName = catSpending.categoryName,
                    actual = catSpending.actualAmount,
                    budgeted = catSpending.budgetAmount,
                    percentUsed = catSpending.percentageUsed,
                    currency = currency,
                    onClick = { onCategoryClick(catSpending.categoryName) }
                )
            }
        }

        // Actions: edit up front; history, reordering and delete in the overflow.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.budgets_edit))
            }
            Spacer(Modifier.weight(1f))
            Box {
                var showMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showMenu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.budgets_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.budgets_view_history)) },
                        onClick = {
                            showMenu = false
                            onViewHistory()
                        },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.budgets_move_up)) },
                        onClick = {
                            showMenu = false
                            onMoveUp()
                        },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                        enabled = !isFirst
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.budgets_move_down)) },
                        onClick = {
                            showMenu = false
                            onMoveDown()
                        },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        enabled = !isLast
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.budgets_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

/** One category inside an expanded budget: its spend against its own share, when it has one. */
@Composable
private fun CategorySpendRow(
    categoryName: String,
    actual: BigDecimal,
    budgeted: BigDecimal,
    percentUsed: Float,
    currency: String,
    onClick: () -> Unit
) {
    val statusColor: Color = when {
        percentUsed >= DANGER_PERCENT -> MaterialTheme.colorScheme.error
        percentUsed >= WARN_PERCENT -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val categoryColor = CategoryMapping.colorFor(categoryName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.avatar)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = Dimensions.Alpha.categoryTint)),
            contentAlignment = Alignment.Center
        ) {
            CategoryIcon(category = categoryName, size = Dimensions.Icon.inline, tint = categoryColor)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = categoryLabel(categoryName),
                    style = PennyWiseText.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (budgeted > BigDecimal.ZERO) {
                    Text(
                        text = stringResource(R.string.budgets_percent, percentUsed.toInt()),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
            if (budgeted > BigDecimal.ZERO) {
                PlanMeter(
                    fraction = percentUsed / 100f,
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.plan_budget_spent_of,
                        CurrencyFormatter.formatCurrency(actual, currency),
                        CurrencyFormatter.formatCurrency(budgeted, currency)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = CurrencyFormatter.formatCurrency(actual, currency),
                    style = PennyWiseText.amountSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpendingPaceChart(
    cumulativeSpending: List<Double>,
    budgetPace: List<Double>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val themeColors = MaterialTheme.colorScheme
    val isOverPace = cumulativeSpending.lastOrNull()?.let { actual ->
        budgetPace.lastOrNull()?.let { pace -> actual > pace }
    } ?: false
    val spendingColor = if (isOverPace) themeColors.error else themeColors.primary
    var showPaceInfo by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        LineChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            data = listOf(
                Line(
                    label = stringResource(R.string.budgets_chart_actual),
                    values = cumulativeSpending,
                    color = SolidColor(spendingColor),
                    firstGradientFillColor = spendingColor.copy(alpha = 0.2f),
                    secondGradientFillColor = Color.Transparent,
                    strokeAnimationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                    gradientAnimationDelay = 600,
                    drawStyle = DrawStyle.Stroke(width = 2.5.dp),
                    curvedEdges = true,
                    dotProperties = DotProperties(
                        enabled = false
                    )
                ),
                Line(
                    label = stringResource(R.string.budgets_chart_pace),
                    values = budgetPace,
                    color = SolidColor(themeColors.onSurfaceVariant.copy(alpha = 0.4f)),
                    drawStyle = DrawStyle.Stroke(width = 1.5.dp),
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

        Spacer(modifier = Modifier.height(Spacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.legendDot)
                    .clip(CircleShape)
                    .background(spendingColor)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = stringResource(R.string.budgets_chart_actual),
                style = MaterialTheme.typography.labelSmall,
                color = themeColors.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Row(
                modifier = Modifier.paceInfoToggle { showPaceInfo = !showPaceInfo },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.Component.legendDot)
                        .clip(CircleShape)
                        .background(themeColors.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(R.string.budgets_chart_pace),
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.onSurfaceVariant
                )
            }
        }
        PaceInfo(visible = showPaceInfo, text = stringResource(R.string.budget_pace_explainer))
    }
}
