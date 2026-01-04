package com.pennywiseai.tracker.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pennywiseai.tracker.ui.MainScreen
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import java.net.URLEncoder

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
    
    NavHost(
        navController = navController,
        startDestination = stableStartDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable<AppLock>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.AppLockScreen(
                onUnlocked = {
                    navController.navigate(Home) {
                        popUpTo(AppLock) { inclusive = true }
                    }
                }
            )
        }
        composable<Permission>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(Home) {
                        popUpTo(Permission) { inclusive = true }
                    }
                }
            )
        }
        composable<Home>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            MainScreen(
                rootNavController = navController
            )
        }
        
        composable<Settings>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.settings.SettingsScreen(
                themeViewModel = themeViewModel,
                onNavigateBack = {
                    navController.popBackStack()
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
                    navController.navigate(Budgets)
                }
            )
        }
        
        composable<Categories>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable<TransactionDetail>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val transactionDetail = backStackEntry.toRoute<TransactionDetail>()
            com.pennywiseai.tracker.presentation.transactions.TransactionDetailScreen(
                transactionId = transactionDetail.transactionId,
                onNavigateBack = {
                    onEditComplete()
                    navController.popBackStack()
                }
            )
        }
        
        composable<AddTransaction>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.presentation.add.AddScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable<UnrecognizedSms>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable<Faq>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<Rules>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.rules.RulesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCreateRule = {
                    navController.navigate(CreateRule())
                },
                onNavigateToEditRule = { ruleId ->
                    navController.navigate(CreateRule(ruleId = ruleId))
                }
            )
        }

        composable<CreateRule>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
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
                    navController.popBackStack()
                },
                onSaveRule = { rule ->
                    rulesViewModel.createRule(rule)
                    navController.popBackStack()
                },
                existingRule = existingRule
            )
        }
        
        composable<AccountDetail>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val accountDetail = backStackEntry.toRoute<AccountDetail>()
            com.pennywiseai.tracker.presentation.accounts.AccountDetailScreen(
                navController = navController
            )
        }

        composable<Budgets>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            com.pennywiseai.tracker.presentation.budgets.BudgetsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCreateBudget = {
                    navController.navigate(CreateBudget())
                },
                onNavigateToEditBudget = { budgetId ->
                    navController.navigate(CreateBudget(budgetId = budgetId))
                },
                onNavigateToTransactions = { budgetWithSpending ->
                    val budget = budgetWithSpending.budget
                    // Build categories string: comma-separated, URL encoded
                    // Only include categories if budget doesn't include all categories
                    val categoriesParam = if (!budget.includeAllCategories && budgetWithSpending.categories.isNotEmpty()) {
                        budgetWithSpending.categories.joinToString(",") { cat ->
                            URLEncoder.encode(cat, "UTF-8")
                        }
                    } else {
                        null
                    }

                    navController.navigate(
                        BudgetTransactions(
                            startDateEpochDay = budget.startDate.toEpochDay(),
                            endDateEpochDay = budget.endDate.toEpochDay(),
                            currency = budget.currency,
                            categories = categoriesParam
                        )
                    )
                }
            )
        }

        composable<CreateBudget>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val createBudget = backStackEntry.toRoute<CreateBudget>()
            com.pennywiseai.tracker.presentation.budgets.CreateBudgetScreen(
                budgetId = createBudget.budgetId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<BudgetTransactions>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val budgetTx = backStackEntry.toRoute<BudgetTransactions>()
            com.pennywiseai.tracker.presentation.transactions.TransactionsScreen(
                initialStartDateEpochDay = budgetTx.startDateEpochDay,
                initialEndDateEpochDay = budgetTx.endDateEpochDay,
                initialCurrency = budgetTx.currency,
                initialCategories = budgetTx.categories,
                initialTransactionType = "EXPENSE",
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTransactionClick = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId))
                },
                onAddTransactionClick = {
                    navController.navigate(AddTransaction)
                },
                onNavigateToSettings = {
                    navController.navigate(Settings)
                }
            )
        }

    }
}