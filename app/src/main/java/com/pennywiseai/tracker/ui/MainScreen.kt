package com.pennywiseai.tracker.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri
import com.pennywiseai.tracker.R
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.GreetingCard
import com.pennywiseai.tracker.ui.components.PennyWiseBottomNavigation
import com.pennywiseai.tracker.data.preferences.CoverStyle
import com.pennywiseai.tracker.ui.components.SpotlightTutorial
import com.pennywiseai.tracker.ui.components.WhatsNewDialog
import com.pennywiseai.tracker.ui.screens.settings.AppearanceScreen
import com.pennywiseai.tracker.ui.screens.settings.SettingsScreen
import com.pennywiseai.tracker.ui.viewmodel.MainViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.ui.viewmodel.SpotlightViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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
    val themeState by themeViewModel.themeUiState.collectAsState()

    // What's New dialog state
    val whatsNewVersion by mainViewModel.whatsNewVersion.collectAsState()

    // Scroll behaviors for collapsible TopAppBar
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()

    // Haze state for blur effects
    val hazeState = remember { HazeState() }

    val context = LocalContext.current

    val isHomeScreen = baseRoute == "home"

    val title = when (baseRoute) {
        "home" -> "PennyWise"
        "transactions" -> "Transactions"
        "subscriptions" -> "Subscriptions"
        "analytics" -> "Analytics"
        "chat" -> "PennyWise AI"
        "settings" -> "Settings"
        "appearance" -> "Appearance"
        "categories" -> "Categories"
        "unrecognized_sms" -> "Unrecognized Messages"
        "manage_accounts" -> "Manage Accounts"
        "add_account" -> "Add Account"
        "faq" -> "Help & FAQ"
        else -> "PennyWise"
    }

    val showBackButton = baseRoute in listOf(
        "subscriptions", "transactions",
        "categories", "unrecognized_sms", "manage_accounts", "add_account", "faq", "appearance"
    )

    val showActionButtons = baseRoute !in listOf(
        "settings", "categories", "unrecognized_sms", "manage_accounts", "add_account", "faq", "appearance"
    )

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
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CustomTitleTopAppBar(
                    scrollBehaviorSmall = scrollBehaviorSmall,
                    scrollBehaviorLarge = scrollBehaviorLarge,
                    title = title,
                    isHomeScreen = isHomeScreen,
                    hasBackButton = showBackButton,
                    hasActionButton = showActionButtons,
                    navigationContent = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actionContent = {
                        if (showActionButtons) {
                            Row {
                                IconButton(onClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://discord.gg/H3xWeMWjKQ")
                                    )
                                    context.startActivity(intent)
                                }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_discord),
                                        contentDescription = "Join Discord Community",
                                        tint = Color(0xFF5865F2)
                                    )
                                }
                                IconButton(onClick = {
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings"
                                    )
                                }
                            }
                        }
                    },
                    extraInfoCard = {
                        if (isHomeScreen) {
                            GreetingCard(
                                userName = themeState.userName,
                                profileImageUri = themeState.profileImageUri,
                                profileBackgroundColor = themeState.profileBackgroundColor,
                                onSettingsClick = {
                    navController.navigate("settings") {
                        launchSingleTop = true
                    }
                },
                                onDiscordClick = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://discord.gg/H3xWeMWjKQ")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                    },
                    userName = themeState.userName,
                    profileImageUri = themeState.profileImageUri,
                    profileBackgroundColor = themeState.profileBackgroundColor,
                    hazeState = hazeState,
                    blurEffects = themeState.blurEffectsEnabled
                )
            },
            bottomBar = {}
        ) { paddingValues ->
            // paddingValues from Scaffold (TopAppBar height) — passed to each screen
            // NavHost has NO .padding(paddingValues) so screens can draw edge-to-edge
            // (like Cashiro: banner sits at y=0, behind the transparent TopAppBar)
            val topBarPadding = paddingValues.calculateTopPadding()

            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
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
                        coverStyle = themeState.coverStyle,
                        blurEffects = themeState.blurEffectsEnabled,
                        topBarPadding = topBarPadding,
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
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
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

                    Box(modifier = Modifier.padding(top = topBarPadding)) {
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
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    )
                    }
                }

                composable("subscriptions") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    SubscriptionsScreen(
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        },
                        onAddSubscriptionClick = {
                            rootNavController?.navigate(
                                com.pennywiseai.tracker.navigation.AddTransaction
                            )
                        }
                    )
                    }
                }

                composable("analytics") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
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
                                launchSingleTop = true
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                    }
                }

                composable("chat") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    com.pennywiseai.tracker.ui.screens.chat.ChatScreen(
                        modifier = Modifier.imePadding(),
                        onNavigateToSettings = {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    )
                    }
                }

                composable("settings") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    SettingsScreen(
                        themeViewModel = themeViewModel,
                        onNavigateBack = {
                            navController.popBackStack()
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
                        },
                        onNavigateToAppearance = {
                            navController.navigate("appearance") {
                                launchSingleTop = true
                            }
                        }
                    )
                    }
                }

                composable("appearance") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    AppearanceScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        themeViewModel = themeViewModel
                    )
                    }
                }

                composable("categories") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                    }
                }

                composable("unrecognized_sms") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                    }
                }

                composable("faq") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                    }
                }

                composable("manage_accounts") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    com.pennywiseai.tracker.presentation.accounts.ManageAccountsScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        },
                        onNavigateToAddAccount = {
                            navController.navigate("add_account") {
                                launchSingleTop = true
                            }
                        }
                    )
                    }
                }

                composable("add_account") {
                    Box(modifier = Modifier.padding(top = topBarPadding)) {
                    com.pennywiseai.tracker.presentation.accounts.AddAccountScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                    }
                }
            }
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
}
