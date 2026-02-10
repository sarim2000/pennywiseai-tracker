package com.pennywiseai.tracker.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri
import com.pennywiseai.tracker.R
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pennywiseai.tracker.presentation.home.HomeScreen
import com.pennywiseai.tracker.presentation.subscriptions.SubscriptionsScreen
import com.pennywiseai.tracker.presentation.transactions.TransactionsScreen
import com.pennywiseai.tracker.ui.components.PennyWiseBottomNavigation
import com.pennywiseai.tracker.ui.components.SpotlightTutorial
import com.pennywiseai.tracker.ui.components.WhatsNewDialog
import com.pennywiseai.tracker.ui.screens.settings.SettingsScreen
import com.pennywiseai.tracker.ui.viewmodel.MainViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.ui.viewmodel.SpotlightViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    rootNavController: NavHostController? = null,
    navController: NavHostController = rememberNavController(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    spotlightViewModel: SpotlightViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
    initialCategory: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // Extract base route name (without query parameters) for title matching
    val baseRoute = currentRoute?.substringBefore("?") ?: ""
    val spotlightState by spotlightViewModel.spotlightState.collectAsState()

    // What's New dialog state
    val whatsNewVersion by mainViewModel.whatsNewVersion.collectAsState()

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
            navController.navigate(route)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // What's New Dialog
        whatsNewVersion?.let { version ->
            WhatsNewDialog(
                version = version,
                onDismiss = { mainViewModel.dismissWhatsNew() }
            )
        }
            Scaffold(
            topBar = {
            val context = LocalContext.current
            PennyWiseTopAppBar(
                title = when (baseRoute) {
                    "home" -> "PennyWise"
                    "transactions" -> "Transactions"
                    "subscriptions" -> "Subscriptions"
                    "analytics" -> "Analytics"
                    "chat" -> "PennyWise AI"
                    "settings" -> "Settings"
                    "categories" -> "Categories"
                    "unrecognized_sms" -> "Unrecognized Messages"
                    "manage_accounts" -> "Manage Accounts"
                    "add_account" -> "Add Account"
                    "faq" -> "Help & FAQ"
                    else -> "PennyWise"
                },
                showBackButton = baseRoute in listOf("chat", "settings", "subscriptions", "transactions", "categories", "unrecognized_sms", "manage_accounts", "add_account", "faq"),
                showSettingsButton = baseRoute !in listOf("settings", "categories", "unrecognized_sms", "manage_accounts", "add_account", "faq"),
                showDiscordButton = baseRoute !in listOf("settings", "categories", "unrecognized_sms", "manage_accounts", "add_account", "faq"), // Hide on these screens
                onBackClick = { navController.popBackStack() },
                onSettingsClick = { navController.navigate("settings") },
                onDiscordClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/H3xWeMWjKQ"))
                    context.startActivity(intent)
                }
            )
        },
        bottomBar = {
            // Show bottom navigation only for main screens
            if (baseRoute in listOf("home", "analytics")) {
                PennyWiseBottomNavigation(navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("home") {
                val homeViewModel: com.pennywiseai.tracker.presentation.home.HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    navController = rootNavController ?: navController,
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onNavigateToTransactions = {
                        navController.navigate("transactions")
                    },
                    onNavigateToTransactionsWithSearch = {
                        navController.navigate("transactions?focusSearch=true")
                    },
                    onNavigateToSubscriptions = {
                        navController.navigate("subscriptions")
                    },
                    onNavigateToBudgets = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.BudgetGroups
                        )
                    },
                    onNavigateToAddScreen = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.AddTransaction
                        )
                    },
                    onTransactionClick = { transactionId ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
                        )
                    },
                    onTransactionTypeClick = { type ->
                        val route = if (type != null) {
                            "transactions?type=$type"
                        } else {
                            "transactions"
                        }
                        navController.navigate(route)
                    },
                    onFabPositioned = { position ->
                        spotlightViewModel.updateFabPosition(position)
                    }
                )
            }
            
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
                )
            ) { backStackEntry ->
                val category = backStackEntry.arguments?.getString("category")
                val merchant = backStackEntry.arguments?.getString("merchant")
                val period = backStackEntry.arguments?.getString("period")
                val currency = backStackEntry.arguments?.getString("currency")
                val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: false
                val transactionType = backStackEntry.arguments?.getString("type")

                TransactionsScreen(
                    initialCategory = category,
                    initialMerchant = merchant,
                    initialPeriod = period,
                    initialCurrency = currency,
                    focusSearch = focusSearch,
                    initialTransactionType = transactionType,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onTransactionClick = { transactionId ->
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)
                        )
                    },
                    onAddTransactionClick = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.AddTransaction
                        )
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }
            
            composable("subscriptions") {
                SubscriptionsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onAddSubscriptionClick = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.AddTransaction
                        )
                    }
                )
            }
            
            composable("analytics") {
                com.pennywiseai.tracker.ui.screens.analytics.AnalyticsScreen(
                    onNavigateToChat = { navController.navigate("chat") },
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
                        navController.navigate(route)
                    },
                    onNavigateToHome = { navController.navigate("home") }
                )
            }
            
            composable("chat") {
                com.pennywiseai.tracker.ui.screens.chat.ChatScreen(
                    modifier = Modifier.imePadding(),
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    }
                )
            }
            
            composable("settings") {
                SettingsScreen(
                    themeViewModel = themeViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToCategories = {
                        navController.navigate("categories")
                    },
                    onNavigateToUnrecognizedSms = {
                        navController.navigate("unrecognized_sms")
                    },
                    onNavigateToManageAccounts = {
                        navController.navigate("manage_accounts")
                    },
                    onNavigateToFaq = {
                        navController.navigate("faq")
                    },
                    onNavigateToRules = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.Rules
                        )
                    },
                    onNavigateToBudgets = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.BudgetGroups
                        )
                    },
                    onNavigateToExchangeRates = {
                        rootNavController?.navigate(
                            com.pennywiseai.tracker.navigation.ExchangeRates
                        )
                    }
                )
            }
            
            composable("categories") {
                com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("unrecognized_sms") {
                com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("faq") {
                com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable("manage_accounts") {
                com.pennywiseai.tracker.presentation.accounts.ManageAccountsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToAddAccount = {
                        navController.navigate("add_account")
                    }
                )
            }
            
            composable("add_account") {
                com.pennywiseai.tracker.presentation.accounts.AddAccountScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PennyWiseTopAppBar(
    title: String,
    showBackButton: Boolean = false,
    showSettingsButton: Boolean = true,
    showDiscordButton: Boolean = true,
    onBackClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onDiscordClick: () -> Unit = {}
) {
    Column {
        TopAppBar(
            title = { Text(title) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            ),
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            },
            actions = {
                if (showDiscordButton) {
                    IconButton(onClick = onDiscordClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_discord),
                            contentDescription = "Join Discord Community",
                            tint = Color(0xFF5865F2) // Discord brand color
                        )
                    }
                }
                if (showSettingsButton) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            }
        )
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}