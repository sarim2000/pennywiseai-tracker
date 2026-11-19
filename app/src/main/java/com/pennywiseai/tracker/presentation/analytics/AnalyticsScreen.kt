package com.pennywiseai.tracker.presentation.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.chipLabel
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.shortLabel
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

private enum class CategoryViewType { CHART, LIST }

/** The Transactions slice an Analytics row drills into: its group plus the view's filters. */
data class TransactionsDrillDown(
    val category: String? = null,
    val merchant: String? = null,
    val account: String? = null,
    val tag: String? = null,
    /** A [TransactionTypeFilter] name; null (all types) for Spending, which Transactions can't show. */
    val type: String? = null,
    val period: String? = null,
    val currency: String? = null,
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null
)

/**
 * The Insights tab: the period switch and filters, a hero with the period's
 * total against the periods before it, where the money went (by category or
 * another grouping), the in-period trend and a way into Ask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onNavigateToTransactions: (TransactionsDrillDown) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    /** False when the Ask tab is hidden: the Ask card then has nowhere to go. */
    askAvailable: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val budgetCycleStartDay by viewModel.budgetCycleStartDay.collectAsStateWithLifecycle()
    val chartType by viewModel.selectedChartType.collectAsStateWithLifecycle()
    val isProEntitled by viewModel.isProEntitled.collectAsStateWithLifecycle()
    val groupBy by viewModel.groupBy.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var categoryViewType by rememberSaveable { mutableStateOf(CategoryViewType.LIST) }
    var tagViewType by rememberSaveable { mutableStateOf(CategoryViewType.LIST) }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    val periodChipLabel = selectedPeriod.chipLabel(
        budgetCycleStartDay, DateRangeUtils.formatDateRange(customDateRange), selectedPeriod.label
    )
    // A tapped row uses the scope of the figures on screen while new data loads.
    val drillDown: (TransactionsDrillDown) -> Unit = { group ->
        val custom = uiState.dateRange.takeIf { uiState.period == TimePeriod.CUSTOM }
        onNavigateToTransactions(
            group.copy(
                category = group.category ?: uiState.categoryFilter,
                account = group.account ?: uiState.accountFilter,
                type = uiState.typeFilter.takeIf { it.inTransactionsList }?.name,
                period = uiState.period.name,
                currency = uiState.currency,
                startDateEpochDay = custom?.first?.toEpochDay(),
                endDateEpochDay = custom?.second?.toEpochDay()
            )
        )
    }
    val hasData = uiState.topMerchants.isNotEmpty() || uiState.categoryBreakdown.isNotEmpty()

    // Scroll behaviors for collapsible TopAppBar
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
                title = stringResource(R.string.nav_analytics),
                overline = stringResource(R.string.analytics_overline),
                hazeState = hazeState,
                actionContent = {
                    IconButton(onClick = onNavigateToCalendar, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.CalendarViewMonth, contentDescription = stringResource(R.string.calendar_open))
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(hazeState)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = paddingValues.calculateTopPadding() + Spacing.md,
                bottom = Dimensions.Component.bottomBarHeight + Spacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            flingBehavior = rememberOverscrollFlingBehavior { listState }
        ) {
            item(key = "period") {
                InsightsPeriodSelector(
                    selected = selectedPeriod,
                    selectedMoreLabel = periodChipLabel,
                    onSelect = { period ->
                        if (period == TimePeriod.CUSTOM) {
                            showDateRangePicker = true
                        } else {
                            viewModel.selectPeriod(period)
                        }
                    }
                )
            }

            item(key = "filters") {
                AnalyticsFilters(viewModel, availableCategories = uiState.availableCategories)
            }

            if (uiState.totalSpending > BigDecimal.ZERO || uiState.transactionCount > 0) {
                item(key = "hero") {
                    val history = uiState.periodHistory
                    val heroPeriod = if (
                        history?.kind == PeriodKind.CYCLE && history.periods.last().start.dayOfMonth == 1
                    ) {
                        history.periods.last().fullLabel(PeriodKind.CYCLE)
                    } else if (uiState.period.followsBudgetCycle || uiState.period == TimePeriod.CUSTOM) {
                        DateRangeUtils.formatDateRange(uiState.dateRange) ?: uiState.period.label
                    } else uiState.period.label
                    val heroLabel = if (uiState.typeFilter.isSpendView()) {
                        stringResource(R.string.analytics_hero_spent, heroPeriod)
                    } else {
                        stringResource(R.string.analytics_hero_type, uiState.typeFilter.label, heroPeriod)
                    }
                    AnalyticsSummaryCard(
                        label = heroLabel,
                        totalAmount = uiState.totalSpending,
                        currency = uiState.currency,
                        history = history,
                        typeFilter = uiState.typeFilter,
                        forecast = uiState.forecast
                    )
                }
            }

            // Where it went: the grouping picked in the header (#422)
            if (hasData) {
                item(key = "breakdown") {
                    BreakdownSection(
                        uiState = uiState,
                        groupBy = groupBy,
                        isProEntitled = isProEntitled,
                        categoryViewType = categoryViewType,
                        tagViewType = tagViewType,
                        onToggleCategoryView = {
                            categoryViewType = categoryViewType.toggled()
                        },
                        onToggleTagView = { tagViewType = tagViewType.toggled() },
                        onGroupBySelected = { option ->
                            // A Pro grouping without Pro opens the upgrade sheet; the view stays put.
                            if (option.allowedFor(isProEntitled) == option) {
                                viewModel.setGroupBy(option)
                            } else {
                                showUpgradeSheet = true
                            }
                        },
                        drillDown = drillDown
                    )
                }
            }

            if (hasData && uiState.dailyTrend.size >= 2 && !AmountPrivacy.hidden) {
                item(key = "trend") {
                    TrendSection(
                        chartType = chartType,
                        onChartTypeSelected = viewModel::setChartType,
                        data = uiState.spendingTrend,
                        currency = uiState.currency,
                        typeFilter = uiState.typeFilter,
                        dailyData = uiState.dailyTrend,
                        onOpenCalendar = onNavigateToCalendar
                    )
                }
            }

            if (askAvailable && hasData) {
                item(key = "ask") {
                    AskCard(onClick = onNavigateToChat)
                }
            }

            // Empty state
            if (!hasData && !uiState.isLoading) {
                item(key = "empty") {
                    EmptyAnalyticsState(onScanSmsClick = onNavigateToHome)
                }
            }
        }
    }

    if (showDateRangePicker) {
        CustomDateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { startDate, endDate ->
                viewModel.setCustomDateRange(startDate, endDate)
                showDateRangePicker = false
            },
            initialStartDate = customDateRange?.first,
            initialEndDate = customDateRange?.second
        )
    }

    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false }
        )
    }
}

