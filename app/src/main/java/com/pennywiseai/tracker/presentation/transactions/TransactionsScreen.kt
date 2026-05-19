package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.components.profileIcon
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    initialCategory: String? = null,
    initialMerchant: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null,
    focusSearch: Boolean = false,
    // New parameters for budget navigation
    initialStartDateEpochDay: Long? = null,
    initialEndDateEpochDay: Long? = null,
    initialCategories: String? = null,  // Comma-separated category names
    initialTransactionType: String? = null,
    viewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onAddTransactionClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val categoriesFilter by viewModel.categoriesFilter.collectAsState()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val categoriesMap by viewModel.categories.collectAsState()
    val filteredTotals by viewModel.filteredTotals.collectAsState()
    val availableCurrencies by viewModel.availableCurrencies.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsState()
    val convertedAmounts by viewModel.convertedAmounts.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profileAccountKeys by viewModel.profileAccountKeys.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) } // Menu doesn't need saving
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showMoreFiltersMenu by remember { mutableStateOf(false) }
    
    // Focus management for search field
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val view = LocalView.current

    val primaryVisibleCurrency = availableCurrencies.firstOrNull() ?: selectedCurrency
    val hasCurrencyFilter = !isUnifiedMode &&
            availableCurrencies.size > 1 &&
            !selectedCurrency.equals(primaryVisibleCurrency, ignoreCase = true)
    
    // Check if any filter is active (for showing "Clear all" button)
    val hasAnyActiveFilter = searchQuery.isNotEmpty() ||
        selectedPeriod != TimePeriod.THIS_MONTH ||
        categoryFilter != null ||
        categoriesFilter != null ||
        transactionTypeFilter != TransactionTypeFilter.ALL ||
        selectedProfileId != null ||
        hasCurrencyFilter ||
        customDateRange != null

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val collapseThresholdPx = with(density) { 48.dp.roundToPx() }
    val collapseTransactionHeader by remember(collapseThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }

    // Cache expensive operations
    val timePeriods = remember { TimePeriod.values().toList() }
    val customRangeLabel = remember(customDateRange) {
        DateRangeUtils.formatDateRange(customDateRange)
    }
    
    // Apply initial filters only once when screen is first created
    LaunchedEffect(Unit) {
        viewModel.applyInitialFilters(
            initialCategory,
            initialMerchant,
            initialPeriod,
            initialCurrency
        )
    }

    // Track if we've already processed these specific nav params
    var processedNavParams by rememberSaveable { mutableStateOf(false) }

    // Apply navigation filters only ONCE when actually navigating (not when returning from detail)
    LaunchedEffect(initialCategory, initialMerchant, initialPeriod, initialCurrency) {
        if (!processedNavParams && (initialCategory != null || initialMerchant != null || initialPeriod != null || initialCurrency != null)) {
            viewModel.applyNavigationFilters(
                initialCategory,
                initialMerchant,
                initialPeriod,
                initialCurrency
            )
            processedNavParams = true
        }
    }

    // Apply budget filters when navigating from budget screen
    LaunchedEffect(initialStartDateEpochDay, initialEndDateEpochDay, initialCategories, initialTransactionType) {
        if (initialStartDateEpochDay != null && initialEndDateEpochDay != null) {
            viewModel.applyBudgetFilters(
                startDateEpochDay = initialStartDateEpochDay,
                endDateEpochDay = initialEndDateEpochDay,
                currency = initialCurrency,
                categories = initialCategories,
                transactionType = initialTransactionType
            )
        }
    }
    
    // Handle delete undo snackbar
    LaunchedEffect(deletedTransaction) {
        deletedTransaction?.let { transaction ->
            // Clear the state immediately to prevent re-triggering
            viewModel.clearDeletedTransaction()
            
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Transaction deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Pass the transaction directly since state is already cleared
                    viewModel.undoDeleteTransaction(transaction)
                }
            }
        }
    }
    
    // Focus search field if requested
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "Transactions",
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                hazeState = hazeState
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Export FAB (only show if transactions exist)
                if (uiState.transactions.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = { showExportDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export to CSV",
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    }
                }
                
                // Add Transaction FAB (consistent with Home screen)
                SmallFloatingActionButton(
                    onClick = onAddTransactionClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
        TransactionFilterHeader(
            searchQuery = searchQuery,
            categoryFilter = categoryFilter,
            selectedPeriod = selectedPeriod,
            customRangeLabel = customRangeLabel,
            transactionTypeFilter = transactionTypeFilter,
            categoryLabel = categoryFilter ?: categoriesFilter?.joinToString(", "),
            hasCategoryFilter = categoryFilter != null || categoriesFilter != null,
            selectedProfileName = profiles.firstOrNull { it.id == selectedProfileId }?.name ?: "Profile",
            hasProfileFilter = selectedProfileId != null,
            hasAnyActiveFilter = hasAnyActiveFilter,
            showSortMenu = showSortMenu,
            showPeriodMenu = showPeriodMenu,
            showTypeMenu = showTypeMenu,
            showMoreFiltersMenu = showMoreFiltersMenu,
            collapsed = collapseTransactionHeader && !showPeriodMenu && !showTypeMenu && !showMoreFiltersMenu,
            sortOption = sortOption,
            timePeriods = timePeriods,
            availableCategories = availableCategories,
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onSortClick = { showSortMenu = true },
            onSortDismiss = { showSortMenu = false },
            onSortSelected = { option ->
                viewModel.setSortOption(option)
                showSortMenu = false
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
            onTransactionTypeSelected = { typeFilter ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.setTransactionTypeFilter(typeFilter)
                showTypeMenu = false
            },
            onMoreFiltersClick = { showMoreFiltersMenu = true },
            onMoreFiltersDismiss = { showMoreFiltersMenu = false },
            onCategorySelected = { category ->
                if (category == null) {
                    viewModel.clearCategoryFilter()
                } else {
                    viewModel.setCategoryFilter(category)
                }
                showMoreFiltersMenu = false
            },
            onProfileSelected = { profileId ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.setSelectedProfile(profileId)
                showMoreFiltersMenu = false
            },
            onResetFilters = viewModel::resetFilters,
            focusRequester = searchFocusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Spacing.sm)
        )
        
        // Transaction List
        when {
            uiState.isLoading -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Spacing.md,
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    item {
                        TransactionTotalsCard(
                            income = filteredTotals.income,
                            expenses = filteredTotals.expenses,
                            netBalance = filteredTotals.netBalance,
                            currency = selectedCurrency,
                            availableCurrencies = availableCurrencies,
                            onCurrencySelected = { viewModel.selectCurrency(it) },
                            isUnifiedMode = isUnifiedMode,
                            isLoading = true,
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                    items(8) {
                        TransactionItemSkeleton()
                    }
                }
            }
            uiState.transactions.isEmpty() -> {
                EmptyTransactionsState(
                    searchQuery = searchQuery,
                    selectedPeriod = selectedPeriod,
                    onAddClick = onAddTransactionClick
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().overScrollVertical(),
                    contentPadding = PaddingValues(
                        start = Dimensions.Padding.content,
                        end = Dimensions.Padding.content,
                        top = Spacing.md,
                        bottom = paddingValues.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    flingBehavior = rememberOverscrollFlingBehavior { listState }
                ) {
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TransactionTotalsCard(
                                income = filteredTotals.income,
                                expenses = filteredTotals.expenses,
                                netBalance = filteredTotals.netBalance,
                                currency = selectedCurrency,
                                availableCurrencies = availableCurrencies,
                                onCurrencySelected = { viewModel.selectCurrency(it) },
                                isUnifiedMode = isUnifiedMode,
                                isLoading = uiState.isLoading,
                                modifier = Modifier.padding(bottom = Spacing.sm)
                            )
                        }
                    }

                    // Show info banner when viewing budget transactions
                    if (categoriesFilter != null) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = Spacing.sm)
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.sm),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Totals may differ from budget due to split transactions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Iterate through date groups in order
                    listOf(
                        DateGroup.TODAY,
                        DateGroup.YESTERDAY,
                        DateGroup.THIS_WEEK,
                        DateGroup.EARLIER
                    ).forEach { dateGroup ->
                        uiState.groupedTransactions[dateGroup]?.let { transactions ->
                            // Date group header
                            stickyHeader {
                                TransactionDateHeader(title = dateGroup.label)
                            }
                            
                            // Transactions in this group
                            itemsIndexed(
                                items = transactions,
                                key = { _, transaction -> transaction.id }
                            ) { index, transaction ->
                                com.pennywiseai.tracker.ui.components.cards.TransactionItem(
                                    transaction = transaction,
                                    showDate = dateGroup == DateGroup.EARLIER,
                                    listItemPosition = ListItemPosition.from(index, transactions.size),
                                    convertedAmount = convertedAmounts[transaction.id],
                                    displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                    profileAccountKeys = profileAccountKeys,
                                    onClick = { onTransactionClick(transaction.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
    
    // Export Dialog
    if (showExportDialog) {
        ExportTransactionsDialog(
            transactions = uiState.transactions,
            onDismiss = { showExportDialog = false }
        )
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
}

@Composable
private fun TransactionDateHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    )
                )
            )
            .padding(top = Spacing.md, bottom = Spacing.sm)
    ) {
        Spacer(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                )
        )
        SectionHeaderV2(
            title = title,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TransactionFilterHeader(
    searchQuery: String,
    categoryFilter: String?,
    selectedPeriod: TimePeriod,
    customRangeLabel: String?,
    transactionTypeFilter: TransactionTypeFilter,
    categoryLabel: String?,
    hasCategoryFilter: Boolean,
    selectedProfileName: String,
    hasProfileFilter: Boolean,
    hasAnyActiveFilter: Boolean,
    showSortMenu: Boolean,
    showPeriodMenu: Boolean,
    showTypeMenu: Boolean,
    showMoreFiltersMenu: Boolean,
    collapsed: Boolean,
    sortOption: SortOption,
    timePeriods: List<TimePeriod>,
    availableCategories: List<String>,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    onSearchQueryChange: (String) -> Unit,
    onSortClick: () -> Unit,
    onSortDismiss: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
    onPeriodClick: () -> Unit,
    onPeriodDismiss: () -> Unit,
    onPeriodSelected: (TimePeriod) -> Unit,
    onTypeClick: () -> Unit,
    onTypeDismiss: () -> Unit,
    onTransactionTypeSelected: (TransactionTypeFilter) -> Unit,
    onMoreFiltersClick: () -> Unit,
    onMoreFiltersDismiss: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onProfileSelected: (Long?) -> Unit,
    onResetFilters: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransactionSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                categoryFilter = categoryFilter,
                focusRequester = focusRequester,
                trailingContent = {
                    Box {
                        IconButton(onClick = onSortClick) {
                            Icon(
                                imageVector = Icons.Rounded.MoreHoriz,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = onSortDismiss,
                            shape = MaterialTheme.shapes.large
                        ) {
                            SortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = sortOption == option,
                                                onClick = null,
                                                modifier = Modifier.size(Dimensions.Icon.medium)
                                            )
                                            Text(option.label)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    },
                                    onClick = { onSortSelected(option) }
                                )
                            }
                            if (hasAnyActiveFilter) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Clear filters") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        onResetFilters()
                                        onSortDismiss()
                                    }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                item {
                    Box {
                        ExpressiveFilterChip(
                            selected = true,
                            text = if (selectedPeriod == TimePeriod.CUSTOM && customRangeLabel != null) {
                                customRangeLabel
                            } else {
                                selectedPeriod.label
                            },
                            icon = Icons.Default.CalendarMonth,
                            onClick = onPeriodClick
                        )

                        DropdownMenu(
                            expanded = showPeriodMenu,
                            onDismissRequest = onPeriodDismiss
                        ) {
                            timePeriods.forEach { period ->
                                DropdownMenuItem(
                                    text = { Text(period.label) },
                                    leadingIcon = {
                                        if (selectedPeriod == period) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = { onPeriodSelected(period) }
                                )
                            }
                        }
                    }
                }

                item {
                    Box {
                        ExpressiveFilterChip(
                            selected = transactionTypeFilter != TransactionTypeFilter.ALL,
                            text = transactionTypeFilter.shortLabel(),
                            icon = transactionTypeFilter.filterIcon(),
                            onClick = onTypeClick
                        )

                        DropdownMenu(
                            expanded = showTypeMenu,
                            onDismissRequest = onTypeDismiss,
                            shape = MaterialTheme.shapes.large
                        ) {
                            TransactionTypeFilter.values().forEach { typeFilter ->
                                DropdownMenuItem(
                                    text = { Text(typeFilter.label) },
                                    leadingIcon = {
                                        if (transactionTypeFilter == typeFilter) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Icon(
                                                imageVector = typeFilter.filterIcon(),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = { onTransactionTypeSelected(typeFilter) }
                                )
                            }
                        }
                    }
                }

                item {
                    Box {
                        ExpressiveFilterChip(
                            selected = hasCategoryFilter || hasProfileFilter,
                            text = moreFiltersLabel(
                                categoryLabel = categoryLabel,
                                selectedProfileName = selectedProfileName,
                                hasCategoryFilter = hasCategoryFilter,
                                hasProfileFilter = hasProfileFilter
                            ),
                            icon = Icons.Default.Tune,
                            onClick = onMoreFiltersClick,
                            enabled = availableCategories.isNotEmpty() || profiles.isNotEmpty() ||
                                    hasCategoryFilter || hasProfileFilter
                        )

                        DropdownMenu(
                            expanded = showMoreFiltersMenu,
                            onDismissRequest = onMoreFiltersDismiss
                        ) {
                            DropdownMenuItem(
                                text = { Text("All categories") },
                                leadingIcon = {
                                    if (!hasCategoryFilter) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Icon(Icons.Default.Category, contentDescription = null)
                                    }
                                },
                                onClick = { onCategorySelected(null) }
                            )
                            availableCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        if (categoryFilter == category) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            CategoryIcon(
                                                category = category,
                                                size = Dimensions.Icon.small
                                            )
                                        }
                                    },
                                    onClick = { onCategorySelected(category) }
                                )
                            }
                            if (profiles.isNotEmpty()) {
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("All profiles") },
                                leadingIcon = {
                                    if (selectedProfileId == null) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    } else {
                                        Icon(Icons.Outlined.AccountBalance, contentDescription = null)
                                    }
                                },
                                onClick = { onProfileSelected(null) }
                            )
                            profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        if (selectedProfileId == profile.id) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Icon(
                                                profileIcon(profile),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    onClick = { onProfileSelected(profile.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun moreFiltersLabel(
    categoryLabel: String?,
    selectedProfileName: String,
    hasCategoryFilter: Boolean,
    hasProfileFilter: Boolean
): String {
    return when {
        hasCategoryFilter && hasProfileFilter -> "2 Filters"
        hasCategoryFilter -> categoryLabel ?: "Category"
        hasProfileFilter -> selectedProfileName
        else -> "Filters"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryFilter: String? = null,
    focusRequester: FocusRequester? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spacing.md, end = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (categoryFilter != null) "Search in $categoryFilter..."
                                else "Search transactions...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}


@Composable
private fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod,
    onAddClick: () -> Unit = {}
) {
    val headline = when {
        searchQuery.isNotEmpty() -> "No results for \"$searchQuery\""
        selectedPeriod != TimePeriod.ALL -> "Nothing for ${selectedPeriod.label.lowercase()}"
        else -> "No transactions yet"
    }
    val description = when {
        searchQuery.isNotEmpty() -> "Try a different search term or clear your filters"
        selectedPeriod != TimePeriod.ALL -> "Try selecting a different time period"
        else -> "Add your first transaction manually, or scan SMS from the home screen"
    }
    val actionLabel = if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) "Add Transaction" else null
    val onAction = if (actionLabel != null) onAddClick else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseEmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            headline = headline,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction
        )
    }
}
