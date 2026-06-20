package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
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
    val accountFilter by viewModel.accountFilter.collectAsState()
    val accountOptions by viewModel.accountOptions.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()

    // Bulk-edit selection (#369)
    val selectedIds by viewModel.selectedIds.collectAsState()
    val bulkSnack by viewModel.bulkSnack.collectAsState()
    val selectionMode = selectedIds.isNotEmpty()
    var showBulkCategorySheet by remember { mutableStateOf(false) }

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
            if (selectionMode) {
                // Contextual top bar for bulk-edit (#369): close left, count as title,
                // Change Category + Delete on the right.
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehaviorLarge,
                    title = "${selectedIds.size} selected",
                    hasBackButton = true,
                    hasActionButton = true,
                    navigationContent = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actionContent = {
                        // Wrap explicitly in a Row so both action IconButtons render
                        // even when the top-bar's actions slot is constrained.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showBulkCategorySheet = true }) {
                                Icon(Icons.Default.Category, contentDescription = "Change category")
                            }
                            IconButton(onClick = { viewModel.bulkDelete() }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    hazeState = hazeState
                )
            } else {
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
            }
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
                            // Match the Scaffold's `background` so AMOLED-style
                            // themes (true-black bg + tinted surface) don't paint
                            // a visible slab behind the sticky totals card.
                            color = MaterialTheme.colorScheme.background,
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
                            val headerContent: @Composable LazyItemScope.(Int) -> Unit = { _ ->
                                TransactionDateHeader(title = dateGroup.label)
                            }
                            stickyHeader(content = headerContent)
                            
                            // Transactions in this group
                            itemsIndexed(
                                items = transactions,
                                key = { _, transaction -> transaction.id }
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
                                        transaction = transaction,
                                        showDate = dateGroup == DateGroup.EARLIER,
                                        listItemPosition = ListItemPosition.from(index, transactions.size),
                                        convertedAmount = convertedAmounts[transaction.id],
                                        displayCurrency = if (isUnifiedMode) selectedCurrency else null,
                                        profileAccountKeys = profileAccountKeys,
                                        onClick = { viewModel.toggleSelection(transaction.id) },
                                        onLongClick = longPressToggle,
                                        containerColor = rowContainerColor
                                    )
                                } else {
                                    Column {
                                        SwipeToEditCategory(
                                            transaction = transaction,
                                            onRequestEdit = { pendingCategoryEditId = it.id }
                                        ) {
                                            com.pennywiseai.tracker.ui.components.cards.TransactionItem(
                                                transaction = transaction,
                                                showDate = dateGroup == DateGroup.EARLIER,
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
                                                label = { Text("Mark as transfer") },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.SwapHoriz,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                modifier = Modifier.padding(start = Spacing.sm, top = 4.dp)
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

    // Bulk action snackbar with Undo (#369).
    LaunchedEffect(bulkSnack) {
        bulkSnack?.let { snack ->
            val result = snackbarHostState.showSnackbar(
                message = snack.message,
                actionLabel = snack.undo?.let { "Undo" },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToEditCategory(
    transaction: TransactionEntity,
    onRequestEdit: (TransactionEntity) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onRequestEdit(transaction)
            }
            // Never let the swipe complete — we use it purely as a gesture trigger
            // and snap back to the resting state.
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart ->
                        MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
                label = "swipe_edit_background"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = Dimensions.Padding.content),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "Change category",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        content = { content() }
    )
}

@Composable
private fun TransactionDateHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    // Fade the sticky date header into a transparent base so scrolling content
    // looks like it's passing under the label. Use `background` (the Scaffold
    // surface) instead of `surface`, otherwise an AMOLED-style theme — where
    // `background` is true-black but `surface` is tinted — paints a visible
    // dark slab behind every date header.
    val pageBg = MaterialTheme.colorScheme.background
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        pageBg,
                        pageBg,
                        pageBg.copy(alpha = 0.92f),
                        pageBg.copy(alpha = 0f)
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
    showAccountMenu: Boolean,
    showTagMenu: Boolean,
    collapsed: Boolean,
    sortOption: SortOption,
    timePeriods: List<TimePeriod>,
    availableCategories: List<String>,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    accountOptions: List<com.pennywiseai.tracker.presentation.common.AccountOption>,
    accountFilter: String?,
    availableTags: List<String>,
    tagFilter: String?,
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
    onAccountClick: () -> Unit,
    onAccountDismiss: () -> Unit,
    onAccountSelected: (String?) -> Unit,
    onTagClick: () -> Unit,
    onTagDismiss: () -> Unit,
    onTagSelected: (String?) -> Unit,
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

                if (availableTags.isNotEmpty() || tagFilter != null) {
                    item {
                        Box {
                            ExpressiveFilterChip(
                                selected = tagFilter != null,
                                text = tagFilter ?: "Tag",
                                icon = Icons.Default.Sell,
                                onClick = onTagClick
                            )

                            DropdownMenu(
                                expanded = showTagMenu,
                                onDismissRequest = onTagDismiss
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All tags") },
                                    leadingIcon = {
                                        if (tagFilter == null) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Icon(Icons.Default.Sell, contentDescription = null)
                                        }
                                    },
                                    onClick = { onTagSelected(null) }
                                )
                                availableTags.forEach { tag ->
                                    DropdownMenuItem(
                                        text = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingIcon = {
                                            if (tagFilter?.equals(tag, ignoreCase = true) == true) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            } else {
                                                Icon(Icons.Default.Sell, contentDescription = null)
                                            }
                                        },
                                        onClick = { onTagSelected(tag) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (accountOptions.isNotEmpty()) {
                    item {
                        Box {
                            val selectedAccountLabel =
                                accountOptions.firstOrNull { it.key == accountFilter }?.label
                            ExpressiveFilterChip(
                                selected = accountFilter != null,
                                text = selectedAccountLabel ?: "Account",
                                icon = Icons.Outlined.AccountBalanceWallet,
                                onClick = onAccountClick
                            )

                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = onAccountDismiss
                            ) {
                                DropdownMenuItem(
                                    text = { Text("All accounts") },
                                    leadingIcon = {
                                        if (accountFilter == null) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        } else {
                                            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                                        }
                                    },
                                    onClick = { onAccountSelected(null) }
                                )
                                accountOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingIcon = {
                                            if (accountFilter == option.key) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            } else {
                                                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null)
                                            }
                                        },
                                        onClick = { onAccountSelected(option.key) }
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
