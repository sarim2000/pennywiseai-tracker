package com.pennywiseai.tracker.ui.screens.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.preferences.TrackedNotificationAppsStore
import com.pennywiseai.tracker.receiver.BankNotificationConfig
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** #994 step 4: which apps' notifications to read as suggested transactions. */
@HiltViewModel
class TrackedAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: TrackedNotificationAppsStore
) : ViewModel() {

    data class App(val packageName: String, val label: String)

    val tracked: StateFlow<Set<String>> = store.allFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), store.all())

    private val _apps = MutableStateFlow<List<App>?>(null)
    val apps: StateFlow<List<App>?> = _apps.asStateFlow()

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.packageName }
                    .distinct()
                    // Our own alerts would loop back in; parser-backed apps are read already.
                    .filter { it != context.packageName && !BankNotificationConfig.isAllowed(it) }
                    // An app uninstalled since the query is skipped, not a crash.
                    .mapNotNull { pkg ->
                        runCatching { App(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
                    }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    fun setTracked(packageName: String, tracked: Boolean) = store.setTracked(packageName, tracked)
}

@Composable
fun TrackedAppsDialog(
    viewModel: TrackedAppsViewModel,
    hasNotificationAccess: Boolean,
    onEnableAccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val tracked by viewModel.tracked.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.loadApps() }
    // Picked apps first, as they were when the dialog opened: re-sorting on every tap would move the row away.
    val pickedAtOpen = remember { viewModel.tracked.value }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tracked_apps_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(R.string.settings_tracked_apps_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!hasNotificationAccess) {
                    TextButton(onClick = onEnableAccess) {
                        Text(stringResource(R.string.settings_tracked_apps_enable_access))
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.settings_tracked_apps_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val list = apps
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val shown = list
                        .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                        .sortedByDescending { it.packageName in pickedAtOpen }
                    LazyColumn(Modifier.heightIn(max = Dimensions.Component.dialogListMaxHeight)) {
                        items(shown, key = { it.packageName }) { app ->
                            val checked = app.packageName in tracked
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Checkbox) { viewModel.setTracked(app.packageName, !checked) }
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(Spacing.sm))
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_tracked_apps_done)) }
        }
    )
}
