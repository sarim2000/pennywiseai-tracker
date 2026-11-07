package com.pennywiseai.tracker.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import com.pennywiseai.tracker.domain.conscience.ConscienceAddress
import com.pennywiseai.tracker.domain.conscience.ConscienceFrequency
import com.pennywiseai.tracker.domain.conscience.ConscienceKind
import com.pennywiseai.tracker.domain.conscience.ConscienceReaction
import com.pennywiseai.tracker.domain.conscience.ConscienceRemark
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.PreferenceSwitch
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.toLatinDigits
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shown kinds, friendliest first. */
private val KIND_ORDER = listOf(
    ConscienceKind.HELLO,
    ConscienceKind.REPEAT,
    ConscienceKind.COMEBACK,
    ConscienceKind.WEEKLY,
    ConscienceKind.LATE_NIGHT,
    ConscienceKind.RECORD_DAY,
    ConscienceKind.SPIKE,
    ConscienceKind.POSITIVE,
)

private const val RECENT_PREVIEW = 3

private fun ConscienceKind.emoji(): String = when (this) {
    ConscienceKind.HELLO -> "👋"
    ConscienceKind.REPEAT -> "🔁"
    ConscienceKind.COMEBACK -> "🪃"
    ConscienceKind.WEEKLY -> "🗓️"
    ConscienceKind.LATE_NIGHT -> "🦉"
    ConscienceKind.RECORD_DAY -> "🏆"
    ConscienceKind.SPIKE -> "📈"
    ConscienceKind.POSITIVE -> "🌟"
}

private fun ConscienceKind.titleRes(): Int = when (this) {
    ConscienceKind.HELLO -> R.string.conscience_kind_hello
    ConscienceKind.REPEAT -> R.string.conscience_kind_repeat
    ConscienceKind.COMEBACK -> R.string.conscience_kind_comeback
    ConscienceKind.WEEKLY -> R.string.conscience_kind_weekly
    ConscienceKind.LATE_NIGHT -> R.string.conscience_kind_late_night
    ConscienceKind.RECORD_DAY -> R.string.conscience_kind_record_day
    ConscienceKind.SPIKE -> R.string.conscience_kind_spike
    ConscienceKind.POSITIVE -> R.string.conscience_kind_positive
}

private fun ConscienceKind.descriptionRes(): Int = when (this) {
    ConscienceKind.HELLO -> R.string.conscience_kind_hello_desc
    ConscienceKind.REPEAT -> R.string.conscience_kind_repeat_desc
    ConscienceKind.COMEBACK -> R.string.conscience_kind_comeback_desc
    ConscienceKind.WEEKLY -> R.string.conscience_kind_weekly_desc
    ConscienceKind.LATE_NIGHT -> R.string.conscience_kind_late_night_desc
    ConscienceKind.RECORD_DAY -> R.string.conscience_kind_record_day_desc
    ConscienceKind.SPIKE -> R.string.conscience_kind_spike_desc
    ConscienceKind.POSITIVE -> R.string.conscience_kind_positive_desc
}

private fun ConscienceStatus.emoji(): String = when (this) {
    ConscienceStatus.OFF -> "😴"
    ConscienceStatus.BLOCKED -> "🙊"
    ConscienceStatus.LEARNING -> "🧐"
    ConscienceStatus.LISTENING -> "😏"
}

private fun ConscienceStatus.textRes(): Int = when (this) {
    ConscienceStatus.OFF -> R.string.conscience_status_off
    ConscienceStatus.BLOCKED -> R.string.conscience_status_blocked
    ConscienceStatus.LEARNING -> R.string.conscience_status_learning
    ConscienceStatus.LISTENING -> R.string.conscience_status_listening
}

