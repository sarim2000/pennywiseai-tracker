package com.pennywiseai.tracker.presentation.budgetgroups

import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.ui.components.cards.BudgetPaceNote
import com.pennywiseai.tracker.ui.components.cards.BudgetBar
import com.pennywiseai.tracker.ui.components.cards.ledgerRule
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.layout.padding
import com.pennywiseai.tracker.ui.components.DEFAULT_BUDGET_COLOR
import com.pennywiseai.tracker.utils.AppDates

import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
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
import com.pennywiseai.tracker.data.database.entity.BudgetPeriodType
import com.pennywiseai.tracker.data.repository.BudgetGroupSpending
import com.pennywiseai.tracker.data.repository.BudgetOverallSummary
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import androidx.compose.ui.graphics.SolidColor
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.models.*
import kotlinx.coroutines.delay
import com.pennywiseai.tracker.data.database.entity.BudgetGroupType
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import com.pennywiseai.tracker.ui.icons.categoryLabel

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
    createGoalOnOpen: Boolean = false
) {
    val goalsSection: @Composable () -> Unit = {
        com.pennywiseai.tracker.presentation.goals.GoalsSection(
            onOpenGoal = onNavigateToGoal,
            onCreateGoal = { onNavigateToGoalEdit(-1L) },
            createOnOpen = createGoalOnOpen
        )
    }
    val uiState by viewModel.uiState.collectAsState()

    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.budgets_title),
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.budgets_back))
                    }
                },
                actionContent = {
                    if (uiState.hasGroups) {
                        IconButton(onClick = { onNavigateToGroupEdit(-1L) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.budgets_add))
                        }
                    }
                },
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Dimensions.Padding.content)
            ) {
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
                Box(Modifier.padding(horizontal = Dimensions.Padding.content)) { goalsSection() }
            }
        } else {
            BudgetGroupsContent(
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                topPadding = paddingValues.calculateTopPadding(),
                bottomPadding = paddingValues.calculateBottomPadding(),
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
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(R.string.budgets_setup_title),
                    style = MaterialTheme.typography.titleLarge,
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
                    shapes = pennyWiseButtonShapes()
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.budgets_setup_smart_defaults))
                }

                OutlinedButton(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth(),
                    shapes = pennyWiseButtonShapes()
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
    bottomPadding: Dp = 0.dp,
    uiState: BudgetGroupsUiState,
    goalsSection: @Composable () -> Unit = {},
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGroupClick: (Long) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onMoveGroupUp: (Long) -> Unit,
    onMoveGroupDown: (Long) -> Unit,
    onNavigateToHistory: (Long) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    val summary = uiState.summary ?: return
    val isCurrentMonth = YearMonth.of(uiState.selectedYear, uiState.selectedMonth) == YearMonth.now()
    val groupCount = summary.groups.size
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
            bottom = bottomPadding + Dimensions.Component.fabScrollClearance
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { slideOffsetPx },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                )
            ) {
                Column {
                    MonthSelector(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        isCurrentMonth = isCurrentMonth,
                        onPrevious = onPreviousMonth,
                        onNext = onNextMonth
                    )
                    if (isCurrentMonth) {
                        Text(
                            text = stringResource(R.string.budgets_pace_hint),
                            style = PennyWiseText.metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
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
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) + slideInVertically(
                    initialOffsetY = { slideOffsetPx },
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                )
            ) {
                BudgetCard(
                    groupSpending = groupSpending,
                    currency = uiState.currency,
                    isFirst = index == 0,
                    isLast = index == groupCount - 1,
                    onClick = { onGroupClick(groupSpending.group.budget.id) },
                    onDelete = {
                        deleteGroupId = groupSpending.group.budget.id
                        deleteGroupName = groupSpending.group.budget.name
                    },
                    onMoveUp = { onMoveGroupUp(groupSpending.group.budget.id) },
                    onMoveDown = { onMoveGroupDown(groupSpending.group.budget.id) },
                    onViewHistory = { onNavigateToHistory(groupSpending.group.budget.id) },
                    onCategoryClick = onCategoryClick,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Goals (#914) follow the budgets the screen is named for; they aren't month-based.
        item(key = "goals") {
            Box(Modifier.padding(top = Spacing.sm)) { goalsSection() }
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
                    shapes = pennyWiseButtonShapes()
                ) {
                    Text(stringResource(R.string.budgets_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupId = null }, shapes = pennyWiseButtonShapes()) {
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
        IconButton(onClick = onPrevious, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.budgets_previous_month),
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
        Text(
            text = yearMonth.format(formatter),
            style = PennyWiseText.sectionHeader,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = !isCurrentMonth, shapes = IconButtonDefaults.shapes()) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onViewHistory: () -> Unit,
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
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "progressAnimation"
    )

    LaunchedEffect(pctUsed) {
        animatedProgress = (pctUsed / 100f).coerceIn(0f, 1f)
    }

    // Stored colours can be too dark for a dark card (a default blue measured 2.2:1).
    val budgetColor = CategoryMapping.visibleOn(
        budget.color.takeUnless { it == DEFAULT_BUDGET_COLOR }.orEmpty().toColorOr(MaterialTheme.colorScheme.primary),
        MaterialTheme.colorScheme.surfaceContainerHighest
    )
    val statusColor: Color = when {
        pctUsed >= 90f -> MaterialTheme.colorScheme.error
        pctUsed >= 70f -> MaterialTheme.colorScheme.warning
        else -> MaterialTheme.colorScheme.onSurface
    }
    // The bar wears the budget's own color while it's healthy; the figures stay
    // semantic so a red budget at 36% doesn't read as danger.
    val barColor = if (pctUsed >= 70f) statusColor else budgetColor

    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth().ledgerRule(!isFirst),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.smd)
                .animateContentSize(
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
                )
        ) {
            // Row 1: Budget name + percentage pill + overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = budget.name,
                    style = PennyWiseText.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (groupSpending.totalBudget > BigDecimal.ZERO && !AmountPrivacy.hidden) {
                        Text(
                            text = stringResource(R.string.budgets_percent, pctUsed.toInt()),
                            style = PennyWiseText.amountSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showMenu = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.budgets_more_options),
                                modifier = Modifier.size(Dimensions.Icon.inline),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.budgets_edit)) },
                                onClick = {
                                    showMenu = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.budgets_view_history)) },
                                onClick = {
                                    showMenu = false
                                    onViewHistory()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.History, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.budgets_move_up)) },
                                onClick = {
                                    showMenu = false
                                    onMoveUp()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null)
                                },
                                enabled = !isFirst
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.budgets_move_down)) },
                                onClick = {
                                    showMenu = false
                                    onMoveDown()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                                },
                                enabled = !isLast
                            )
                            // Opens the confirmation dialog; nothing is deleted from here.
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.budgets_delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (groupSpending.totalBudget > BigDecimal.ZERO) {
                // Row 3: Remaining amount (hero) + what's left to spend per
                // day. Spending limits only — "per day" means nothing for a
                // savings target or expected bills.
                val remainingAbs = groupSpending.remaining.abs()
                val showDaily = groupSpending.group.budget.groupType == BudgetGroupType.LIMIT &&
                    groupSpending.dailyAllowance > BigDecimal.ZERO
                // FlowRow: when there isn't room (narrow card, large font) the
                // per-day label wraps below instead of squeezing the hero amount.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    itemVerticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = if (isOverBudget) {
                            stringResource(R.string.budgets_over_budget, CurrencyFormatter.formatCurrency(remainingAbs, currency))
                        } else {
                            stringResource(
                                R.string.budgets_remaining,
                                CurrencyFormatter.formatCurrency(groupSpending.remaining.coerceAtLeast(BigDecimal.ZERO), currency)
                            )
                        },
                        style = PennyWiseText.amountMedium,
                        color = statusColor
                    )
                    if (showDaily) {
                        Text(
                            text = stringResource(R.string.budget_card_per_day, CurrencyFormatter.formatEstimate(groupSpending.dailyAllowance, currency)),
                            style = PennyWiseText.amountSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                BudgetBar(
                    progress = if (AmountPrivacy.hidden) 0f else animatedProgressState,
                    pace = groupSpending.daysElapsed.toFloat() / groupSpending.windowDays.coerceAtLeast(1),
                    color = barColor
                )
                if (groupSpending.group.budget.groupType == BudgetGroupType.LIMIT && !AmountPrivacy.hidden) {
                    BudgetPaceNote(
                        progress = pctUsed / 100f,
                        pace = groupSpending.daysElapsed.toFloat() / groupSpending.windowDays.coerceAtLeast(1),
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))

                // Row 4: Per-cadence renewal countdown — always framed
                // as "Resets in X days" so the user knows when the
                // budget's window ends. The displayed window is the
                // budget's own current window (Jun 29..Jul 5 even when
                // the page is the July view) so this number is
                // consistent across month views.
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
                        // "day 1" says nothing about a calendar-month budget; a custom
                        // start day (salary on the 25th) is worth naming.
                        when {
                            renewalIn == 0 && startDay == 1 -> stringResource(R.string.budgets_resets_today)
                            renewalIn == 0 -> stringResource(R.string.budgets_resets_today_monthly, startDay)
                            startDay == 1 -> pluralStringResource(R.plurals.budgets_resets_in, renewalIn, renewalIn)
                            else -> pluralStringResource(R.plurals.budgets_resets_in_monthly, renewalIn, renewalIn, startDay)
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
                Text(
                    text = listOf(
                        stringResource(
                            R.string.budgets_spent_of,
                            CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency),
                            CurrencyFormatter.formatCurrency(groupSpending.totalBudget, currency)
                        ),
                        subtitleText
                    ).joinToString(" · "),
                    style = PennyWiseText.amountSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (groupSpending.isTrackingAllExpenses) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.budgets_spent, CurrencyFormatter.formatCurrency(groupSpending.totalActual, currency)),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.budgets_tracking_all),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expandable category list + pace chart
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Per-budget spending pace chart
                    // Not while hidden: the curve, a category's share and its bar all give amounts away.
                    if (groupSpending.dailyCumulativeSpending.size >= 2 && groupSpending.dailyBudgetPace.isNotEmpty() && !AmountPrivacy.hidden) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        SpendingPaceChart(
                            cumulativeSpending = groupSpending.dailyCumulativeSpending,
                            budgetPace = groupSpending.dailyBudgetPace,
                            currency = currency
                        )
                    }

                    if (groupSpending.categorySpending.isNotEmpty()) {
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
                                    .background(CategoryMapping.colorFor(catSpending.categoryName).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryIcon(
                                    category = catSpending.categoryName,
                                    size = 18.dp,
                                    tint = CategoryMapping.colorFor(catSpending.categoryName)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = categoryLabel(catSpending.categoryName),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        // Explicit: the card's tinted container doesn't supply a
                                        // content colour, so this inherited black in dark mode.
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (catSpending.budgetAmount > BigDecimal.ZERO) {
                                        Text(
                                            text = if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.budgets_percent, catPctUsed.toInt()),
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
                                            .background(catStatusColor.copy(alpha = 0.15f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = if (AmountPrivacy.hidden) 0f else (catPctUsed / 100f).coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .clip(catBarShape)
                                                .background(catStatusColor)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = stringResource(
                                            R.string.budgets_amount_of,
                                            CurrencyFormatter.formatCurrency(catSpending.actualAmount, currency),
                                            CurrencyFormatter.formatCurrency(catSpending.budgetAmount, currency)
                                        ),
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
                        }
                    }
                    }
                }
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
                        .size(8.dp)
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
                Box(
                    modifier = Modifier
                        .size(8.dp)
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
}
