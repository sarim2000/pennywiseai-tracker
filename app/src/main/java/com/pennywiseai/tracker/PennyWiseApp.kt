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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pennywiseai.tracker.navigation.AppLock
import com.pennywiseai.tracker.navigation.Home
import com.pennywiseai.tracker.navigation.OnBoarding
import com.pennywiseai.tracker.navigation.PennyWiseNavHost
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.viewmodel.AppLockViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.widget.WidgetRefresher

@Composable
fun PennyWiseApp(
    themeViewModel: ThemeViewModel? = null,
    appLockViewModel: AppLockViewModel? = null,
    editTransactionId: Long? = null,
    openAddTransaction: Boolean = false,
    onEditComplete: (() -> Unit)? = null,
    onAddTransactionShortcutHandled: (() -> Unit)? = null,
    sharedText: String? = null,
    onSharedTextHandled: (() -> Unit)? = null,
    widgetDestination: Any? = null,
    onWidgetDestinationHandled: (() -> Unit)? = null
) {
    val owner = androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current ?: error("No ViewModelStoreOwner")
    val actualThemeViewModel: ThemeViewModel = themeViewModel ?: hiltViewModel(viewModelStoreOwner = owner, key = null)
    val actualAppLockViewModel: AppLockViewModel = appLockViewModel ?: hiltViewModel(viewModelStoreOwner = owner, key = null)

    val themeUiState by actualThemeViewModel.themeUiState.collectAsStateWithLifecycle()
    val appLockUiState by actualAppLockViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val darkTheme = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle events and refresh lock state when app resumes from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // App came to foreground - check if it should be locked
                actualAppLockViewModel.refreshLockState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Wait for preferences to load before deciding start destination
    if (themeUiState.isLoading) {
        return
    }

    // Migrate existing users: if they already have SMS permission from the old flow,
    // auto-complete onboarding so they aren't forced through it on update
    LaunchedEffect(themeUiState.hasCompletedOnboarding) {
        if (!themeUiState.hasCompletedOnboarding) {
            val hasSmsPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasSmsPermission) {
                actualThemeViewModel.markOnboardingCompleted()
            }
        }
    }

    // Set correct start destination based on loaded preferences
    val startDestination: Any = remember(themeUiState.hasCompletedOnboarding) {
        if (themeUiState.hasCompletedOnboarding) Home else OnBoarding
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
            navController.navigate(com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)) { launchSingleTop = true }
        }
    }

    // Navigate directly to Add Transaction when requested (e.g., from widget shortcut)
    LaunchedEffect(openAddTransaction) {
        if (openAddTransaction) {
            navController.navigate(com.pennywiseai.tracker.navigation.AddTransaction()) {
                launchSingleTop = true
            }
            onAddTransactionShortcutHandled?.invoke()
        }
    }

    // "Share to PennyWise" and goal widget taps (#914) open a screen with private
    // data, so they wait for onboarding and an unlocked app (see NavigateWhenUnlocked).
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigateWhenUnlocked(
        request = sharedText,
        onboarded = themeUiState.hasCompletedOnboarding,
        lock = appLockUiState,
        currentRoute = currentRoute,
        refreshLock = actualAppLockViewModel::refreshLockState,
    ) { text ->
        navController.navigate(com.pennywiseai.tracker.navigation.AddTransaction(sharedText = text))
        onSharedTextHandled?.invoke()
    }
    NavigateWhenUnlocked(
        request = widgetDestination,
        onboarded = themeUiState.hasCompletedOnboarding,
        lock = appLockUiState,
        currentRoute = currentRoute,
        refreshLock = actualAppLockViewModel::refreshLockState,
    ) { destination ->
        // Replaces the same screen if it's open, so a repeated "+" starts New goal again.
        navController.navigate(destination) { popUpTo(destination) { inclusive = true } }
        onWidgetDestinationHandled?.invoke()
    }

    // Keep widgets current when app launches (covers upgrades/installs with existing widgets)
    LaunchedEffect(Unit) {
        WidgetRefresher.refreshTransactionWidgets(context.applicationContext)
    }

    PennyWiseTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.isDynamicColorEnabled,
        themeStyle = themeUiState.themeStyle,
        accentColor = themeUiState.accentColor,
        isAmoledMode = themeUiState.isAmoledMode,
        appFont = themeUiState.appFont,
        blurEffects = themeUiState.blurEffectsEnabled
    ) {
        PennyWiseNavHost(
            navController = navController,
            themeViewModel = actualThemeViewModel,
            startDestination = startDestination,
            onEditComplete = onEditComplete ?: {}
        )
    }
}

/**
 * Runs [navigate] for [request] once onboarding is done and the app is unlocked,
 * so the screen never shows above the lock screen or skips setup. "Unlocked"
 * must come from a lock check that ran *after* the request arrived (the resume
 * re-check can still be pending), and the lock screen must have navigated away,
 * otherwise its unlock/lock navigation would pop the new screen. The effect
 * re-runs as each of these settles.
 */
@Composable
private fun <T : Any> NavigateWhenUnlocked(
    request: T?,
    onboarded: Boolean,
    lock: com.pennywiseai.tracker.ui.viewmodel.AppLockUiState,
    currentRoute: String?,
    refreshLock: () -> Unit,
    navigate: (T) -> Unit,
) {
    val checksAtRequest = remember(request) { lock.checks }
    // Guarantee a lock check after that baseline: on a cold start the startup
    // checks can finish before this composes (themeUiState loads first), and no
    // other check would follow, leaving the request pending forever.
    LaunchedEffect(request) {
        if (request != null) refreshLock()
    }
    LaunchedEffect(request, onboarded, lock.checks, lock.isLocked, currentRoute) {
        val pending = request ?: return@LaunchedEffect
        val ready = onboarded &&
            lock.checks > checksAtRequest &&
            !lock.isLocked &&
            currentRoute != null &&
            currentRoute != AppLock::class.qualifiedName
        if (ready) navigate(pending)
    }
}
