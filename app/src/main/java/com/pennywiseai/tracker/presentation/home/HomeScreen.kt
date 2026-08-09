package com.pennywiseai.tracker.presentation.home

import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.work.WorkInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.presentation.share.ShareCardSheet
import com.pennywiseai.tracker.ui.components.ShareMonthBanner
import androidx.navigation.NavController
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.ui.components.BrandIcon
import com.pennywiseai.tracker.ui.components.cards.HomeGroupCard
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.SmsParsingProgressDialog
import com.pennywiseai.tracker.ui.components.cards.AccountCarousel
import com.pennywiseai.tracker.ui.components.cards.BudgetCarousel
import com.pennywiseai.tracker.ui.components.cards.CashFlowCard
import com.pennywiseai.tracker.ui.components.cards.GroupCard
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.TransactionItem
import com.pennywiseai.tracker.ui.components.skeleton.BalanceCardSkeleton
import com.pennywiseai.tracker.ui.components.skeleton.TransactionItemSkeleton
import com.pennywiseai.tracker.ui.components.spotlightTarget
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.ui.components.ProfileFilterDropdown
import com.pennywiseai.tracker.ui.components.profileFilterIcon
import com.pennywiseai.tracker.ui.components.CoverGradientBanner
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.GreetingCard
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.*
import com.pennywiseai.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/** Distance each card travels on the first-composition entrance animation. */
private val ENTRANCE_SLIDE_DISTANCE = Spacing.lg

/** Diameter of one avatar in the overlapping subscription stack. */
private val STACKED_AVATAR_SIZE = Dimensions.Icon.large

/** Horizontal step between stacked avatars — less than the diameter, so they
 *  overlap and read as a group. */
private val STACKED_AVATAR_STEP = Spacing.lg - Spacing.xs

/** The surface-coloured ring that separates one stacked avatar from the next. */
private val STACKED_AVATAR_RING = Spacing.xxs

