package com.pennywiseai.tracker.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.challenges.ChallengeDay
import com.pennywiseai.tracker.domain.challenges.ChallengeKind
import com.pennywiseai.tracker.domain.challenges.ChallengeOutcome
import com.pennywiseai.tracker.domain.challenges.ChallengeProgress
import com.pennywiseai.tracker.domain.challenges.ChallengeRun
import com.pennywiseai.tracker.domain.challenges.DayStatus
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.celebration.Celebration
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.icons.CategoryMapping
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AppDates
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * The Plan tab's Challenges section: the running challenge with its days, or the last
 * result, then the challenges to start. One runs at a time.
 */
@Composable
fun PlanChallengesScreen(
    host: PlanTabHost,
    onOpenTransaction: (Long) -> Unit,
    viewModel: ChallengesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val celebrate by viewModel.celebrate.collectAsStateWithLifecycle()
    var confirmGiveUp by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PlanListScreen(host) {
            val shown = state ?: return@PlanListScreen
            val active = shown.active
            if (active != null) {
                item(key = "active") {
                    ActiveChallengeCard(active, onGiveUp = { confirmGiveUp = true }, onOpenTransaction = onOpenTransaction)
                }
                return@PlanListScreen
            }
            shown.result?.let { outcome ->
                item(key = "result") {
                    ResultCard(
                        outcome,
                        onTryAgain = { viewModel.start(outcome.run.kind, outcome.run.categoryName, outcome.run.categoryId) },
                        onDone = viewModel::dismissResult,
                    )
                }
            }
            item(key = "presets") {
                ChallengePresets(categories = shown.categories, afterResult = shown.result != null, onStart = { kind, name -> viewModel.start(kind, name) })
            }
        }
        Celebration(play = celebrate, onFinished = viewModel::onCelebrated)
    }

    if (confirmGiveUp) {
        AlertDialog(
            onDismissRequest = { confirmGiveUp = false },
            title = { Text(stringResource(R.string.challenge_give_up_title)) },
            text = { Text(stringResource(R.string.challenge_give_up_body)) },
            confirmButton = {
                TextButton(onClick = { confirmGiveUp = false; state?.active?.run?.let(viewModel::giveUp) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.challenge_give_up))
                }
            },
            dismissButton = {
                Button(onClick = { confirmGiveUp = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.challenge_keep_going))
                }
            },
        )
    }
}

@Composable
private fun ChallengeRun.title(): String = when (kind) {
    ChallengeKind.NO_SPEND_DAYS -> pluralStringResource(R.plurals.challenge_no_spend_title, target, target)
    ChallengeKind.CATEGORY_WEEK -> stringResource(R.string.challenge_category_week_title, categoryLabel(categoryName.orEmpty()))
}

@Composable
private fun ChallengeRun.description(): String = when (kind) {
    ChallengeKind.NO_SPEND_DAYS -> pluralStringResource(R.plurals.challenge_no_spend_body, target, target, length)
    ChallengeKind.CATEGORY_WEEK -> stringResource(R.string.challenge_category_week_body, categoryLabel(categoryName.orEmpty()))
}

private fun ChallengeRun.icon(): ImageVector = when (kind) {
    ChallengeKind.NO_SPEND_DAYS -> Icons.Default.MoneyOff
    ChallengeKind.CATEGORY_WEEK -> categoryName?.let { CategoryMapping.categories[it]?.icon } ?: Icons.Default.Category
}

/** A day as TalkBack reads it: "Thursday, 20 Nov: clean". */
@Composable
private fun ChallengeDay.description(): String {
    val date = AppDates.pattern("EEEE").format(date) + ", " + AppDates.dayMonthFormatter().format(date)
    val state = when (status) {
        DayStatus.CLEAN -> stringResource(R.string.challenge_day_clean)
        DayStatus.MISSED -> stringResource(R.string.challenge_day_missed, misses.joinToString { it.merchant })
        DayStatus.TODAY -> stringResource(R.string.challenge_day_today)
        DayStatus.FUTURE -> stringResource(R.string.challenge_day_future)
    }
    return stringResource(R.string.challenge_day_description, date, state)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveChallengeCard(progress: ChallengeProgress, onGiveUp: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val run = progress.run
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onTertiaryContainer
    // The missed day whose transactions are shown, by epoch day.
    var picked by rememberSaveable(run.startEpochDay, run.kind) { mutableStateOf<Long?>(null) }
    val pickedDay = progress.days.firstOrNull { it.date.toEpochDay() == picked && it.status == DayStatus.MISSED }
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = scheme.tertiaryContainer,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    Modifier
                        .size(Dimensions.Icon.hero)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(scheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(run.icon(), contentDescription = null, tint = ink, modifier = Modifier.size(Dimensions.Icon.medium))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.challenge_active_overline, progress.dayNumber, run.length),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(run.title(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text(run.description(), style = MaterialTheme.typography.bodyMedium)
            DayDots(progress.days, picked = pickedDay?.date, onPick = { day ->
                picked = day.date.toEpochDay().takeUnless { it == picked }
            })
            pickedDay?.let { day ->
                Column {
                    Text(
                        stringResource(R.string.challenge_missed_on, AppDates.dayMonthFormatter().format(day.date)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    day.misses.forEach { miss ->
                        TextButton(
                            onClick = { onOpenTransaction(miss.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = ink),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(miss.merchant, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.Icon.inline),
                            )
                        }
                    }
                }
            }
            MascotLine(progress)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.challenge_days_done, run.target, progress.cleanDays, run.target),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onGiveUp,
                    colors = ButtonDefaults.textButtonColors(contentColor = ink),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.challenge_give_up)) }
            }
        }
    }
}

