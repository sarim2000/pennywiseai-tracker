package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.review.CLOSE_GUESS_PERCENT
import com.pennywiseai.tracker.domain.review.WeeklyGuessQuestion
import com.pennywiseai.tracker.domain.review.guessGapPercent
import com.pennywiseai.tracker.domain.review.isCloseGuess
import com.pennywiseai.tracker.domain.review.sliderStops
import com.pennywiseai.tracker.domain.review.snap
import com.pennywiseai.tracker.ui.components.celebration.Celebration
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import java.math.BigDecimal
import kotlin.math.roundToInt

/** The open guess: its question, the locked-in guess once there is one, and whether to cheer it. */
data class WeeklyGuessSheet(
    val question: WeeklyGuessQuestion,
    val guess: BigDecimal? = null,
    val celebrate: Boolean = false,
)

/** Home's weekly question in the prompt slot: the owl, the question, a way in and "Not now". */
@Composable
internal fun WeeklyGuessCard(
    question: WeeklyGuessQuestion,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, top = Spacing.md, end = Spacing.md, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(size = Dimensions.Icon.hero)
            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                Text(
                    text = stringResource(R.string.weekly_guess_label).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.weekly_guess_question, categoryLabel(question.category), question.currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            FilledIconButton(
                onClick = onOpen,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.weekly_guess_open))
            }
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.padding(start = Spacing.md + Dimensions.Icon.hero + Spacing.xs, bottom = Spacing.xs),
        ) {
            Text(stringResource(R.string.weekly_guess_not_now), color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/**
 * The guess itself: a slider up to about twice a usual week, quick picks, then the reveal.
 * A close guess gets the happy owl and confetti; any other a calm owl and the week to look at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeeklyGuessSheet(
    sheet: WeeklyGuessSheet,
    onLockIn: (BigDecimal) -> Unit,
    onSeeWeek: () -> Unit,
    onCelebrated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val question = sheet.question
    val guess = sheet.guess
    val close = guess != null && isCloseGuess(guess, question.actual)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(R.string.weekly_guess_label), style = MaterialTheme.typography.titleMedium)
                Mascot(
                    size = Dimensions.Icon.detailBadge,
                    mood = when {
                        guess == null -> MascotMood.Thinking
                        close -> MascotMood.Happy
                        else -> MascotMood.Calm
                    },
                )
                Text(
                    text = stringResource(R.string.weekly_guess_question, categoryLabel(question.category), question.currency),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.weekly_guess_week, DateRangeUtils.formatDateRange(question.weekStart, question.weekEnd)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (guess == null) {
                    GuessPicker(question, onLockIn)
                } else {
                    GuessReveal(question, guess, close, onSeeWeek, onDismiss)
                }
            }
            Celebration(play = sheet.celebrate, onFinished = onCelebrated)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuessPicker(question: WeeklyGuessQuestion, onLockIn: (BigDecimal) -> Unit) {
    // A quarter of the way, as a starting point the user moves: never the answer's own spot.
    var picked by rememberSaveable(question.weekStart) {
        mutableStateOf(snap(question.max.divide(BigDecimal(4)), question.step).toPlainString())
    }
    val amount = BigDecimal(picked)
    val shown = CurrencyFormatter.formatCurrency(amount, question.currency)
    val label = stringResource(R.string.weekly_guess_your_guess)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val amountStyle = MaterialTheme.typography.displayMedium.money()
            Text(
                text = shown,
                style = amountStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, amountStyle.fontSize),
            )
            // Whole stops, and never more than the slider can allocate a tick for. A keyboard
            // or TalkBack nudge moves one stop. The ticks aren't drawn.
            val stops = remember(question.weekStart, question.max, question.step, question.actual) {
                sliderStops(question.max, question.step, question.actual)
            }
            val index = stops.indices.minBy { (stops[it] - amount).abs() }.toFloat()
            val slider = remember(question.weekStart, stops) {
                SliderState(index, (stops.size - 2).coerceAtLeast(0), 0f..stops.lastIndex.toFloat())
            }
            // A chip moves the thumb too.
            SideEffect { if (slider.value != index) slider.value = index }
            Slider(
                state = slider,
                onValueChange = {
                    val at = it.roundToInt().coerceIn(stops.indices)
                    picked = stops[at].stripTrailingZeros().toPlainString()
                },
                track = { SliderDefaults.Track(sliderState = it, drawTick = { _, _ -> }) },
                modifier = Modifier.semantics {
                    contentDescription = label
                    stateDescription = shown
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(BigDecimal.ZERO, question.max).forEach {
                    Text(
                        text = CurrencyFormatter.formatCurrency(it, question.currency),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            ) {
                question.chips.forEach { chip ->
                    FilterChip(
                        selected = amount.compareTo(chip) == 0,
                        onClick = { picked = chip.toPlainString() },
                        label = { Text(CurrencyFormatter.formatCurrency(chip, question.currency)) },
                    )
                }
            }
        }
    }
    Button(
        onClick = { onLockIn(amount) },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    ) {
        Text(stringResource(R.string.weekly_guess_lock_in), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GuessReveal(
    question: WeeklyGuessQuestion,
    guess: BigDecimal,
    close: Boolean,
    onSeeWeek: () -> Unit,
    onDone: () -> Unit,
) {
    val currency = question.currency
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (close) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(if (close) R.string.weekly_guess_hit else R.string.weekly_guess_miss),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (close) {
                    stringResource(R.string.weekly_guess_hit_body, CLOSE_GUESS_PERCENT)
                } else {
                    stringResource(R.string.weekly_guess_miss_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.weekly_guess_recorded),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            val recordedStyle = MaterialTheme.typography.displaySmall.money()
            Text(
                text = CurrencyFormatter.formatCurrency(question.actual, currency),
                style = recordedStyle,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, recordedStyle.fontSize),
            )
            Text(
                text = stringResource(R.string.weekly_guess_your_guess) + " · " + CurrencyFormatter.formatCurrency(guess, currency),
                style = MaterialTheme.typography.titleMedium.money(),
            )
            Text(
                text = stringResource(
                    R.string.weekly_guess_gap,
                    CurrencyFormatter.formatCurrency((guess - question.actual).abs(), currency),
                    guessGapPercent(guess, question.actual),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Text(
        text = stringResource(R.string.weekly_guess_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (!close) {
        TextButton(onClick = onSeeWeek) { Text(stringResource(R.string.weekly_guess_see_week)) }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.weekly_guess_done))
    }
}