private fun CategoryViewType.toggled() =
    if (this == CategoryViewType.CHART) CategoryViewType.LIST else CategoryViewType.CHART

/** Views where less is better, so a drop shows green and the hero says "Spent". */
internal fun TransactionTypeFilter.isSpendView(): Boolean =
    this == TransactionTypeFilter.EXPENSE ||
        this == TransactionTypeFilter.SPENDING ||
        this == TransactionTypeFilter.CREDIT

@Composable
private fun BreakdownSection(
    uiState: AnalyticsUiState,
    groupBy: AnalyticsGroupBy,
    isProEntitled: Boolean,
    categoryViewType: CategoryViewType,
    tagViewType: CategoryViewType,
    onToggleCategoryView: () -> Unit,
    onToggleTagView: () -> Unit,
    onGroupBySelected: (AnalyticsGroupBy) -> Unit,
    drillDown: (TransactionsDrillDown) -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val currency = uiState.currency
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        SectionHeaderV2(
            title = stringResource(R.string.analytics_where_it_went),
            topSpacing = Spacing.none
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GroupByMenuButton(
                groupBy = groupBy,
                isProEntitled = isProEntitled,
                onSelected = onGroupBySelected,
                modifier = Modifier.weight(1f, fill = false)
            )
            val viewType = when (groupBy) {
                AnalyticsGroupBy.CATEGORY -> categoryViewType
                AnalyticsGroupBy.TAG -> tagViewType
                else -> null
            }
            if (viewType != null && !AmountPrivacy.hidden) {
                IconButton(
                    onClick = if (groupBy == AnalyticsGroupBy.CATEGORY) onToggleCategoryView else onToggleTagView,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = if (viewType == CategoryViewType.CHART)
                            Icons.AutoMirrored.Filled.List
                        else Icons.Default.PieChart,
                        contentDescription = stringResource(R.string.analytics_toggle_view),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        when (groupBy) {
            AnalyticsGroupBy.CATEGORY -> {
                AnimatedContent(
                    targetState = if (AmountPrivacy.hidden) CategoryViewType.LIST else categoryViewType,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith
                            fadeOut(motion.fastEffectsSpec()) using
                            SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                    },
                    label = "category_view_transition"
                ) { viewType ->
                    when (viewType) {
                        CategoryViewType.CHART -> if (!AmountPrivacy.hidden) ChartCard {
                            CategoryPieChart(
                                categories = uiState.categoryBreakdown,
                                currency = currency,
                                onCategoryClick = { drillDown(TransactionsDrillDown(category = it.name)) }
                            )
                        }
                        CategoryViewType.LIST -> CategoryBreakdownCard(
                            categories = uiState.categoryBreakdown,
                            currency = currency,
                            onCategoryClick = { drillDown(TransactionsDrillDown(category = it.name)) }
                        )
                    }
                }
                // Explain why the bars can sum above the headline total: refunds are
                // netted in full off the total, but a category bar can't go negative (#704).
                if (uiState.refundNettedFromTotal > BigDecimal.ZERO) {
                    Footnote(
                        stringResource(
                            R.string.analytics_refund_note,
                            CurrencyFormatter.formatCurrency(uiState.refundNettedFromTotal, currency)
                        )
                    )
                }
            }

            AnalyticsGroupBy.TAG -> {
                AnimatedContent(
                    targetState = if (AmountPrivacy.hidden) CategoryViewType.LIST else tagViewType,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith
                            fadeOut(motion.fastEffectsSpec()) using
                            SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                    },
                    label = "tag_view_transition"
                ) { viewType ->
                    when (viewType) {
                        CategoryViewType.CHART -> if (!AmountPrivacy.hidden) ChartCard {
                            TagPieChart(
                                tags = uiState.tagBreakdown,
                                currency = currency,
                                onTagClick = { tag ->
                                    if (!tag.isUntagged) drillDown(TransactionsDrillDown(tag = tag.name))
                                }
                            )
                        }
                        CategoryViewType.LIST -> TagBreakdownCard(
                            tags = uiState.tagBreakdown,
                            currency = currency,
                            onTagClick = { tag -> drillDown(TransactionsDrillDown(tag = tag.name)) }
                        )
                    }
                }
                if (uiState.tagsOverlap) {
                    Footnote(stringResource(R.string.analytics_tag_overlap_note))
                }
            }

            // Day of week: there's no Transactions filter for a weekday, so rows don't drill in.
            AnalyticsGroupBy.DAY_OF_WEEK -> {
                val barColor = MaterialTheme.colorScheme.primary
                TagBreakdownCard(
                    tags = uiState.dayOfWeekBreakdown,
                    currency = currency,
                    visibleItemCount = uiState.dayOfWeekBreakdown.size,
                    barColor = { barColor },
                    icon = null,
                    onTagClick = null
                )
            }

            AnalyticsGroupBy.MERCHANT -> MerchantRows(
                merchants = uiState.topMerchants,
                currency = currency,
                visibleCount = 6,
                drillDown = drillDown
            )

            AnalyticsGroupBy.ACCOUNT -> {
                val accentColor = MaterialTheme.colorScheme.primary
                val maxAmount = uiState.accountBreakdown.maxOfOrNull { it.amount } ?: BigDecimal.ZERO
                ExpandableGroupedList(items = uiState.accountBreakdown) { account, position ->
                    ShareRow(
                        position = position,
                        title = account.label,
                        amount = CurrencyFormatter.formatCurrency(account.amount, currency),
                        percent = account.percentage.toInt(),
                        fraction = shareOfMax(account.amount, maxAmount),
                        barColor = accentColor,
                        onClick = { drillDown(TransactionsDrillDown(account = account.key)) },
                        leading = { TintBadge(Icons.Default.AccountBalanceWallet, accentColor) }
                    )
                }
            }
        }
    }
}

