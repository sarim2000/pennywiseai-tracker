package com.pennywiseai.tracker.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.pennywiseai.tracker.presentation.accounts.AccountDetailScreen
import com.pennywiseai.tracker.presentation.transactions.TransactionDetailScreen
import com.pennywiseai.tracker.ui.MainScreen
import com.pennywiseai.tracker.ui.screens.PermissionScreen
import com.pennywiseai.tracker.ui.screens.settings.SettingsScreen
import com.pennywiseai.tracker.ui.screens.settings.FAQScreen
import com.pennywiseai.tracker.ui.screens.unrecognized.UnrecognizedSmsScreen
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel

@Composable
fun PennyWiseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    startDestination: Any = Home,
    editTransactionId: Long? = null,
    onEditComplete: () -> Unit = {}
) {
    // Use a stable start destination
    val stableStartDestination = remember { startDestination }
    
    // Navigate to edit transaction if provided
    editTransactionId?.let { transactionId ->
        androidx.compose.runtime.LaunchedEffect(transactionId) {
            navController.navigate(TransactionDetail(transactionId = transactionId))
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = stableStartDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable<Permission>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            PermissionScreen(
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
            SettingsScreen(
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
            val isFromNotification = editTransactionId == transactionDetail.transactionId
            
            TransactionDetailScreen(
                transactionId = transactionDetail.transactionId,
                onNavigateBack = {
                    if (isFromNotification) {
                        onEditComplete()
                        // Navigate to home if came from notification
                        navController.navigate(Home) {
                            popUpTo(Home) { inclusive = false }
                        }
                    } else {
                        navController.popBackStack()
                    }
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
            UnrecognizedSmsScreen(
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
            FAQScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable<AccountDetail>(
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val accountDetail = backStackEntry.toRoute<AccountDetail>()
            AccountDetailScreen(
                navController = navController
            )
        }
        
    }
}