/** The window's days under their narrow weekday names; a missed day opens what counted against it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayDots(days: List<ChallengeDay>, picked: LocalDate?, onPick: (ChallengeDay) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onTertiaryContainer
    val cookie = MaterialShapes.Cookie9Sided.toShape()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        days.forEach { day ->
            val description = day.description()
            val missed = day.status == DayStatus.MISSED
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .then(if (missed) Modifier.clickable(role = Role.Button) { onPick(day) } else Modifier)
                    .clearAndSetSemantics { contentDescription = description }
                    .padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val (shape, fill) = when (day.status) {
                    DayStatus.CLEAN -> cookie to ink
                    DayStatus.MISSED, DayStatus.TODAY -> CircleShape to scheme.surfaceContainerLowest
                    DayStatus.FUTURE -> CircleShape to ink.copy(alpha = Dimensions.Alpha.categoryTint).compositeOver(scheme.tertiaryContainer)
                }
                Box(
                    Modifier
                        .widthIn(max = Dimensions.Icon.avatarLarge)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(fill)
                        .then(if (day.date == picked) Modifier.border(Dimensions.Component.avatarRingWidth, ink, shape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    val glyph = when (day.status) {
                        DayStatus.CLEAN -> Icons.Default.Check to scheme.tertiaryContainer
                        DayStatus.MISSED -> Icons.Default.Close to scheme.onSurfaceVariant
                        DayStatus.TODAY -> Icons.Default.Schedule to ink
                        DayStatus.FUTURE -> null
                    }
                    glyph?.let { (icon, tint) ->
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimensions.Icon.inline))
                    }
                }
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, AppDates.locale()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** The owl on how today is going. Today only settles tomorrow, so it says "so far". */
@Composable
private fun MascotLine(progress: ChallengeProgress) {
    val run = progress.run
    val today = progress.today
    val missedToday = today?.status == DayStatus.MISSED
    val line = when {
        missedToday -> stringResource(R.string.challenge_today_missed, today.misses.firstOrNull()?.merchant.orEmpty())
        run.kind == ChallengeKind.NO_SPEND_DAYS -> stringResource(R.string.challenge_today_no_spend)
        else -> stringResource(R.string.challenge_today_no_category, categoryLabel(run.categoryName.orEmpty()))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Mascot(size = Dimensions.Icon.large, mood = if (missedToday) MascotMood.Calm else MascotMood.Happy)
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** A finished challenge: a win cheers, a miss says how close it came. */
@Composable
private fun ResultCard(outcome: ChallengeOutcome, onTryAgain: () -> Unit, onDone: () -> Unit) {
    val run = outcome.run
    val scheme = MaterialTheme.colorScheme
    val headline = when {
        outcome.won -> stringResource(R.string.challenge_won_title)
        run.target - outcome.cleanDays <= CLOSE -> stringResource(R.string.challenge_close_title, outcome.cleanDays, run.target)
        else -> stringResource(R.string.challenge_lost_title, outcome.cleanDays, run.target)
    }
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = if (outcome.won) scheme.tertiaryContainer else scheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Mascot(size = Dimensions.Icon.hero, mood = if (outcome.won) MascotMood.Happy else MascotMood.Calm)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(if (outcome.won) R.string.challenge_won_body else R.string.challenge_lost_body, run.title()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            TextButton(
                onClick = onDone,
                colors = ButtonDefaults.textButtonColors(contentColor = if (outcome.won) scheme.onTertiaryContainer else scheme.primary),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.challenge_done)) }
            if (!outcome.won) {
                Button(onClick = onTryAgain, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.challenge_try_again)) }
            }
        }
    }
}

/** The challenges to start: no-spend days, then a week without each of the user's top categories. */
@Composable
private fun ChallengePresets(categories: List<String>, afterResult: Boolean, onStart: (ChallengeKind, String?) -> Unit) {
    val presets = listOf(ChallengeKind.NO_SPEND_DAYS to null) + categories.map { ChallengeKind.CATEGORY_WEEK to it }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.smd)) {
        SectionHeaderV2(
            title = stringResource(if (afterResult) R.string.challenges_try_another else R.string.challenges_start_one),
            subtitle = stringResource(R.string.challenges_subtitle),
            topSpacing = Spacing.none,
        )
        GroupedList {
            presets.forEachIndexed { i, (kind, category) ->
                val run = ChallengeRun.start(kind, category, LocalDate.now())
                val title = run.title()
                val color = if (category == null) MaterialTheme.colorScheme.primary else CategoryMapping.colorFor(category)
                GroupedRow(position = ListItemPosition.from(i, presets.size)) {
                    PlanIconBadge(icon = run.icon(), color = color, index = i)
                    RowLabels(title = title, subtitle = run.description(), titleMaxLines = 2, subtitleMaxLines = 3)
                    val startLabel = stringResource(R.string.challenge_start_description, title)
                    FilledTonalButton(onClick = { onStart(kind, category) }, shapes = ButtonDefaults.shapes()) {
                        // "Start" alone doesn't say which, out of context.
                        Text(stringResource(R.string.challenge_start), Modifier.semantics { contentDescription = startLabel })
                    }
                }
            }
        }
    }
}

/** Within this many days of the target, a miss reads as "close". */
private const val CLOSE = 2