/** The grouping picker in the "Where it went" header (#422); a view choice, so Clear leaves it alone. */
@Composable
private fun GroupByMenuButton(
    groupBy: AnalyticsGroupBy,
    isProEntitled: Boolean,
    onSelected: (AnalyticsGroupBy) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, shapes = ButtonDefaults.shapes()) {
            Text(groupBy.label(), modifier = Modifier.weight(1f, fill = false))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large
        ) {
            AnalyticsGroupBy.entries.forEachIndexed { index, option ->
                SelectableDropdownMenuItem(
                    selected = groupBy == option,
                    text = { Text(option.label()) },
                    shapes = MenuDefaults.itemShape(index, AnalyticsGroupBy.entries.size),
                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                    leadingIcon = { Icon(option.icon(), contentDescription = null) },
                    selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                    // Subtle Pro hint for free users; picking it opens the upgrade sheet.
                    trailingContent = if (option.isPro && !isProEntitled) {
                        {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.analytics_group_by_pro),
                                tint = yellow_dark,
                                modifier = Modifier.size(Dimensions.Icon.small)
                            )
                        }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

/** Merchant rows: brand glyph, name and payment count, amount. */
@Composable
private fun MerchantRows(
    merchants: List<MerchantData>,
    currency: String,
    visibleCount: Int,
    drillDown: (TransactionsDrillDown) -> Unit
) {
    // Brand icon stays keyed on the raw merchant; only the label uses the alias (#583).
    val merchantDisplay = LocalMerchantDisplay.current
    ExpandableGroupedList(items = merchants, visibleCount = visibleCount) { merchant, position ->
        GroupedRow(
            position = position,
            onClick = { drillDown(TransactionsDrillDown(merchant = merchant.name)) },
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            BrandIcon(
                merchantName = merchant.name,
                size = Dimensions.Icon.avatar,
                showBackground = true
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(text = merchantDisplay(merchant.name) ?: merchant.name, style = PennyWiseText.rowTitle)
                Text(
                    text = pluralStringResource(
                        if (merchant.isSubscription) R.plurals.analytics_transaction_count_subscription
                        else R.plurals.analytics_transaction_count,
                        merchant.transactionCount,
                        merchant.transactionCount
                    ),
                    style = PennyWiseText.rowSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCurrency(merchant.amount, currency),
                    style = PennyWiseText.amountRow
                )
            }
        }
    }
}

/** Activity within the selected period, grouped by day, month or year. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendSection(
    chartType: ChartType,
    onChartTypeSelected: (ChartType) -> Unit,
    data: List<BalancePoint>,
    currency: String,
    typeFilter: TransactionTypeFilter,
    dailyData: List<BalancePoint>,
    onOpenCalendar: () -> Unit
) {
    val label = when (typeFilter) {
        TransactionTypeFilter.EXPENSE, TransactionTypeFilter.SPENDING ->
            stringResource(R.string.insights_spending_trend)
        else -> stringResource(R.string.insights_type_trend, typeFilter.shortLabel())
    }
    var showValues by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        SectionHeaderV2(title = stringResource(R.string.analytics_trends), topSpacing = Spacing.none)
        ChartCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ConnectedChoiceRow(
                    options = ChartType.entries,
                    selected = chartType,
                    onSelect = onChartTypeSelected,
                    label = { type ->
                        when (type) {
                            ChartType.LINE -> stringResource(R.string.analytics_chart_line)
                            ChartType.HEATMAP -> stringResource(R.string.analytics_chart_heatmap)
                        }
                    }
                )
                Crossfade(
                    targetState = chartType,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "chart_transition"
                ) { type ->
                    when (type) {
                        ChartType.LINE -> BalanceChart(
                            primaryCurrency = currency,
                            balanceHistory = data,
                            modifier = Modifier.clearAndSetSemantics {},
                            height = 220,
                            label = label
                        )
                        ChartType.HEATMAP -> Column {
                            SpendingHeatmap(data = dailyData)
                            TextButton(onClick = onOpenCalendar, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.calendar_open))
                            }
                        }
                    }
                }
                TextButton(onClick = { showValues = true }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.insights_chart_values))
                }
            }
        }
    }
    if (showValues) {
        val points = if (chartType == ChartType.HEATMAP) dailyData else data
        val frame = remember(points, currency) { BarFrame.from(points, currency) }
        ModalBottomSheet(
            onDismissRequest = { showValues = false },
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Dimensions.Padding.dialog,
                    end = Dimensions.Padding.dialog,
                    bottom = Dimensions.Padding.dialog
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
            ) {
                item {
                    SectionHeaderV2(title = label, topSpacing = Spacing.none)
                }
                items(frame.bars.size, key = { frame.bars[it].key }) { i ->
                    val bar = frame.bars[i]
                    GroupedRow(position = ListItemPosition.from(i, frame.bars.size)) {
                        RowLabels(
                            title = if (chartType == ChartType.HEATMAP) {
                                bar.key.toLocalDate().format(AppDates.dateFormatter())
                            } else bar.label,
                            subtitle = CurrencyFormatter.formatCurrency(points[i].balance, currency),
                            titleMaxLines = Int.MAX_VALUE
                        )
                    }
                }
            }
        }
    }
}

/** A roomy flat card for a chart, matching the hero. */
@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.padding(Dimensions.Padding.card)) { content() }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.sm)
    )
}

