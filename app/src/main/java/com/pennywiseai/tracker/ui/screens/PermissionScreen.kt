package com.pennywiseai.tracker.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.viewmodel.PermissionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var readSmsGranted by remember { mutableStateOf(false) }
    var receiveSmsGranted by remember { mutableStateOf(false) }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        readSmsGranted = permissions[Manifest.permission.READ_SMS] == true
        receiveSmsGranted = permissions[Manifest.permission.RECEIVE_SMS] == true

        if (readSmsGranted) {
            viewModel.onPermissionResult(true)
            onPermissionGranted()
        } else {
            viewModel.onPermissionDenied()
        }
    }

    val notificationAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshNotificationAccess()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationAccess()
    }

    PennyWiseScaffold(
        modifier = modifier,
        transparentTopBar = true
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MailOutline,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            Text(
                text = "Enable Automatic Transaction Detection",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            Text(
                text = "PennyWise can automatically detect and categorize your bank transactions from SMS messages, saving you time and effort.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md)
                ) {
                    Text(
                        text = "Your Privacy Matters",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "• Only transaction messages are processed\n" +
                            "• All data stays on your device\n" +
                            "• No personal messages are read\n" +
                            "• You can revoke access anytime in Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = "Enable Bank Notification Access",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Allow PennyWise to read transaction notifications from supported banking apps. This helps capture purchases when SMS is delayed or unavailable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    if (uiState.hasNotificationAccess) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Notification access enabled") }
                        )
                    } else {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                notificationAccessLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Notification Access Settings")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (uiState.showRationale) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Without SMS access, you'll need to manually add all your transactions. " +
                            "We only read bank transaction messages, not personal conversations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            Button(
                onClick = {
                    val permissions = mutableListOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    multiplePermissionLauncher.launch(permissions.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Automatic Detection")
            }
        }
    }
}
