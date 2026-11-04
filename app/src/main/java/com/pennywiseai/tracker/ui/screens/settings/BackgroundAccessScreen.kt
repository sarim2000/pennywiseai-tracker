package com.pennywiseai.tracker.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.ui.viewmodel.BackgroundAccessViewModel
import com.pennywiseai.tracker.utils.batteryUnrestrictedIntents
import com.pennywiseai.tracker.utils.startFirstAvailable

@Composable
fun BackgroundAccessScreen(onNavigateBack: () -> Unit) {
    PennyWiseScaffold(
        title = stringResource(R.string.background_access_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(Spacing.md)
        ) {
            BackgroundAccessChecklist()
        }
    }
}

private class ChecklistStep(
    val title: String,
    val done: Boolean,
    val why: String,
    val actionLabel: String,
    val onAction: () -> Unit,
    /** Only the manufacturer step: Android can't read it, so the user vouches for it. */
    val selfConfirm: ((Boolean) -> Unit)? = null,
    /** Another way to do a step already listed, so not one more thing to fix. */
    val optional: Boolean = false
)

/** Shared by Settings and onboarding. Every step is optional and user-initiated. */
@Composable
fun BackgroundAccessChecklist(
    modifier: Modifier = Modifier,
    viewModel: BackgroundAccessViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val loaded by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    // Android requires this one to be started for a result.
    val unusedRestrictionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refresh() }

    val state = loaded ?: return

    val openSettings = stringResource(R.string.background_access_open_settings)
    val steps = buildList {
        if (state.showBatteryStep) {
            add(
                ChecklistStep(
                    title = stringResource(R.string.background_access_battery_title),
                    done = state.batteryUnrestricted,
                    why = stringResource(R.string.background_access_battery_why),
                    actionLabel = stringResource(R.string.background_access_allow),
                    onAction = { context.startFirstAvailable(context.batteryUnrestrictedIntents()) }
                )
            )
        }
        state.backgroundRestricted?.let { restricted ->
            add(
                ChecklistStep(
                    title = stringResource(R.string.background_access_activity_title),
                    done = !restricted,
                    why = stringResource(R.string.background_access_activity_why),
                    actionLabel = openSettings,
                    onAction = { context.startFirstAvailable() }
                )
            )
        }
        if (state.showKeepAccess) {
            add(
                ChecklistStep(
                    title = stringResource(R.string.background_access_keep_title),
                    done = state.keepAccessDone,
                    why = stringResource(R.string.background_access_keep_why),
                    actionLabel = openSettings,
                    onAction = {
                        runCatching {
                            unusedRestrictionsLauncher.launch(
                                IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
                            )
                        }.onFailure { context.startFirstAvailable() }
                    }
                )
            )
        }
        state.oem?.takeIf { state.showOemStep }?.let { oem ->
            add(
                ChecklistStep(
                    title = stringResource(oem.titleRes),
                    done = state.oemConfirmed,
                    why = stringResource(oem.howToRes),
                    actionLabel = openSettings,
                    onAction = { context.startFirstAvailable(oem.intents()) },
                    selfConfirm = viewModel::setOemConfirmed,
                    optional = state.oemIsBatteryAlternative
                )
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            text = if (state.stepsLeft == 0) {
                stringResource(R.string.background_access_all_set)
            } else {
                stringResource(R.string.background_access_intro) + " " + pluralStringResource(
                    R.plurals.background_access_steps_left,
                    state.stepsLeft,
                    state.stepsLeft
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GroupedList {
            steps.forEachIndexed { index, step ->
                ChecklistRow(step, ListItemPosition.from(index, steps.size))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistRow(step: ChecklistStep, position: ListItemPosition) {
    val statusColor = when {
        step.done -> MaterialTheme.colorScheme.success
        step.optional -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.warning
    }
    GroupedColumn(position = position) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(
                icon = when {
                    step.done -> Icons.Default.Check
                    step.optional -> Icons.Default.MoreHoriz
                    else -> Icons.Default.PriorityHigh
                },
                containerColor = statusColor.copy(alpha = 0.15f),
                contentColor = statusColor,
                contentDescription = stringResource(
                    when {
                        step.done -> R.string.background_access_status_done
                        step.optional -> R.string.background_access_status_optional
                        else -> R.string.background_access_status_needed
                    }
                )
            )
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        when {
            !step.done -> {
                Text(
                    text = step.why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = step.onAction) { Text(step.actionLabel) }
                    step.selfConfirm?.let { confirm ->
                        TextButton(onClick = { confirm(true) }) {
                            Text(stringResource(R.string.background_access_mark_done))
                        }
                    }
                }
            }
            step.selfConfirm != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.background_access_confirmed_by_you),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { step.selfConfirm.invoke(false) }) {
                        Text(stringResource(R.string.background_access_undo))
                    }
                }
            }
        }
    }
}
