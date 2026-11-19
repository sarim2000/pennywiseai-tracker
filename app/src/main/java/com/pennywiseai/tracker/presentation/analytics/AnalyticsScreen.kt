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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.chipLabel
import com.pennywiseai.tracker.presentation.common.filterIcon
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.shortLabel
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.*
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
 * another grouping), the top merchants, the in-period trend and a way into Ask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onNavigateToTransactions: (TransactionsDrillDown) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    /** False when the Ask tab is hidden: the "story behind it" card then has nowhere to go. */
    askAvailable: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsStateWithLifecycle()
    val defaultTypeFilter by viewModel.defaultTypeFilter.collectAsStateWithLifecycle()
    val selectedCurrency by viewModel.selectedCurrency.collectAsStateWithLifecycle()
    val availableCurrencies by viewModel.availableCurrencies.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()
    val budgetCycleStartDay by viewModel.budgetCycleStartDay.collectAsStateWithLifecycle()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsStateWithLifecycle()
    val chartType by viewModel.selectedChartType.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val accountFilter by viewModel.accountFilter.collectAsStateWithLifecycle()
    val accountOptions by viewModel.accountOptions.collectAsStateWithLifecycle()
    val isProEntitled by viewModel.isProEntitled.collectAsStateWithLifecycle()
    val groupBy by viewModel.groupBy.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var categoryViewType by rememberSaveable { mutableStateOf(CategoryViewType.LIST) }
    var tagViewType by rememberSaveable { mutableStateOf(CategoryViewType.LIST) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    val customRangeLabel = remember(customDateRange, AppDates.dateFormatStyle) {
        DateRangeUtils.formatDateRange(customDateRange)
    }
    val periodLabel = selectedPeriod.label
    val periodChipLabel = remember(selectedPeriod, budgetCycleStartDay, customRangeLabel, periodLabel) {
        selectedPeriod.chipLabel(budgetCycleStartDay, customRangeLabel, periodLabel)
    }
    // Carry the custom range through drill-down navigation so the Transactions
    // screen (and its CSV export) shows exactly the slice being viewed here.
    val navStartEpochDay = if (selectedPeriod == TimePeriod.CUSTOM) customDateRange?.first?.toEpochDay() else null
    val navEndEpochDay = if (selectedPeriod == TimePeriod.CUSTOM) customDateRange?.second?.toEpochDay() else null
    // Drill into Transactions for one group, keeping this view's period, currency,
    // type and category filter so the list matches the figure that was tapped.
    val drillDown: (TransactionsDrillDown) -> Unit = { group ->
        onNavigateToTransactions(
            group.copy(
                category = group.category ?: categoryFilter,
                type = transactionTypeFilter.takeIf { it.inTransactionsList }?.name,
                period = selectedPeriod.name,
                currency = selectedCurrency,
                startDateEpochDay = navStartEpochDay,
                endDateEpochDay = navEndEpochDay
            )
        )
    }
    val primaryVisibleCurrency = availableCurrencies.firstOrNull() ?: selectedCurrency
    val hasCurrencyFilter = !isUnifiedMode &&
        availableCurrencies.size > 1 &&
        selectedCurrency.isNotBlank() &&
        !selectedCurrency.equals(primaryVisibleCurrency, ignoreCase = true)
    val hasActiveAnalyticsFilter = selectedPeriod != TimePeriod.THIS_MONTH ||
        customDateRange != null ||
        transactionTypeFilter != defaultTypeFilter ||
        categoryFilter != null ||
        accountFilter != null ||
        hasCurrencyFilter
    val hasData = uiState.topMerchants.isNotEmpty() || uiState.categoryBreakdown.isNotEmpty()

    // Scroll behaviors for collapsible TopAppBar
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
                AnalyticsFilterBar(
                    transactionTypeFilter = transactionTypeFilter,
                    defaultTypeFilter = defaultTypeFilter,
                    selectedCurrency = selectedCurrency,
                    availableCurrencies = availableCurrencies,
                    isUnifiedMode = isUnifiedMode,
                    categoryFilter = categoryFilter,
                    availableCategories = uiState.availableCategories,
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    accountOptions = accountOptions,
                    accountFilter = accountFilter,
                    showTypeMenu = showTypeMenu,
                    showCurrencyMenu = showCurrencyMenu,
                    showCategoryMenu = showCategoryMenu,
                    showProfileMenu = showProfileMenu,
                    showAccountMenu = showAccountMenu,
                    hasActiveFilter = hasActiveAnalyticsFilter,
                    onTypeClick = { showTypeMenu = true },
                    onTypeDismiss = { showTypeMenu = false },
                    onTypeSelected = { typeFilter ->
                        viewModel.setTransactionTypeFilter(typeFilter)
                        showTypeMenu = false
                    },
                    onCurrencyClick = { showCurrencyMenu = true },
                    onCurrencyDismiss = { showCurrencyMenu = false },
                    onCurrencySelected = { currency ->
                        viewModel.selectCurrency(currency)
                        showCurrencyMenu = false
                    },
                    onCategoryClick = { showCategoryMenu = true },
                    onCategoryDismiss = { showCategoryMenu = false },
                    onCategorySelected = { category ->
                        if (category == null) {
                            viewModel.clearCategoryFilter()
                        } else {
                            viewModel.setCategoryFilter(category)
                        }
                        showCategoryMenu = false
                    },
                    onProfileClick = { showProfileMenu = true },
                    onProfileDismiss = { showProfileMenu = false },
                    onProfileSelected = { profileId ->
                        viewModel.selectProfile(profileId)
                        showProfileMenu = false
                    },
                    onAccountClick = { showAccountMenu = true },
                    onAccountDismiss = { showAccountMenu = false },
                    onAccountSelected = { accountKey ->
                        viewModel.setAccountFilter(accountKey)
                        showAccountMenu = false
                    },
                    onResetFilters = {
                        viewModel.selectPeriod(TimePeriod.THIS_MONTH)
                        viewModel.resetTransactionTypeFilter()
                        viewModel.clearCategoryFilter()
                        viewModel.setAccountFilter(null)
                        if (!isUnifiedMode && primaryVisibleCurrency.isNotBlank()) {
                            viewModel.selectCurrency(primaryVisibleCurrency)
                        }
                        showTypeMenu = false
                        showCurrencyMenu = false
                        showCategoryMenu = false
                        showAccountMenu = false
                    }
                )
            }

            // Hero: the period's total, its change and the periods before it
            if (uiState.totalSpending > BigDecimal.ZERO || uiState.transactionCount > 0) {
                item(key = "hero") {
                    val history = uiState.periodHistory
                    // A calendar-month cycle reads as its month ("November"); a custom
                    // cycle, the FY, all time and a custom range keep their chip label.
                    val heroPeriod = if (history?.kind == PeriodKind.CYCLE && budgetCycleStartDay == 1) {
                        history.periods.last().fullLabel(PeriodKind.CYCLE)
                    } else periodChipLabel
                    val heroLabel = if (transactionTypeFilter.isSpendView()) {
                        stringResource(R.string.analytics_hero_spent, heroPeriod)
                    } else {
                        stringResource(R.string.analytics_hero_type, transactionTypeFilter.label, heroPeriod)
                    }
                    AnalyticsSummaryCard(
                        label = heroLabel,
                        totalAmount = uiState.totalSpending,
                        // The figures' own currency, set with the data in one state.
                        currency = uiState.currency.ifEmpty { selectedCurrency },
                        transactionCount = uiState.transactionCount,
                        averageAmount = uiState.averageAmount,
                        history = history,
                        typeFilter = transactionTypeFilter,
                        isLoading = uiState.isLoading
                    )
                }
            }

            uiState.forecast?.let { forecast ->
                item(key = "forecast") { ForecastCard(forecast) }
            }

            // Where it went: the grouping picked in the header (#422)
            if (hasData) {
                item(key = "breakdown") {
                    BreakdownSection(
                        uiState = uiState,
                        groupBy = groupBy,
                        isProEntitled = isProEntitled,
                        currency = selectedCurrency,
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

            // Top merchants sit under the breakdown unless they are the breakdown.
            if (groupBy != AnalyticsGroupBy.MERCHANT && uiState.topMerchants.isNotEmpty()) {
                item(key = "merchants") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
                        SectionHeaderV2(
                            title = stringResource(R.string.analytics_top_merchants_title),
                            topSpacing = Spacing.none
                        )
                        MerchantRows(
                            merchants = uiState.topMerchants,
                            currency = selectedCurrency,
                            visibleCount = 3,
                            drillDown = drillDown
                        )
                    }
                }
            }

            // The trend inside the period: line, bar or heatmap
            if (uiState.spendingTrend.size >= 2) {
                item(key = "trend") {
                    TrendSection(
                        chartType = chartType,
                        onChartTypeSelected = viewModel::setChartType,
                        data = uiState.spendingTrend,
                        // The trend's own currency, set with the data in one state: the
                        // picker's selectedCurrency flips first, which labelled the old
                        // figures with the new symbol for a frame.
                        currency = uiState.currency.ifEmpty { selectedCurrency }
                    )
                }
            }

            if (askAvailable && hasData) {
                item(key = "ask") {
                    AskCard(history = uiState.periodHistory, onClick = onNavigateToChat)
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
    currency: String,
    categoryViewType: CategoryViewType,
    tagViewType: CategoryViewType,
    onToggleCategoryView: () -> Unit,
    onToggleTagView: () -> Unit,
    onGroupBySelected: (AnalyticsGroupBy) -> Unit,
    drillDown: (TransactionsDrillDown) -> Unit
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        SectionHeaderV2(
            title = stringResource(R.string.analytics_where_it_went),
            topSpacing = Spacing.none,
            action = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupByMenuButton(
                        groupBy = groupBy,
                        isProEntitled = isProEntitled,
                        onSelected = onGroupBySelected
                    )
                    val viewType = when (groupBy) {
                        AnalyticsGroupBy.CATEGORY -> categoryViewType
                        AnalyticsGroupBy.TAG -> tagViewType
                        else -> null
                    }
                    if (viewType != null) {
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
            }
        )

        when (groupBy) {
            AnalyticsGroupBy.CATEGORY -> {
                AnimatedContent(
                    targetState = categoryViewType,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith
                            fadeOut(motion.fastEffectsSpec()) using
                            SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                    },
                    label = "category_view_transition"
                ) { viewType ->
                    when (viewType) {
                        CategoryViewType.CHART -> ChartCard {
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
                    targetState = tagViewType,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith
                            fadeOut(motion.fastEffectsSpec()) using
                            SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                    },
                    label = "tag_view_transition"
                ) { viewType ->
                    when (viewType) {
                        CategoryViewType.CHART -> ChartCard {
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
    onSelected: (AnalyticsGroupBy) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, shapes = ButtonDefaults.shapes()) {
            Text(groupBy.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            RowLabels(
                title = merchantDisplay(merchant.name) ?: merchant.name,
                subtitle = pluralStringResource(
                    if (merchant.isSubscription) R.plurals.analytics_transaction_count_subscription
                    else R.plurals.analytics_transaction_count,
                    merchant.transactionCount,
                    merchant.transactionCount
                ),
                subtitleMaxLines = 1
            )
            Text(
                text = CurrencyFormatter.formatCurrency(merchant.amount, currency),
                style = PennyWiseText.amountRow,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** The in-period trend in a card, with a connected Line · Bar · Heatmap switch. */
@Composable
private fun TrendSection(
    chartType: ChartType,
    onChartTypeSelected: (ChartType) -> Unit,
    data: List<BalancePoint>,
    currency: String
) {
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
                            ChartType.BAR -> stringResource(R.string.analytics_chart_bar)
                            ChartType.HEATMAP -> stringResource(R.string.analytics_chart_heatmap)
                        }
                    }
                )
                // Data swaps fade on effects specs: no full-width slides, no overshoot.
                Crossfade(
                    targetState = chartType,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "chart_transition"
                ) { type ->
                    when (type) {
                        ChartType.LINE -> BalanceChart(
                            primaryCurrency = currency,
                            balanceHistory = data,
                            height = 220
                        )
                        ChartType.BAR -> SpendingBarChart(
                            primaryCurrency = currency,
                            data = data,
                            height = 220
                        )
                        ChartType.HEATMAP -> SpendingHeatmap(data = data)
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

/**
 * The way into Ask: a tonal card with the assistant's mascot and a question
 * worth asking — why the highest of the past periods ran above the rest, when
 * one did.
 */
@Composable
private fun AskCard(history: PeriodHistory?, onClick: () -> Unit) {
    val past = history?.periods?.dropLast(1)?.filter { it.hasData }.orEmpty()
    val peak = past.maxByOrNull { it.amount }
    val average = if (past.isEmpty()) null else past.fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
        .divide(BigDecimal(past.size), 2, java.math.RoundingMode.HALF_UP)
    val question = if (history != null && peak != null && average != null && past.size > 1 && peak.amount > average) {
        stringResource(R.string.analytics_ask_why_higher, peak.fullLabel(history.kind))
    } else {
        stringResource(R.string.analytics_ask_generic)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.Padding.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
        ) {
            Mascot(
                size = Dimensions.Icon.hero,
                shape = MascotShape.Bars,
                // Its eyes follow the period's change: up when spending rose.
                value = when (history?.change?.isUp) {
                    true -> 1f
                    false -> -1f
                    null -> 0f
                }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = stringResource(R.string.analytics_ask_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = question, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}

@Composable
private fun AnalyticsFilterBar(
    transactionTypeFilter: TransactionTypeFilter,
    defaultTypeFilter: TransactionTypeFilter,
    selectedCurrency: String,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    categoryFilter: String?,
    availableCategories: List<String>,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    accountOptions: List<com.pennywiseai.tracker.presentation.common.AccountOption>,
    accountFilter: String?,
    showTypeMenu: Boolean,
    showCurrencyMenu: Boolean,
    showCategoryMenu: Boolean,
    showProfileMenu: Boolean,
    showAccountMenu: Boolean,
    hasActiveFilter: Boolean,
    onTypeClick: () -> Unit,
    onTypeDismiss: () -> Unit,
    onTypeSelected: (TransactionTypeFilter) -> Unit,
    onCurrencyClick: () -> Unit,
    onCurrencyDismiss: () -> Unit,
    onCurrencySelected: (String) -> Unit,
    onCategoryClick: () -> Unit,
    onCategoryDismiss: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onProfileClick: () -> Unit,
    onProfileDismiss: () -> Unit,
    onProfileSelected: (Long?) -> Unit,
    onAccountClick: () -> Unit,
    onAccountDismiss: () -> Unit,
    onAccountSelected: (String?) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (hasActiveFilter) {
            item {
                AssistChip(
                    onClick = onResetFilters,
                    label = { Text(stringResource(R.string.analytics_filter_clear)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.small)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                        leadingIconContentColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        }

        item {
            Box {
                ExpressiveFilterChip(
                    colors = analyticsFilterChipColors(),
                    border = analyticsFilterChipBorder(
                        selected = transactionTypeFilter != defaultTypeFilter
                    ),
                    selected = transactionTypeFilter != defaultTypeFilter,
                    text = transactionTypeFilter.shortLabel(),
                    icon = transactionTypeFilter.filterIcon(),
                    onClick = onTypeClick
                )

                DropdownMenu(
                    expanded = showTypeMenu,
                    onDismissRequest = onTypeDismiss,
                    shape = MaterialTheme.shapes.large
                ) {
                    TransactionTypeFilter.entries.forEachIndexed { index, typeFilter ->
                        SelectableDropdownMenuItem(
                            selected = transactionTypeFilter == typeFilter,
                            text = { Text(typeFilter.label) },
                            shapes = MenuDefaults.itemShape(index, TransactionTypeFilter.entries.size),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            leadingIcon = { Icon(typeFilter.filterIcon(), contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { onTypeSelected(typeFilter) }
                        )
                    }
                }
            }
        }

        // Multi-currency: one currency at a time, never summed across (unless unified mode converts).
        if (availableCurrencies.size > 1 && !isUnifiedMode) {
            item {
                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(
                            selected = selectedCurrency != availableCurrencies.firstOrNull()
                        ),
                        selected = selectedCurrency != availableCurrencies.firstOrNull(),
                        text = selectedCurrency.ifBlank { stringResource(R.string.analytics_filter_currency) },
                        icon = Icons.Default.CurrencyExchange,
                        onClick = onCurrencyClick
                    )

                    DropdownMenu(
                        expanded = showCurrencyMenu,
                        onDismissRequest = onCurrencyDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        availableCurrencies.forEachIndexed { index, currency ->
                            SelectableDropdownMenuItem(
                                selected = selectedCurrency == currency,
                                text = { Text(currency) },
                                shapes = MenuDefaults.itemShape(index, availableCurrencies.size),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = { onCurrencySelected(currency) }
                            )
                        }
                    }
                }
            }
        }

        if (profiles.isNotEmpty()) {
            item {
                val selectedProfileLabel = profiles.find { it.id == selectedProfileId }?.displayName()
                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(selected = selectedProfileId != null),
                        selected = selectedProfileId != null,
                        text = selectedProfileLabel ?: stringResource(R.string.analytics_filter_all_profiles),
                        icon = profileFilterIcon(profiles, selectedProfileId),
                        onClick = onProfileClick
                    )

                    ProfileFilterDropdown(
                        expanded = showProfileMenu,
                        profiles = profiles,
                        selectedProfileId = selectedProfileId,
                        onProfileSelected = onProfileSelected,
                        onDismiss = onProfileDismiss
                    )
                }
            }
        }

        if (availableCategories.isNotEmpty() || categoryFilter != null) {
            item {
                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(selected = categoryFilter != null),
                        selected = categoryFilter != null,
                        text = categoryFilter?.let { categoryLabel(it) } ?: stringResource(R.string.analytics_filter_category),
                        icon = Icons.Default.Category,
                        onClick = onCategoryClick
                    )

                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = onCategoryDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        SelectableDropdownMenuItem(
                            selected = categoryFilter == null,
                            text = { Text(stringResource(R.string.analytics_filter_all_categories)) },
                            shapes = MenuDefaults.itemShape(0, availableCategories.size + 1),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { onCategorySelected(null) }
                        )

                        availableCategories.forEachIndexed { index, category ->
                            SelectableDropdownMenuItem(
                                selected = categoryFilter == category,
                                text = {
                                    Text(
                                        categoryLabel(category),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                shapes = MenuDefaults.itemShape(index + 1, availableCategories.size + 1),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                leadingIcon = {
                                    CategoryIcon(
                                        category = category,
                                        size = Dimensions.Icon.small
                                    )
                                },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }
            }
        }

        if (accountOptions.isNotEmpty()) {
            item {
                val selectedAccountLabel =
                    accountOptions.firstOrNull { it.key == accountFilter }?.label
                Box {
                    ExpressiveFilterChip(
                        colors = analyticsFilterChipColors(),
                        border = analyticsFilterChipBorder(selected = accountFilter != null),
                        selected = accountFilter != null,
                        text = selectedAccountLabel ?: stringResource(R.string.analytics_filter_account),
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = onAccountClick
                    )

                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = onAccountDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        SelectableDropdownMenuItem(
                            selected = accountFilter == null,
                            text = { Text(stringResource(R.string.analytics_filter_all_accounts)) },
                            shapes = MenuDefaults.itemShape(0, accountOptions.size + 1),
                            colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                            selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                            onClick = { onAccountSelected(null) }
                        )
                        accountOptions.forEachIndexed { index, option ->
                            SelectableDropdownMenuItem(
                                selected = accountFilter == option.key,
                                text = {
                                    Text(
                                        option.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                shapes = MenuDefaults.itemShape(index + 1, accountOptions.size + 1),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                selectedLeadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = { onAccountSelected(option.key) }
                            )
                        }
                    }
                }
            }
        }
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
private fun analyticsFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
)

@Composable
private fun analyticsFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    selected = selected,
    enabled = true,
    borderWidth = Dimensions.Elevation.none,
    selectedBorderWidth = Dimensions.Elevation.none
)

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
