package com.pennywiseai.tracker.ui.screens.settings.webhooks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.webhook.WebhookHeader
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun WebhooksScreen(onNavigateBack: () -> Unit, viewModel: WebhooksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = remember(state.editor != null, state.editor?.id, state.historyId) { LazyListState() }
    var deleteProfile by remember { mutableStateOf<WebhookProfileEntity?>(null) }
    val back: () -> Unit = {
        when {
            state.editor != null -> viewModel.closeEditor()
            state.historyId != null -> viewModel.closeHistory()
            else -> onNavigateBack()
        }
    }
    BackHandler(state.editor != null || state.historyId != null, onBack = back)
    val title = when {
        state.editor != null -> if (state.editor?.id == null) "Add webhook" else "Edit webhook"
        state.historyId != null -> "Delivery history"
        else -> "Webhooks"
    }
    PennyWiseScaffold(title = title, navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }
    }) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
            contentPadding = PaddingValues(vertical = Spacing.md)
        ) {
            if (state.editor == null) {
                state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
            }
            when {
                state.editor != null -> {
                    item { WebhookEditor(state.editor!!, viewModel::updateEditor) }
                    state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                    item {
                        Button(onClick = viewModel::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Save webhook")
                        }
                    }
                }
                state.historyId != null -> {
                    item {
                        Text(state.profiles.firstOrNull { it.id == state.historyId }?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.logs.isEmpty()) item { Text("No deliveries yet. Queued work waits for a network connection.") }
                    items(state.logs, key = { it.id }) { log ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Text(if (log.status == WebhookLogStatus.SUCCESS) "Delivered" else "Failed",
                                    color = if (log.status == WebhookLogStatus.SUCCESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.titleSmall)
                                Text(log.message)
                                Text("${log.syncReason.name.lowercase()} · ${log.createdAt.toLocalDate()} ${log.createdAt.toLocalTime().withNano(0)}",
                                    style = MaterialTheme.typography.bodySmall)
                                if (log.batchCount > 1) Text("Delivery has ${log.batchCount} batches", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                else -> {
                    item {
                        Text("Webhooks send your selected financial data outside this device to an endpoint you choose. Only enable endpoints you trust. Use HTTPS to protect data and credentials.")
                    }
                    item { Button(onClick = viewModel::newProfile, modifier = Modifier.fillMaxWidth()) { Text("Add webhook") } }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(state.interval, viewModel::setInterval, Modifier.weight(1f),
                                label = { Text("Sync interval in hours") }, singleLine = true,
                                supportingText = { Text("1–24 hours. Android may delay background delivery.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            TextButton(onClick = viewModel::saveInterval, enabled = !state.busy) { Text("Apply") }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { viewModel.syncNow() }, enabled = !state.busy && state.profiles.any { it.enabled },
                            modifier = Modifier.fillMaxWidth()) { Text("Sync enabled webhooks now") }
                    }
                    if (state.profiles.isEmpty()) item { Text("No webhooks saved") }
                    items(state.profiles, key = { it.id }) { profile ->
                        WebhookProfileCard(profile, !state.busy,
                            onEdit = { viewModel.edit(profile) },
                            onEnabled = { viewModel.setEnabled(profile.id, it) },
                            onTest = { viewModel.sendTest(profile.id) },
                            onSync = { viewModel.syncNow(profile.id) },
                            onHistory = { viewModel.showHistory(profile.id) },
                            onDelete = { deleteProfile = profile })
                    }
                }
            }
        }
    }
    deleteProfile?.let { profile ->
        AlertDialog(onDismissRequest = { deleteProfile = null }, title = { Text("Delete webhook?") },
            text = { Text("Delete ${profile.name} and its delivery history?") },
            confirmButton = { TextButton(onClick = { viewModel.delete(profile.id); deleteProfile = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteProfile = null }) { Text("Cancel") } })
    }
}

@Composable
private fun WebhookProfileCard(
    profile: WebhookProfileEntity, enabled: Boolean, onEdit: () -> Unit,
    onEnabled: (Boolean) -> Unit, onTest: () -> Unit, onSync: () -> Unit,
    onHistory: () -> Unit, onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(runCatching { java.net.URI(profile.url).host }.getOrNull().orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${profile.currency} · ${profile.rangePreset.label()}", style = MaterialTheme.typography.bodySmall)
                }
                Switch(profile.enabled, onEnabled, enabled = enabled)
            }
            Text(if (profile.enabled) "Automatic sync enabled" else "Automatic sync disabled", style = MaterialTheme.typography.bodySmall)
            profile.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            profile.lastSyncedAt?.let { Text("Last sync: ${it.toLocalDate()} ${it.toLocalTime().withNano(0)}", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = onEdit, enabled = enabled) { Text("Edit") }
                TextButton(onClick = onTest, enabled = enabled) { Text("Test") }
                TextButton(onClick = onSync, enabled = enabled && profile.enabled) { Text("Sync") }
                TextButton(onClick = onHistory) { Text("History") }
                IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Default.Delete, "Delete ${profile.name}") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebhookEditor(editor: WebhookEditorState, update: ((WebhookEditorState) -> WebhookEditorState) -> Unit) {
    var rangeMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.smd)) {
        OutlinedTextField(editor.name, { value -> update { it.copy(name = value) } }, Modifier.fillMaxWidth(),
            label = { Text("Name") }, singleLine = true)
        OutlinedTextField(editor.url, { value -> update { it.copy(url = value) } }, Modifier.fillMaxWidth(),
            label = { Text("HTTPS endpoint URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        if (editor.url.trim().startsWith("http://", true)) {
            Text("HTTP sends data and header credentials without encryption.", color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(editor.currency, { value -> update { it.copy(currency = value.uppercase()) } }, Modifier.fillMaxWidth(),
            label = { Text("Currency code") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable automatic delivery", Modifier.weight(1f))
            Switch(editor.enabled, { value -> update { it.copy(enabled = value) } })
        }
        Text("Data to send", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            WebhookDataType.entries.forEach { type ->
                FilterChip(type in editor.types, onClick = { update {
                    it.copy(types = if (type in it.types) it.types - type else it.types + type)
                } }, label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
        Box {
            OutlinedButton(onClick = { rangeMenu = true }) { Text("Range: ${editor.range.label()}") }
            DropdownMenu(rangeMenu, onDismissRequest = { rangeMenu = false }) {
                WebhookRangePreset.entries.forEach { range ->
                    DropdownMenuItem(text = { Text(range.label()) }, onClick = {
                        update { it.copy(range = range) }; rangeMenu = false
                    })
                }
            }
        }
        Text("Since last success sends transaction edits and deletion records. Budgets, accounts, and subscriptions are current snapshots.",
            style = MaterialTheme.typography.bodySmall)
        if (editor.range == WebhookRangePreset.CUSTOM) {
            OutlinedTextField(editor.start, { value -> update { it.copy(start = value) } }, Modifier.fillMaxWidth(),
                label = { Text("Start date (YYYY-MM-DD)") }, singleLine = true)
            OutlinedTextField(editor.end, { value -> update { it.copy(end = value) } }, Modifier.fillMaxWidth(),
                label = { Text("End date (YYYY-MM-DD)") }, singleLine = true)
        }
        Text("Custom headers", style = MaterialTheme.typography.titleSmall)
        Text("Header values are hidden and sent only to this endpoint. Tests use synthetic financial data and your configured headers.",
            style = MaterialTheme.typography.bodySmall)
        editor.headers.forEachIndexed { index, header ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(header.key, { value -> update { it.copy(headers = it.headers.toMutableList().apply {
                    this[index] = this[index].copy(key = value)
                }) } }, Modifier.fillMaxWidth(), label = { Text("Header name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(header.value, { value -> update { it.copy(headers = it.headers.toMutableList().apply {
                        this[index] = this[index].copy(value = value)
                    }) } }, Modifier.weight(1f), label = { Text("Header value") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    IconButton(onClick = { update { it.copy(headers = it.headers.filterIndexed { i, _ -> i != index }) } }) {
                        Icon(Icons.Default.Delete, "Remove header")
                    }
                }
            }
        }
        OutlinedButton(onClick = { update { it.copy(headers = it.headers + WebhookHeader("", "")) } }) { Text("Add header") }
    }
}

private fun WebhookRangePreset.label(): String = when (this) {
    WebhookRangePreset.SINCE_LAST_SUCCESS -> "Since last success"
    WebhookRangePreset.TODAY -> "Today"
    WebhookRangePreset.CURRENT_WEEK -> "Current week"
    WebhookRangePreset.CURRENT_MONTH -> "Current month"
    WebhookRangePreset.PREVIOUS_MONTH -> "Previous month"
    WebhookRangePreset.LAST_30_DAYS -> "Last 30 days"
    WebhookRangePreset.CUSTOM -> "Custom dates"
}
