package com.pennywiseai.tracker.presentation.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.data.preferences.HomeSection
import com.pennywiseai.tracker.data.preferences.HomeSectionLayout
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.work.WorkInfo
import androidx.activity.compose.LocalActivity
import com.pennywiseai.tracker.ui.animations.StaggeredEntrance
import com.pennywiseai.tracker.ui.animations.rememberEntranceDone
import com.pennywiseai.tracker.ui.animations.staggerDelayMs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.presentation.share.ShareCardSheet
import com.pennywiseai.tracker.ui.components.DockNavAction
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.presentation.review.ReviewPromptBanner
import com.pennywiseai.tracker.ui.components.PaydaySuggestionCard
import com.pennywiseai.tracker.ui.components.RecurringSuggestionsCard
import com.pennywiseai.tracker.ui.components.ProHomeCard
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.SmsAccessCard
import com.pennywiseai.tracker.ui.components.rememberSmsAccess
import com.pennywiseai.tracker.ui.components.SmsParsingProgressDialog
import com.pennywiseai.tracker.ui.components.cards.CashFlowCard
import com.pennywiseai.tracker.ui.components.skeleton.BalanceCardSkeleton
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.ui.components.CoverGradientBanner
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.GreetingCard
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onAccountClick: (bankName: String, accountLast4: String) -> Unit,
    coverStyle: CoverStyle = CoverStyle.AURORA,
    blurEffects: Boolean = false,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToTransactionsWithSearch: () -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToReview: (ReviewKind) -> Unit = {},
    onNavigateToBudgets: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToTransactionGroups: () -> Unit = {},
    onLoanClick: (Long) -> Unit = {},
    onNavigateToAddScreen: () -> Unit = {},
    onNavigateToManageAccounts: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
    onGoalClick: (Long) -> Unit = {},
    onTransactionTypeClick: (String?) -> Unit = {},
    onFabPositioned: (Rect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // Time to full display: the balance and recent list show data, not skeletons.
    ReportDrawnWhen { !uiState.isLoading && uiState.isBalanceReady }
    val isProEntitled by viewModel.isProEntitled.collectAsState()
    val currentCycleWindow by viewModel.currentCycleWindow.collectAsState()
    var showUpgradeSheet by rememberSaveable { mutableStateOf(false) }
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val smsScanWorkInfo by viewModel.smsScanWorkInfo.collectAsState()
    val groupSummaries by viewModel.groupSummaries.collectAsState()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val homeSectionLayout by viewModel.homeSectionLayout.collectAsStateWithLifecycle()
    val reviewPrompt by viewModel.reviewPrompt.collectAsStateWithLifecycle()
    val showProHomeCard by viewModel.showProHomeCard.collectAsStateWithLifecycle()
    val paydaySuggestion by viewModel.paydaySuggestion.collectAsStateWithLifecycle()
    val recurringSuggestions by viewModel.recurringSuggestions.collectAsStateWithLifecycle()
    val safeToSpend by viewModel.safeToSpend.collectAsStateWithLifecycle()
    var showSafeToSpendSheet by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    // A grant from the card, the sync button or app settings starts the first scan.
    val smsAccess = rememberSmsAccess(onGranted = viewModel::onSmsAccessGranted)
    // Recreated while App info was open: access starts granted, so onGranted never fires.
    LaunchedEffect(smsAccess.granted) {
        if (smsAccess.granted) viewModel.runPendingScan()
    }
    // The "tap here to scan" hint needs access Home opened with: without it the SMS access
    // card asks instead, and a grant made here already starts the scan the hint would teach.
    val hadSmsAccessOnOpen = remember { smsAccess.granted }
    val scanHintTarget: ((Rect) -> Unit)? = onFabPositioned.takeIf { hadSmsAccessOnOpen }
    LaunchedEffect(uiState.smsAccessNeeded) {
        if (uiState.smsAccessNeeded) {
            viewModel.onSmsAccessPrompted()
            smsAccess.request()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for full resync confirmation dialog
    var showFullResyncDialog by remember { mutableStateOf(false) }

    // Bottom sheet menu state
    var showMenuSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showEditWidgetsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberBottomSheetState(SheetValue.Hidden)

    // Profile filter dropdown state
    var showProfileFilterMenu by remember { mutableStateOf(false) }
    val deletedMessage = stringResource(R.string.home_transaction_deleted)
    val undoLabel = stringResource(R.string.home_undo)

    // Haptic feedback
    val view = LocalView.current

    // Set while the floating nav bar is up: Sync docks beside it, and Add
    // moves to the header so no FAB stack floats over the content.
    val navDock = LocalNavDock.current

    // Haptic on successful SMS scan completion
    LaunchedEffect(smsScanWorkInfo?.state) {
        if (smsScanWorkInfo?.state == WorkInfo.State.SUCCEEDED) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Haze state for TopAppBar blur
    val hazeState = remember { HazeState() }

    // Haze state for banner blur effect
    val hazeStateBanner = remember { HazeState() }

    // LazyColumn scroll state for overscroll physics
    val lazyListState = rememberLazyListState()

    // Staggered entrance — only animates on first composition.
    val hasAnimated = rememberEntranceDone()

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
    
    // A new day moves the forecast and Safe to spend on, even with no data change.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose { }
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
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    
    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        // The bottom nav bar is overlaid on top of this Scaffold by MainScreen,
        // so pad the host clear of it — otherwise snackbars (notably the
        // "restart to apply update" prompt) render behind the nav bar.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = Dimensions.Component.bottomBarHeight)
            )
        },
        customTopBar = {
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
                actionContent = {
                    HomeTopBarActions(
                        isProEntitled = isProEntitled,
                        blurEffects = blurEffects,
                        profiles = uiState.profiles,
                        selectedProfileId = uiState.selectedProfileId,
                        showProfileFilterMenu = showProfileFilterMenu,
                        showAddAction = navDock != null,
                        onUpgradeClick = { showUpgradeSheet = true },
                        onOpenProfileFilter = { showProfileFilterMenu = true },
                        onProfileSelected = { viewModel.updateSelectedProfile(it) },
                        onDismissProfileFilter = { showProfileFilterMenu = false },
                        onNavigateToAddScreen = onNavigateToAddScreen,
                        onMoreClick = { showMenuSheet = true }
                    )
                },
                extraInfoCard = {
                    GreetingCard(
                        userName = uiState.userName,
                        profileImageUri = uiState.profileImageUri,
                        profileBackgroundColor = uiState.profileBackgroundColor,
                        onAvatarClick = onNavigateToSettings,
                        onMenuClick = { showMenuSheet = true },
                        onAddClick = onNavigateToAddScreen.takeIf { navDock != null },
                        profiles = uiState.profiles,
                        selectedProfileId = uiState.selectedProfileId,
                        onProfileSelected = { viewModel.updateSelectedProfile(it) },
                        isProEntitled = isProEntitled,
                        onUpgradeClick = { showUpgradeSheet = true },
                        cycleEnd = currentCycleWindow.second
                    )
                }
            )
        }
    ) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Banner gradient at y=0 — paints behind the transparent TopAppBar
        if (coverStyle != CoverStyle.NONE) {
            CoverGradientBanner(
                coverStyle = coverStyle,
                hazeStateBanner = hazeStateBanner,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // LazyColumn scrolls over the banner
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .overScrollVertical(),
            flingBehavior = rememberOverscrollFlingBehavior { lazyListState },
            contentPadding = PaddingValues(
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                // Clears the nav bar plus the Add + Sync FAB stack above it.
                bottom = Dimensions.Component.bottomBarHeight +
                    Dimensions.Component.fabScrollClearance
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 1. Balance Card (0ms delay)
            item {
                StaggeredEntrance(delayMs = 0L, skip = hasAnimated) {
                    if (!uiState.isBalanceReady) {
                        BalanceCardSkeleton(
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                        )
                    } else {
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
                            currentMonthLent = uiState.currentMonthLent,
                            currentMonthTotal = uiState.currentMonthTotal,
                            balanceHistory = uiState.balanceHistory,
                            spendingHistory = uiState.spendingHistory,
                            lastMonthSpendingHistory = uiState.lastMonthSpendingHistory,
                            lastMonthSpending = uiState.lastMonthExpenses,
                            availableCurrencies = uiState.availableCurrencies,
                            isUnifiedMode = uiState.isUnifiedMode,
                            isApproximate = uiState.isApproximateBalance,
                            isBalanceHidden = uiState.isBalanceHidden,
                            onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() },
                            onCurrencySelected = { viewModel.selectCurrency(it) },
                            onShowBreakdown = { viewModel.showBreakdownDialog() },
                            accountBalances = uiState.accountBalances,
                            creditCards = uiState.creditCards,
                            totalAvailableCredit = uiState.totalAvailableCredit,
                            forecast = uiState.spendForecast,
                            onAccountClick = onAccountClick
                        )
                    }
                }
            }

            // Without SMS access nothing gets tracked: ask right under the balance card,
            // above the fold, not at the bottom of the list behind the nav bar.
            if (!smsAccess.granted && !uiState.isLoading && uiState.recentItems.isEmpty()) {
                item(key = "sms_access") {
                    SmsAccessCard(
                        access = smsAccess,
                        onRequest = { viewModel.scanSmsMessages() },
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            }

            // Share / one-time Pro card: below the first section that actually shows
            // (Safe to spend by default), so the decision card stays first and the
            // prompt still sits high enough to be seen and dismissed.
            val promoSlot: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                val payday = paydaySuggestion
                val recurring = recurringSuggestions
                // Review prompt. Offers the user something ("your month, summed up")
                // rather than asking a favour, and only appears when the finished period
                // actually has enough in it to be worth reading.
                val prompt = reviewPrompt
                if (prompt != null) {
                    item {
                        ReviewPromptBanner(
                            prompt = prompt,
                            onOpen = {
                                viewModel.markReviewPromptHandled()
                                onNavigateToReview(prompt.kind)
                            },
                            onDismiss = { viewModel.markReviewPromptHandled() },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        )
                    }
                } else if (payday != null) {
                    // One-time "start budgets on payday?" suggestion. Ahead of the Pro card:
                    // it's about the user's own money, not an upsell.
                    item {
                        PaydaySuggestionCard(
                            day = payday,
                            onAccept = { viewModel.acceptPaydaySuggestion(payday) },
                            onDismiss = { viewModel.dismissPaydaySuggestion() },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        )
                    }
                } else if (recurring != null) {
                    // Untracked repeating payments: a compact entry to Subscriptions →
                    // Suggested, where each one is reviewed.
                    item {
                        RecurringSuggestionsCard(
                            merchantNames = recurring.map { it.merchantName },
                            onReview = onNavigateToSubscriptions,
                            onDismiss = { viewModel.dismissRecurringSuggestions(recurring) },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        )
                    }
                } else if (showProHomeCard) {
                    // One-time Pro card (#945). Shares the share banner's slot and
                    // never stacks with it (the eligibility check also excludes it).
                    item {
                        ProHomeCard(
                            onSeePro = {
                                viewModel.markProHomeCardHandled()
                                showUpgradeSheet = true
                            },
                            onDismiss = { viewModel.markProHomeCardHandled() },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        )
                    }
                }
            }

            // 2–6. User-orderable sections (#770). Hidden ones are absent from the
            // list entirely; the entrance stagger follows the *displayed* position so
            // it survives reordering. Each branch keeps its own data guard.
            val visibleSections = homeSectionLayout.filter { it.second }.map { it.first }
            // Mirrors each branch's own data guard below, so an empty section first in
            // line doesn't drag the promo up under the balance card.
            val promoAfter = visibleSections.firstOrNull { section ->
                when (section) {
                    HomeSection.SAFE_TO_SPEND -> safeToSpend != null
                    HomeSection.BUDGETS -> uiState.budgetSummary != null
                    HomeSection.GOALS -> goals.isNotEmpty()
                    HomeSection.LOANS -> uiState.loanSummary != null
                    HomeSection.GROUPS -> groupSummaries.isNotEmpty()
                    HomeSection.ACCOUNTS -> uiState.creditCards.isNotEmpty() || uiState.accountBalances.isNotEmpty()
                    HomeSection.SUBSCRIPTIONS -> uiState.upcomingSubscriptions.isNotEmpty()
                    HomeSection.MONEY_IN_MOTION -> false // ponytail: secondary card, never the promo's anchor
                    HomeSection.RECENT_TRANSACTIONS, HomeSection.ACTIVITY -> true
                }
            }
            for ((index, section) in visibleSections.withIndex()) {
                val delayMs = staggerDelayMs(index)
                when (section) {
                    HomeSection.SAFE_TO_SPEND -> {
                        // Only once there's a spendable account with a balance.
                        safeToSpend?.let { state ->
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    SafeToSpendCard(
                                        state = state,
                                        isBalanceHidden = uiState.isBalanceHidden,
                                        onReveal = { viewModel.toggleBalanceVisibility() },
                                        onOpenBreakdown = { showSafeToSpendSheet = true },
                                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.BUDGETS -> {
                        // 2. Budget Carousel
                        uiState.budgetSummary?.let { summary ->
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeBudgetsSection(
                                        summary = summary,
                                        onNavigateToBudgets = onNavigateToBudgets
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.GOALS -> {
                        // 2.25. Goals (#914) — only when there's a non-archived goal.
                        if (goals.isNotEmpty()) {
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeGoalsSection(
                                        goals = goals,
                                        onNavigateToBudgets = onNavigateToBudgets,
                                        onGoalClick = onGoalClick
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.LOANS -> {
                        // 2.5. Loans Summary — only when active loans exist
                        uiState.loanSummary?.let { summary ->
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeLoansSection(
                                        summary = summary,
                                        selectedCurrency = uiState.selectedCurrency,
                                        onNavigateToLoans = onNavigateToLoans
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.GROUPS -> {
                        // 2.5. Groups section (#664) — a horizontal rail of the user's
                        // transaction groups, absent entirely when none exist so Home
                        // stays uncluttered for everyone else. Discovery was the ask:
                        // groups only surfaced via Settings or a group card that happened
                        // to have recent activity.
                        if (groupSummaries.isNotEmpty()) {
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeGroupsSection(
                                        groupSummaries = groupSummaries,
                                        onNavigateToTransactionGroups = onNavigateToTransactionGroups,
                                        onGroupClick = onGroupClick
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.RECENT_TRANSACTIONS -> {
                        // 3. Recent Transactions Section
                        item(key = section.name) {
                            StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                HomeRecentTransactionsHeader(
                                    onNavigateToTransactionsWithSearch = onNavigateToTransactionsWithSearch,
                                    onNavigateToTransactions = onNavigateToTransactions
                                )
                            }
                        }

                        if (uiState.isLoading) {
                            item(key = "${section.name}_body") {
                                Column(
                                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.Layout.listGap)
                                ) {
                                    repeat(5) {
                                        TransactionItemSkeleton()
                                    }
                                }
                            }
                        } else if (uiState.recentItems.isEmpty() && !smsAccess.granted) {
                            // No button: the SMS access card at the top is the one place that asks.
                            item(key = "${section.name}_body") {
                                PennyWiseEmptyState(
                                    icon = Icons.Default.Sync,
                                    headline = stringResource(R.string.home_empty_headline),
                                    description = stringResource(R.string.sms_access_empty_body),
                                    modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                                )
                            }
                        } else if (uiState.recentItems.isEmpty()) {
                            item(key = "${section.name}_body") {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeRecentTransactionsEmptyState(
                                        onScanSms = { viewModel.scanSmsMessages() }
                                    )
                                }
                            }
                        } else {
                            item(key = "${section.name}_body") {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeRecentTransactionsList(
                                        recentItems = uiState.recentItems,
                                        accountBalances = uiState.accountBalances,
                                        isUnifiedMode = uiState.isUnifiedMode,
                                        selectedCurrency = uiState.selectedCurrency,
                                        onTransactionClick = onTransactionClick,
                                        onGroupClick = onGroupClick
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.ACCOUNTS -> {
                        // 4. Account Carousel
                        if (uiState.creditCards.isNotEmpty() || uiState.accountBalances.isNotEmpty()) {
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeAccountsSection(
                                        accountBalances = uiState.accountBalances,
                                        creditCards = uiState.creditCards,
                                        isUnifiedMode = uiState.isUnifiedMode,
                                        selectedCurrency = uiState.selectedCurrency,
                                        isBalanceHidden = uiState.isBalanceHidden,
                                        blurEffects = blurEffects,
                                        hazeStateBanner = hazeStateBanner,
                                        onNavigateToManageAccounts = onNavigateToManageAccounts,
                                        onAccountClick = onAccountClick,
                                        onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() }
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.SUBSCRIPTIONS -> {
                        // 5. Upcoming Subscriptions Alert
                        if (uiState.upcomingSubscriptions.isNotEmpty()) {
                            item(key = section.name) {
                                StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                    HomeSubscriptionsSection(
                                        upcomingSubscriptions = uiState.upcomingSubscriptions,
                                        upcomingSubscriptionsTotal = uiState.upcomingSubscriptionsTotal,
                                        upcomingSubscriptionsByCurrency = uiState.upcomingSubscriptionsByCurrency,
                                        nextDueSubscription = uiState.nextDueSubscription,
                                        isUnifiedMode = uiState.isUnifiedMode,
                                        selectedCurrency = uiState.selectedCurrency,
                                        blurEffects = blurEffects,
                                        hazeStateBanner = hazeStateBanner,
                                        onNavigateToSubscriptions = onNavigateToSubscriptions
                                    )
                                }
                            }
                        }
                    }
                    HomeSection.MONEY_IN_MOTION -> {
                        // Credit / invested / transferred: money outside net cash flow.
                        // Reference detail, so a late, hideable section rather than a fixed
                        // card above the decision cards. Hides itself on dormant months.
                    item(key = section.name) {
                        StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                            CashFlowCard(
                                currency = uiState.selectedCurrency,
                                // When card spend counts as expense (#705) it's already in
                                // "Spent this month", so drop it from this "outside cash flow"
                                // card (a zero channel is filtered out) to avoid double-showing.
                                creditCardSpend = if (uiState.countCreditCardAsExpense) BigDecimal.ZERO else uiState.currentMonthCreditCard,
                                investments = uiState.currentMonthInvestment,
                                transfers = uiState.currentMonthTransfer,
                                isBalanceHidden = uiState.isBalanceHidden,
                                onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() },
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                            )
                        }
                    }
                    }
                    HomeSection.ACTIVITY -> {
                        // 6. Heatmap Widget
                        item(key = section.name) {
                            StaggeredEntrance(delayMs = delayMs, skip = hasAnimated) {
                                HomeActivitySection(
                                    transactionHeatmap = uiState.transactionHeatmap,
                                    blurEffects = blurEffects,
                                    hazeStateBanner = hazeStateBanner
                                )
                            }
                        }
                    }
                }
                if (section == promoAfter) promoSlot()
            }
            // Nothing shown to anchor on: the promo still gets a slot.
            if (promoAfter == null) promoSlot()
        }
        

        if (navDock != null) {
            // Sync docks beside the floating nav bar; Add lives in the header.
            val syncLabel = stringResource(R.string.home_fab_sync)
            val fullResyncLabel = stringResource(R.string.home_resync_title)
            DockNavAction(
                NavDockAction(
                    icon = Icons.Default.Sync,
                    contentDescription = syncLabel,
                    onClick = { viewModel.scanSmsMessages() },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        showFullResyncDialog = true
                    },
                    onLongClickLabel = fullResyncLabel,
                    loading = uiState.isScanning,
                    resultCount = uiState.scanNewCount,
                    onPositioned = scanHintTarget
                )
            )
        } else {
            // FABs - Direct access (no speed dial)
            HomeFabStack(
                isScanning = uiState.isScanning,
                showSyncHint = uiState.recentItems.isEmpty() && !uiState.isLoading,
                onNavigateToAddScreen = onNavigateToAddScreen,
                onSync = { viewModel.scanSmsMessages() },
                onRequestFullResync = { showFullResyncDialog = true },
                onFabPositioned = scanHintTarget
            )
        }

        // Full Resync Confirmation Dialog
        if (showFullResyncDialog) {
            FullResyncDialog(
                onDismiss = { showFullResyncDialog = false },
                onScanAgain = { viewModel.scanSmsMessages(rescanAll = true) },
                onRebuild = { viewModel.scanSmsMessages(forceResync = true) }
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

    // Share-card sheet, rendered at screen scope so dismissing the menu can't tear it down.
    if (showShareSheet) {
        ShareCardSheet(onDismiss = { showShareSheet = false })
    }

    // Avatar menu bottom sheet
    if (showMenuSheet) {
        HomeMenuSheet(
            sheetState = sheetState,
            onDismiss = { showMenuSheet = false },
            onOpenReview = { onNavigateToReview(ReviewKind.MONTH) },
            onOpenShareCard = { showShareSheet = true },
            onOpenSettings = onNavigateToSettings,
            onEditHome = { showEditWidgetsSheet = true },
            onReadMessagesAgain = { showFullResyncDialog = true }
        )
    }
    }

    safeToSpend?.takeIf { showSafeToSpendSheet }?.let { state ->
        SafeToSpendSheet(
            state = state,
            onSetIncluded = viewModel::setSafeToSpendAccountIncluded,
            onDismiss = { showSafeToSpendSheet = false }
        )
    }

    // Edit home screen sheet (#770). Every change persists immediately — no Save.
    if (showEditWidgetsSheet) {
        EditHomeSectionsSheet(
            homeSectionLayout = homeSectionLayout,
            onDismiss = { showEditWidgetsSheet = false },
            onMoveSection = viewModel::moveHomeSection,
            onSetSectionVisible = viewModel::setHomeSectionVisible
        )
    }

    // Pro upgrade sheet — triggered from the subtle ✨ chip in the top bar
    // for free users; reuses the same composable Settings uses.
    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
    }
}
