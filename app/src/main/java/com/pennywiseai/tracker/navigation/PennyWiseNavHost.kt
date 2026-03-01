package com.pennywiseai.tracker.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pennywiseai.tracker.ui.LocalNavAnimatedVisibilityScope
import com.pennywiseai.tracker.ui.LocalSharedTransitionScope
import com.pennywiseai.tracker.ui.MainScreen
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel

/**
 * Safe version of popBackStack that prevents rapid back presses from causing
 * screen overlap. Only pops if the current entry is fully RESUMED.
 */
fun NavHostController.safePopBackStack() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

@Composable
fun PennyWiseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    startDestination: Any = Home,
    onEditComplete: () -> Unit = {}
) {
    // Use a stable start destination
    val stableStartDestination = remember { startDestination }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
    NavHost(
        navController = navController,
        startDestination = stableStartDestination,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
    ) {
        composable<AppLock>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.ui.screens.AppLockScreen(
                onUnlocked = {
                    navController.navigate(Home) {
                        launchSingleTop = true
                        popUpTo(AppLock) { inclusive = true }
                    }
                }
            )
        }
        composable<OnBoarding>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.ui.screens.onboarding.OnBoardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Home) {
                        launchSingleTop = true
                        popUpTo(OnBoarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Home>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                MainScreen(
                    rootNavController = navController
                )
            }
        }

        composable<HomeWithCategoryFilter>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val args = backStackEntry.toRoute<HomeWithCategoryFilter>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                MainScreen(
                    rootNavController = navController,
                    initialCategory = args.category,
                    initialPeriod = args.period,
                    initialCurrency = args.currency
                )
            }
        }

        composable<Settings>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.ui.screens.settings.SettingsScreen(
                themeViewModel = themeViewModel,
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToCategories = {
                    navController.navigate(Categories)
                },
                onNavigateToUnrecognizedSms = {
                    navController.navigate(UnrecognizedSms)
                },
                onNavigateToFaq = {
                    navController.navigate(Faq)
                },
                onNavigateToBudgets = {
                    navController.navigate(BudgetGroups)
                },
                onNavigateToExchangeRates = {
                    navController.navigate(ExchangeRates)
                }
            )
        }

        composable<Categories>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<TransactionDetail>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val transactionDetail = backStackEntry.toRoute<TransactionDetail>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                com.pennywiseai.tracker.presentation.transactions.TransactionDetailScreen(
                    transactionId = transactionDetail.transactionId,
                    onNavigateBack = {
                        onEditComplete()
                        navController.safePopBackStack()
                    }
                )
            }
        }
        
        composable<AddTransaction>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.presentation.add.AddScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<UnrecognizedSms>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<Faq>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<Rules>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.ui.screens.rules.RulesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToCreateRule = {
                    navController.navigate(CreateRule()) {
                        launchSingleTop = true
                    }
                },
                onNavigateToEditRule = { ruleId ->
                    navController.navigate(CreateRule(ruleId = ruleId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<CreateRule>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) { backStackEntry ->
            val createRule = backStackEntry.toRoute<CreateRule>()
            val rulesViewModel: com.pennywiseai.tracker.ui.viewmodel.RulesViewModel = hiltViewModel()

            // Collect rules from the flow to find the existing rule
            val rules by rulesViewModel.rules.collectAsStateWithLifecycle()
            val existingRule = createRule.ruleId?.let { ruleId ->
                rules.firstOrNull { it.id == ruleId }
            }

            com.pennywiseai.tracker.ui.screens.rules.CreateRuleScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onSaveRule = { rule ->
                    rulesViewModel.createRule(rule)
                    navController.safePopBackStack()
                },
                existingRule = existingRule
            )
        }
        
        composable<AccountDetail>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) { backStackEntry ->
            val accountDetail = backStackEntry.toRoute<AccountDetail>()
            com.pennywiseai.tracker.presentation.accounts.AccountDetailScreen(
                navController = navController
            )
        }

        composable<BudgetGroups>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToGroupEdit = { groupId ->
                    navController.navigate(BudgetGroupEdit(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCategory = { category, yearMonth, currency ->
                    navController.navigate(HomeWithCategoryFilter(category, yearMonth, currency)) {
                        launchSingleTop = true
                        popUpTo(Home) { inclusive = true }
                    }
                }
            )
        }

        composable<BudgetGroupEdit>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupEditScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<MonthlyBudget>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            // Redirect old budget screen to new budget groups
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(BudgetGroups) {
                    launchSingleTop = true
                    popUpTo(MonthlyBudget) { inclusive = true }
                }
            }
        }

        composable<MonthlyBudgetSettings>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.presentation.monthlybudget.MonthlyBudgetSettingsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<ExchangeRates>(
            enterTransition = { fadeIn(tween(300)) + slideInVertically { it / 4 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(300)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutVertically { it / 4 } }
        ) {
            com.pennywiseai.tracker.presentation.exchangerates.ExchangeRatesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

    }
    }
    }
}