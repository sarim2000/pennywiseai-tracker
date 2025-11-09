package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.data.database.entity.CategoryEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.common.TimePeriod
import com.pennywiseai.tracker.presentation.common.TransactionTypeFilter
import com.pennywiseai.tracker.ui.components.*
import com.pennywiseai.tracker.ui.components.CollapsibleFilterRow
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import com.pennywiseai.tracker.utils.formatAmount
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    initialCategory: String? = null,
    initialMerchant: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null,
    focusSearch: Boolean = false,
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
    val transactionTypeFilter by viewModel.transactionTypeFilter.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val categoriesMap by viewModel.categories.collectAsState()
    val filteredTotals by viewModel.filteredTotals.collectAsState()
    val currencyGroupedTotals by viewModel.currencyGroupedTotals.collectAsState()
    val availableCurrencies by viewModel.availableCurrencies.collectAsState()
    val selectedCurrency by viewModel.selectedCurrency.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val smsScanMonths by viewModel.smsScanMonths.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showExportDialog by remember { mutableStateOf(false) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    
    // Focus management for search field
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Calculate active filter count for advanced filters
    val activeFilterCount = listOf(
        transactionTypeFilter != TransactionTypeFilter.ALL,
        categoryFilter != null
    ).count { it }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
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

    // Apply navigation filters when navigation parameters change (for deep links)
    LaunchedEffect(initialCategory, initialMerchant, initialPeriod, initialCurrency) {
        if (initialCategory != null || initialMerchant != null || initialPeriod != null || initialCurrency != null) {
            viewModel.applyNavigationFilters(
                initialCategory,
                initialMerchant,
                initialPeriod,
                initialCurrency
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
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                            modifier = Modifier.size(24.dp)
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
                .padding(paddingValues)
        ) {
        // Search Bar with Sort Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            TransactionSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                categoryFilter = categoryFilter,
                focusRequester = searchFocusRequester,
                modifier = Modifier.weight(1f)
            )
            
            // Sort button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "Sort",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(option.label)
                                }
                            },
                            onClick = {
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
        
        // Period Filter Chips - Always visible
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Period filter chips
            items(timePeriods) { period ->
                FilterChip(
                    // Only show CUSTOM as selected if both period is CUSTOM AND dates are set
                    selected = if (period == TimePeriod.CUSTOM) {
                        selectedPeriod == period && customDateRange != null
                    } else {
                        selectedPeriod == period
                    },
                    onClick = {
                        if (period == TimePeriod.CUSTOM) {
                            showDateRangePicker = true
                            // Don't change selectedPeriod until user confirms dates
                        } else {
                            viewModel.selectPeriod(period)
                        }
                    },
                    label = {
                        Text(
                            if (period == TimePeriod.CUSTOM && customRangeLabel != null) {
                                customRangeLabel
                            } else {
                                period.label
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
        
        // Data scope info banner
        if (viewModel.isShowingLimitedData()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.xs),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(Spacing.md)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Dimensions.Icon.small)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Showing last $smsScanMonths months of SMS data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Adjust in Settings to scan more history",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(
                        onClick = onNavigateToSettings,
                        contentPadding = PaddingValues(horizontal = Spacing.sm)
                    ) {
                        Text("Settings", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        
        // Collapsible Advanced Filters
        CollapsibleFilterRow(
            isExpanded = showAdvancedFilters,
            activeFilterCount = activeFilterCount,
            onToggle = { showAdvancedFilters = !showAdvancedFilters },
            modifier = Modifier.fillMaxWidth()
        ) {
            // Transaction Type Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(TransactionTypeFilter.values().toList()) { typeFilter ->
                    FilterChip(
                        selected = transactionTypeFilter == typeFilter,
                        onClick = { viewModel.setTransactionTypeFilter(typeFilter) },
                        label = { Text(typeFilter.label) },
                        leadingIcon = if (transactionTypeFilter == typeFilter) {
                            {
                                when (typeFilter) {
                                    TransactionTypeFilter.INCOME -> Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    TransactionTypeFilter.EXPENSE -> Icon(
                                        Icons.AutoMirrored.Filled.TrendingDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    TransactionTypeFilter.CREDIT -> Icon(
                                        Icons.Default.CreditCard,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    TransactionTypeFilter.TRANSFER -> Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    TransactionTypeFilter.INVESTMENT -> Icon(
                                        Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(Dimensions.Icon.small)
                                    )
                                    else -> null
                                }
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    )
                }
            }
        }
        
        // Totals Card - Moved after filters
        TransactionTotalsCard(
            income = filteredTotals.income,
            expenses = filteredTotals.expenses,
            netBalance = filteredTotals.netBalance,
            currency = selectedCurrency,
            availableCurrencies = availableCurrencies,
            onCurrencySelected = { viewModel.selectCurrency(it) },
            isLoading = uiState.isLoading,
            modifier = Modifier
                .padding(horizontal = Dimensions.Padding.content)
                .padding(top = Spacing.sm)
        )
        
        // Category Filter Chip (if active) - Moved to its own row
        categoryFilter?.let { category ->
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { /* No action on click, use trailing icon to clear */ },
                        label = { Text(category) },
                        leadingIcon = {
                            categoriesMap[category]?.let { categoryEntity ->
                                CategoryChip(
                                    category = categoryEntity,
                                    showText = false,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { viewModel.clearCategoryFilter() },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear category filter",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }
        
        // Transaction List
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Dimensions.Padding.content),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.transactions.isEmpty() -> {
                EmptyTransactionsState(
                    searchQuery = searchQuery,
                    selectedPeriod = selectedPeriod
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = Dimensions.Padding.content,
                        vertical = Spacing.md
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    // Iterate through date groups in order
                    listOf(
                        DateGroup.TODAY,
                        DateGroup.YESTERDAY,
                        DateGroup.THIS_WEEK,
                        DateGroup.EARLIER
                    ).forEach { dateGroup ->
                        uiState.groupedTransactions[dateGroup]?.let { transactions ->
                            // Date group header
                            item {
                                SectionHeader(
                                    title = dateGroup.label,
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                )
                            }
                            
                            // Transactions in this group
                            items(
                                items = transactions,
                                key = { it.id }
                            ) { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    categoriesMap = categoriesMap,
                                    showDate = dateGroup == DateGroup.EARLIER,
                                    onClick = { onTransactionClick(transaction.id) }
                                )
                                if (transaction != transactions.last()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = Spacing.md),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryFilter: String? = null,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { 
            Text(
                text = if (categoryFilter != null) "Search in $categoryFilter..." 
                else "Search transactions...",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.then(
            focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
        )
    )
}


@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    categoriesMap: Map<String, CategoryEntity>,
    showDate: Boolean,
    onClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val amountColor = when (transaction.transactionType) {
        TransactionType.INCOME -> if (!isSystemInDarkTheme()) income_light else income_dark
        TransactionType.EXPENSE -> if (!isSystemInDarkTheme()) expense_light else expense_dark
        TransactionType.CREDIT -> if (!isSystemInDarkTheme()) credit_light else credit_dark
        TransactionType.TRANSFER -> if (!isSystemInDarkTheme()) transfer_light else transfer_dark
        TransactionType.INVESTMENT -> if (!isSystemInDarkTheme()) investment_light else investment_dark
    }
    
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
    val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d • h:mm a")
    
    // Always show both date and time
    val dateTimeText = transaction.dateTime.format(dateTimeFormatter)
    
    // Build subtitle without category (will show category separately)
    val subtitleParts = buildList {
        // Add transaction type indicator for special types
        when (transaction.transactionType) {
            TransactionType.CREDIT -> add("Credit Card")
            TransactionType.TRANSFER -> add("Transfer")
            TransactionType.INVESTMENT -> add("Investment")
            else -> {} // No special label for INCOME/EXPENSE
        }
        add(dateTimeText)
        if (transaction.isRecurring) add("Recurring")
    }
    
    ListItemCard(
        title = transaction.merchantName,
        subtitle = subtitleParts.joinToString(" • "),
        amount = transaction.formatAmount(),
        amountColor = amountColor,
        onClick = onClick,
        leadingContent = {
            BrandIcon(
                merchantName = transaction.merchantName,
                size = 40.dp,
                showBackground = true
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Show icon for special transaction types
                when (transaction.transactionType) {
                    TransactionType.CREDIT -> Icon(
                        Icons.Default.CreditCard,
                        contentDescription = "Credit Card",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) credit_light else credit_dark
                    )
                    TransactionType.TRANSFER -> Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Transfer",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) transfer_light else transfer_dark
                    )
                    TransactionType.INVESTMENT -> Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = "Investment",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) investment_light else investment_dark
                    )
                    TransactionType.INCOME -> Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Income",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) income_light else income_dark
                    )
                    TransactionType.EXPENSE -> Icon(
                        Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = "Expense",
                        modifier = Modifier.size(Dimensions.Icon.small),
                        tint = if (!isSystemInDarkTheme()) expense_light else expense_dark
                    )
                }
                
                // Always show amount
                Text(
                    text = transaction.formatAmount(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
            }
        }
    )
}

@Composable
private fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        PennyWiseCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.empty),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = when {
                        searchQuery.isNotEmpty() -> "No transactions matching \"$searchQuery\""
                        selectedPeriod != TimePeriod.ALL -> "No transactions for ${selectedPeriod.label.lowercase()}"
                        else -> "No transactions yet"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Sync your SMS to see transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
