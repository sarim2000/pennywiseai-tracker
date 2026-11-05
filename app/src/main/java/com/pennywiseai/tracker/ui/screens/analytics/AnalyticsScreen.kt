package com.pennywiseai.tracker.ui.screens.analytics

import com.pennywiseai.tracker.utils.AmountPrivacy
import androidx.compose.ui.res.pluralStringResource
import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.chipLabel
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.cards.ListItemCardV2
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.filterIcon
import com.pennywiseai.tracker.ui.components.shortLabel
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.DateRangeUtils
import androidx.compose.ui.graphics.Color
import java.math.BigDecimal
import com.pennywiseai.tracker.ui.icons.categoryDisplayColor
import com.pennywiseai.tracker.ui.icons.categoryLabel
import kotlin.math.roundToInt

private enum class CategoryViewType { CHART, LIST }

/** Trend chart height at 100% font; it grows with the font so axis labels keep their room. */
private const val TREND_CHART_HEIGHT = 220

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel(),
    onNavigateToChat: () -> Unit = {},
    onNavigateToTransactions: (TransactionsDrillDown) -> Unit = {},
    onNavigateToHome: () -> Unit = {}
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
    var categoryViewType by rememberSaveable { mutableStateOf(CategoryViewType.CHART) }
    var tagViewType by rememberSaveable { mutableStateOf(CategoryViewType.CHART) }
    var showChartTypeSelector by remember { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showGroupByMenu by remember { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // Cache expensive operations
    val timePeriods = remember { TimePeriod.values().toList() }
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
    // What the Filters chip hides that narrows the data. Period and type keep their own
    // always-visible chips, and the grouping is a view choice rather than a filter.
    val secondaryFilterCount = listOf(
        selectedProfileId != null,
        hasCurrencyFilter,
        categoryFilter != null,
        accountFilter != null
    ).count { it }
    // Keyed rather than an initial value: the profile arrives from preferences after the
    // first frame. The user can still collapse the row afterwards.
    LaunchedEffect(secondaryFilterCount > 0) {
        if (secondaryFilterCount > 0) filtersExpanded = true
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.analytics_title),
            )
        }
    ) { paddingValues ->
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            top = paddingValues.calculateTopPadding() + Spacing.md,
            // Scaffold's bottom padding is the system bar the nav sits above.
            bottom = paddingValues.calculateBottomPadding() +
                Dimensions.Component.bottomBarHeight + Spacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        flingBehavior = rememberOverscrollFlingBehavior { listState }
    ) {
        item {
            AnalyticsFilterBar(
                selectedPeriod = selectedPeriod,
                customRangeLabel = customRangeLabel,
                periodChipLabel = periodChipLabel,
                timePeriods = timePeriods,
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
                groupBy = groupBy,
                isProEntitled = isProEntitled,
                showGroupByMenu = showGroupByMenu,
                showPeriodMenu = showPeriodMenu,
                showTypeMenu = showTypeMenu,
                showCurrencyMenu = showCurrencyMenu,
                showCategoryMenu = showCategoryMenu,
                showProfileMenu = showProfileMenu,
                showAccountMenu = showAccountMenu,
                hasActiveFilter = hasActiveAnalyticsFilter,
                filtersExpanded = filtersExpanded,
                secondaryFilterCount = secondaryFilterCount,
                onFiltersToggle = { filtersExpanded = !filtersExpanded },
                onGroupByClick = { showGroupByMenu = true },
                onGroupByDismiss = { showGroupByMenu = false },
                onGroupBySelected = { option ->
                    showGroupByMenu = false
                    // A Pro grouping without Pro opens the upgrade sheet; the view stays put.
                    if (option.allowedFor(isProEntitled) == option) {
                        viewModel.setGroupBy(option)
                    } else {
                        showUpgradeSheet = true
                    }
                },
                onPeriodClick = { showPeriodMenu = true },
                onPeriodDismiss = { showPeriodMenu = false },
                onPeriodSelected = { period ->
                    if (period == TimePeriod.CUSTOM) {
                        showDateRangePicker = true
                    } else {
                        viewModel.selectPeriod(period)
                    }
                    showPeriodMenu = false
                },
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
                    showPeriodMenu = false
                    showTypeMenu = false
                    showCurrencyMenu = false
                    showCategoryMenu = false
                    showAccountMenu = false
                }
            )
        }

        // Analytics Summary Card
        if (uiState.totalSpending > BigDecimal.ZERO || uiState.transactionCount > 0) {
            item {
                AnalyticsSummaryCard(
                    scopeLabel = periodChipLabel,
                    totalAmount = uiState.totalSpending,
                    transactionCount = uiState.transactionCount,
                    averageAmount = uiState.averageAmount,
                    topCategory = uiState.topCategory,
                    topCategoryPercentage = uiState.topCategoryPercentage,
                    currency = uiState.currency,
                    isLoading = uiState.isLoading,
                    forecast = uiState.forecast
                )
            }
        }

        // Chart Section with Type Selector. A flat zero series has nothing to plot.
        if (uiState.spendingTrend.size >= 2 && uiState.spendingTrend.any { it.balance.signum() != 0 }) {
            item {
                val trendChartHeight =
                    (TREND_CHART_HEIGHT * LocalDensity.current.fontScale.coerceIn(1f, 2f)).roundToInt()
                // What the line actually plots: the selected type, never a balance.
                val trendLabel = stringResource(
                    if (transactionTypeFilter == TransactionTypeFilter.ALL) R.string.analytics_trends
                    else transactionTypeFilter.labelRes
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = trendLabel,
                        action = {
                            TextButton(
                                onClick = { showChartTypeSelector = !showChartTypeSelector },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = Spacing.smd, vertical = Spacing.xs),
                                shapes = pennyWiseButtonShapes()
                            ) {
                                Icon(
                                    imageVector = when (chartType) {
                                        ChartType.LINE -> Icons.AutoMirrored.Outlined.ShowChart
                                        ChartType.BAR -> Icons.Outlined.BarChart
                                        ChartType.HEATMAP -> Icons.Outlined.GridView
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(
                                    text = when (chartType) {
                                        ChartType.LINE -> stringResource(R.string.analytics_chart_line)
                                        ChartType.BAR -> stringResource(R.string.analytics_chart_bar)
                                        ChartType.HEATMAP -> stringResource(R.string.analytics_chart_heatmap)
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )

                    // Expandable chart type selector card
                    AnimatedVisibility(visible = showChartTypeSelector) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.sm),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            ChartType.entries.forEach { type ->
                                val isSelected = chartType == type
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.inverseSurface
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.setChartType(type)
                                            showChartTypeSelector = false
                                        }
                                        .padding(horizontal = Spacing.md, vertical = Dimensions.Padding.listRowVertical),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (type) {
                                                ChartType.LINE -> Icons.AutoMirrored.Outlined.ShowChart
                                                ChartType.BAR -> Icons.Outlined.BarChart
                                                ChartType.HEATMAP -> Icons.Outlined.GridView
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected)
                                                MaterialTheme.colorScheme.inverseOnSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = when (type) {
                                                ChartType.LINE -> stringResource(R.string.analytics_chart_line_full)
                                                ChartType.BAR -> stringResource(R.string.analytics_chart_bar_full)
                                                ChartType.HEATMAP -> stringResource(R.string.analytics_chart_heatmap)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isSelected)
                                                MaterialTheme.colorScheme.inverseOnSurface
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.size(Dimensions.Icon.medium)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chart display with crossfade transition. Bars and lines give amounts away.
                    if (AmountPrivacy.hidden) {
                        Text(
                            text = stringResource(R.string.analytics_hidden_chart),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.md)
                        )
                    } else Crossfade(
                        targetState = chartType,
                        label = "chart_transition"
                    ) { type ->
                        when (type) {
                            ChartType.LINE -> BalanceChart(
                                primaryCurrency = selectedCurrency,
                                balanceHistory = uiState.spendingTrend,
                                height = trendChartHeight,
                                seriesLabel = null
                            )
                            ChartType.BAR -> SpendingBarChart(
                                primaryCurrency = selectedCurrency,
                                data = uiState.spendingTrend,
                                height = trendChartHeight
                            )
                            ChartType.HEATMAP -> SpendingHeatmap(
                                data = uiState.spendingTrend
                            )
                        }
                    }
                }
            }
        }

        // Category Breakdown Section with Pie/List toggle
        if (groupBy == AnalyticsGroupBy.CATEGORY && uiState.categoryBreakdown.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = stringResource(R.string.analytics_top_categories),
                        action = {
                            IconButton(onClick = {
                                categoryViewType = if (categoryViewType == CategoryViewType.CHART) {
                                    CategoryViewType.LIST
                                } else {
                                    CategoryViewType.CHART
                                }
                            }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    imageVector = if (categoryViewType == CategoryViewType.CHART)
                                        Icons.AutoMirrored.Outlined.List
                                    else Icons.Outlined.PieChart,
                                    contentDescription = stringResource(R.string.analytics_toggle_view),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    // Animated content swap
                    AnimatedContent(
                        targetState = categoryViewType,
                        transitionSpec = {
                            if (targetState == CategoryViewType.CHART) {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut()) using
                                    SizeTransform(clip = false)
                            } else {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut()) using
                                    SizeTransform(clip = false)
                            }
                        },
                        label = "category_view_transition"
                    ) { viewType ->
                        when (viewType) {
                            CategoryViewType.CHART -> CategoryPieChart(
                                categories = uiState.categoryBreakdown,
                                currency = selectedCurrency,
                                onCategoryClick = { category ->
                                    drillDown(TransactionsDrillDown(category = category.name))
                                }
                            )
                            CategoryViewType.LIST -> CategoryBreakdownCard(
                                categories = uiState.categoryBreakdown,
                                currency = selectedCurrency,
                                onCategoryClick = { category ->
                                    drillDown(TransactionsDrillDown(category = category.name))
                                }
                            )
                        }
                    }

                    // Explain why the bars can sum above the headline total: refunds are
                    // netted in full off the total, but a category bar can't go negative (#704).
                    if (uiState.refundNettedFromTotal > BigDecimal.ZERO) {
                        Text(
                            text = stringResource(
                                R.string.analytics_refund_note,
                                CurrencyFormatter.formatCurrency(uiState.refundNettedFromTotal, selectedCurrency)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.sm)
                        )
                    }
                }
            }
        }

        // Tag Breakdown Section with Pie/List toggle
        if (groupBy == AnalyticsGroupBy.TAG && uiState.tagBreakdown.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(
                        title = stringResource(R.string.analytics_top_tags),
                        action = {
                            IconButton(onClick = {
                                tagViewType = if (tagViewType == CategoryViewType.CHART) {
                                    CategoryViewType.LIST
                                } else {
                                    CategoryViewType.CHART
                                }
                            }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    imageVector = if (tagViewType == CategoryViewType.CHART)
                                        Icons.AutoMirrored.Outlined.List
                                    else Icons.Outlined.PieChart,
                                    contentDescription = stringResource(R.string.analytics_toggle_view),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    AnimatedContent(
                        targetState = tagViewType,
                        transitionSpec = {
                            if (targetState == CategoryViewType.CHART) {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { it } + fadeOut()) using
                                    SizeTransform(clip = false)
                            } else {
                                (slideInHorizontally { it } + fadeIn()) togetherWith
                                    (slideOutHorizontally { -it } + fadeOut()) using
                                    SizeTransform(clip = false)
                            }
                        },
                        label = "tag_view_transition"
                    ) { viewType ->
                        when (viewType) {
                            CategoryViewType.CHART -> TagPieChart(
                                tags = uiState.tagBreakdown,
                                currency = selectedCurrency,
                                onTagClick = { tag ->
                                    if (!tag.isUntagged) drillDown(TransactionsDrillDown(tag = tag.name))
                                }
                            )
                            CategoryViewType.LIST -> TagBreakdownCard(
                                tags = uiState.tagBreakdown,
                                currency = selectedCurrency,
                                onTagClick = { tag ->
                                    drillDown(TransactionsDrillDown(tag = tag.name))
                                }
                            )
                        }
                    }

                    if (uiState.tagsOverlap) {
                        Text(
                            text = stringResource(R.string.analytics_tag_overlap_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.sm)
                        )
                    }
                }
            }
        }

        // Day-of-week breakdown: there's no Transactions filter for a weekday, so rows don't drill in.
        if (groupBy == AnalyticsGroupBy.DAY_OF_WEEK && uiState.dayOfWeekBreakdown.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeaderV2(title = stringResource(R.string.analytics_by_day_of_week))
                    val barColor = MaterialTheme.colorScheme.primary
                    TagBreakdownCard(
                        tags = uiState.dayOfWeekBreakdown,
                        currency = selectedCurrency,
                        title = null,
                        visibleItemCount = uiState.dayOfWeekBreakdown.size,
                        barColor = { barColor },
                        onTagClick = null
                    )
                }
            }
        }

        // Top Merchants Section
        if (groupBy == AnalyticsGroupBy.MERCHANT && uiState.topMerchants.isNotEmpty()) {
            item {
                SectionHeaderV2(
                    title = stringResource(R.string.analytics_top_merchants)
                )
            }

            // All Merchants with expandable list
            item {
                ExpandableList(
                    items = uiState.topMerchants,
                    visibleItemCount = 3,
                    modifier = Modifier.fillMaxWidth()
                ) { merchant ->
                    MerchantListItem(
                        merchant = merchant,
                        currency = selectedCurrency,
                        onClick = {
                            drillDown(TransactionsDrillDown(merchant = merchant.name))
                        }
                    )
                }
            }
        }


        // By Account Section
        if (groupBy == AnalyticsGroupBy.ACCOUNT && uiState.accountBreakdown.isNotEmpty()) {
            item {
                SectionHeaderV2(
                    title = stringResource(R.string.analytics_by_account)
                )
            }

            item {
                ExpandableList(
                    items = uiState.accountBreakdown,
                    visibleItemCount = 3,
                    modifier = Modifier.fillMaxWidth()
                ) { account ->
                    AccountBreakdownListItem(
                        account = account,
                        currency = selectedCurrency,
                        onClick = { drillDown(TransactionsDrillDown(account = account.key)) }
                    )
                }
            }
        }

        // Empty state
        if (uiState.topMerchants.isEmpty() && uiState.categoryBreakdown.isEmpty() && !uiState.isLoading) {
            item {
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

@Composable
private fun AnalyticsFilterBar(
    selectedPeriod: TimePeriod,
    customRangeLabel: String?,
    periodChipLabel: String,
    timePeriods: List<TimePeriod>,
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
    groupBy: AnalyticsGroupBy,
    isProEntitled: Boolean,
    showGroupByMenu: Boolean,
    showPeriodMenu: Boolean,
    showTypeMenu: Boolean,
    showCurrencyMenu: Boolean,
    showCategoryMenu: Boolean,
    showProfileMenu: Boolean,
    showAccountMenu: Boolean,
    hasActiveFilter: Boolean,
    filtersExpanded: Boolean,
    secondaryFilterCount: Int,
    onFiltersToggle: () -> Unit,
    onGroupByClick: () -> Unit,
    onGroupByDismiss: () -> Unit,
    onGroupBySelected: (AnalyticsGroupBy) -> Unit,
    onPeriodClick: () -> Unit,
    onPeriodDismiss: () -> Unit,
    onPeriodSelected: (TimePeriod) -> Unit,
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
    Column(modifier = modifier.fillMaxWidth()) {
        FilterChipRow {
            run {
                Box {
                    ExpressiveFilterChip(
                        selected = selectedPeriod != TimePeriod.THIS_MONTH || customRangeLabel != null,
                        text = periodChipLabel,
                        onClick = onPeriodClick
                    )

                    DropdownMenu(
                        expanded = showPeriodMenu,
                        onDismissRequest = onPeriodDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        timePeriods.forEachIndexed { index, period ->
                            SelectableDropdownMenuItem(
                                selected = selectedPeriod == period,
                                text = { Text(period.label) },
                                shapes = MenuDefaults.itemShape(index, timePeriods.size),
                                colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                onClick = { onPeriodSelected(period) }
                            )
                        }
                    }
                }
            }

            run {
                Box {
                    ExpressiveFilterChip(
                        selected = transactionTypeFilter != defaultTypeFilter,
                        text = transactionTypeFilter.shortLabel(),
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
                                selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                onClick = { onTypeSelected(typeFilter) }
                            )
                        }
                    }
                }
            }

            val filtersState = stringResource(
                if (filtersExpanded) R.string.analytics_filters_expanded else R.string.analytics_filters_collapsed
            )
            ExpressiveFilterChip(
                selected = secondaryFilterCount > 0,
                text = if (secondaryFilterCount > 0) {
                    stringResource(R.string.analytics_filters_count, secondaryFilterCount)
                } else {
                    stringResource(R.string.analytics_filters)
                },
                onClick = onFiltersToggle,
                modifier = Modifier.semantics { stateDescription = filtersState }
            )
        }

        AnimatedVisibility(
            visible = filtersExpanded,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
        ) {
            FilterChipRow {
                // Group by (#422) — a view choice rather than a filter, so Clear leaves it alone.
                run {
                    Box {
                        ExpressiveFilterChip(
                            selected = groupBy != AnalyticsGroupBy.CATEGORY,
                            text = groupBy.chipLabel(),
                            onClick = onGroupByClick
                        )

                        DropdownMenu(
                            expanded = showGroupByMenu,
                            onDismissRequest = onGroupByDismiss,
                            shape = MaterialTheme.shapes.large
                        ) {
                            AnalyticsGroupBy.entries.forEachIndexed { index, option ->
                                SelectableDropdownMenuItem(
                                    selected = groupBy == option,
                                    text = { Text(option.label()) },
                                    shapes = MenuDefaults.itemShape(index, AnalyticsGroupBy.entries.size),
                                    colors = MenuDefaults.selectableItemColors(containerColor = Color.Transparent),
                                    leadingIcon = { Icon(option.icon(), contentDescription = null) },
                                    selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                    // Subtle Pro hint for free users; picking it opens the upgrade sheet.
                                    trailingContent = if (option.isPro && !isProEntitled) {
                                        {
                                            Icon(
                                                Icons.Outlined.Lock,
                                                contentDescription = stringResource(R.string.analytics_group_by_pro),
                                                tint = yellow_dark,
                                                modifier = Modifier.size(Dimensions.Icon.small)
                                            )
                                        }
                                    } else null,
                                    onClick = { onGroupBySelected(option) }
                                )
                            }
                        }
                    }
                }

                if (profiles.isNotEmpty()) {
                    run {
                        val selectedProfileLabel = profiles.find { it.id == selectedProfileId }?.displayName()
                        Box {
                            ExpressiveFilterChip(
                                selected = selectedProfileId != null,
                                text = selectedProfileLabel ?: stringResource(R.string.analytics_filter_all_profiles),
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

                if (availableCurrencies.size > 1 && !isUnifiedMode) {
                    run {
                        Box {
                            ExpressiveFilterChip(
                                selected = selectedCurrency != availableCurrencies.firstOrNull(),
                                text = selectedCurrency.ifBlank { stringResource(R.string.analytics_filter_currency) },
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
                                        selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                        onClick = { onCurrencySelected(currency) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (availableCategories.isNotEmpty() || categoryFilter != null) {
                    run {
                        Box {
                            ExpressiveFilterChip(
                                selected = categoryFilter != null,
                                text = categoryFilter?.let { categoryLabel(it) } ?: stringResource(R.string.analytics_filter_category),
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
                                    leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                                    selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
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
                                                size = Dimensions.Icon.small,
                                                tint = categoryDisplayColor(category)
                                            )
                                        },
                                        selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                        onClick = { onCategorySelected(category) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (accountOptions.isNotEmpty()) {
                    run {
                        val selectedAccountLabel =
                            accountOptions.firstOrNull { it.key == accountFilter }?.label
                        Box {
                            ExpressiveFilterChip(
                                selected = accountFilter != null,
                                text = selectedAccountLabel ?: stringResource(R.string.analytics_filter_account),
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
                                    leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                                    selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
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
                                        leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                                        selectedLeadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                                        onClick = { onAccountSelected(option.key) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasActiveFilter) {
                    run {
                        AssistChip(
                            onClick = onResetFilters,
                            label = { Text(stringResource(R.string.analytics_filter_clear)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.Icon.small)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null
                        )
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

/** The chip's wording: says it groups the view, so it can't be mistaken for the Category filter. */
@Composable
private fun AnalyticsGroupBy.chipLabel(): String = stringResource(
    when (this) {
        AnalyticsGroupBy.CATEGORY -> R.string.analytics_group_chip_category
        AnalyticsGroupBy.MERCHANT -> R.string.analytics_group_chip_merchant
        AnalyticsGroupBy.ACCOUNT -> R.string.analytics_group_chip_account
        AnalyticsGroupBy.TAG -> R.string.analytics_group_chip_tag
        AnalyticsGroupBy.DAY_OF_WEEK -> R.string.analytics_group_chip_day_of_week
    }
)

private fun AnalyticsGroupBy.icon() = when (this) {
    AnalyticsGroupBy.CATEGORY -> Icons.Outlined.Category
    AnalyticsGroupBy.MERCHANT -> Icons.Outlined.Store
    AnalyticsGroupBy.ACCOUNT -> Icons.Outlined.AccountBalanceWallet
    AnalyticsGroupBy.TAG -> Icons.Outlined.Sell
    AnalyticsGroupBy.DAY_OF_WEEK -> Icons.Outlined.DateRange
}



@Composable
private fun CategoryListItem(
    category: CategoryData,
    currency: String
) {
    val categoryInfo = CategoryMapping.categories[category.name]
        ?: CategoryMapping.categories["Others"]!!

    ListItemCardV2(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatar)
                    .clip(CircleShape)
                    .background(CategoryMapping.colorFor(category.name).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                CategoryIcon(
                    category = category.name,
                    size = Dimensions.Icon.medium,
                    tint = CategoryMapping.colorFor(category.name)
                )
            }
        },
        title = categoryLabel(category.name),
        subtitle = pluralStringResource(
            R.plurals.analytics_transaction_count,
            category.transactionCount,
            category.transactionCount
        ),
        amount = CurrencyFormatter.formatCurrency(category.amount, currency),
        trailingContent = {
            Text(
                text = if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.analytics_percent, category.percentage.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun MerchantListItem(
    merchant: MerchantData,
    currency: String,
    onClick: () -> Unit = {}
) {
    val subtitle = pluralStringResource(
        if (merchant.isSubscription) R.plurals.analytics_transaction_count_subscription
        else R.plurals.analytics_transaction_count,
        merchant.transactionCount,
        merchant.transactionCount
    )

    // Brand icon stays keyed on the raw merchant; only the label uses the alias (#583).
    val merchantDisplay = LocalMerchantDisplay.current

    ListItemCardV2(
        leadingContent = {
            BrandIcon(
                merchantName = merchant.name,
                size = Dimensions.Icon.avatarLarge,
                showBackground = true
            )
        },
        title = merchantDisplay(merchant.name) ?: merchant.name,
        subtitle = subtitle,
        amount = CurrencyFormatter.formatCurrency(merchant.amount, currency),
        onClick = onClick
    )
}

@Composable
private fun AccountBreakdownListItem(
    account: AccountBreakdownData,
    currency: String,
    onClick: () -> Unit
) {
    ListItemCardV2(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.avatar)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        },
        title = account.label,
        subtitle = pluralStringResource(
            R.plurals.analytics_transaction_count,
            account.transactionCount,
            account.transactionCount
        ),
        amount = CurrencyFormatter.formatCurrency(account.amount, currency),
        onClick = onClick,
        // Amount and share: a trailing slot replaces the card's own amount text.
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatCurrency(account.amount, currency),
                    style = PennyWiseText.amountRow,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.analytics_percent, account.percentage.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
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
            icon = Icons.AutoMirrored.Outlined.ShowChart,
            headline = stringResource(R.string.analytics_empty_headline),
            description = stringResource(R.string.analytics_empty_description),
            actionLabel = stringResource(R.string.analytics_empty_action),
            onAction = onScanSmsClick
        )
    }
}
