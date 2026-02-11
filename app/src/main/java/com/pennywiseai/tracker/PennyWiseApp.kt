package com.pennywiseai.tracker

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.pennywiseai.tracker.navigation.AppLock
import com.pennywiseai.tracker.navigation.Home
import com.pennywiseai.tracker.navigation.Permission
import com.pennywiseai.tracker.navigation.PennyWiseNavHost
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.viewmodel.AppLockViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.widget.RecentTransactionsWidgetUpdateWorker

@Composable
fun PennyWiseApp(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    appLockViewModel: AppLockViewModel = hiltViewModel(),
    editTransactionId: Long? = null,
    openAddTransaction: Boolean = false,
    onEditComplete: () -> Unit = {},
    onAddTransactionShortcutHandled: () -> Unit = {}
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val darkTheme = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle events and refresh lock state when app resumes from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // App came to foreground - check if it should be locked
                appLockViewModel.refreshLockState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Determine initial destination based on app lock and permissions
    val startDestination = remember {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        // Note: We can't check app lock or hasSkippedPermission here because they're async from DataStore
        // App lock will be checked after initial load via LaunchedEffect
        if (hasSmsPermission) Home else Permission
    }

    // Observe lock state changes and navigate to lock screen if needed
    // But don't navigate when user is actively in Settings configuring app lock
    LaunchedEffect(appLockUiState.isLocked, appLockUiState.isLockEnabled) {
        if (appLockUiState.isLocked && appLockUiState.isLockEnabled) {
            val currentRoute = navController.currentDestination?.route
            // Don't navigate if already on lock screen or in Settings (user is configuring)
            if (currentRoute != AppLock::class.qualifiedName &&
                currentRoute != com.pennywiseai.tracker.navigation.Settings::class.qualifiedName) {
                navController.navigate(AppLock) {
                    // Don't add to back stack, force lock screen
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }
    
    // Navigate to transaction detail when editTransactionId changes
    LaunchedEffect(editTransactionId) {
        editTransactionId?.let { transactionId ->
            navController.navigate(com.pennywiseai.tracker.navigation.TransactionDetail(transactionId))
        }
    }

    // Navigate directly to Add Transaction when requested (e.g., from widget shortcut)
    LaunchedEffect(openAddTransaction) {
        if (openAddTransaction) {
            navController.navigate(com.pennywiseai.tracker.navigation.AddTransaction) {
                launchSingleTop = true
            }
            onAddTransactionShortcutHandled()
        }
    }

    // Keep widgets current when app launches (covers upgrades/installs with existing widgets)
    LaunchedEffect(Unit) {
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context.applicationContext)
    }

    PennyWiseTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.isDynamicColorEnabled
    ) {
        PennyWiseNavHost(
            navController = navController,
            themeViewModel = themeViewModel,
            startDestination = startDestination,
            onEditComplete = onEditComplete
        )
    }
}
