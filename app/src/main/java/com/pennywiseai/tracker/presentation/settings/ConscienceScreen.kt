package com.pennywiseai.tracker.presentation.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.manager.ConscienceText
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceKind
import com.pennywiseai.tracker.domain.conscience.ConscienceReaction
import com.pennywiseai.tracker.domain.conscience.ConscienceRemark
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import java.time.Instant
import java.time.ZoneId

/** The order the screen lists the kinds in: the friendliest first. */
private val KIND_ORDER = listOf(
    ConscienceKind.POSITIVE,
    ConscienceKind.REPEAT,
    ConscienceKind.LATE_NIGHT,
    ConscienceKind.COMEBACK,
    ConscienceKind.WEEKLY,
    ConscienceKind.RECORD_DAY,
    ConscienceKind.SPIKE,
    ConscienceKind.HELLO,
)

private const val RECENT_PREVIEW = 3

private fun ConscienceKind.emoji(): String = when (this) {
    ConscienceKind.POSITIVE -> "🌟"
    ConscienceKind.COMEBACK -> "🪃"
    ConscienceKind.REPEAT -> "🔁"
    ConscienceKind.WEEKLY -> "🗓️"
    ConscienceKind.LATE_NIGHT -> "🦉"
    ConscienceKind.RECORD_DAY -> "🏆"
    ConscienceKind.SPIKE -> "📈"
    ConscienceKind.HELLO -> "👋"
}

private fun ConscienceKind.titleRes(): Int = when (this) {
    ConscienceKind.POSITIVE -> R.string.conscience_kind_positive
    ConscienceKind.COMEBACK -> R.string.conscience_kind_comeback
    ConscienceKind.REPEAT -> R.string.conscience_kind_repeat
    ConscienceKind.WEEKLY -> R.string.conscience_kind_weekly
    ConscienceKind.LATE_NIGHT -> R.string.conscience_kind_late_night
    ConscienceKind.RECORD_DAY -> R.string.conscience_kind_record_day
    ConscienceKind.SPIKE -> R.string.conscience_kind_spike
    ConscienceKind.HELLO -> R.string.conscience_kind_hello
}

private fun ConscienceKind.descriptionRes(): Int = when (this) {
    ConscienceKind.POSITIVE -> R.string.conscience_kind_positive_desc
    ConscienceKind.COMEBACK -> R.string.conscience_kind_comeback_desc
    ConscienceKind.REPEAT -> R.string.conscience_kind_repeat_desc
    ConscienceKind.WEEKLY -> R.string.conscience_kind_weekly_desc
    ConscienceKind.LATE_NIGHT -> R.string.conscience_kind_late_night_desc
    ConscienceKind.RECORD_DAY -> R.string.conscience_kind_record_day_desc
    ConscienceKind.SPIKE -> R.string.conscience_kind_spike_desc
    ConscienceKind.HELLO -> R.string.conscience_kind_hello_desc
}

private fun ConscienceStatus.mood(): MascotMood = when (this) {
    ConscienceStatus.OFF -> MascotMood.Asleep
    ConscienceStatus.BLOCKED -> MascotMood.Worried
    ConscienceStatus.LEARNING -> MascotMood.Thinking
    ConscienceStatus.LISTENING -> MascotMood.Happy
}

private fun ConscienceStatus.textRes(): Int = when (this) {
    ConscienceStatus.OFF -> R.string.conscience_status_off
    ConscienceStatus.BLOCKED -> R.string.conscience_status_blocked
    ConscienceStatus.LEARNING -> R.string.conscience_status_learning
    ConscienceStatus.LISTENING -> R.string.conscience_status_listening
}

/** A line may be in a script that runs the other way from the UI's, so each sets its own direction. */
@Composable
private fun lineStyle() = MaterialTheme.typography.bodyLarge.copy(
    textDirection = TextDirection.Content,
    textAlign = TextAlign.Start
)

