package com.pennywiseai.tracker.ui

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.contacts.displayMerchantName
import com.pennywiseai.tracker.navigation.AccountDetail
import com.pennywiseai.tracker.navigation.AddAccount
import com.pennywiseai.tracker.navigation.AddTransaction
import com.pennywiseai.tracker.navigation.AiAccess
import com.pennywiseai.tracker.navigation.BackgroundAccess
import com.pennywiseai.tracker.navigation.LearnedCategories
import com.pennywiseai.tracker.navigation.Analytics
import com.pennywiseai.tracker.navigation.Appearance
import com.pennywiseai.tracker.navigation.BalanceHistory
import com.pennywiseai.tracker.navigation.BudgetGroups
import com.pennywiseai.tracker.navigation.Categories
import com.pennywiseai.tracker.navigation.Chat
import com.pennywiseai.tracker.navigation.ExchangeRates
import com.pennywiseai.tracker.navigation.Faq
import com.pennywiseai.tracker.navigation.GoalDetail
import com.pennywiseai.tracker.navigation.Home
import com.pennywiseai.tracker.navigation.ImportStatement
import com.pennywiseai.tracker.navigation.LoanDetail
import com.pennywiseai.tracker.navigation.Loans
import com.pennywiseai.tracker.navigation.ManageAccounts
import com.pennywiseai.tracker.navigation.RecurringTransactions
import com.pennywiseai.tracker.navigation.Rules
import com.pennywiseai.tracker.navigation.Settings
import com.pennywiseai.tracker.navigation.SettingsPage
import com.pennywiseai.tracker.navigation.SettingsSection
import com.pennywiseai.tracker.navigation.Subscriptions
import com.pennywiseai.tracker.navigation.TransactionDetail
import com.pennywiseai.tracker.navigation.TransactionGroupDetail
import com.pennywiseai.tracker.navigation.TransactionGroups
import com.pennywiseai.tracker.navigation.Transactions
import com.pennywiseai.tracker.navigation.UnrecognizedSms
import com.pennywiseai.tracker.navigation.Webhooks
import com.pennywiseai.tracker.navigation.navigate
import com.pennywiseai.tracker.navigation.rememberEntryDecorators
import com.pennywiseai.tracker.navigation.withRouteArgs
import com.pennywiseai.tracker.presentation.navigation.BottomNavItem
import com.pennywiseai.tracker.ui.animations.pwPop
import com.pennywiseai.tracker.ui.animations.pwPredictivePop
import com.pennywiseai.tracker.ui.animations.pwPush
import com.pennywiseai.tracker.presentation.accounts.AddAccountScreen
import com.pennywiseai.tracker.presentation.accounts.ManageAccountsScreen
import com.pennywiseai.tracker.presentation.accounts.ManageAccountsViewModel
import com.pennywiseai.tracker.presentation.home.HomeScreen
import com.pennywiseai.tracker.presentation.statement.ImportStatementScreen
import com.pennywiseai.tracker.presentation.statement.ImportStatementViewModel
import com.pennywiseai.tracker.presentation.subscriptions.SubscriptionsScreen
import com.pennywiseai.tracker.presentation.transactions.TransactionsScreen
import com.pennywiseai.tracker.data.preferences.NavBarStyle
import com.pennywiseai.tracker.ui.components.LocalHideNavBar
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.NavDockState
import com.pennywiseai.tracker.ui.components.SpotlightTutorial
import com.pennywiseai.tracker.ui.components.WhatsNewDialog
import com.pennywiseai.tracker.presentation.settings.AppearanceScreen
import com.pennywiseai.tracker.presentation.settings.FAQScreen
import com.pennywiseai.tracker.presentation.settings.SettingsScreen
import com.pennywiseai.tracker.presentation.settings.SettingsSectionScreen
import com.pennywiseai.tracker.ui.viewmodel.MainViewModel
import com.pennywiseai.tracker.ui.viewmodel.SpotlightViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** The tab a key's screen belongs to in the bar, if it's a tab's own screen. */
private fun tabOf(key: NavKey?): BottomNavItem? = when (key) {
    Home -> BottomNavItem.Home
    is Transactions -> BottomNavItem.Transactions
    Analytics -> BottomNavItem.Analytics
    Chat -> BottomNavItem.Chat
    else -> null
}

/** A tab's plain first screen. */
private fun rootOf(tab: BottomNavItem): NavKey = when (tab) {
    BottomNavItem.Home -> Home
    BottomNavItem.Transactions -> Transactions()
    BottomNavItem.Analytics -> Analytics
    BottomNavItem.Chat -> Chat
}

