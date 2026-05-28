package com.pennywiseai.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import com.pennywiseai.tracker.data.contacts.LocalMerchantDisplay
import com.pennywiseai.tracker.data.contacts.displayMerchantName
import com.pennywiseai.tracker.presentation.accounts.AddAccountScreen
import com.pennywiseai.tracker.presentation.accounts.ManageAccountsScreen
import com.pennywiseai.tracker.presentation.accounts.ManageAccountsViewModel
import com.pennywiseai.tracker.presentation.home.HomeScreen
import com.pennywiseai.tracker.presentation.statement.ImportStatementScreen
import com.pennywiseai.tracker.presentation.statement.ImportStatementViewModel
import com.pennywiseai.tracker.presentation.subscriptions.SubscriptionsScreen
import com.pennywiseai.tracker.presentation.transactions.TransactionsScreen
import com.pennywiseai.tracker.presentation.categories.CategoriesViewModel
import com.pennywiseai.tracker.ui.components.PennyWiseBottomNavigation
import com.pennywiseai.tracker.ui.components.SpotlightTutorial
import com.pennywiseai.tracker.ui.components.WhatsNewDialog
import com.pennywiseai.tracker.ui.screens.settings.AppearanceScreen
import com.pennywiseai.tracker.ui.screens.settings.SettingsScreen
import com.pennywiseai.tracker.ui.screens.settings.FAQScreen
import com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsViewModel
import com.pennywiseai.tracker.ui.viewmodel.MainViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.ui.viewmodel.SpotlightViewModel
import com.pennywiseai.tracker.ui.screens.settings.SettingsViewModel
import com.pennywiseai.tracker.navigation.safePopBackStack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun MainScreen(
    rootNavController: NavHostController? = null,
    navController: NavHostController? = null,
    themeViewModel: ThemeViewModel? = null,
    spotlightViewModel: SpotlightViewModel? = null,
    mainViewModel: MainViewModel? = null,
    initialCategory: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null
) {
    val navController = navController ?: rememberNavController()
    val themeViewModel = themeViewModel ?: hiltViewModel()
    val spotlightViewModel = spotlightViewModel ?: hiltViewModel()
    val mainViewModel = mainViewModel ?: hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = currentRoute?.substringBefore("?") ?: ""
    val spotlightState by spotlightViewModel.spotlightState.collectAsState()
    val themeState by themeViewModel.themeUiState.collectAsState()

    // What's New dialog state
    val whatsNewVersion by mainViewModel.whatsNewVersion.collectAsState()

    // UPI VPA → contact name. Read the toggle as state; the lambda closes
    // over the current value + the singleton resolver, and is provided
    // through a CompositionLocal so every transaction-rendering composable
    // (TransactionItem, TransactionDetailScreen header, etc.) can apply the
    // same rule without prop-drilling the resolver everywhere.
    val useContactsForVpa by mainViewModel.useContactsForVpa.collectAsState()
    val merchantDisplay = remember(useContactsForVpa) {
        { raw: String? -> displayMerchantName(raw, useContactsForVpa, mainViewModel.contactsResolver) }
    }

    // Haze state for blur effects
    val hazeState = remember { HazeState() }

    val isHomeScreen = baseRoute == "home"

    // Back from non-Home tab goes to Home instead of popping (prevents overlap)
    BackHandler(enabled = !isHomeScreen) {
        navController.navigate("home") {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Navigate to transactions with filter if provided
    LaunchedEffect(initialCategory) {
        if (initialCategory != null) {
            val route = buildString {
                append("transactions")
                val params = mutableListOf<String>()
                val encoded = java.net.URLEncoder.encode(initialCategory, "UTF-8")
                params.add("category=$encoded")
                initialPeriod?.let { params.add("period=$it") }
                initialCurrency?.let { params.add("currency=$it") }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(LocalMerchantDisplay provides merchantDisplay) {
    Box(modifier = Modifier
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

        // NavHost — NO padding, fills the full screen
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(
                route = "home",
                content = { _: NavBackStackEntry ->
                    val homeViewModel: com.pennywiseai.tracker.presentation.home.HomeViewModel = hiltViewModel()
                    HomeScreen(
                        viewModel = homeViewModel,
                        navController = rootNavController ?: navController,
                        coverStyle = themeState.coverStyle,
                        blurEffects = themeState.blurEffectsEnabled,
                        onNavigateToSettings = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToTransactions = {
                            navController.navigate("transactions") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToTransactionsWithSearch = {
                            navController.navigate("transactions?focusSearch=true") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToSubscriptions = {
                            navController.navigate("subscriptions") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToBudgets = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.BudgetGroups
                            ) { launchSingleTop = true }
                        },
                        onNavigateToLoans = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.Loans
                            ) { launchSingleTop = true }
                        },
                        onLoanClick = { loanId ->
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.LoanDetail(loanId)
                            ) { launchSingleTop = true }
                        },
                        onNavigateToManageAccounts = {
                            navController.navigate("manage_accounts") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAddScreen = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.AddTransaction
                            ) { launchSingleTop = true }
                        },
                        onTransactionClick = { transactionId ->
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
                            ) { launchSingleTop = true }
                        },
                        onGroupClick = { groupId ->
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.TransactionGroupDetail(groupId)
                            ) { launchSingleTop = true }
                        },
                        onTransactionTypeClick = { type ->
                            val route = if (type != null) {
                                "transactions?type=$type"
                            } else {
                                "transactions"
                            }
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        },
                        onFabPositioned = { position ->
                            spotlightViewModel.updateFabPosition(position)
                        }
                    )
                }
            )

            composable(
                route = "transactions?category={category}&merchant={merchant}&period={period}&currency={currency}&focusSearch={focusSearch}&type={type}",
                arguments = listOf(
                    navArgument("category") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("merchant") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("period") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("currency") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("focusSearch") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                content = { backStackEntry: NavBackStackEntry ->
                    val category = backStackEntry.arguments?.getString("category")
                    val merchant = backStackEntry.arguments?.getString("merchant")
                    val period = backStackEntry.arguments?.getString("period")
                    val currency = backStackEntry.arguments?.getString("currency")
                    val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: false
                    val transactionType = backStackEntry.arguments?.getString("type")

                    TransactionsScreen(
                        modifier = Modifier.imePadding(),
                        initialCategory = category,
                        initialMerchant = merchant,
                        initialPeriod = period,
                        initialCurrency = currency,
                        focusSearch = focusSearch,
                        initialTransactionType = transactionType,
                        onNavigateBack = {
                            navController.safePopBackStack()
                        },
                        onTransactionClick = { transactionId ->
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
                            ) { launchSingleTop = true }
                        },
                        onAddTransactionClick = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.AddTransaction
                            ) { launchSingleTop = true }
                        },
                        onNavigateToSettings = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            )

            composable(
                route = "subscriptions",
                content = { _: NavBackStackEntry ->
                    SubscriptionsScreen(
                        onNavigateBack = {
                            if (navController.currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED) {
                                if (!navController.popBackStack()) {
                                    navController.navigate("home") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        },
                        onAddSubscriptionClick = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.AddTransaction
                            ) { launchSingleTop = true }
                        }
                    )
                }
            )

            composable(
                route = "analytics",
                content = { _: NavBackStackEntry ->
                    com.pennywiseai.tracker.ui.screens.analytics.AnalyticsScreen(
                        onNavigateToChat = {
                            navController.navigate("chat") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToTransactions = { category, merchant, period, currency ->
                            val route = buildString {
                                append("transactions")
                                val params = mutableListOf<String>()
                                category?.let {
                                    val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                    params.add("category=$encoded")
                                }
                                merchant?.let {
                                    val encoded = java.net.URLEncoder.encode(it, "UTF-8")
                                    params.add("merchant=$encoded")
                                }
                                period?.let {
                                    params.add("period=$it")
                                }
                                currency?.let {
                                    params.add("currency=$it")
                                }
                                if (params.isNotEmpty()) {
                                    append("?")
                                    append(params.joinToString("&"))
                                }
                            }
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToHome = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            )

            composable(
                route = "chat",
                content = { _: NavBackStackEntry ->
                    com.pennywiseai.tracker.ui.screens.chat.ChatScreen(
                        modifier = Modifier.imePadding(),
                        onNavigateToSettings = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            )

            composable(
                route = "settings",
                content = { _: NavBackStackEntry ->
                    SettingsScreen(
                        themeViewModel = themeViewModel,
                        onNavigateBack = {
                            navController.safePopBackStack()
                        },
                        onNavigateToCategories = {
                            navController.navigate("categories") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToUnrecognizedSms = {
                            navController.navigate("unrecognized_sms") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToManageAccounts = {
                            navController.navigate("manage_accounts") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToFaq = {
                            navController.navigate("faq") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToRules = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.Rules
                            ) { launchSingleTop = true }
                        },
                        onNavigateToBudgets = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.BudgetGroups
                            ) { launchSingleTop = true }
                        },
                        onNavigateToLoans = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.Loans
                            ) { launchSingleTop = true }
                        },
                        onNavigateToExchangeRates = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.ExchangeRates
                            ) { launchSingleTop = true }
                        },
                        onNavigateToAppearance = {
                            navController.navigate("appearance") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToImportStatement = {
                            navController.navigate("import_statement") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToTransactionGroups = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.TransactionGroups
                            ) { launchSingleTop = true }
                        }
                    )
                }
            )

            composable(
                route = "appearance",
                content = { _: NavBackStackEntry ->
                    AppearanceScreen(
                        onNavigateBack = {
                            navController.safePopBackStack()
                        },
                        themeViewModel = themeViewModel
                    )
                }
            )

            composable(
                route = "categories",
                content = { _: NavBackStackEntry ->
                    com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                        onNavigateBack = {
                            navController.safePopBackStack()
                        }
                    )
                }
            )

            composable(
                route = "unrecognized_sms",
                content = { _: NavBackStackEntry ->
                    com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                        onNavigateBack = {
                            navController.safePopBackStack()
                        }
                    )
                }
            )

            composable(
                route = "faq",
                content = { _: NavBackStackEntry ->
                    FAQScreen(
                        onNavigateBack = {
                            navController.safePopBackStack()
                        }
                    )
                }
            )

            composable(
                route = "manage_accounts",
                content = { _: NavBackStackEntry ->
                    val viewModel: ManageAccountsViewModel = hiltViewModel()
                    ManageAccountsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.safePopBackStack()
                        },
                        onNavigateToAddAccount = {
                            navController.navigate("add_account") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            )

            composable(
                route = "import_statement",
                content = { _: NavBackStackEntry ->
                    val viewModel: ImportStatementViewModel = hiltViewModel()
                    ImportStatementScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.safePopBackStack()
                        }
                    )
                }
            )

            composable(
                route = "add_account",
                content = { _: NavBackStackEntry ->
                    val viewModel: ManageAccountsViewModel = hiltViewModel()
                    AddAccountScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.safePopBackStack()
                        }
                    )
                }
            )
        }

        // Bottom navigation OVERLAID on content
        if (baseRoute in listOf("home", "analytics", "chat")) {
            PennyWiseBottomNavigation(
                navController = navController,
                currentDestination = navBackStackEntry?.destination,
                navBarStyle = themeState.navBarStyle,
                blurEffects = themeState.blurEffectsEnabled,
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Spotlight Tutorial overlay - outside Scaffold to overlay everything
        if (baseRoute == "home" && spotlightState.showTutorial && spotlightState.fabPosition != null) {
            val homeViewModel: com.pennywiseai.tracker.presentation.home.HomeViewModel? =
                navController.currentBackStackEntry?.let { hiltViewModel(it) }

            SpotlightTutorial(
                isVisible = true,
                targetPosition = spotlightState.fabPosition,
                message = "Tap here to scan your SMS messages for transactions",
                onDismiss = {
                    spotlightViewModel.dismissTutorial()
                },
                onTargetClick = {
                    homeViewModel?.scanSmsMessages()
                }
            )
        }
    }
    } // CompositionLocalProvider
}
