package com.pennywiseai.tracker.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.viewmodel.AppLockViewModel
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Prevent back navigation when app is locked
    BackHandler(enabled = true) {
        // Do nothing - prevent back navigation
        // User must authenticate to proceed
    }

    // Auto-trigger authentication when screen is shown
    // (With no screen lock at all, authenticate lets the user straight in.)
    LaunchedEffect(Unit) {
        if (context is FragmentActivity) {
            triggerAuthentication(context, viewModel)
        }
    }

    // Navigate away only on explicit authentication success
    LaunchedEffect(uiState.authenticationSucceeded) {
        if (uiState.authenticationSucceeded) {
            viewModel.resetAuthenticationSucceeded()
            onUnlocked()
        }
    }

    PennyWiseScaffold(
        modifier = modifier,
        transparentTopBar = true
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Lock icon
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.applock_icon_cd),
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Title
                Text(
                    text = stringResource(R.string.applock_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                // Description
                Text(
                    text = stringResource(R.string.applock_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Show error if authentication failed
                if (uiState.authenticationError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.authenticationError!!.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.md),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                // Always offered: the prompt falls back to the device PIN, and a
                // device with no screen lock is let straight in.
                Button(
                    onClick = {
                        if (context is FragmentActivity) {
                            viewModel.clearAuthError()
                            triggerAuthentication(context, viewModel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.applock_unlock))
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                // Privacy note
                Text(
                    text = when (uiState.timeoutMinutes) {
                        0 -> stringResource(R.string.applock_protected_immediate)
                        else -> pluralStringResource(
                            R.plurals.applock_protected_timeout,
                            uiState.timeoutMinutes,
                            uiState.timeoutMinutes
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun triggerAuthentication(
    activity: FragmentActivity,
    viewModel: AppLockViewModel
) {
    // Application context: the notice can land after the lock screen has gone.
    val appContext = activity.applicationContext
    viewModel.triggerAuthentication(activity) {
        Toast.makeText(appContext, R.string.applock_no_screen_lock_notice, Toast.LENGTH_LONG).show()
    }
}
