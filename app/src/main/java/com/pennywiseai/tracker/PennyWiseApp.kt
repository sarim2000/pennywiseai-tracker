package com.pennywiseai.tracker

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pennywiseai.tracker.navigation.AppLock
import com.pennywiseai.tracker.navigation.Home
import com.pennywiseai.tracker.navigation.OnBoarding
import com.pennywiseai.tracker.navigation.PennyWiseNavHost
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.ShakeToHideAmounts
import com.pennywiseai.tracker.ui.viewmodel.AppLockViewModel
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel
import com.pennywiseai.tracker.widget.WidgetRefresher

@Composable
fun PennyWiseApp(
    themeViewModel: ThemeViewModel? = null,
    appLockViewModel: AppLockViewModel? = null,
    editTransactionId: Long? = null,
    openAddTransaction: com.pennywiseai.tracker.navigation.AddTransaction? = null,
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

    // Observe lifecycle events and refresh lock state when app comes back from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // On start, not resume: until it runs the screen stays covered
            // (coverForLock), and a split-screen half can start without resuming.
            if (event == Lifecycle.Event.ON_START) {
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

    // Observe lock state changes and navigate to lock screen if needed.
    // On top of the current screen, which unlocking returns to: with "Immediately"
    // this runs after every trip out of the app (a file picker, the camera), and
    // the screen and its pending result have to survive it. Back can't leave it.
    val showLockScreen = {
        navController.navigate(AppLock) { launchSingleTop = true }
    }
    LaunchedEffect(appLockUiState.isLocked, appLockUiState.isLockEnabled) {
        if (appLockUiState.isLocked && appLockUiState.isLockEnabled &&
            navController.currentDestination?.route != AppLock::class.qualifiedName
        ) {
            showLockScreen()
        }
    }
    
    // Every outside entry point — notification and Recent-widget transaction taps,
    // the Add widget/shortcut, "Share to PennyWise" and goal widget taps (#914) —
    // opens a screen with private data, so they all wait for onboarding and an
    // unlocked app (see NavigateWhenUnlocked).
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigateWhenUnlocked(
        request = editTransactionId,
        onboarded = themeUiState.hasCompletedOnboarding,
        lock = appLockUiState,
        currentRoute = currentRoute,
        refreshLock = actualAppLockViewModel::refreshLockState,
        showLockScreen = showLockScreen,
    ) { transactionId ->
        navController.navigate(com.pennywiseai.tracker.navigation.TransactionDetail(transactionId)) { launchSingleTop = true }
        // Consume now, or leaving the detail screen would re-run this and reopen it.
        onEditComplete?.invoke()
    }
    NavigateWhenUnlocked(
        request = openAddTransaction,
        onboarded = themeUiState.hasCompletedOnboarding,
        lock = appLockUiState,
        currentRoute = currentRoute,
        refreshLock = actualAppLockViewModel::refreshLockState,
        showLockScreen = showLockScreen,
    ) { add ->
        navController.navigate(add) { launchSingleTop = true }
        onAddTransactionShortcutHandled?.invoke()
    }
    NavigateWhenUnlocked(
        request = sharedText,
        onboarded = themeUiState.hasCompletedOnboarding,
        lock = appLockUiState,
        currentRoute = currentRoute,
        refreshLock = actualAppLockViewModel::refreshLockState,
        showLockScreen = showLockScreen,
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
        showLockScreen = showLockScreen,
    ) { destination ->
        // Replace the same kind of screen only when it's on top, so a repeated "+"
        // starts New goal again; anything under it (an unsaved edit) stays.
        val top = navController.currentBackStackEntry?.destination
        navController.navigate(destination) {
            if (top != null && top.hasRoute(destination::class)) popUpTo(top.id) { inclusive = true }
        }
        onWidgetDestinationHandled?.invoke()
    }

    // Shake to hide / show amounts (opt-in in Settings), only while unlocked: a shake
    // on the lock screen, or on the old screen in the moment before a resume's lock
    // check sends us there, would change what shows (widgets too) before anyone
    // authenticated. So after each resume, wait for a fresh lock check; if it says
    // locked, wait for the lock screen to be passed (isLocked can stay true after an
    // "Immediately" unlock, see NavigateWhenUnlocked).
    // A pass counts until the app locks again, so it resets when isLocked turns true,
    // not on every resume: after an "Immediately" unlock isLocked never changes, the
    // lock screen doesn't come back, and a reset on resume would leave shake off.
    val onLockScreen = currentRoute == AppLock::class.qualifiedName
    var lockChecksAtResume by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var passedLockScreen by remember(appLockUiState.isLocked && appLockUiState.isLockEnabled) {
        mutableStateOf(false)
    }
    LifecycleResumeEffect(Unit) {
        lockChecksAtResume = appLockUiState.checks
        // Ask for that check ourselves: on a cold start the resume's own check can
        // finish before this composes (themeUiState loads first), leaving none to wait for.
        actualAppLockViewModel.refreshLockState()
        onPauseOrDispose { }
    }
    SideEffect { if (onLockScreen) passedLockScreen = true }
    // A check also runs on every resume with the lock off, and isLockEnabled is only
    // real once one has come in.
    val unlocked = appLockUiState.checks > lockChecksAtResume &&
        (!appLockUiState.isLockEnabled || !appLockUiState.isLocked || passedLockScreen)
    ShakeToHideAmounts(
        active = themeUiState.hasCompletedOnboarding && currentRoute != null && !onLockScreen && unlocked,
        viewModel = hiltViewModel(viewModelStoreOwner = owner, key = null)
    )

    // Keep widgets current when app launches (covers upgrades/installs with existing widgets)
    LaunchedEffect(Unit) {
        WidgetRefresher.refreshTransactionWidgets(context.applicationContext)
    }

    val covered = with(appLockUiState) {
        coverForLock(checks, isLockEnabled, isLocked, unlockedOnScreen, onLockScreen)
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
        Box(Modifier.fillMaxSize()) {
            PennyWiseNavHost(
                navController = navController,
                themeViewModel = actualThemeViewModel,
                startDestination = startDestination,
                onEditComplete = onEditComplete ?: {}
            )
            if (covered) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        // Swallow touches meant for the screen it hides.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                )
            }
        }
    }
}

