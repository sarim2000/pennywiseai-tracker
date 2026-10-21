package com.pennywiseai.tracker.ui.screens.settings.aiaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
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
    val relayUrl by viewModel.relayUrl.collectAsStateWithLifecycle()
    var showUpgrade by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showRelay by rememberSaveable { mutableStateOf(relayUrl != AiRelayClient.DEFAULT_RELAY_URL) }
    var relayInput by rememberSaveable(relayUrl) { mutableStateOf(relayUrl) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    // Messages are momentary: clear them so a passing network blip doesn't stay on screen.
    LaunchedEffect(state.message) { if (state.message != null) { delay(6_000); viewModel.clearMessage() } }
    val clipboard = LocalClipboardManager.current
    val connectorUrl = AiRelayClient.connectorUrl(relayUrl)
    val dashboardUrl = AiRelayClient.dashboardUrl(relayUrl)
    fun mmss(seconds: Long) = (seconds / 60).toInt() to (seconds % 60).toInt()

    PennyWiseScaffold(title = stringResource(R.string.ai_access_title), navigationIcon = {
        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.ai_access_back)) }
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
            contentPadding = PaddingValues(vertical = Spacing.md)
        ) {
            item { Text(stringResource(R.string.ai_access_intro), style = MaterialTheme.typography.bodyMedium) }
            if (!isPro) {
                item { Text(stringResource(R.string.ai_access_pro_only), style = MaterialTheme.typography.titleMedium) }
                item { Button(onClick = { showUpgrade = true }) { Text(stringResource(R.string.ai_access_unlock)) } }
                return@LazyColumn
            }
            // The relay-address error shows under its field instead.
            state.message?.takeIf { it != R.string.ai_access_relay_invalid }?.let { message ->
                item {
                    Text(
                        stringResource(message),
                        color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── AI session ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.ai_access_session_title), style = MaterialTheme.typography.titleMedium)
                        val s = session
                        if (s == null) {
                            Text(stringResource(R.string.ai_access_session_off))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                listOf(15, 30, 60).forEach { m ->
                                    val label = stringResource(R.string.ai_access_session_minutes, m)
                                    if (m == 30) Button(onClick = { viewModel.startSession(m) }) { Text(label) }
                                    else OutlinedButton(onClick = { viewModel.startSession(m) }) { Text(label) }
                                }
                            }
                        } else {
                            val (min, sec) = mmss(((s.endsAtMillis - now) / 1000).coerceAtLeast(0))
                            Text(
                                stringResource(if (s.lineUp) R.string.ai_access_session_on else R.string.ai_access_session_connecting, min, sec),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                FilledTonalButton(onClick = viewModel::extendSession) { Text(stringResource(R.string.ai_access_extend)) }
                                OutlinedButton(onClick = viewModel::stopSession) { Text(stringResource(R.string.ai_access_stop)) }
                            }
                        }
                    }
                }
            }

            // ── Connect an AI app ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.ai_access_connect_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ai_access_connect_step1))
                        CopyableAddress(connectorUrl, stringResource(R.string.ai_access_copy_url)) { clipboard.setText(AnnotatedString(connectorUrl)) }
                        Text(stringResource(R.string.ai_access_connect_step2))
                        val code = state.pairingCode
                        val codeLeft = ((state.pairingExpiresAtMillis - now) / 1000).coerceAtLeast(0)
                        if (code != null && codeLeft > 0) {
                            val a11y = stringResource(R.string.ai_access_code_a11y, code.toList().joinToString(" "))
                            Text(
                                code.chunked(3).joinToString(" "),
                                style = MaterialTheme.typography.displaySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.semantics { contentDescription = a11y }
                            )
                            val (min, sec) = mmss(codeLeft)
                            Text(stringResource(R.string.ai_access_code_left, min, sec), style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = viewModel::newPairingCode, enabled = !state.busy) {
                            Text(stringResource(if (code != null && codeLeft > 0) R.string.ai_access_new_code else R.string.ai_access_get_code))
                        }
                    }
                }
            }

            // ── Dashboard ──
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.ai_access_dashboard_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.ai_access_dashboard_body))
                        CopyableAddress(dashboardUrl, stringResource(R.string.ai_access_copy_dashboard)) { clipboard.setText(AnnotatedString(dashboardUrl)) }
                    }
                }
            }

            // ── Connected apps ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ai_access_connected_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.refreshConnections() }) { Text(stringResource(R.string.ai_access_refresh)) }
                }
            }
            if (state.connections.isEmpty()) {
                item { Text(stringResource(if (state.loadingConnections) R.string.ai_access_loading else R.string.ai_access_none_connected)) }
            }
            items(state.connections, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.clientName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.ai_access_connected_on, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(c.createdAt * 1000))),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { viewModel.revoke(c.id) }, enabled = !state.busy) { Text(stringResource(R.string.ai_access_disconnect)) }
                    }
                }
            }

            // ── Privacy ──
            item { Text(stringResource(R.string.ai_access_privacy), style = MaterialTheme.typography.bodySmall) }

            // ── Use your own relay ──
            item {
                TextButton(onClick = { showRelay = !showRelay }) {
                    Text(stringResource(R.string.ai_access_relay_title))
                    Icon(if (showRelay) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
            }
            if (showRelay) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(stringResource(R.string.ai_access_relay_body), style = MaterialTheme.typography.bodySmall)
                        val locked = session != null
                        OutlinedTextField(
                            value = relayInput,
                            onValueChange = { relayInput = it },
                            label = { Text(stringResource(R.string.ai_access_relay_label)) },
                            singleLine = true,
                            enabled = !locked,
                            isError = state.message == R.string.ai_access_relay_invalid,
                            supportingText = if (state.message == R.string.ai_access_relay_invalid) {
                                { Text(stringResource(R.string.ai_access_relay_invalid)) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (locked) Text(stringResource(R.string.ai_access_relay_busy), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilledTonalButton(onClick = { viewModel.setRelayUrl(relayInput) }, enabled = !locked && relayInput.trim() != relayUrl) {
                                Text(stringResource(R.string.ai_access_relay_save))
                            }
                            if (relayUrl != AiRelayClient.DEFAULT_RELAY_URL) {
                                TextButton(onClick = { viewModel.setRelayUrl(AiRelayClient.DEFAULT_RELAY_URL) }, enabled = !locked) {
                                    Text(stringResource(R.string.ai_access_relay_reset))
                                }
                            }
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { confirmReset = true }, enabled = !state.busy) { Text(stringResource(R.string.ai_access_disconnect_all)) }
            }
        }
    }

    if (showUpgrade) UpgradeSheet(onDismiss = { showUpgrade = false })
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.ai_access_disconnect_all_title)) },
            text = { Text(stringResource(R.string.ai_access_disconnect_all_body)) },
            confirmButton = { TextButton(onClick = { confirmReset = false; viewModel.disconnectEverything() }) { Text(stringResource(R.string.ai_access_disconnect_all_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun CopyableAddress(address: String, copyLabel: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(address, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
        IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, copyLabel) }
    }
}