/** Pops this stack back to its first screen, which becomes [root]. */
private fun NavBackStack<NavKey>.resetTo(root: NavKey) {
    subList(1, size).clear()
    set(0, root)
}

/**
 * A tab switch fades through; a push or pop inside one tab slides like the root host
 * ([pwPush] / [pwPop], [pwPredictivePop] under a back swipe). A scene belongs to the tab whose stack it tops: after a switch
 * both the old and new screens still top their stacks, while an in-tab push or pop
 * leaves the old screen buried or removed.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.tabTransition(
    tabTops: Map<BottomNavItem, Any?>,
    motion: MotionScheme,
    pop: Boolean,
    predictive: Boolean = false,
): ContentTransform {
    fun tabTopping(scene: Scene<NavKey>): BottomNavItem? {
        val key = scene.entries.lastOrNull()?.contentKey ?: return null
        return tabTops.entries.firstOrNull { it.value == key }?.key
    }
    val from = tabTopping(initialState)
    val to = tabTopping(targetState)
    return when {
        from != null && to != null && from != to ->
            fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
        predictive -> pwPredictivePop()
        pop -> pwPop(motion)
        else -> pwPush(motion)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    rootBackStack: NavBackStack<NavKey>? = null,
    themeViewModel: ThemeViewModel? = null,
    spotlightViewModel: SpotlightViewModel? = null,
    mainViewModel: MainViewModel? = null
) {
    val themeViewModel = themeViewModel ?: hiltViewModel()
    val spotlightViewModel = spotlightViewModel ?: hiltViewModel()
    val mainViewModel = mainViewModel ?: hiltViewModel()
    // Root-level screens (details, editors) open above the tabs.
    val openRoot: (NavKey) -> Unit = { rootBackStack?.navigate(it) }

    // One back stack per bottom tab, each keeping its own screens and state while
    // another tab shows. Home's stays underneath the others, so Back from another
    // tab's first screen lands on Home.
    val tabStacks = mapOf(
        BottomNavItem.Home to rememberNavBackStack(Home),
        BottomNavItem.Transactions to rememberNavBackStack(Transactions()),
        BottomNavItem.Analytics to rememberNavBackStack(Analytics),
        BottomNavItem.Chat to rememberNavBackStack(Chat),
    )
    var currentTabRoute by rememberSaveable { mutableStateOf(BottomNavItem.Home.route) }
    val currentTab = BottomNavItem.all.firstOrNull { it.route == currentTabRoute } ?: BottomNavItem.Home
    val stack = tabStacks.getValue(currentTab)
    val topKey = stack.last()
    // The bar shows on a tab-level screen and always highlights the tab on screen,
    // even when that tab drilled into another tab's screen (Analytics → Transactions).
    val baseRoute = if (tabOf(topKey) != null) currentTab.route else ""
    val spotlightState by spotlightViewModel.spotlightState.collectAsState()
    val themeState by themeViewModel.themeUiState.collectAsState()

    // Each tab's screens act on that tab's own stack (entries are built once,
    // so they mustn't capture which tab is current).
    fun back(tab: BottomNavItem) {
        val tabStack = tabStacks.getValue(tab)
        when {
            tabStack.size > 1 -> tabStack.removeAt(tabStack.lastIndex)
            // Back from a tab's first screen goes Home; the tab starts over plain.
            tab != BottomNavItem.Home -> {
                currentTabRoute = BottomNavItem.Home.route
                tabStack.resetTo(rootOf(tab))
            }
        }
    }

    // A screen's own back arrow: only while it's still on top (double taps).
    fun backFrom(tab: BottomNavItem, key: NavKey) {
        if (tabStacks.getValue(tab).lastOrNull() == key) back(tab)
    }

    // A tab's own screen opened from Home ("See all", a type filter) switches to
    // that tab, fresh with those filters; anything else opens in the same tab.
    fun open(tab: BottomNavItem, key: NavKey) {
        val target = tabOf(key)
        if (tab == BottomNavItem.Home && target != null && target != BottomNavItem.Home) {
            val fresh = if (key is Transactions) key.copy(openId = System.nanoTime()) else key
            tabStacks.getValue(target).resetTo(fresh)
            currentTabRoute = target.route
        } else if (target != null && target != tab && key == rootOf(target)) {
            // A plain tab root from another tab (e.g. Chat from Analytics): switch to it, don't duplicate it.
            currentTabRoute = target.route
        } else {
            tabStacks.getValue(tab).navigate(key)
        }
    }

    // What a Settings row opens lives either in this tab's graph or above the tabs.
    fun openFromSettings(tab: BottomNavItem, key: NavKey) = when (key) {
        Rules, Loans, RecurringTransactions, ExchangeRates, TransactionGroups, Webhooks, AiAccess, BackgroundAccess, LearnedCategories,
        is BudgetGroups -> openRoot(key)
        else -> open(tab, key)
    }

    fun selectTab(tab: BottomNavItem) {
        currentTabRoute = tab.route
    }

    // Tab bar tap: show that tab as it was left; reselecting the tab on screen
    // pops it back to its first screen (as navigation-compose's popUpTo did).
    // Reads the tab from state at tap time: Compose memoizes `::onTabClick`, so a
    // captured `currentTab` would be stale (reselect never popped).
    fun onTabClick(tab: BottomNavItem) {
        if (tab.route == currentTabRoute) tabStacks.getValue(tab).resetTo(rootOf(tab)) else selectTab(tab)
    }

    // What's New dialog state
    val whatsNewVersion by mainViewModel.whatsNewVersion.collectAsState()

    // UPI VPA → contact name. Read the toggle as state; the lambda closes
    // over the current value + the singleton resolver, and is provided
    // through a CompositionLocal so every transaction-rendering composable
    // (TransactionItem, TransactionDetailScreen header, etc.) can apply the
    // same rule without prop-drilling the resolver everywhere.
    val useContactsForVpa by mainViewModel.useContactsForVpa.collectAsState()
    val merchantAliases by mainViewModel.merchantAliases.collectAsState()
    val merchantDisplay = remember(useContactsForVpa, merchantAliases) {
        { raw: String? ->
            displayMerchantName(raw, useContactsForVpa, mainViewModel.contactsResolver, merchantAliases)
        }
    }

    // Haze state for blur effects
    val hazeState = remember { HazeState() }

    // The floating bar docks the current tab's primary action beside it. Offered
    // while prefs load too, so Home doesn't flash its own FABs before the bar
    // style is known.
    val navDock = remember { NavDockState() }
    val dockForTabs = navDock.takeIf {
        themeState.isLoading || themeState.navBarStyle == NavBarStyle.FLOATING
    }
    // Set by a tab drawing its own bottom toolbar (Transactions' bulk select).
    val hideNavBar = remember { mutableStateOf(false) }

    // Hiding the tab you're on (or the one under Settings) sends you Home, once,
    // on the next tab you land on. Other ways into a hidden tab's screen (e.g.
    // Home cards) keep working afterwards. Saveable so a rotation between
    // hiding and going back doesn't drop the redirect. (#912)
    val hiddenTabs = themeState.hiddenNavTabs
    var lastHiddenTabs by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    var fallBackPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(themeState.isLoading, hiddenTabs) {
        if (themeState.isLoading) return@LaunchedEffect
        if (lastHiddenTabs?.containsAll(hiddenTabs) == false) fallBackPending = true
        lastHiddenTabs = ArrayList(hiddenTabs)
    }
    LaunchedEffect(fallBackPending, baseRoute) {
        if (!fallBackPending || BottomNavItem.all.none { it.route == baseRoute }) return@LaunchedEffect
        fallBackPending = false
        BottomNavItem.fallbackFor(baseRoute, hiddenTabs)?.let {
            val left = currentTab
            selectTab(BottomNavItem.Home)
            if (left != BottomNavItem.Home) tabStacks.getValue(left).resetTo(rootOf(left))
        }
    }

    // Home's scan, for the Spotlight tutorial drawn over the bar (set by Home's entry).
    var scanSms by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Entries read state through State objects (themeState), not captured values:
    // an entry's content is built once per back stack change.
    fun tabEntries(tab: BottomNavItem): (NavKey) -> NavEntry<NavKey> = withRouteArgs(entryProvider {
        entry<Home> { key ->
                val homeViewModel: com.pennywiseai.tracker.presentation.home.HomeViewModel = hiltViewModel()
                DisposableEffect(homeViewModel) {
                    scanSms = homeViewModel::scanSmsMessages
                    onDispose { scanSms = null }
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onAccountClick = { bankName, accountLast4 ->
                        openRoot(AccountDetail(bankName = bankName, accountLast4 = accountLast4))
                    },
                    coverStyle = themeState.coverStyle,
                    blurEffects = themeState.blurEffectsEnabled,
                    onNavigateToSettings = {
                        open(tab, Settings)
                    },
                    onNavigateToTransactions = {
                        open(tab, Transactions())
                    },
                    onNavigateToTransactionsWithSearch = {
                        open(tab, Transactions(focusSearch = true))
                    },
                    onNavigateToReview = { kind ->
                        openRoot(com.pennywiseai.tracker.navigation.Review(kind.name))
                    },
                    onNavigateToSubscriptions = {
                        open(tab, Subscriptions)
                    },
                    onNavigateToBudgets = {
                        openRoot(BudgetGroups())
                    },
                    onNavigateToLoans = {
                        openRoot(Loans)
                    },
                    onLoanClick = { loanId ->
                        openRoot(LoanDetail(loanId))
                    },
                    onNavigateToManageAccounts = {
                        open(tab, ManageAccounts)
                    },
                    onNavigateToAddScreen = {
                        openRoot(AddTransaction())
                    },
                    onTransactionClick = { transactionId ->
                        openRoot(TransactionDetail(transactionId))
                    },
                    onNavigateToTransactionGroups = {
                        openRoot(TransactionGroups)
                    },
                    onGroupClick = { groupId ->
                        openRoot(TransactionGroupDetail(groupId))
                    },
                    onGoalClick = { goalId ->
                        openRoot(GoalDetail(goalId))
                    },
                    onTransactionTypeClick = { type ->
                        open(tab, Transactions(type = type))
                    },
                    onFabPositioned = { position ->
                        spotlightViewModel.updateFabPosition(position)
                    }
                )
        }

        entry<Transactions> { key ->
                TransactionsScreen(
                    modifier = Modifier.imePadding(),
                    initialCategory = key.category,
                    initialMerchant = key.merchant,
                    initialPeriod = key.period,
                    initialCurrency = key.currency,
                    initialCustomStartEpochDay = key.startDate,
                    initialCustomEndEpochDay = key.endDate,
                    initialAccount = key.account,
                    initialTag = key.tag,
                    focusSearch = key.focusSearch,
                    initialTransactionType = key.type,
                    // No back arrow for a plain tab tap; show it for a filtered
                    // drill-down (Home category/search) so the user can return. (#635)
                    showBackButton = key != Transactions(),
                    // The bottom nav is overlaid on this route — always reserve clearance.
                    reserveBottomBarSpace = true,
                    onNavigateBack = {
                        backFrom(tab, key)
                    },
                    onTransactionClick = { transactionId ->
                        openRoot(TransactionDetail(transactionId))
                    },
                    onAddTransactionClick = {
                        openRoot(AddTransaction())
                    }
                )
        }

        entry<Subscriptions> { key ->
                SubscriptionsScreen(
                    onNavigateBack = {
                        backFrom(tab, key)
                    },
                    onAddSubscriptionClick = {
                        openRoot(AddTransaction())
                    }
                )
        }

        entry<Analytics> { key ->
                com.pennywiseai.tracker.presentation.analytics.AnalyticsScreen(
                    onNavigateToChat = {
                        if (BottomNavItem.Chat.route !in themeState.hiddenNavTabs) {
                            open(tab, Chat)
                        }
                    },
                    onNavigateToTransactions = { drill ->
                        open(
                            tab,
                            Transactions(
                                category = drill.category,
                                merchant = drill.merchant,
                                period = drill.period,
                                currency = drill.currency,
                                startDate = drill.startDateEpochDay,
                                endDate = drill.endDateEpochDay,
                                account = drill.account,
                                tag = drill.tag,
                                type = drill.type
                            )
                        )
                    },
                    onNavigateToHome = {
                        selectTab(BottomNavItem.Home)
                    }
                )
        }

        entry<Chat> { key ->
                // Hidden AI tab: don't start the chat (and its model) for
                // the frame before the fallback to Home lands.
                if (BottomNavItem.Chat.route !in themeState.hiddenNavTabs) {
                    com.pennywiseai.tracker.presentation.chat.ChatScreen(
                        modifier = Modifier.imePadding(),
                        onNavigateToSettings = {
                            open(tab, SettingsPage(SettingsSection.AI))
                        }
                    )
                }
        }

        entry<Settings> { key ->
                SettingsScreen(
                    onNavigateBack = { backFrom(tab, key) },
                    onNavigate = { openFromSettings(tab, it) }
                )
        }

        entry<SettingsPage> { key ->
                SettingsSectionScreen(
                    section = key.section,
                    onNavigateBack = { backFrom(tab, key) },
                    onNavigate = { openFromSettings(tab, it) }
                )
        }

        entry<Appearance> { key ->
                AppearanceScreen(
                    onNavigateBack = {
                        backFrom(tab, key)
                    },
                    themeViewModel = themeViewModel
                )
        }

        entry<Categories> { key ->
                com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                    onNavigateBack = {
                        backFrom(tab, key)
                    }
                )
        }

        entry<UnrecognizedSms> { key ->
                com.pennywiseai.tracker.presentation.unrecognized.UnrecognizedSmsScreen(
                    onNavigateBack = {
                        backFrom(tab, key)
                    },
                    onAddSuggestion = { draft ->
                        openRoot(draft)
                    }
                )
        }

        entry<Faq> { key ->
                FAQScreen(
                    onNavigateBack = {
                        backFrom(tab, key)
                    }
                )
        }

        entry<ManageAccounts> { key ->
                val viewModel: ManageAccountsViewModel = hiltViewModel()
                ManageAccountsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        backFrom(tab, key)
                    },
                    onNavigateToAddAccount = {
                        open(tab, AddAccount)
                    },
                    onNavigateToBalanceHistory = { bankName, accountLast4 ->
                        open(tab, BalanceHistory(bankName, accountLast4))
                    }
                )
        }

        entry<BalanceHistory> { key ->
                com.pennywiseai.tracker.presentation.accounts.BalanceHistoryScreen(
                    onNavigateBack = {
                        backFrom(tab, key)
                    }
                )
        }

        entry<ImportStatement> { key ->
                val viewModel: ImportStatementViewModel = hiltViewModel()
                ImportStatementScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        backFrom(tab, key)
                    }
                )
        }

        entry<AddAccount> { key ->
                val viewModel: ManageAccountsViewModel = hiltViewModel()
                AddAccountScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        backFrom(tab, key)
                    }
                )
        }
    })
    val decoratedTabs = BottomNavItem.all.associateWith { tab ->
        key(tab.route) {
            rememberDecoratedNavEntries(tabStacks.getValue(tab), rememberEntryDecorators(), tabEntries(tab))
        }
    }
    val motion = MaterialTheme.motionScheme
    val tabTops = decoratedTabs.mapValues { it.value.lastOrNull()?.contentKey }
    val shownEntries = decoratedTabs.getValue(BottomNavItem.Home) +
        if (currentTab == BottomNavItem.Home) emptyList() else decoratedTabs.getValue(currentTab)

    CompositionLocalProvider(
        LocalMerchantDisplay provides merchantDisplay,
        LocalNavDock provides dockForTabs,
        LocalHideNavBar provides hideNavBar,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // What's New Dialog
            whatsNewVersion?.let { version ->
                WhatsNewDialog(
                    version = version,
                    onDismiss = { mainViewModel.dismissWhatsNew() }
                )
            }

            // NavDisplay — NO padding, fills the full screen. Tab switches are a
            // fast fade-through; pushes and pops within a tab slide like the root
            // host's, and a back gesture previews the pop (cancelling restores it).
            // Only the top enabled back handler gets a gesture: this one while a tab
            // has somewhere to go back to, else the root's (never both at rest).
            NavDisplay(
                entries = shownEntries,
                onBack = { back(currentTab) },
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                transitionSpec = { tabTransition(tabTops, motion, pop = false) },
                popTransitionSpec = { tabTransition(tabTops, motion, pop = true) },
                predictivePopTransitionSpec = { tabTransition(tabTops, motion, pop = true, predictive = true) }
            )

            // Bottom navigation OVERLAID on content
            // Wait for prefs so a hidden tab never flashes in on launch.
            // A pushed screen hides the bar with its own exit (it never slides with
            // the screen); it keeps the last tab selected while it leaves.
            val onTabScreen = baseRoute in listOf("home", "transactions", "analytics", "chat")
            val lastTabRoute = remember { arrayOf(baseRoute) }
            if (onTabScreen) lastTabRoute[0] = baseRoute
            if (!themeState.isLoading) {
                PennyWiseBottomNavigation(
                    selectedRoute = lastTabRoute[0],
                    onTabSelected = ::onTabClick,
                    navBarStyle = themeState.navBarStyle,
                    // Step aside while typing (Transactions search, Chat) rather
                    // than sit under the keyboard with the docked action.
                    visible = onTabScreen && !WindowInsets.isImeVisible && !hideNavBar.value,
                    hiddenTabs = hiddenTabs,
                    blurEffects = themeState.blurEffectsEnabled,
                    hazeState = hazeState,
                    dock = dockForTabs,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Spotlight Tutorial overlay - outside Scaffold to overlay everything
            if (baseRoute == "home" && spotlightState.showTutorial && spotlightState.fabPosition != null) {
                SpotlightTutorial(
                    isVisible = true,
                    targetPosition = spotlightState.fabPosition,
                    message = stringResource(R.string.spotlight_scan_sms),
                    onDismiss = {
                        spotlightViewModel.dismissTutorial()
                    },
                    onTargetClick = {
                        scanSms?.invoke()
                    }
                )
            }
        }
    }
}
