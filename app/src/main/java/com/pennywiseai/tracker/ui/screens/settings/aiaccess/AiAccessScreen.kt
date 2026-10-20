package com.pennywiseai.tracker.ui.screens.settings.aiaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.aiaccess.AiRelayClient
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun AiAccessScreen(onNavigateBack: () -> Unit, viewModel: AiAccessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isPro by viewModel.isProEntitled.collectAsStateWithLifecycle()
    var showUpgrade by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val clipboard = LocalClipboardManager.current

    PennyWiseScaffold(title = "AI access", navigationIcon = {
        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") }
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
            contentPadding = PaddingValues(vertical = Spacing.md)
        ) {
            item {
                Text(
                    "Ask Claude, ChatGPT or another AI app about your spending. They can read your " +
                        "transactions, balances and subscriptions only while an AI session is on. They can't change anything.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (!isPro) {
                item { Text("AI access is a Pro feature.", style = MaterialTheme.typography.titleMedium) }
                item { Button(onClick = { showUpgrade = true }) { Text("Unlock with Pro") } }
                return@LazyColumn
            }
            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

            // ── AI session ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("AI session", style = MaterialTheme.typography.titleMedium)
                        val s = session
                        if (s == null) {
                            Text("Off. Start one before you ask, or use the PennyWise AI tile in Quick Settings.")
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                listOf(15, 30, 60).forEach { m ->
                                    if (m == 30) Button(onClick = { viewModel.startSession(m) }) { Text("$m min") }
                                    else OutlinedButton(onClick = { viewModel.startSession(m) }) { Text("$m min") }
                                }
                            }
                        } else {
                            val left = ((s.endsAtMillis - now) / 1000).coerceAtLeast(0)
                            Text(
                                (if (s.lineUp) "On · " else "Connecting… · ") + "%d:%02d left".format(left / 60, left % 60),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                FilledTonalButton(onClick = viewModel::extendSession) { Text("+15 min") }
                                OutlinedButton(onClick = viewModel::stopSession) { Text("Stop") }
                            }
                        }
                    }
                }
            }

            // ── Connect an AI app ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text("Connect an AI app", style = MaterialTheme.typography.titleMedium)
                        Text("1. In Claude or ChatGPT, add a custom connector with this URL:")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(AiRelayClient.CONNECTOR_URL, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(AiRelayClient.CONNECTOR_URL)) }) {
                                Icon(Icons.Default.ContentCopy, "Copy connector URL")
                            }
                        }
                        Text("2. When it asks you to sign in, enter a pairing code from here.")
                        val code = state.pairingCode
                        val codeLeft = ((state.pairingExpiresAtMillis - now) / 1000).coerceAtLeast(0)
                        if (code != null && codeLeft > 0) {
                            Text(
                                code.chunked(3).joinToString(" "),
                                style = MaterialTheme.typography.displaySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.semantics { contentDescription = "Pairing code ${code.toList().joinToString(" ")}" }
                            )
                            Text("Works once · %d:%02d left".format(codeLeft / 60, codeLeft % 60), style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = viewModel::newPairingCode, enabled = !state.busy) {
                            Text(if (code != null && codeLeft > 0) "New code" else "Get pairing code")
                        }
                    }
                }
            }

            // ── Connected apps ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Connected apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.refreshConnections() }) { Text("Refresh") }
                }
            }
            if (state.connections.isEmpty()) {
                item { Text(if (state.loadingConnections) "Loading…" else "No AI apps connected yet.") }
            }
            items(state.connections, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.clientName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Connected " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.createdAt * 1000)),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { viewModel.revoke(c.id) }, enabled = !state.busy) { Text("Disconnect") }
                    }
                }
            }

            // ── Privacy ──
            item {
                Text(
                    "Your data stays on your phone. During an AI session, the AI app's questions pass through " +
                        "PennyWise's relay to your phone and the answers go back the same way. The relay never stores " +
                        "or logs them. The AI app's provider (for example Anthropic or OpenAI) sees the answers it receives.\n\n" +
                        "Connected apps can search your whole history across all profiles, including payee names on transfers. " +
                        "They never get SMS text, full account numbers or reference numbers.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                TextButton(onClick = { confirmReset = true }, enabled = !state.busy) { Text("Disconnect every AI app") }
            }
        }
    }

    if (showUpgrade) UpgradeSheet(onDismiss = { showUpgrade = false })
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Disconnect every AI app?") },
            text = { Text("Every connected AI app loses access. To use one again, connect it with a new pairing code.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; viewModel.disconnectEverything() }) { Text("Disconnect all") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}
