package com.pennywiseai.tracker.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
    val motion = MaterialTheme.motionScheme

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout) {
    NavHost(
        navController = navController,
        startDestination = stableStartDestination,
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        // M3 Expressive motion: spatial spring for the slide, effects spring
        // for the fade, both from the theme's MotionScheme.
        enterTransition = {
            fadeIn(motion.defaultEffectsSpec()) +
                slideInVertically(motion.defaultSpatialSpec()) { it / 4 }
        },
        exitTransition = { fadeOut(motion.fastEffectsSpec()) },
        popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
        popExitTransition = {
            fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { it / 4 }
        }
    ) {
        composable<AppLock>(
            // At once: a fade-in shows the screen it's locking through it.
            // The other three transitions fall back to the NavHost defaults.
            enterTransition = { EnterTransition.None }
        ) {
            com.pennywiseai.tracker.ui.screens.AppLockScreen(
                onUnlocked = {
                    // Back to the screen it covered. Pops AppLock itself, so a repeat
                    // call (a second success mid-transition) doesn't pop that screen too.
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack<AppLock>(inclusive = true)
                    } else {
                        navController.navigate(Home) {
                            popUpTo(AppLock) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable<OnBoarding> {
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
            enterTransition = { fadeIn(motion.defaultEffectsSpec()) },
            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
            popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
            popExitTransition = { fadeOut(motion.fastEffectsSpec()) }
        ) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                MainScreen(
                    rootNavController = navController
                )
            }
        }

        composable<Settings> {
            com.pennywiseai.tracker.ui.screens.settings.SettingsScreen(
                themeViewModel = themeViewModel,
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToCategories = {
                    navController.navigate(Categories) { launchSingleTop = true }
                },
                onNavigateToUnrecognizedSms = {
                    navController.navigate(UnrecognizedSms) { launchSingleTop = true }
                },
                onNavigateToFaq = {
                    navController.navigate(Faq) { launchSingleTop = true }
                },
                onNavigateToBudgets = {
                    navController.navigate(BudgetGroups()) { launchSingleTop = true }
                },
                onNavigateToExchangeRates = {
                    navController.navigate(ExchangeRates) { launchSingleTop = true }
                },
                onNavigateToImportStatement = {
                    navController.navigate(ImportStatement) { launchSingleTop = true }
                },
                onNavigateToTransactionGroups = {
                    navController.navigate(TransactionGroups) { launchSingleTop = true }
                },
                onNavigateToWebhooks = {
                    navController.navigate(Webhooks) { launchSingleTop = true }
                },
                onNavigateToAiAccess = {
                    navController.navigate(AiAccess) { launchSingleTop = true }
                }
            )
        }

        composable<AiAccess> {
            com.pennywiseai.tracker.ui.screens.settings.aiaccess.AiAccessScreen(
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable<Webhooks> {
            com.pennywiseai.tracker.ui.screens.settings.webhooks.WebhooksScreen(
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable<Categories> {
            com.pennywiseai.tracker.presentation.categories.CategoriesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<TransactionDetail>(
            enterTransition = { fadeIn(motion.defaultEffectsSpec()) },
            exitTransition = { fadeOut(motion.fastEffectsSpec()) },
            popEnterTransition = { fadeIn(motion.defaultEffectsSpec()) },
            popExitTransition = { fadeOut(motion.fastEffectsSpec()) }
        ) { backStackEntry ->
            val transactionDetail = backStackEntry.toRoute<TransactionDetail>()
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                com.pennywiseai.tracker.presentation.transactions.TransactionDetailScreen(
                    transactionId = transactionDetail.transactionId,
                    onNavigateBack = {
                        onEditComplete()
                        navController.safePopBackStack()
                    },
                    onNavigateToLoanDetail = { loanId ->
                        navController.navigate(LoanDetail(loanId)) {
                            launchSingleTop = true
                        }
                    },
                    onDuplicateTransaction = { sourceId ->
                        navController.navigate(AddTransaction(sourceTransactionId = sourceId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToGoal = { goalId ->
                        navController.navigate(GoalDetail(goalId)) { launchSingleTop = true }
                    },
                    onCreateGoal = {
                        navController.navigate(GoalEdit()) { launchSingleTop = true }
                    }
                )
            }
        }

        composable<AddTransaction> {
            com.pennywiseai.tracker.presentation.add.AddScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }
        
        composable<UnrecognizedSms> {
            com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onAddSuggestion = { draft ->
                    navController.navigate(draft) { launchSingleTop = true }
                }
            )
        }
        
        composable<Faq> {
            com.pennywiseai.tracker.ui.screens.settings.FAQScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<Rules> {
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
                },
                onNavigateToDuplicateRule = { ruleId ->
                    navController.navigate(CreateRule(duplicateFromId = ruleId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<CreateRule> { backStackEntry ->
            val createRule = backStackEntry.toRoute<CreateRule>()
            val rulesViewModel: com.pennywiseai.tracker.ui.viewmodel.RulesViewModel = hiltViewModel()

            // Collect rules from the flow to find the rule being edited or duplicated.
            val rules by rulesViewModel.rules.collectAsStateWithLifecycle()
            val isEditing = createRule.ruleId != null

            // Stable id for a duplicate, kept across recompositions/config changes.
            val duplicateId = rememberSaveable { java.util.UUID.randomUUID().toString() }

            // Edit prefills from the saved rule; duplicate prefills from the source but
            // becomes a fresh rule (new id, "(copy)" name) so saving inserts a new one.
            val prefillRule = when {
                createRule.ruleId != null ->
                    rules.firstOrNull { it.id == createRule.ruleId }
                createRule.duplicateFromId != null ->
                    rules.firstOrNull { it.id == createRule.duplicateFromId }?.let { source ->
                        source.copy(
                            id = duplicateId,
                            name = "${source.name} (copy)",
                            isSystemTemplate = false
                        )
                    }
                else -> null
            }
            val accounts by rulesViewModel.accounts.collectAsStateWithLifecycle()

            com.pennywiseai.tracker.ui.screens.rules.CreateRuleScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onSaveRule = { rule ->
                    // Edit updates in place; create/duplicate inserts a new rule.
                    if (isEditing) {
                        rulesViewModel.updateRule(rule)
                    } else {
                        rulesViewModel.createRule(rule)
                    }
                    navController.safePopBackStack()
                },
                existingRule = prefillRule,
                isEditing = isEditing,
                allAccounts = accounts
            )
        }
        
        composable<AccountDetail> {
            com.pennywiseai.tracker.presentation.accounts.AccountDetailScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onTransactionClick = { id ->
                    navController.navigate(TransactionDetail(id)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<BudgetGroups> { backStackEntry ->
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupsScreen(
                createGoalOnOpen = backStackEntry.toRoute<BudgetGroups>().createGoal,
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToGroupEdit = { groupId ->
                    navController.navigate(BudgetGroupEdit(groupId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToHistory = { groupId, year, month ->
                    navController.navigate(BudgetHistory(groupId, year, month)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCategory = { category, yearMonth, currency ->
                    navController.navigate(TransactionsWithFilter(category, yearMonth, currency)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToGoal = { goalId ->
                    navController.navigate(GoalDetail(goalId)) { launchSingleTop = true }
                },
                onNavigateToGoalEdit = { goalId ->
                    navController.navigate(GoalEdit(goalId)) { launchSingleTop = true }
                }
            )
        }

        composable<GoalDetail> {
            com.pennywiseai.tracker.presentation.goals.GoalDetailScreen(
                onNavigateBack = { navController.safePopBackStack() },
                onEdit = { goalId -> navController.navigate(GoalEdit(goalId)) { launchSingleTop = true } },
                onOpenTransaction = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId)) { launchSingleTop = true }
                },
                // Repeat for next year opens the new copy in place of this one.
                onOpenGoal = { goalId ->
                    navController.navigate(GoalDetail(goalId)) {
                        popUpTo<GoalDetail> { inclusive = true }
                    }
                }
            )
        }

        composable<GoalEdit> {
            com.pennywiseai.tracker.presentation.goals.GoalEditScreen(
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable<BudgetGroupEdit> {
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetGroupEditScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<BudgetHistory> {
            com.pennywiseai.tracker.presentation.budgetgroups.BudgetHistoryScreen(
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable<Loans> {
            com.pennywiseai.tracker.presentation.loans.LoansScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToLoanDetail = { loanId ->
                    navController.navigate(LoanDetail(loanId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<RecurringTransactions> {
            com.pennywiseai.tracker.presentation.recurring.RecurringTransactionsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<LoanDetail> {
            com.pennywiseai.tracker.presentation.loans.LoanDetailScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToTransactionDetail = { txId ->
                    navController.navigate(TransactionDetail(txId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<TransactionGroups> {
            com.pennywiseai.tracker.presentation.groups.TransactionGroupsScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToGroupDetail = { groupId ->
                    navController.navigate(TransactionGroupDetail(groupId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<TransactionGroupDetail> {
            com.pennywiseai.tracker.presentation.groups.TransactionGroupDetailScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                },
                onNavigateToTransactionDetail = { txId ->
                    navController.navigate(TransactionDetail(txId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<ExchangeRates> {
            com.pennywiseai.tracker.presentation.exchangerates.ExchangeRatesScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<ImportStatement> {
            com.pennywiseai.tracker.presentation.statement.ImportStatementScreen(
                onNavigateBack = {
                    navController.safePopBackStack()
                }
            )
        }

        composable<TransactionsWithFilter> { backStackEntry ->
            val args = backStackEntry.toRoute<TransactionsWithFilter>()
            com.pennywiseai.tracker.presentation.transactions.TransactionsScreen(
                initialCategory = args.category,
                initialPeriod = args.period,
                initialCurrency = args.currency,
                onNavigateBack = { navController.safePopBackStack() },
                onTransactionClick = { transactionId ->
                    navController.navigate(TransactionDetail(transactionId)) { launchSingleTop = true }
                },
                onAddTransactionClick = {
                    navController.navigate(AddTransaction()) { launchSingleTop = true }
                },
                onNavigateToSettings = {
                    navController.navigate(Settings) { launchSingleTop = true }
                }
            )
        }

    }
    }
    }
}