@Composable
private fun AskCard(onClick: () -> Unit) {
    GroupedRow(
        position = ListItemPosition.Single,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        Text(
            text = stringResource(R.string.insights_ask),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun AnalyticsGroupBy.label(): String = stringResource(
    when (this) {
        AnalyticsGroupBy.CATEGORY -> R.string.analytics_group_by_category
        AnalyticsGroupBy.MERCHANT -> R.string.analytics_group_by_merchant
        AnalyticsGroupBy.ACCOUNT -> R.string.analytics_group_by_account
        AnalyticsGroupBy.TAG -> R.string.analytics_group_by_tag
        AnalyticsGroupBy.DAY_OF_WEEK -> R.string.analytics_group_by_day_of_week
    }
)

private fun AnalyticsGroupBy.icon() = when (this) {
    AnalyticsGroupBy.CATEGORY -> Icons.Default.Category
    AnalyticsGroupBy.MERCHANT -> Icons.Default.Store
    AnalyticsGroupBy.ACCOUNT -> Icons.Default.AccountBalanceWallet
    AnalyticsGroupBy.TAG -> Icons.Default.Sell
    AnalyticsGroupBy.DAY_OF_WEEK -> Icons.Default.DateRange
}

@Composable
private fun EmptyAnalyticsState(
    onScanSmsClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            headline = stringResource(R.string.analytics_empty_headline),
            description = stringResource(R.string.analytics_empty_description),
            actionLabel = stringResource(R.string.analytics_empty_action),
            onAction = onScanSmsClick
        )
    }
}