@Composable
fun ConscienceScreen(onNavigateBack: () -> Unit, viewModel: ConscienceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales.toLanguageTags()
    LaunchedEffect(locales) {
        // Switched to a language without lines: the restored back stack must not keep this screen.
        if (!ConscienceText(context).speaksAppLanguage) onNavigateBack() else viewModel.onLocalesChanged(locales)
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
    }
    // On Android 13+ turning it on asks for the permission first; a refusal leaves it off with a word on why.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.setEnabled(true)
            viewModel.refreshPermission()
        } else {
            Toast.makeText(context, R.string.conscience_needs_permission, Toast.LENGTH_LONG).show()
        }
    }
    val onToggle: (Boolean) -> Unit = { enabled ->
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
    }

    SettingsPageScaffold(title = stringResource(R.string.conscience_title), onNavigateBack = onNavigateBack) {
        GroupedList {
            HeroBlock(state.status)
            GroupedRow(position = ListItemPosition.Bottom, onClick = { onToggle(!state.enabled) }) {
                RowLabels(
                    title = stringResource(R.string.conscience_switch_title),
                    subtitle = stringResource(state.status.textRes()),
                    titleMaxLines = 2,
                    subtitleMaxLines = Int.MAX_VALUE
                )
                Switch(checked = state.enabled, onCheckedChange = onToggle)
            }
        }
        if (state.status == ConscienceStatus.BLOCKED) {
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.conscience_open_notification_settings))
            }
        }

        SectionHeaderV2(title = stringResource(R.string.conscience_examples_title))
        ExampleBlock(
            state = state,
            onAnother = viewModel::nextExample,
            onSend = {
                val message = if (viewModel.tryIt()) R.string.conscience_try_sent else R.string.conscience_needs_permission
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )

        SectionHeaderV2(title = stringResource(R.string.conscience_kinds_title))
        GroupedList {
            KIND_ORDER.forEachIndexed { index, kind ->
                val on = kind !in state.mutedKinds
                GroupedRow(
                    position = ListItemPosition.from(index, KIND_ORDER.size),
                    onClick = { viewModel.setKindEnabled(kind, !on) }
                ) {
                    EmojiBadge(kind.emoji())
                    RowLabels(
                        title = stringResource(kind.titleRes()),
                        subtitle = stringResource(kind.descriptionRes()),
                        titleMaxLines = 2,
                        subtitleMaxLines = Int.MAX_VALUE
                    )
                    Switch(checked = on, onCheckedChange = { viewModel.setKindEnabled(kind, it) })
                }
            }
        }

        SectionHeaderV2(title = stringResource(R.string.conscience_frequency_title))
        GroupedList {
            GroupedColumn(position = ListItemPosition.Single) {
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        RecentRemarks(state, viewModel)

        Text(
            text = stringResource(R.string.conscience_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The owl wears the status: asleep when off, worried when blocked, thinking while it learns. */
@Composable
private fun HeroBlock(status: ConscienceStatus) {
    GroupedRow(position = ListItemPosition.Top, contentPadding = PaddingValues(Dimensions.Padding.card)) {
        Mascot(size = Dimensions.Icon.extraLarge, mood = status.mood(), interactive = true)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(R.string.conscience_hero_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.conscience_hero_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmojiBadge(emoji: String) {
    Box(
        modifier = Modifier
            .size(Dimensions.Icon.avatarLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialShapes.Cookie9Sided.toShape())
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleLarge)
    }
}

/** Looks like the notification the user would get, so the joke is read where it lands. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExampleBlock(state: ConscienceUiState, onAnother: () -> Unit, onSend: () -> Unit) {
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    GroupedList {
        GroupedColumn(position = ListItemPosition.Top) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smd), verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(state.exampleKind.emoji())
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.conscience_notification_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(state.exampleKind.titleRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.conscience_example_badge),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            }
            AnimatedContent(
                targetState = state.example,
                transitionSpec = {
                    fadeIn(effects) togetherWith fadeOut(effects)
                },
                label = "conscience_example"
            ) { example ->
                Text(text = example, modifier = Modifier.fillMaxWidth(), style = lineStyle())
            }
        }
        GroupedColumn(position = ListItemPosition.Bottom) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(onClick = onAnother, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.conscience_another_example))
                }
                Button(onClick = onSend, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.conscience_try_it))
                }
            }
        }
    }
}

@Composable
private fun RecentRemarks(state: ConscienceUiState, viewModel: ConscienceViewModel) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val canExpand = state.remarks.size > RECENT_PREVIEW
    SectionHeaderV2(
        title = stringResource(R.string.conscience_recent_title),
        action = if (canExpand) {
            {
                TextButton(onClick = { showAll = !showAll }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(if (showAll) R.string.conscience_recent_show_less else R.string.conscience_recent_show_all))
                }
            }
        } else {
            null
        }
    )
    GroupedList {
        if (state.remarks.isEmpty()) {
            GroupedColumn(position = ListItemPosition.Single) {
                Text(
                    text = stringResource(R.string.conscience_recent_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val shown = if (showAll) state.remarks else state.remarks.take(RECENT_PREVIEW)
            shown.forEachIndexed { index, remark ->
                RemarkRow(remark, ListItemPosition.from(index, shown.size)) { viewModel.react(remark, it) }
            }
        }
    }
    if (state.likedCount + state.retiredCount > 0) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.conscience_feedback_summary, state.likedCount.toString(), state.retiredCount.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = viewModel::resetFeedback, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.conscience_reset_feedback))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemarkRow(remark: ConscienceRemark, position: ListItemPosition, onReact: (ConscienceReaction) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(remark.atMillis, locale) {
        AppDates.dayMonthTimeFormatter(locale).format(Instant.ofEpochMilli(remark.atMillis).atZone(ZoneId.systemDefault()))
    }
    val meta = if (remark.notified) time else "$time · ${stringResource(R.string.conscience_recent_saved_quietly)}"
    GroupedColumn(position = position) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smd)) {
            EmojiBadge(remark.kind.emoji())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(text = remark.text, modifier = Modifier.fillMaxWidth(), style = lineStyle())
                Text(text = meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (remark.reaction) {
            ConscienceReaction.LIKED -> ReactionNote(R.string.conscience_reaction_liked)
            ConscienceReaction.RETIRED -> ReactionNote(R.string.conscience_reaction_retired)
            null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { onReact(ConscienceReaction.LIKED) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.conscience_action_like))
                }
                OutlinedButton(onClick = { onReact(ConscienceReaction.RETIRED) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.conscience_action_dislike))
                }
            }
        }
    }
}

@Composable
private fun ReactionNote(res: Int) {
    Text(text = stringResource(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