@OptIn(ExperimentalMaterial3Api::class)
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
    onNavigateToLoans: () -> Unit = {},
    onNavigateToTransactionGroups: () -> Unit = {},
    onLoanClick: (Long) -> Unit = {},
    onNavigateToAddScreen: () -> Unit = {},
    onNavigateToManageAccounts: () -> Unit = {},
    onTransactionClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
    onTransactionTypeClick: (String?) -> Unit = {},
    onFabPositioned: (Rect) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isProEntitled by viewModel.isProEntitled.collectAsState()
    val currentCycleWindow by viewModel.currentCycleWindow.collectAsState()
    var showUpgradeSheet by rememberSaveable { mutableStateOf(false) }
    val deletedTransaction by viewModel.deletedTransaction.collectAsState()
    val smsScanWorkInfo by viewModel.smsScanWorkInfo.collectAsState()
    val groupSummaries by viewModel.groupSummaries.collectAsState()
    val showSharePrompt by viewModel.showSharePrompt.collectAsState()
    val activity = LocalActivity.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for full resync confirmation dialog
    var showFullResyncDialog by remember { mutableStateOf(false) }

    // Bottom sheet menu state
    var showMenuSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    // Period the sheet should open on. The monthly banner points it at the finished
    // month; opening from the overflow menu uses whatever the user saved.
    var sharePromptPeriod by remember { mutableStateOf<SharePeriod?>(null) }
    val sheetState = rememberModalBottomSheetState()

    // Profile filter dropdown state
    var showProfileFilterMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Haptic feedback
    val view = LocalView.current

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

    // Staggered entrance animation state — only animates on first composition
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val slideOffsetPx = with(density) { ENTRANCE_SLIDE_DISTANCE.roundToPx() }

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
                actionContent = {
                    val containerColor = MaterialTheme.colorScheme.surfaceContainer
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = Dimensions.Padding.content),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Subtle Pro discovery chip — yellow sparkle that ties
                        // back to the Settings → PennyWise Pro entry. Hidden
                        // for already-entitled users so it's never pushy.
                        // Tap → opens the same UpgradeSheet as Settings.
                        if (!isProEntitled) {
                            Box(
                                modifier = Modifier
                                    .size(Dimensions.Component.iconButton)
                                    .clip(CircleShape)
                                    .background(
                                        color = com.pennywiseai.tracker.ui.theme.yellow_light,
                                        shape = CircleShape,
                                    )
                                    .clickable(
                                        onClick = { showUpgradeSheet = true },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Upgrade to PennyWise Pro",
                                    tint = com.pennywiseai.tracker.ui.theme.yellow_dark,
                                    modifier = Modifier.size(Dimensions.Icon.inline),
                                )
                            }
                        }

                        // Business/Personal filter dropdown
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(Dimensions.Component.iconButton)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (blurEffects) containerColor.copy(0.5f) else containerColor,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        onClick = { showProfileFilterMenu = true },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = profileFilterIcon(uiState.profiles, uiState.selectedProfileId),
                                    contentDescription = "Profile filter",
                                    tint = MaterialTheme.colorScheme.inverseSurface,
                                    modifier = Modifier.size(Dimensions.Icon.inline)
                                )
                            }
                            ProfileFilterDropdown(
                                expanded = showProfileFilterMenu,
                                profiles = uiState.profiles,
                                selectedProfileId = uiState.selectedProfileId,
                                onProfileSelected = { viewModel.updateSelectedProfile(it) },
                                onDismiss = { showProfileFilterMenu = false }
                            )
                        }
                        // More options button
                        Box(
                            modifier = Modifier
                                .size(Dimensions.Component.iconButton)
                                .clip(CircleShape)
                                .background(
                                    color = if (blurEffects) containerColor.copy(0.5f) else containerColor,
                                    shape = CircleShape
                                )
                                .clickable(
                                    onClick = { showMenuSheet = true },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.inverseSurface,
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                },
                extraInfoCard = {
                    GreetingCard(
                        userName = uiState.userName,
                        profileImageUri = uiState.profileImageUri,
                        profileBackgroundColor = uiState.profileBackgroundColor,
                        onAvatarClick = onNavigateToSettings,
                        onMenuClick = { showMenuSheet = true },
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
                            onCurrencyClick = {
                                // Cycle through currencies when tapped
                                val currencies = uiState.availableCurrencies
                                if (currencies.size > 1) {
                                    val currentIdx = currencies.indexOf(uiState.selectedCurrency)
                                    val nextIdx = (currentIdx + 1) % currencies.size
                                    viewModel.selectCurrency(currencies[nextIdx])
                                }
                            },
                            onShowBreakdown = { viewModel.showBreakdownDialog() },
                            accountBalances = uiState.accountBalances,
                            creditCards = uiState.creditCards,
                            totalAvailableCredit = uiState.totalAvailableCredit,
                            onAccountClick = { bankName, accountLast4 ->
                                navController.navigate(
                                    com.pennywiseai.tracker.navigation.AccountDetail(
                                        bankName = bankName,
                                        accountLast4 = accountLast4
                                    )
                                ) { launchSingleTop = true }
                            }
                        )
                    }
                }
            }

            // 1.2. Monthly share prompt. Offers the user something ("your month, summed
            // up") rather than asking a favour, and only appears when the finished month
            // actually has enough in it to be worth sending.
            if (showSharePrompt) {
                item {
                    ShareMonthBanner(
                        onOpen = {
                            viewModel.markSharePromptHandled()
                            sharePromptPeriod = SharePeriod.LAST_MONTH
                            showShareSheet = true
                        },
                        onDismiss = { viewModel.markSharePromptHandled() },
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                    )
                }
            }

            // 1.5. Cash-flow card (25ms delay) — hides itself on dormant months.
            item {
                val visible = remember { mutableStateOf(hasAnimated) }
                LaunchedEffect(Unit) {
                    if (!hasAnimated) { delay(25); visible.value = true }
                }
                AnimatedVisibility(
                    visible = visible.value,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { slideOffsetPx },
                        animationSpec = tween(300)
                    )
                ) {
                    CashFlowCard(
                        currency = uiState.selectedCurrency,
                        creditCardSpend = uiState.currentMonthCreditCard,
                        investments = uiState.currentMonthInvestment,
                        transfers = uiState.currentMonthTransfer,
                        isBalanceHidden = uiState.isBalanceHidden,
                        onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() },
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                    )
                }
            }

            // 2. Budget Carousel (50ms delay)
            uiState.budgetSummary?.let { summary ->
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
                        ) {
                            SectionHeaderV2(
                                title = "Budgets",
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                                action = {
                                    TextButton(onClick = onNavigateToBudgets) {
                                        Text("View All")
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                }
                            )
                            BudgetCarousel(
                                summary = summary,
                                onClick = onNavigateToBudgets,
                                onCreateBudget = onNavigateToBudgets,
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                            )
                        }
                    }
                }
            }

            // 2.5. Loans Summary (75ms delay) — only when active loans exist
            uiState.loanSummary?.let { summary ->
                item {
                    val visible = remember { mutableStateOf(hasAnimated) }
                    LaunchedEffect(Unit) {
                        if (!hasAnimated) { delay(75); visible.value = true }
                    }
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = fadeIn(tween(300)) + slideInVertically(
                            initialOffsetY = { slideOffsetPx },
                            animationSpec = tween(300)
                        )
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
                        ) {
                            SectionHeaderV2(
                                title = "Loans",
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                                action = {
                                    TextButton(onClick = onNavigateToLoans) {
                                        Text("View All")
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                }
                            )
                            Box(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
                                ActiveLoansSummaryCard(
                                    loans = summary.activeLoans,
                                    totalLentRemaining = summary.totalLentRemaining,
                                    totalBorrowedRemaining = summary.totalBorrowedRemaining,
                                    currency = uiState.selectedCurrency,
                                    onClick = onNavigateToLoans
                                )
                            }
                        }
                    }
                }
            }

            // 2.5. Groups section (#664) — a horizontal rail of the user's
            // transaction groups, absent entirely when none exist so Home
            // stays uncluttered for everyone else. Discovery was the ask:
            // groups only surfaced via Settings or a group card that happened
            // to have recent activity.
            if (groupSummaries.isNotEmpty()) {
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
                        ) {
                            SectionHeaderV2(
                                title = "Groups",
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                                action = {
                                    TextButton(onClick = onNavigateToTransactionGroups) {
                                        Text("View All")
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                items(groupSummaries, key = { it.group.id }) { summary ->
                                    HomeGroupCard(
                                        summary = summary,
                                        onClick = { onGroupClick(summary.group.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Recent Transactions Section (100ms delay)
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
                    Column(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
                        SectionHeaderV2(
                            title = "Recent Transactions",
                            action = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Search button
                                    IconButton(
                                        onClick = onNavigateToTransactionsWithSearch,
                                        modifier = Modifier.size(Dimensions.Component.iconButton)
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
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.listGap)
                    ) {
                        repeat(5) {
                            TransactionItemSkeleton()
                        }
                    }
                }
            } else if (uiState.recentItems.isEmpty()) {
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
                        PennyWiseEmptyState(
                            icon = Icons.Default.Sync,
                            headline = "No transactions yet",
                            description = "Scan your SMS to get started — we'll find your transactions automatically",
                            actionLabel = "Scan Now",
                            onAction = { viewModel.scanSmsMessages() },
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            ghostContent = {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.Layout.listGap)
                                ) {
                                    repeat(3) {
                                        TransactionItemSkeleton()
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
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
                        Column(
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.listGap)
                        ) {
                            val profileAccountKeys = remember(uiState.accountBalances) {
                                buildProfileAccountKeys(uiState.accountBalances)
                            }
                            uiState.recentItems.forEach { item ->
                                when (item) {
                                    is HomeRecentItem.SingleTransaction -> TransactionItem(
                                        transaction = item.transaction,
                                        convertedAmount = item.convertedAmount,
                                        displayCurrency = if (uiState.isUnifiedMode) uiState.selectedCurrency else null,
                                        showTypeLabel = false,
                                        profileAccountKeys = profileAccountKeys,
                                        onClick = { onTransactionClick(item.transaction.id) }
                                    )
                                    is HomeRecentItem.GroupItem -> GroupCard(
                                        group = item.group,
                                        transactions = item.transactions,
                                        convertedAmounts = item.convertedAmounts,
                                        displayCurrency = if (uiState.isUnifiedMode) uiState.selectedCurrency else null,
                                        onClick = { onGroupClick(item.group.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Account Carousel (200ms delay)
            if (uiState.creditCards.isNotEmpty() || uiState.accountBalances.isNotEmpty()) {
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
                        ) {
                            SectionHeaderV2(
                                title = "Bank Accounts",
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                                action = {
                                    TextButton(onClick = onNavigateToManageAccounts) {
                                        Text("Manage")
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                }
                            )
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
                                    ) { launchSingleTop = true }
                                },
                                isUnifiedMode = uiState.isUnifiedMode,
                                selectedCurrency = uiState.selectedCurrency,
                                blurEffects = blurEffects,
                                hazeState = hazeStateBanner
                            )
                        }
                    }
                }
            }

            // 5. Upcoming Subscriptions Alert (250ms delay)
            if (uiState.upcomingSubscriptions.isNotEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
                        ) {
                            SectionHeaderV2(
                                title = "Upcoming Subscriptions",
                                modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                                action = {
                                    TextButton(onClick = onNavigateToSubscriptions) {
                                        Text("View All")
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(Dimensions.Icon.small)
                                        )
                                    }
                                }
                            )
                            Box(modifier = Modifier.padding(horizontal = Dimensions.Padding.content)) {
                                UpcomingSubscriptionsCard(
                                    subscriptions = uiState.upcomingSubscriptions,
                                    totalAmount = uiState.upcomingSubscriptionsTotal,
                                    totalByCurrency = uiState.upcomingSubscriptionsByCurrency,
                                    isUnified = uiState.isUnifiedMode,
                                    currency = uiState.selectedCurrency,
                                    onClick = onNavigateToSubscriptions,
                                    blurEffects = blurEffects,
                                    hazeState = hazeStateBanner
                                )
                            }
                        }
                    }
                }
            }

            // 6. Heatmap Widget (300ms delay)
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)
                    ) {
                        SectionHeaderV2(
                            title = "Activity",
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content)
                        )
                        com.pennywiseai.tracker.ui.components.cards.HeatmapWidget(
                            transactionHeatmap = uiState.transactionHeatmap,
                            modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
                            blurEffects = blurEffects,
                            hazeState = hazeStateBanner
                        )
                    }
                }
            }
        }
        
        // Scan FAB rotation animation
        val infiniteTransition = rememberInfiniteTransition(label = "scan_rotation")
        val rotationAngle by animateFloatAsState(
            targetValue = if (uiState.isScanning) 1f else 0f,
            animationSpec = tween(300),
            label = "scan_trigger"
        )
        val continuousRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scan_rotation"
        )
        val scanRotation = if (uiState.isScanning) continuousRotation else 0f

        // FABs - Direct access (no speed dial)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = Dimensions.Padding.content,
                    bottom = Dimensions.Component.fabBottomInset
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
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
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Surface(
                    modifier = Modifier
                        .spotlightTarget(onFabPositioned)
                        .size(Dimensions.Component.fab)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { viewModel.scanSmsMessages() },
                                onLongPress = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    showFullResyncDialog = true
                                }
                            )
                        },
                    shape = FloatingActionButtonDefaults.shape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = Dimensions.Elevation.fab,
                    tonalElevation = Dimensions.Elevation.fab,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync SMS (long press for full resync)",
                            modifier = if (uiState.isScanning) Modifier.rotate(scanRotation) else Modifier,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                // Hint for long-press functionality - only show for new users (no transactions yet)
                if (uiState.recentItems.isEmpty() && !uiState.isLoading) {
                    Text(
                        text = "Hold for full resync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        "Your loans, grouped transactions, and merchant mappings " +
                        "are preserved.\n\n" +
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

    // Share-card sheet, rendered at screen scope so dismissing the menu can't tear it down.
    if (showShareSheet) {
        ShareCardSheet(
            initialPeriod = sharePromptPeriod,
            onDismiss = { showShareSheet = false; sharePromptPeriod = null },
        )
    }

    // Avatar menu bottom sheet
    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.Layout.groupedListGap)
            ) {
                // Title
                Text(
                    text = "More Options",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = Spacing.sm)
                        .fillMaxWidth()
                )

                // Monthly recap (Top) — the growth loop lives here rather than buried
                // in the Subscriptions screen, where nobody would find it. Named for what
                // it is rather than "Share ...": the menu sits beside Settings and Rate,
                // and leading with the ask makes it read as a favour being requested.
                MenuListItem(
                    headline = "Monthly recap",
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    position = ListItemPosition.Top,
                    onClick = {
                        showMenuSheet = false
                        showShareSheet = true
                    }
                )

                // Settings (Middle)
                MenuListItem(
                    headline = "Settings",
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    position = ListItemPosition.Middle,
                    onClick = {
                        showMenuSheet = false
                        onNavigateToSettings()
                    }
                )

                // Join Discord (Middle)
                MenuListItem(
                    headline = "Join Discord for feedback",
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_discord),
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.Icon.medium)
                        )
                    },
                    position = ListItemPosition.Middle,
                    onClick = {
                        showMenuSheet = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.Links.DISCORD_URL))
                        context.startActivity(intent)
                    }
                )

                // Rate on Play Store (Bottom)
                MenuListItem(
                    headline = "Rate on Play Store",
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    position = ListItemPosition.Bottom,
                    onClick = {
                        showMenuSheet = false
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                            context.startActivity(intent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                            context.startActivity(intent)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Version footer
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (_: Exception) {
                        null
                    }
                }
                versionName?.let {
                    Text(
                        text = "v$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    }

    // Pro upgrade sheet — triggered from the subtle ✨ chip in the top bar
    // for free users; reuses the same composable Settings uses.
    if (showUpgradeSheet) {
        com.pennywiseai.tracker.presentation.paywall.UpgradeSheet(
            onDismiss = { showUpgradeSheet = false },
        )
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
    val currentPeriod = "${now.month.name.lowercase().let { s -> if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1) }} 1-${now.dayOfMonth}"
    val lastMonth = now.minusMonths(1)
    val lastPeriod = "${lastMonth.month.name.lowercase().let { s -> if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1) }} 1-${now.dayOfMonth}"
    
    Dialog(onDismissRequest = onDismiss) {
        PennyWiseCardV2(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            contentPadding = Dimensions.Padding.dialog
        ) {
            // Period blocks are the unit of meaning here, so the gap *between*
            // blocks (lg) is larger than the gap between a block's own rows
            // (sm). The previous uniform 16dp made the period label float
            // equidistant from both periods' figures.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Text(
                    text = "Calculation Breakdown",
                    style = MaterialTheme.typography.headlineSmall
                )

                BreakdownPeriod(
                    period = currentPeriod,
                    income = currentMonthIncome,
                    expenses = currentMonthExpenses,
                    net = currentMonthTotal,
                    currency = currency
                )

                BreakdownPeriod(
                    period = lastPeriod,
                    income = lastMonthIncome,
                    expenses = lastMonthExpenses,
                    net = lastMonthTotal,
                    currency = currency
                )

                PennyWiseCardV2(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.smd
                ) {
                    Text(
                        text = "Formula: Income - Expenses = Net Worth\n" +
                               "Green (+) = Savings | Red (-) = Overspending",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center
                    )
                }

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

/** One period's income / expenses / net block inside [BreakdownDialog]. */
@Composable
private fun BreakdownPeriod(
    period: String,
    income: BigDecimal,
    expenses: BigDecimal,
    net: BigDecimal,
    currency: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = period,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        BreakdownRow(label = "Income", amount = income, isIncome = true, currency = currency)
        BreakdownRow(label = "Expenses", amount = expenses, isIncome = false, currency = currency)
        HorizontalDivider()
        BreakdownRow(
            label = "Net Worth",
            amount = net,
            isIncome = net >= BigDecimal.ZERO,
            isBold = true,
            currency = currency
        )
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
            style = if (isBold) PennyWiseText.amountRow else PennyWiseText.amountRow.copy(
                fontWeight = FontWeight.Normal
            ),
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
    totalByCurrency: Map<String, com.pennywiseai.tracker.utils.Money> = emptyMap(),
    isUnified: Boolean = false,
    currency: String = "INR",
    onClick: () -> Unit = {},
    blurEffects: Boolean = false,
    hazeState: HazeState? = null
) {
    val containerColor = if (blurEffects)
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.secondaryContainer

    PennyWiseCardV2(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                    .hazeEffect(
                        state = hazeState,
                        block = fun HazeEffectScope.() {
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                tint = HazeDefaults.tint(containerColor),
                                blurRadius = 20.dp,
                                noiseFactor = -1f,
                            )
                            blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                        }
                    )
                else Modifier
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        contentPadding = Dimensions.Padding.content
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (subscriptions.isNotEmpty()) {
                    val maxIcons = 4
                    val visibleSubs = subscriptions.take(maxIcons)
                    val extraCount = subscriptions.size - maxIcons
                    // Overlapping avatar stack. The trailing Spacer reserves the
                    // stack's true width, since the offset children don't
                    // contribute to the Box's measured size — derive it from the
                    // same constants rather than restating the numbers.
                    val stackedCount = visibleSubs.size + if (extraCount > 0) 1 else 0
                    Box {
                        visibleSubs.forEachIndexed { index, sub ->
                            BrandIcon(
                                merchantName = sub.merchantName,
                                size = STACKED_AVATAR_SIZE,
                                modifier = Modifier
                                    .offset(x = STACKED_AVATAR_STEP * index)
                                    .zIndex((maxIcons - index).toFloat())
                                    .border(
                                        width = STACKED_AVATAR_RING,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                            )
                        }
                        if (extraCount > 0) {
                            Box(
                                modifier = Modifier
                                    .offset(x = STACKED_AVATAR_STEP * visibleSubs.size)
                                    .zIndex(0f)
                                    .size(STACKED_AVATAR_SIZE)
                                    .border(
                                        width = STACKED_AVATAR_RING,
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$extraCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(
                            modifier = Modifier
                                .width(
                                    STACKED_AVATAR_SIZE +
                                        STACKED_AVATAR_STEP * (stackedCount - 1).coerceAtLeast(0)
                                )
                                .height(STACKED_AVATAR_SIZE)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(Dimensions.Icon.medium)
                    )
                }
                Column {
                    Text(
                        text = "${subscriptions.size} active subscriptions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        // Unified mode: one converted figure. Native mode: per-currency
                        // ("₹499 · $10") so a mixed set isn't summed into a mislabel.
                        text = "Monthly total: " + if (isUnified) {
                            CurrencyFormatter.formatCurrency(totalAmount, currency)
                        } else {
                            CurrencyFormatter.formatByCurrency(
                                totalByCurrency,
                                fallbackCurrency = currency
                            )
                        },
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

@Composable
private fun ActiveLoansSummaryCard(
    loans: List<com.pennywiseai.tracker.data.database.entity.LoanEntity>,
    totalLentRemaining: java.math.BigDecimal,
    totalBorrowedRemaining: java.math.BigDecimal,
    currency: String,
    onClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val loanColor = if (isDark) loan_dark else loan_light

    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loan icon
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = loanColor,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${loans.size} active loan${if (loans.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                val subtitle = when {
                    totalLentRemaining > java.math.BigDecimal.ZERO && totalBorrowedRemaining > java.math.BigDecimal.ZERO ->
                        "${CurrencyFormatter.formatCurrency(totalLentRemaining, currency)} owed to you"
                    totalLentRemaining > java.math.BigDecimal.ZERO ->
                        "${CurrencyFormatter.formatCurrency(totalLentRemaining, currency)} owed to you"
                    else ->
                        "You owe ${CurrencyFormatter.formatCurrency(totalBorrowedRemaining, currency)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.labelLarge,
                color = loanColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * A row in the "More options" bottom sheet. Uses the shared grouped-list
 * primitives so it matches the rows in Settings exactly — this sheet is the
 * shortcut *into* Settings, so the two looking different was jarring.
 */
@Composable
private fun MenuListItem(
    headline: String,
    icon: @Composable () -> Unit,
    position: ListItemPosition,
    onClick: () -> Unit,
) {
    GroupedRow(
        position = position,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.secondary
        ) { icon() }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

