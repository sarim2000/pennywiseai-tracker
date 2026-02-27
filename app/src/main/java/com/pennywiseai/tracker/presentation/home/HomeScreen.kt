package com.pennywiseai.tracker.presentation.home

import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.ui.components.PennyWiseCard
import com.pennywiseai.tracker.ui.components.SectionHeader
import com.pennywiseai.tracker.ui.components.SmsParsingProgressDialog
import com.pennywiseai.tracker.ui.components.cards.AccountCarousel
import com.pennywiseai.tracker.ui.components.cards.BudgetCarousel
import com.pennywiseai.tracker.ui.components.cards.TransactionItem
import com.pennywiseai.tracker.ui.components.spotlightTarget
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.ui.components.CoverGradientBanner
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.GreetingCard
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    navController: NavController,
    coverStyle: CoverStyle = CoverStyle.AURORA,
    blurEffects: Boolean = false,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionsWithSearch: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToAddScreen: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onTransactionTypeClick: (String?) -> Unit = {},
    onFabPositioned: (Rect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val smsScanWorkInfo by viewModel.smsScanWorkInfo.collectAsState()
    val activity = LocalActivity.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for full resync confirmation dialog
    var showFullResyncDialog by remember { mutableStateOf(false) }

    // Haptic feedback
    val view = LocalView.current

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Haze state for TopAppBar blur
    val hazeState = remember { HazeState() }

    // Haze state for banner blur effect
    val hazeStateBanner = remember { HazeState() }

    // Staggered entrance animation state — only animates on first composition
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { 30.dp.roundToPx() }

    // Mark entrance animation as complete after all stagger delays have fired
    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            delay(350) // slightly after the last stagger (300ms)
            hasAnimated = true
        }
    }

    // Check for app updates and reviews when the screen is first displayed
    LaunchedEffect(Unit) {
        // Refresh account balances to ensure proper currency conversion
        viewModel.refreshAccountBalances()

        activity?.let {
            val componentActivity = it as ComponentActivity
            
            // Check for app updates
            viewModel.checkForAppUpdate(
                activity = componentActivity,
                snackbarHostState = snackbarHostState,
                scope = scope
            )
            
            // Check for in-app review eligibility
            viewModel.checkForInAppReview(componentActivity)
        }
    }
    
    // Refresh hidden accounts whenever this screen becomes visible
    // This ensures changes from ManageAccountsScreen are reflected immediately
    DisposableEffect(Unit) {
        viewModel.refreshHiddenAccounts()
        onDispose { }
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
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = "PennyWise",
                isHomeScreen = true,
                userName = uiState.userName,
                profileImageUri = uiState.profileImageUri,
                profileBackgroundColor = uiState.profileBackgroundColor,
                hazeState = hazeState,
                blurEffects = blurEffects,
                extraInfoCard = {
                    GreetingCard(
                        userName = uiState.userName,
                        profileImageUri = uiState.profileImageUri,
                        profileBackgroundColor = uiState.profileBackgroundColor,
                        onSettingsClick = onNavigateToSettings
                    )
                }
            )
        }
    ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        // Banner gradient at y=0 — paints behind the transparent TopAppBar
        if (coverStyle != CoverStyle.NONE) {
            CoverGradientBanner(
                coverStyle = coverStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // LazyColumn scrolls over the banner
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = Dimensions.Padding.content + 120.dp // Space for dual FABs (Add + Sync)
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Balance Card
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(0); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    com.pennywiseai.tracker.ui.components.cards.BalanceCard(
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        blurEffects = blurEffects,
                        hazeState = hazeStateBanner,
                        userName = uiState.userName,
                        totalBalance = uiState.totalBalance,
                        monthlyChange = uiState.monthlyChange,
                        monthlyChangePercent = uiState.monthlyChangePercent,
                        currency = uiState.selectedCurrency,
                        currentMonthIncome = uiState.currentMonthIncome,
                        currentMonthExpenses = uiState.currentMonthExpenses,
                        currentMonthTotal = uiState.currentMonthTotal,
                        balanceHistory = uiState.balanceHistory,
                        availableCurrencies = uiState.availableCurrencies,
                        onCurrencyClick = {
                            // Cycle through currencies when tapped
                            val currencies = uiState.availableCurrencies
                            if (currencies.size > 1) {
                                val currentIdx = currencies.indexOf(uiState.selectedCurrency)
                                val nextIdx = (currentIdx + 1) % currencies.size
                                viewModel.selectCurrency(currencies[nextIdx])
                            }
                        },
                        onShowBreakdown = { viewModel.showBreakdownDialog() }
                    )
                }
            }
            
            // Account Carousel (Credit Cards + Bank Accounts)
            if (uiState.creditCards.isNotEmpty() || uiState.accountBalances.isNotEmpty()) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(50); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        AccountCarousel(
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            bankAccounts = uiState.accountBalances,
                            creditCards = uiState.creditCards,
                            onAccountClick = { bankName, accountLast4 ->
                                navController.navigate(
                                    com.pennywiseai.tracker.navigation.AccountDetail(
                                        bankName = bankName,
                                        accountLast4 = accountLast4
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Budget Carousel
            uiState.budgetSummary?.let { summary ->
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(100); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        BudgetCarousel(
                            summary = summary,
                            onClick = onNavigateToBudgets,
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                        )
                    }
                }
            }

            // Upcoming Subscriptions Alert
            if (uiState.upcomingSubscriptions.isNotEmpty()) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(150); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        Box(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
                            UpcomingSubscriptionsCard(
                                subscriptions = uiState.upcomingSubscriptions,
                                totalAmount = uiState.upcomingSubscriptionsTotal,
                                currency = uiState.selectedCurrency,
                                onClick = onNavigateToSubscriptions
                            )
                        }
                    }
                }
            }
            
            // Recent Transactions Section
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(200); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        SectionHeader(
                            title = "Recent Transactions",
                            action = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Search button
                                    IconButton(
                                        onClick = onNavigateToTransactionsWithSearch,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search transactions",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // View All button
                                    TextButton(onClick = onNavigateToTransactions) {
                                        Text("View All")
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.Padding.content)
                            .height(Dimensions.Component.minTouchTarget * 2),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.recentTransactions.isEmpty()) {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(250); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        PennyWiseCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimensions.Padding.content)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimensions.Padding.card),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "No transactions yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap the sync button below to scan your SMS and automatically detect transactions",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.scanSmsMessages() },
                                    modifier = Modifier.padding(top = Spacing.xs)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text("Scan SMS")
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(250); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            uiState.recentTransactions.forEach { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    convertedAmount = uiState.recentTransactionConvertedAmounts[transaction.id],
                                    displayCurrency = if (uiState.isUnifiedMode) uiState.selectedCurrency else null,
                                    onClick = { onTransactionClick(transaction.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Heatmap Widget - at the bottom of the home screen
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(300); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    com.pennywiseai.tracker.ui.components.cards.HeatmapWidget(
                        transactionHeatmap = uiState.transactionHeatmap,
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            }
        }
        
        // FABs - Direct access (no speed dial)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Dimensions.Padding.content),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Add FAB (top, small)
            SmallFloatingActionButton(
                onClick = onNavigateToAddScreen,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction or Subscription"
                )
            }
            
            // Sync FAB (bottom, primary)
            // Single tap: incremental scan, Long press: full resync
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FloatingActionButton(
                    onClick = { viewModel.scanSmsMessages() },
                    modifier = Modifier
                        .spotlightTarget(onFabPositioned)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.scanSmsMessages() },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                showFullResyncDialog = true
                            }
                        ),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync SMS (long press for full resync)"
                    )
                }
                // Hint for long-press functionality - only show for new users (no transactions yet)
                if (uiState.recentTransactions.isEmpty() && !uiState.isLoading) {
                    Text(
                        text = "Hold for full resync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Full Resync Confirmation Dialog
        if (showFullResyncDialog) {
            AlertDialog(
                onDismissRequest = { showFullResyncDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Full Resync")
                },
                text = {
                    Text(
                        "This will reprocess all SMS messages from scratch. " +
                        "Use this to fix issues caused by updated bank parsers.\n\n" +
                        "This may take a few seconds depending on your message history."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFullResyncDialog = false
                            viewModel.scanSmsMessages(forceResync = true)
                        }
                    ) {
                        Text("Resync All")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFullResyncDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // SMS Parsing Progress Dialog
        SmsParsingProgressDialog(
            isVisible = uiState.isScanning,
            workInfo = smsScanWorkInfo,
            onDismiss = { viewModel.cancelSmsScan() },
            onCancel = { viewModel.cancelSmsScan() }
        )
        
        // Breakdown Dialog
        if (uiState.showBreakdownDialog) {
            BreakdownDialog(
                currentMonthIncome = uiState.currentMonthIncome,
                currentMonthExpenses = uiState.currentMonthExpenses,
                currentMonthTotal = uiState.currentMonthTotal,
                lastMonthIncome = uiState.lastMonthIncome,
                lastMonthExpenses = uiState.lastMonthExpenses,
                lastMonthTotal = uiState.lastMonthTotal,
                currency = uiState.selectedCurrency,
                onDismiss = { viewModel.hideBreakdownDialog() }
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownDialog(
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthTotal: BigDecimal,
    lastMonthIncome: BigDecimal,
    lastMonthExpenses: BigDecimal,
    lastMonthTotal: BigDecimal,
    currency: String = "INR",
    onDismiss: () -> Unit
) {
    val now = LocalDate.now()
    val currentPeriod = "${now.month.name.lowercase().replaceFirstChar { it.uppercase() }} 1-${now.dayOfMonth}"
    val lastMonth = now.minusMonths(1)
    val lastPeriod = "${lastMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} 1-${now.dayOfMonth}"
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md), // Reduced horizontal padding for wider modal
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Title
                Text(
                    text = "Calculation Breakdown",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                // Current Period Section
                Text(
                    text = currentPeriod,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                BreakdownRow(
                    label = "Income",
                    amount = currentMonthIncome,
                    isIncome = true,
                    currency = currency
                )

                BreakdownRow(
                    label = "Expenses",
                    amount = currentMonthExpenses,
                    isIncome = false,
                    currency = currency
                )

                HorizontalDivider()

                BreakdownRow(
                    label = "Net Worth",
                    amount = currentMonthTotal,
                    isIncome = currentMonthTotal >= BigDecimal.ZERO,
                    isBold = true,
                    currency = currency
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Last Period Section
                Text(
                    text = lastPeriod,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                BreakdownRow(
                    label = "Income",
                    amount = lastMonthIncome,
                    isIncome = true,
                    currency = currency
                )

                BreakdownRow(
                    label = "Expenses",
                    amount = lastMonthExpenses,
                    isIncome = false,
                    currency = currency
                )

                HorizontalDivider()

                BreakdownRow(
                    label = "Net Worth",
                    amount = lastMonthTotal,
                    isIncome = lastMonthTotal >= BigDecimal.ZERO,
                    isBold = true,
                    currency = currency
                )
                
                // Formula explanation
                Spacer(modifier = Modifier.height(Spacing.sm))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Formula: Income - Expenses = Net Worth\n" +
                               "Green (+) = Savings | Red (-) = Overspending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(Spacing.sm),
                        textAlign = TextAlign.Center
                    )
                }
                
                // Close button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: BigDecimal,
    isIncome: Boolean,
    isBold: Boolean = false,
    currency: String = "INR"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = "${if (isIncome) "+" else "-"}${CurrencyFormatter.formatCurrency(amount.abs(), currency)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isIncome) {
                if (!isSystemInDarkTheme()) income_light else income_dark
            } else {
                if (!isSystemInDarkTheme()) expense_light else expense_dark
            }
        )
    }
}

@Composable
private fun UpcomingSubscriptionsCard(
    subscriptions: List<SubscriptionEntity>,
    totalAmount: BigDecimal,
    currency: String = "INR",
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.Padding.content),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
                Column {
                    Text(
                        text = "${subscriptions.size} active subscriptions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Monthly total: ${CurrencyFormatter.formatCurrency(totalAmount, currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = Dimensions.Alpha.subtitle)
                    )
                }
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

