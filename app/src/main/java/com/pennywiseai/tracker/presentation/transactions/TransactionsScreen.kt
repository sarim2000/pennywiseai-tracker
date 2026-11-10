package com.pennywiseai.tracker.presentation.transactions

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.chipLabel
import com.pennywiseai.tracker.presentation.common.label
import com.pennywiseai.tracker.domain.model.TransactionTypeFilter
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.AppDates
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
    // Custom date range carried by an analytics drill-down (period == CUSTOM)
    initialCustomStartEpochDay: Long? = null,
    initialCustomEndEpochDay: Long? = null,
    currencyOnly: Boolean = false,
    // Account key / tag carried by an analytics group drill-down (#422)
    initialAccount: String? = null,
    initialTag: String? = null,
    focusSearch: Boolean = false,
    // New parameters for budget navigation
    initialStartDateEpochDay: Long? = null,
    initialEndDateEpochDay: Long? = null,
    initialCategories: String? = null,  // Comma-separated category names
    initialTransactionType: String? = null,
    viewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    // False for a plain bottom-nav tab tap (no back arrow, like the other tab
    // roots); true when reached as a filtered drill-down so the user can return. (#635)
    showBackButton: Boolean = true,
    // True when the app's bottom nav is overlaid on this screen (tab context), so
    // the list + FAB reserve matching clearance regardless of the back arrow. (#635)
    reserveBottomBarSpace: Boolean = false,
    onTransactionClick: (Long) -> Unit = {},
    onAddTransactionClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val categoriesFilter by viewModel.categoriesFilter.collectAsState()
    val categoriesFromBudget by viewModel.categoriesFromBudget.collectAsStateWithLifecycle()
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val categoriesMap by viewModel.categories.collectAsState()
    val filteredTotals by viewModel.filteredTotals.collectAsState()
    val availableCurrencies by viewModel.availableCurrencies.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val budgetCycleStartDay by viewModel.budgetCycleStartDay.collectAsStateWithLifecycle()
    val isUnifiedMode by viewModel.isUnifiedMode.collectAsState()
    val convertedAmounts by viewModel.convertedAmounts.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profileAccountKeys by viewModel.profileAccountKeys.collectAsState()
    val accountFilter by viewModel.accountFilter.collectAsState()
    val accountOptions by viewModel.accountOptions.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()

    // Bulk-edit selection (#369)
    val selectedIds by viewModel.selectedIds.collectAsState()
    val selectionTotals by viewModel.selectionTotals.collectAsState()
    val bulkSnack by viewModel.bulkSnack.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkCategorySheet by remember { mutableStateOf(false) }
    var showBulkGroupSheet by remember { mutableStateOf(false) }
    val groups by viewModel.groups.collectAsState()

    // Self-transfer suggestions (#385): map of txn-id → partner-id.
    val transferPartnerOf by viewModel.suggestedTransferPartnerOf.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    // Holds the id of the transaction whose category is being quick-edited via
    // swipe. Stored as Long? (not TransactionEntity?) so it survives rotation
    // without making the entity Parcelable; we look up the live row from the
    // current list when rendering the sheet.
    var pendingCategoryEditId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSortMenu by remember { mutableStateOf(false) } // Menu doesn't need saving
    var showDateRangePicker by rememberSaveable { mutableStateOf(false) }
    var showPeriodMenu by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showMoreFiltersMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }

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
        accountFilter != null ||
        tagFilter != null ||
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
    val customRangeLabel = remember(customDateRange, AppDates.dateFormatStyle) {
        DateRangeUtils.formatDateRange(customDateRange)
    }
    val periodLabel = selectedPeriod.label
    val periodChipLabel = remember(selectedPeriod, budgetCycleStartDay, customRangeLabel, periodLabel) {
        selectedPeriod.chipLabel(budgetCycleStartDay, customRangeLabel, periodLabel)
    }
    
    // Apply initial filters only once when screen is first created
    LaunchedEffect(Unit) {
        viewModel.applyInitialFilters(
            initialCategory,
            initialMerchant,
            initialPeriod,
            initialCurrency,
            initialCustomStartEpochDay,
            initialCustomEndEpochDay,
            initialAccount,
            initialTag,
            initialTransactionType
        )
        if (currencyOnly) initialCurrency?.let(viewModel::pinCurrency)
    }

    // Track if we've already processed these specific nav params
    var processedNavParams by rememberSaveable { mutableStateOf(false) }

    // Apply navigation filters only ONCE when actually navigating (not when returning from detail)
    LaunchedEffect(initialCategory, initialMerchant, initialPeriod, initialCurrency, initialCustomStartEpochDay, initialCustomEndEpochDay, initialAccount, initialTag, initialTransactionType) {
        if (!processedNavParams && (initialCategory != null || initialMerchant != null || initialPeriod != null || initialCurrency != null || initialAccount != null || initialTag != null)) {
            viewModel.applyNavigationFilters(
                initialCategory,
                initialMerchant,
                initialPeriod,
                initialCurrency,
                initialCustomStartEpochDay,
                initialCustomEndEpochDay,
                initialAccount,
                initialTag,
                initialTransactionType
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
    val deletedMessage = stringResource(R.string.txn_list_deleted)
    val undoLabel = stringResource(R.string.txn_list_undo)
    LaunchedEffect(deletedTransaction) {
        deletedTransaction?.let { transaction ->
            // Clear the state immediately to prevent re-triggering
            viewModel.clearDeletedTransaction()
            
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = deletedMessage,
                    actionLabel = undoLabel,
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

    // When the app bottom nav is overlaid on this screen (#635), it sits over an
    // 80.dp strip the Scaffold inset here doesn't know about, so the list + FAB
    // stack need matching bottom clearance (whether or not a back arrow shows).
    // In selection mode the bulk toolbar replaces the nav bar and the Scaffold
    // pads for it instead.
    val bottomBarClearance = if (reserveBottomBarSpace && !selectionMode) Dimensions.Component.bottomBarHeight else 0.dp

    // As a tab under the floating nav bar, Add docks beside the bar and Export
    // moves to the top bar instead of a FAB stack.
    val navDock = if (reserveBottomBarSpace) LocalNavDock.current else null
    if (navDock != null) {
        DockNavAction(
            NavDockAction(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.txn_list_add_transaction),
                onClick = onAddTransactionClick
            )
        )
    }
    if (selectionMode) HideNavBar()

    PennyWiseScaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        // With no FAB to lift it, clear the overlaid floating nav bar instead.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (navDock != null) bottomBarClearance else 0.dp)
            )
        },
        customTopBar = {
            if (selectionMode) {
                // Contextual top bar for bulk-edit (#369): close left, count as title,
                // Select all on the right. The bulk actions live in BulkActionsToolbar.
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehaviorLarge,
                    title = stringResource(R.string.txn_list_selected_count, selectedIds.size),
                    hasBackButton = true,
                    navigationContent = {
                        IconButton(onClick = { viewModel.clearSelection() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.txn_list_exit_selection))
                        }
                    },
                    actionContent = {
                        if (selectedIds.size < uiState.transactions.size) {
                            IconButton(onClick = { viewModel.selectAllVisible() }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.txn_list_select_all))
                            }
                        }
                    },
                    hazeState = hazeState
                )
            } else {
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehaviorLarge,
                    title = stringResource(R.string.txn_list_title),
                    // The period chip row hides once the list scrolls; this keeps
                    // the active period readable in the collapsed bar.
                    subtitle = periodChipLabel,
                    hasBackButton = showBackButton,
                    navigationContent = {
                        IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.txn_list_back)
                            )
                        }
                    },
                    actionContent = {
                        if (navDock != null && uiState.transactions.isNotEmpty()) {
                            IconButton(onClick = { showExportDialog = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = stringResource(R.string.txn_list_export_csv)
                                )
                            }
                        }
                    },
                    hazeState = hazeState
                )
            }
        },
        bottomBar = {
            BulkActionsToolbar(
                visible = selectionMode,
                canMarkAsTransfer = selectedIds.size == 2,
                onChangeCategory = { showBulkCategorySheet = true },
                onAddToGroup = { showBulkGroupSheet = true },
                onDelete = { viewModel.bulkDelete() },
                onMarkAsTransfer = { viewModel.bulkMarkAsTransfer() }
            )
        },
        floatingActionButton = {
            if (navDock == null && !selectionMode) Column(
                modifier = Modifier.padding(bottom = bottomBarClearance),
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
                            contentDescription = stringResource(R.string.txn_list_export_csv),
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
                        contentDescription = stringResource(R.string.txn_list_add_transaction)
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
            periodChipLabel = periodChipLabel,
            transactionTypeFilter = transactionTypeFilter,
            categoryLabel = categoryFilterLabel(categoryFilter, categoriesFilter, availableCategories),
            hasCategoryFilter = categoryFilter != null || categoriesFilter != null,
            selectedCategories = categoriesFilter?.toSet()
                ?: categoryFilter?.let { setOf(it) }
                ?: availableCategories.toSet(),
            onCategoryToggled = { viewModel.toggleCategory(it, availableCategories) },
            selectedProfileName = profiles.firstOrNull { it.id == selectedProfileId }?.displayName() ?: stringResource(R.string.txn_list_filter_profile),
            hasProfileFilter = selectedProfileId != null,
            hasAnyActiveFilter = hasAnyActiveFilter,
            showSortMenu = showSortMenu,
            showPeriodMenu = showPeriodMenu,
            showTypeMenu = showTypeMenu,
            showMoreFiltersMenu = showMoreFiltersMenu,
            showAccountMenu = showAccountMenu,
            showTagMenu = showTagMenu,
            collapsed = collapseTransactionHeader && !showPeriodMenu && !showTypeMenu && !showMoreFiltersMenu && !showAccountMenu && !showTagMenu,
            sortOption = sortOption,
            timePeriods = timePeriods,
            availableCategories = availableCategories,
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            accountOptions = accountOptions,
            accountFilter = accountFilter,
            availableTags = availableTags,
            tagFilter = tagFilter,
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
                    // "All categories" clears both the single and the ticked-list filter
                    viewModel.clearCategoryFilter()
                    viewModel.clearCategoriesFilter()
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
            onAccountClick = { showAccountMenu = true },
            onAccountDismiss = { showAccountMenu = false },
            onAccountSelected = { accountKey ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.setAccountFilter(accountKey)
                showAccountMenu = false
            },
            onTagClick = { showTagMenu = true },
            onTagDismiss = { showTagMenu = false },
            onTagSelected = { tag ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                viewModel.setTagFilter(tag)
                showTagMenu = false
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
                        bottom = paddingValues.calculateBottomPadding() + bottomBarClearance
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
                        bottom = paddingValues.calculateBottomPadding() + bottomBarClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap),
                    flingBehavior = rememberOverscrollFlingBehavior { listState }
                ) {
                    stickyHeader(contentType = "totals") {
                        Surface(
                            // Match the Scaffold's `background` so AMOLED-style
                            // themes (true-black bg + tinted surface) don't paint
                            // a visible slab behind the sticky totals card.
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // In selection mode the sticky card shows the totals of
                            // just the selected rows (#634); otherwise the filtered-list
                            // totals. The fourth tile is picked by [extraTotalFor].
                            val cardTotals = if (selectionMode) selectionTotals else filteredTotals
                            TransactionTotalsCard(
                                income = cardTotals.income,
                                expenses = cardTotals.expenses,
                                netBalance = cardTotals.netBalance,
                                extra = extraTotalFor(cardTotals, transactionTypeFilter, selectionMode),
                                title = if (selectionMode) {
                                    stringResource(R.string.txn_list_selected_count, selectedIds.size)
                                } else {
                                    null
                                },
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
                    if (categoriesFromBudget) {
                        item(contentType = "budgetNotice") {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
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
                                        text = stringResource(R.string.txn_list_budget_split_notice),
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
                            val headerContent: @Composable LazyItemScope.(Int) -> Unit = { _ ->
                                TransactionDateHeader(title = stringResource(dateGroup.labelRes))
                            }
                            stickyHeader(contentType = "dateHeader", content = headerContent)
                            
                            // Transactions in this group
                            itemsIndexed(
                                items = transactions,
                                key = { _, transaction -> transaction.id },
                                contentType = { _, _ -> "transaction" }
                            ) { index, transaction ->
                                val isSelected = transaction.id in selectedIds
                                // Selected highlight via the Card's container colour — the prior
                                // Modifier.background on a wrapping Box was painted *under* the
                                // Card and was completely covered by the Card's own surface, so
                                // selection had no visible effect.
                                val rowContainerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else null

                                // Long-press now lives on TransactionItem's own
                                // gesture surface (Material combinedClickable),
                                // which cooperates with the parent SwipeToDismissBox
                                // — fixes bulk-edit not firing in nested gesture
                                // contexts.
                                val longPressToggle = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    viewModel.toggleSelection(transaction.id)
                                }
                                if (selectionMode) {
                                    com.pennywiseai.tracker.ui.components.cards.TransactionItem(
                                        modifier = Modifier.animateItem(),
                                        transaction = transaction,
                                        showDate = dateGroup == DateGroup.THIS_WEEK || dateGroup == DateGroup.EARLIER,
                                        listItemPosition = ListItemPosition.from(index, transactions.size),
                                        convertedAmount = convertedAmounts[transaction.id],
                                        displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                        profileAccountKeys = profileAccountKeys,
                                        onClick = { viewModel.toggleSelection(transaction.id) },
                                        onLongClick = longPressToggle,
                                        containerColor = rowContainerColor
                                    )
                                } else {
                                    Column(Modifier.animateItem()) {
                                        SwipeToEditCategory(
                                            transaction = transaction,
                                            onRequestEdit = { pendingCategoryEditId = it.id }
                                        ) {
                                            com.pennywiseai.tracker.ui.components.cards.TransactionItem(
                                                transaction = transaction,
                                                showDate = dateGroup == DateGroup.THIS_WEEK || dateGroup == DateGroup.EARLIER,
                                                listItemPosition = ListItemPosition.from(index, transactions.size),
                                                convertedAmount = convertedAmounts[transaction.id],
                                                displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                                profileAccountKeys = profileAccountKeys,
                                                onClick = { onTransactionClick(transaction.id) },
                                                onLongClick = longPressToggle
                                            )
                                        }
                                        // Self-transfer suggestion (#385): show the affordance on
                                        // the EXPENSE row only so each pair surfaces once.
                                        val partnerId = transferPartnerOf[transaction.id]
                                        if (partnerId != null &&
                                            transaction.transactionType == TransactionType.EXPENSE
                                        ) {
                                            AssistChip(
                                                onClick = { viewModel.markPairAsTransfer(transaction.id, partnerId) },
                                                label = { Text(stringResource(R.string.txn_list_mark_as_transfer)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.SwapHoriz,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(Dimensions.Icon.small)
                                                    )
                                                },
                                                modifier = Modifier.padding(start = Spacing.sm, top = Spacing.xs)
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

    pendingCategoryEditId?.let { id ->
        val transaction = uiState.transactions.firstOrNull { it.id == id }
        if (transaction == null) {
            // Row vanished (e.g. deleted via another path while sheet was open).
            // Drop the pending edit so we don't keep an orphan sheet alive.
            LaunchedEffect(id) { pendingCategoryEditId = null }
        } else {
            QuickCategoryPickerSheet(
                transaction = transaction,
                categories = categoriesMap.values.toList(),
                onCategorySelected = { name ->
                    viewModel.updateCategory(transaction, name)
                    pendingCategoryEditId = null
                },
                onDismiss = { pendingCategoryEditId = null }
            )
        }
    }

    // Bulk category picker (#369). If every selected row already shares a
    // category, mark it; otherwise pass a "(multiple)" sentinel so nothing is
    // pre-checked.
    if (showBulkCategorySheet && selectedIds.isNotEmpty()) {
        val selectedTxns = uiState.transactions.filter { it.id in selectedIds }
        val commonCategory = selectedTxns.map { it.category }.distinct().singleOrNull()
        QuickCategoryPickerSheet(
            currentCategory = commonCategory ?: "(multiple)",
            categories = categoriesMap.values.toList(),
            onCategorySelected = { name ->
                viewModel.bulkUpdateCategory(name)
                showBulkCategorySheet = false
            },
            onDismiss = { showBulkCategorySheet = false }
        )
    }

    // Bulk add-to-group picker (#506): add every selected row to an existing
    // group or a brand-new one in a single action.
    if (showBulkGroupSheet && selectedIds.isNotEmpty()) {
        BulkGroupPickerSheet(
            selectedCount = selectedIds.size,
            groups = groups,
            onGroupSelected = { group ->
                viewModel.bulkAddToGroup(group.id, group.name)
                showBulkGroupSheet = false
            },
            onCreateGroup = { name ->
                viewModel.bulkCreateGroupAndAdd(name)
                showBulkGroupSheet = false
            },
            onDismiss = { showBulkGroupSheet = false }
        )
    }

    // Bulk action snackbar with Undo (#369).
    val bulkSnackText = bulkSnack?.message?.asString()
    LaunchedEffect(bulkSnack) {
        bulkSnack?.let { snack ->
            val result = snackbarHostState.showSnackbar(
                message = bulkSnackText.orEmpty(),
                actionLabel = snack.undo?.let { undoLabel },
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) snack.undo?.invoke()
            viewModel.consumeBulkSnack()
        }
    }

    // Hardware back exits selection mode instead of leaving the screen.
    BackHandler(enabled = selectionMode) {
        viewModel.clearSelection()
    }
}