/** Lines may be in a script that runs the other way from the UI's, so each sets its own direction. */
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
    LaunchedEffect(locales) { viewModel.onLocalesChanged(locales) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
    }
    // On Android 13+ turning it on asks for the permission first; a refusal leaves it off with a word on why.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
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
                .padding(
                    start = Spacing.Layout.screenHorizontal,
                    end = Spacing.Layout.screenHorizontal,
                    top = Spacing.md,
                    bottom = Spacing.Layout.scrollBottomPadding
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            HeroCard(state.status, onBadgeTap = viewModel::nextExample)
            GroupedList {
                PreferenceSwitch(
                    title = stringResource(R.string.conscience_switch_title),
                    subtitle = stringResource(state.status.textRes()),
                    checked = state.enabled,
                    onCheckedChange = onToggle,
                    position = ListItemPosition.Single
                )
            }
            if (state.status == ConscienceStatus.BLOCKED) {
                FilledTonalButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.conscience_open_notification_settings))
                }
            }

            SectionHeaderV2(title = stringResource(R.string.conscience_examples_title))
            ExampleBubble(
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
                    PreferenceSwitch(
                        title = stringResource(kind.titleRes()),
                        subtitle = stringResource(kind.descriptionRes()),
                        checked = kind !in state.mutedKinds,
                        onCheckedChange = { viewModel.setKindEnabled(kind, it) },
                        position = ListItemPosition.from(index, KIND_ORDER.size),
                        leadingIcon = { EmojiBadge(kind.emoji()) }
                    )
                }
            }

            SectionHeaderV2(title = stringResource(R.string.conscience_settings_title))
            HowItTalks(state, viewModel)

            RecentRemarks(state, viewModel)

            Text(
                text = stringResource(R.string.conscience_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(status: ConscienceStatus, onBadgeTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Icon.emptyStateContainer)
                    .scale(bounce.value)
                    .background(MaterialTheme.colorScheme.tertiary, MaterialShapes.Cookie9Sided.toShape())
                    .clickable {
                        onBadgeTap()
                        scope.launch {
                            bounce.snapTo(0.8f)
                            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
                        }
                    }
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = status.emoji(),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "conscience_mood"
                ) { emoji ->
                    Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.conscience_hero_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.conscience_hero_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmojiBadge(emoji: String) {
    Box(
        modifier = Modifier
            .size(Dimensions.Icon.list)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie6Sided.toShape())
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
    }
}

/** Looks like the notification the user would get, so the joke is read where it lands. */
@Composable
private fun ExampleBubble(state: ConscienceUiState, onAnother: () -> Unit, onSend: () -> Unit) {
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.smd)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiBadge(state.exampleKind.emoji())
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.conscience_notification_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(state.exampleKind.titleRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "conscience_example"
            ) { example ->
                Text(text = example, modifier = Modifier.fillMaxWidth(), style = lineStyle())
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(onClick = onAnother, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.conscience_another_example))
                }
                FilledTonalButton(onClick = onSend, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.conscience_try_it))
                }
            }
        }
    }
}

@Composable
private fun HowItTalks(state: ConscienceUiState, viewModel: ConscienceViewModel) {
    val rows = if (state.showAddress) 3 else 2
    GroupedList {
        GroupedColumn(position = ListItemPosition.from(0, rows), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(text = stringResource(R.string.conscience_language_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.conscience_language_follows, state.languageName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.showAddress) {
            GroupedColumn(position = ListItemPosition.from(1, rows)) {
                Text(text = stringResource(R.string.conscience_address_title), style = MaterialTheme.typography.bodyLarge)
                ConnectedChoiceRow(
                    options = ConscienceAddress.entries,
                    selected = state.address,
                    onSelect = viewModel::setAddress,
                    label = { address ->
                        stringResource(
                            when (address) {
                                ConscienceAddress.AUTO -> R.string.conscience_address_auto
                                ConscienceAddress.MASCULINE -> R.string.conscience_address_masculine
                                ConscienceAddress.FEMININE -> R.string.conscience_address_feminine
                            }
                        )
                    }
                )
            }
        }
        GroupedColumn(position = ListItemPosition.from(rows - 1, rows)) {
            Text(text = stringResource(R.string.conscience_frequency_title), style = MaterialTheme.typography.bodyLarge)
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
private fun RecentRemarks(state: ConscienceUiState, viewModel: ConscienceViewModel) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val canExpand = state.remarks.size > RECENT_PREVIEW
    SectionHeaderV2(
        title = stringResource(R.string.conscience_recent_title),
        action = if (canExpand) {
            {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(stringResource(if (showAll) R.string.conscience_recent_show_less else R.string.conscience_recent_show_all))
                }
            }
        } else {
            null
        }
    )
    if (state.remarks.isEmpty()) {
        PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.conscience_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val shown = if (showAll) state.remarks else state.remarks.take(RECENT_PREVIEW)
        GroupedList {
            shown.forEachIndexed { index, remark ->
                RemarkRow(remark, ListItemPosition.from(index, shown.size)) { reaction ->
                    viewModel.react(remark.id, reaction)
                }
            }
        }
    }
    if (state.likedCount + state.retiredCount > 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.conscience_feedback_summary,
                    state.likedCount.toString(),
                    state.retiredCount.toString()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = viewModel::resetFeedback) {
                Text(stringResource(R.string.conscience_reset_feedback))
            }
        }
    }
}

@Composable
private fun RemarkRow(remark: ConscienceRemark, position: ListItemPosition, onReact: (ConscienceReaction) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM · HH:mm", locale) }
    val time = remember(remark.atMillis, formatter) {
        formatter.format(Instant.ofEpochMilli(remark.atMillis).atZone(ZoneId.systemDefault())).toLatinDigits()
    }
    GroupedColumn(position = position, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smd)) {
            EmojiBadge(remark.kind.emoji())
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(text = remark.text, modifier = Modifier.fillMaxWidth(), style = lineStyle())
                val meta = buildList {
                    add(time)
                    if (!remark.notified) add(stringResource(R.string.conscience_recent_saved_quietly))
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary
    )
}
