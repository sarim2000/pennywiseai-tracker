package com.pennywiseai.tracker

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.pennywiseai.tracker.navigation.Home
import com.pennywiseai.tracker.navigation.Permission
import com.pennywiseai.tracker.navigation.PennyWiseNavHost
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.viewmodel.ThemeViewModel

@Composable
fun PennyWiseApp(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    editTransactionId: Long? = null,
    onEditComplete: () -> Unit = {}
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val darkTheme = themeUiState.isDarkTheme ?: isSystemInDarkTheme()
    
    val navController = rememberNavController()
    
    // Check initial permission state only once
    val startDestination = remember {
        val hasReadSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasReceiveSms = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        // Note: We can't check hasSkippedPermission here because it's async from DataStore
        // So we always start at Permission if no SMS permissions, and let the screen handle skipped state
        if (hasReadSms && hasReceiveSms) Home else Permission
    }
    
    PennyWiseTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.isDynamicColorEnabled
    ) {
        PennyWiseNavHost(
            navController = navController,
            themeViewModel = themeViewModel,
            startDestination = startDestination,
            editTransactionId = editTransactionId,
            onEditComplete = onEditComplete
        )
    }
}