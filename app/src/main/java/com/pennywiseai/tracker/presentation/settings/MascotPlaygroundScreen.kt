package com.pennywiseai.tracker.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.events.MascotMoment
import com.pennywiseai.tracker.data.events.MascotMoments
import com.pennywiseai.tracker.data.events.TransactionEvents
import com.pennywiseai.tracker.data.events.TransactionOrigin
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.tabular
import com.pennywiseai.tracker.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.roundToInt

/** Announces saves and moments for the playground's reaction buttons, through the app's own event sources. */
@HiltViewModel
class MascotPlaygroundViewModel @Inject constructor(
    private val transactionEvents: TransactionEvents,
    private val currencyPreferences: CurrencyPreferences,
    private val mascotMoments: MascotMoments,
) : ViewModel() {
    /**
     * What a manual add of [type] announces once it commits, with a sample amount in the
     * base currency for the tab's chip. Nothing is written.
     */
    fun fire(type: TransactionType) {
        viewModelScope.launch {
            val money = Money(SAMPLE_AMOUNT, currencyPreferences.baseCurrency.first())
            transactionEvents.onSaved(type, TransactionOrigin.MANUAL, money = money)
        }
    }

    /** Announces the moment named [name] (see [PLAYGROUND_MOMENTS]), with sample figures. Nothing changes. */
    fun fireMoment(name: String) {
        viewModelScope.launch {
            val money = Money(SAMPLE_AMOUNT, currencyPreferences.baseCurrency.first())
            sampleMoment(name, money)?.let(mascotMoments::announce)
        }
    }

    private companion object {
        val SAMPLE_AMOUNT = BigDecimal("12.50")
    }
}

/** Every moment, by the code's own name, in the order the playground shows them. */
internal val PLAYGROUND_MOMENTS = listOf(
    "GoalContributed", "GoalReached", "DebtPaidOff", "MessageTaught", "RulesApplied", "StatementImported",
    "BackupSaved", "BackupRestored", "BillTracked", "BillPaid", "AmountsHidden", "AmountsShown",
)

/** The moment named [name], with [money] and a sample count where it carries one. */
internal fun sampleMoment(name: String, money: Money): MascotMoment? = when (name) {
    "GoalContributed" -> MascotMoment.GoalContributed(money)
    "GoalReached" -> MascotMoment.GoalReached
    "DebtPaidOff" -> MascotMoment.DebtPaidOff
    "MessageTaught" -> MascotMoment.MessageTaught
    "RulesApplied" -> MascotMoment.RulesApplied(SAMPLE_COUNT)
    "StatementImported" -> MascotMoment.StatementImported(SAMPLE_COUNT)
    "BackupSaved" -> MascotMoment.BackupSaved
    "BackupRestored" -> MascotMoment.BackupRestored
    "BillTracked" -> MascotMoment.BillTracked
    "BillPaid" -> MascotMoment.BillPaid(money)
    "AmountsHidden" -> MascotMoment.AmountsHidden
    "AmountsShown" -> MascotMoment.AmountsShown
    else -> null
}

private const val SAMPLE_COUNT = 12