/**
 * Whether to hide the app behind a plain cover. The lock screen is pushed only
 * once a check has run on return (or at launch), so until then, and while
 * locked with the lock screen not up yet, the screen under it would show: on a
 * return past the timeout, over half a second of it. So with App Lock on,
 * nothing but the lock screen shows unless the app is unlocked on screen, and
 * nothing at all before the first check says whether the lock is on.
 */
internal fun coverForLock(
    checks: Int,
    isLockEnabled: Boolean,
    isLocked: Boolean,
    unlockedOnScreen: Boolean,
    onLockScreen: Boolean
): Boolean = !onLockScreen && (checks == 0 || (isLockEnabled && (isLocked || !unlockedOnScreen)))

/**
 * Runs [navigate] for [request] once onboarding is done and the app is unlocked,
 * so the screen never shows above the lock screen or skips setup. The lock state
 * must come from a check that ran *after* the request arrived (the resume re-check
 * can still be pending), and the lock screen must have navigated away, otherwise
 * its unlock/lock navigation would pop the new screen. The effect re-runs as each
 * of these settles.
 *
 * Passing through the lock screen also counts as unlocked: it can only be left by
 * authenticating (back is blocked), while [lock] (the activity's view model, not
 * the lock screen's) can still say locked for a moment after it closes. When a
 * fresh check says locked and the lock screen isn't showing, this shows it
 * ([showLockScreen]) rather than waiting for the lock-state effect, which only
 * runs when `isLocked` changes.
 */
@Composable
private fun <T : Any> NavigateWhenUnlocked(
    request: T?,
    onboarded: Boolean,
    lock: com.pennywiseai.tracker.ui.viewmodel.AppLockUiState,
    currentRoute: String?,
    refreshLock: () -> Unit,
    showLockScreen: () -> Unit,
    navigate: (T) -> Unit,
) {
    val checksAtRequest = remember(request) { lock.checks }
    var passedLockScreen by remember(request) { mutableStateOf(false) }
    // Guarantee a lock check after that baseline: on a cold start the startup
    // checks can finish before this composes (themeUiState loads first), and no
    // other check would follow, leaving the request pending forever.
    LaunchedEffect(request) {
        if (request != null) refreshLock()
    }
    LaunchedEffect(request, onboarded, lock.checks, lock.isLocked, currentRoute) {
        val pending = request ?: return@LaunchedEffect
        val onLockScreen = currentRoute == AppLock::class.qualifiedName
        if (onLockScreen) passedLockScreen = true
        if (!onboarded || lock.checks <= checksAtRequest || currentRoute == null || onLockScreen) return@LaunchedEffect
        val ready = !lock.isLocked || passedLockScreen
        if (ready) navigate(pending) else showLockScreen()
    }
}
