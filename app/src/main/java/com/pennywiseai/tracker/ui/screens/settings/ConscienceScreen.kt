package com.pennywiseai.tracker.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.conscience.ConscienceAddress
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceLines
import com.pennywiseai.tracker.domain.conscience.ConscienceVoice
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.PreferenceSwitch
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun ConscienceScreen(onNavigateBack: () -> Unit, viewModel: ConscienceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // On Android 13+ turning it on asks for the permission first; a refusal leaves it off with a word on why.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setEnabled(true)
        } else {
            Toast.makeText(context, R.string.conscience_needs_permission, Toast.LENGTH_LONG).show()
        }
    }

    PennyWiseScaffold(
        title = stringResource(R.string.conscience_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Layout.screenHorizontal, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.conscience_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PreferenceSwitch(
                title = stringResource(R.string.conscience_switch_title),
                checked = state.enabled,
                onCheckedChange = { enabled ->
                    val needsPermission = enabled &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setEnabled(enabled)
                    }
                },
                position = ListItemPosition.Single
            )
            if (state.enabled) {
                ConscienceOptions(state, viewModel)
            }
            ConsciencePreview(state.voice, state.address)
            FilledTonalButton(
                onClick = {
                    val message = if (viewModel.tryIt()) R.string.conscience_try_sent else R.string.conscience_needs_permission
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.conscience_try_it))
            }
            Text(
                text = stringResource(R.string.conscience_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConscienceOptions(state: ConscienceUiState, viewModel: ConscienceViewModel) {
    // The English lines don't change with address, so that choice only exists for Jordanian.
    val showAddress = state.voice == ConscienceVoice.JORDANIAN
    val rows = if (showAddress) 3 else 2
    GroupedList {
        ChoiceGroup(stringResource(R.string.conscience_voice_title), ListItemPosition.from(0, rows)) {
            ConnectedChoiceRow(
                options = ConscienceVoice.entries,
                selected = state.voice,
                onSelect = viewModel::setVoice,
                label = { voice ->
                    stringResource(
                        when (voice) {
                            ConscienceVoice.JORDANIAN -> R.string.conscience_voice_jordanian
                            ConscienceVoice.ENGLISH -> R.string.conscience_voice_english
                        }
                    )
                }
            )
        }
        if (showAddress) {
            ChoiceGroup(stringResource(R.string.conscience_address_title), ListItemPosition.from(1, rows)) {
                ConnectedChoiceRow(
                    options = ConscienceAddress.entries,
                    selected = state.address,
                    onSelect = viewModel::setAddress,
                    label = { address ->
                        stringResource(
                            when (address) {
                                ConscienceAddress.MASCULINE -> R.string.conscience_address_masculine
                                ConscienceAddress.FEMININE -> R.string.conscience_address_feminine
                            }
                        )
                    }
                )
            }
        }
        ChoiceGroup(stringResource(R.string.conscience_frequency_title), ListItemPosition.from(rows - 1, rows)) {
            ConnectedChoiceRow(
                options = ConscienceFrequency.entries,
                selected = state.frequency,
                onSelect = viewModel::setFrequency,
                label = { frequency ->
                    stringResource(
                        when (frequency) {
                            ConscienceFrequency.NORMAL -> R.string.conscience_frequency_normal
                            ConscienceFrequency.LESS -> R.string.conscience_frequency_less
                        }
                    )
                }
            )
            Text(
                text = stringResource(
                    when (state.frequency) {
                        ConscienceFrequency.NORMAL -> R.string.conscience_frequency_normal_note
                        ConscienceFrequency.LESS -> R.string.conscience_frequency_less_note
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChoiceGroup(title: String, position: ListItemPosition, content: @Composable ColumnScope.() -> Unit) {
    GroupedColumn(position = position) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
private fun ConsciencePreview(voice: ConscienceVoice, address: ConscienceAddress) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(R.string.conscience_preview_title),
                style = MaterialTheme.typography.titleMedium
            )
            ConscienceLines.samples(voice, address).forEach { line ->
                // The voice is chosen apart from the UI language, so the line sets its own direction.
                Text(
                    text = line,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.Content,
                        textAlign = TextAlign.Start
                    )
                )
            }
        }
    }
}