/**
 * Developer mode's mascot playground, for checking it on a device: a large mascot to poke
 * and drag, the same pose at the sizes the app draws it (96dp, the chat's 32dp glyph and
 * the tab's 24dp glyph in its pill), a slider for the shapes that draw a figure, and every
 * shape in every mood to pick from. The bottom bar stays up on this screen, so the
 * reaction buttons play on the real Chat tab.
 *
 * Shape and mood names are the code's own, as the plan and the QA notes name them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MascotPlaygroundScreen(
    onNavigateBack: () -> Unit,
    viewModel: MascotPlaygroundViewModel = hiltViewModel(),
) {
    var shape by rememberSaveable { mutableStateOf(MascotShape.Owl) }
    var mood by rememberSaveable { mutableStateOf(MascotMood.Calm) }
    var value by rememberSaveable { mutableFloatStateOf(SAMPLE_VALUE) }
    val range = valueRange(shape)
    // The figure carries over between shapes, brought into the new one's range.
    val slider = remember(range) { range?.let { SliderState(value.coerceIn(it), 0, it) } }
    val shown = slider?.value ?: 0f
    // Collapsing, the slider row keeps drawing the slider it last had.
    val lastSlider = remember { arrayOfNulls<SliderState>(1) }
    if (slider != null) lastSlider[0] = slider

    PennyWiseScaffold(
        title = stringResource(R.string.mascot_playground_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.mascot_playground_back))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The stage stays put while the grid scrolls, so a pick shows at once.
            PennyWiseCardV2(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
                // The sizes the app really draws: hero, reply, and the tab in its selection pill.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Mascot(size = Dimensions.Icon.extraLarge, shape = shape, mood = mood, value = shown, interactive = true, alive = true)
                    Mascot(size = Dimensions.Icon.large, shape = shape, mood = mood, value = shown)
                    // As the tab draws it: in the selection pill, its eyes cut through to it.
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = Spacing.md + Spacing.xs, vertical = Spacing.xs)
                    ) {
                        Mascot(size = Dimensions.Icon.medium, shape = shape, mood = mood, value = shown)
                    }
                }
                Text(
                    text = "${shape.name} · ${mood.name}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                Text(
                    text = stringResource(R.string.mascot_playground_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(visible = slider != null, enter = pwExpand(), exit = pwCollapse()) {
                    val state = lastSlider[0] ?: return@AnimatedVisibility
                    val label = stringResource(R.string.mascot_playground_value, (state.value * 100).roundToInt())
                    Column(Modifier.padding(top = Spacing.sm)) {
                        Text(label, style = MaterialTheme.typography.labelLarge.tabular())
                        Slider(
                            state = state,
                            onValueChange = {
                                state.value = it
                                value = it
                            },
                            modifier = Modifier.semantics { contentDescription = label },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    // The bottom bar stays up over this screen.
                    bottom = Dimensions.Component.bottomBarHeight + Spacing.md,
                ),
            ) {
                item {
                    SectionHeaderV2(
                        title = stringResource(R.string.mascot_playground_reactions_title),
                        subtitle = stringResource(R.string.mascot_playground_reactions_body),
                        topSpacing = Spacing.md,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(top = Spacing.Layout.headerToContent),
                    ) {
                        TransactionType.entries.forEach { type ->
                            OutlinedButton(onClick = { viewModel.fire(type) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(type.label()))
                            }
                        }
                    }
                }
                item {
                    SectionHeaderV2(
                        title = stringResource(R.string.mascot_playground_moments_title),
                        subtitle = stringResource(R.string.mascot_playground_moments_body),
                        topSpacing = Spacing.Layout.sectionGap,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(top = Spacing.Layout.headerToContent),
                    ) {
                        PLAYGROUND_MOMENTS.forEach { name ->
                            OutlinedButton(onClick = { viewModel.fireMoment(name) }, shapes = ButtonDefaults.shapes()) {
                                Text(name)
                            }
                        }
                    }
                }
                item {
                    SectionHeaderV2(
                        title = stringResource(R.string.mascot_playground_grid_title),
                        subtitle = stringResource(R.string.mascot_playground_grid_body),
                        topSpacing = Spacing.Layout.sectionGap,
                    )
                    Row(Modifier.padding(top = Spacing.Layout.headerToContent)) {
                        Box(Modifier.weight(1f))
                        MascotMood.entries.forEach {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                items(MascotShape.entries, key = { it.name }) { rowShape ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rowShape.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MascotMood.entries.forEach { cellMood ->
                            GridCell(
                                shape = rowShape,
                                mood = cellMood,
                                value = if (rowShape == shape) shown else SAMPLE_VALUE,
                                selected = rowShape == shape && cellMood == mood,
                                onClick = {
                                    shape = rowShape
                                    mood = cellMood
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    shape: MascotShape,
    mood: MascotMood,
    value: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "cell",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(Spacing.xxs)
            .clip(MaterialTheme.shapes.medium)
            .background(fill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${shape.name}, ${mood.name}" },
        contentAlignment = Alignment.Center,
    ) {
        Mascot(size = Dimensions.Icon.avatar, shape = shape, mood = mood, value = value)
    }
}

/** The range of a shape's figure ([Mascot]'s `value`), or null for a shape without one. */
private fun valueRange(shape: MascotShape): ClosedFloatingPointRange<Float>? = when (shape) {
    MascotShape.Pie, MascotShape.Jar -> 0f..1f
    // Past 1 the needle pins at the end of the dial.
    MascotShape.Gauge -> 0f..1.25f
    // Which way spending went: the eyes look up as it rose, down as it fell.
    MascotShape.Bars -> -1f..1f
    else -> null
}

private fun TransactionType.label(): Int = when (this) {
    TransactionType.INCOME -> R.string.type_filter_income
    TransactionType.EXPENSE -> R.string.type_filter_expense
    TransactionType.CREDIT -> R.string.type_filter_credit
    TransactionType.TRANSFER -> R.string.type_filter_transfer
    TransactionType.INVESTMENT -> R.string.type_filter_investment
}

/** The figure every value-drawing shape starts at, and shows in the grid until picked. */
private const val SAMPLE_VALUE = 0.6f
