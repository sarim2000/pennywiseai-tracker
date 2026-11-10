package com.pennywiseai.tracker.presentation.goals

import com.pennywiseai.tracker.domain.goals.GoalPill
import com.pennywiseai.tracker.domain.goals.GoalTone
import com.pennywiseai.tracker.domain.goals.percent
import com.pennywiseai.tracker.domain.goals.pill
import com.pennywiseai.tracker.domain.goals.ratioOf
import com.pennywiseai.tracker.domain.goals.tone
import com.pennywiseai.tracker.utils.AppDates

import android.content.Context
import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.data.repository.GoalStatus
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.cards.tintedSurface
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.tabular
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val goalDateFormat: DateTimeFormatter get() = AppDates.dateFormatter()

@Composable
internal fun GoalProgress.color(): Color = goal.color.toColorOr(MaterialTheme.colorScheme.primary)

/**
 * Circular progress in the goal's colour over a neutral track. [content] sits in
 * the middle and gets the whole percent read off the animated sweep, so the
 * number and the arc move together.
 *
 * [addedFrom] is what was saved just before the user's own Add money, in the
 * goal's currency. The portion it adds draws as its own segment (split off by a
 * gap, the older part dimmed) and then settles into the ring, after which
 * [onAddedSettled] fires. Leave it null for loads, refreshes and withdrawals:
 * those just move the ring.
 */
@Composable
fun GoalRing(
    progress: GoalProgress,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    addedFrom: BigDecimal? = null,
    onAddedSettled: () -> Unit = {},
    content: @Composable (percent: Int) -> Unit,
) {
    // Effects specs throughout: a progress arc never bounces past its value.
    val target = progress.ratioOf(progress.saved)
    val animated by animateFloatAsState(target, MaterialTheme.motionScheme.slowEffectsSpec(), label = "goalRing")
    val percent = ringPercent(animated, target, progress.percent())

    // 1 while the added segment stands apart, easing to 0 as it joins the ring.
    val emphasis = remember { Animatable(0f) }
    val fromRatio = addedFrom?.let(progress::ratioOf)
    val settleSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val currentTarget by rememberUpdatedState(target)
    val currentSaved by rememberUpdatedState(progress.saved)
    val currentOnSettled by rememberUpdatedState(onAddedSettled)
    LaunchedEffect(addedFrom) {
        val from = addedFrom ?: return@LaunchedEffect
        emphasis.snapTo(1f)
        // The save and the new total can arrive in either order: wait for the
        // total to pass the old one and the sweep to reach it, then settle once.
        // Compares money, not the 4-dp ratio: a small Add can leave the ratio unchanged.
        snapshotFlow { currentSaved > from && animated == currentTarget }.first { it }
        emphasis.animateTo(0f, settleSpec)
        currentOnSettled()
    }

    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = progress.color()
    val description = stringResource(R.string.goals_ring_description, progress.percent())
    Box(
        modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            fun arc(c: Color, from: Float, sweep: Float) =
                drawArc(c, -90f + from, sweep, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
            val end = animated.coerceAtMost(1f)
            val start = fromRatio?.coerceAtMost(1f)
            val e = emphasis.value
            if (start == null || e == 0f || end <= start) {
                if (end > 0f) arc(color, 0f, 360f * end)
            } else {
                if (start > 0f) arc(color.copy(alpha = lerp(1f, Dimensions.Alpha.medium, e)), 0f, 360f * start)
                // Round caps reach half the stroke past each end, so the gap clears both before it shows.
                val gap = if (start > 0f) e * Math.toDegrees(((width + Spacing.xs.toPx()) / (arcSize.width / 2)).toDouble()).toFloat() else 0f
                val sweep = 360f * (end - start) - gap
                if (sweep > 0f) arc(color, 360f * start + gap, sweep)
            }
        }
        content(percent)
    }
}

/** The one pill a goal shows: per month, Behind, Reached, Missed by or Archived. */
@Composable
fun GoalStatusPill(progress: GoalProgress, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val currency = progress.goal.currency
    val (text, content) = when (val pill = progress.pill()) {
        GoalPill.Reached -> stringResource(R.string.goals_reached) to scheme.success
        is GoalPill.MissedBy -> stringResource(R.string.goals_missed_by, CurrencyFormatter.formatCurrency(pill.amount, currency)) to scheme.error
        is GoalPill.Behind -> stringResource(R.string.goals_behind, CurrencyFormatter.formatCurrency(pill.amount, currency)) to scheme.warning
        is GoalPill.PerMonth -> stringResource(R.string.goals_per_month, CurrencyFormatter.formatCurrency(pill.amount, currency)) to scheme.onSecondaryContainer
        GoalPill.Archived -> stringResource(R.string.goals_archived) to scheme.onSurfaceVariant
    }
    val container = when (progress.pill()) {
        is GoalPill.PerMonth -> scheme.secondaryContainer
        GoalPill.Archived -> scheme.surfaceContainerHighest
        else -> content.copy(alpha = Dimensions.Alpha.divider).compositeOver(scheme.surface)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        maxLines = 1,
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    )
}

/** "5 months left" while in progress, else the due date. */
@Composable
fun goalTimeLabel(progress: GoalProgress): String =
    if (progress.status == GoalStatus.IN_PROGRESS) {
        pluralStringResource(R.plurals.goals_months_left, progress.monthsLeft, progress.monthsLeft)
    } else {
        stringResource(R.string.goals_due_on, progress.goal.dueDate.format(goalDateFormat))
    }

/** How a status line writes money: the app's usual; the widgets pass their own. */
private val appMoney: (BigDecimal, String) -> String = { amount, currency -> CurrencyFormatter.formatCurrency(amount, currency) }

/**
 * A month count. [latinDigits] keeps "12" Latin where Arabic resources would
 * write "١٢": the widgets do, beside their Latin amounts.
 */
private fun Resources.months(id: Int, count: Int, latinDigits: Boolean): String =
    if (latinDigits) String.format(Locale.US, getQuantityString(id, count), count)
    else getQuantityString(id, count, count)

/**
 * One-line status for compact rows (Home card, goals widget):
 * "₹1,920/mo · 5 months left", "Behind ₹2,000", "Reached" or "Missed by ₹X".
 * Takes a [Context] so the widget worker builds the same text the Home card shows.
 */
fun GoalProgress.statusLine(context: Context, money: (BigDecimal, String) -> String = appMoney, latinDigits: Boolean = false): String {
    val res = context.resources
    val currency = goal.currency
    return when (val pill = pill()) {
        GoalPill.Reached -> res.getString(R.string.goals_reached)
        is GoalPill.MissedBy -> res.getString(R.string.goals_missed_by, money(pill.amount, currency))
        is GoalPill.Behind -> res.getString(R.string.goals_behind, money(pill.amount, currency))
        is GoalPill.PerMonth -> res.getString(
            R.string.goals_line_join,
            res.getString(R.string.goals_per_month, money(pill.amount, currency)),
            res.months(R.plurals.goals_months_left, monthsLeft, latinDigits),
        )
        GoalPill.Archived -> res.getString(R.string.goals_archived)
    }
}

/** The 2×2 widget's line: "₹9,600 left · 5 mo" while in progress, else the same as [statusLine]. */
fun GoalProgress.shortLine(context: Context, money: (BigDecimal, String) -> String = appMoney, latinDigits: Boolean = false): String {
    if (status != GoalStatus.IN_PROGRESS) return statusLine(context, money, latinDigits)
    val res = context.resources
    return res.getString(
        R.string.goals_line_join,
        res.getString(R.string.goals_left, money(left, goal.currency)),
        res.months(R.plurals.goals_months_short, monthsLeft, latinDigits),
    )
}

@Composable
private fun GoalTone.color(): Color = when (this) {
    GoalTone.SUCCESS -> MaterialTheme.colorScheme.success
    GoalTone.WARNING -> MaterialTheme.colorScheme.warning
    GoalTone.ERROR -> MaterialTheme.colorScheme.error
    GoalTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * The Home "Goals" card (#914): up to [maxGoals] goals in the order given
 * (soonest due first, Reached last), each a row that opens the goal.
 */
@Composable
fun GoalsHomeCard(goals: List<GoalProgress>, onOpenGoal: (Long) -> Unit, modifier: Modifier = Modifier, maxGoals: Int = 3) {
    val context = LocalContext.current
    val shown = goals.take(maxGoals)
    GroupedList(modifier) {
        shown.forEachIndexed { i, progress ->
            GroupedRow(position = ListItemPosition.from(i, shown.size), onClick = { onOpenGoal(progress.goal.id) }) {
                GoalRing(progress, Dimensions.Component.goalRingSmall, Dimensions.Component.goalRingSmallStroke) {}
                RowLabels(
                    title = progress.goal.name,
                    subtitle = progress.statusLine(context),
                    subtitleColor = progress.tone().color(),
                    subtitleMaxLines = 1,
                )
                Text(
                    text = stringResource(R.string.budgets_percent, progress.percent()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun GoalCard(progress: GoalProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val goal = progress.goal
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        containerColor = progress.color().tintedSurface(),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GoalRing(progress, Dimensions.Component.goalRing, Dimensions.Component.goalRingStroke) { percent ->
                Text(
                    text = stringResource(R.string.budgets_percent, percent),
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.goals_amount_of,
                        CurrencyFormatter.formatCurrency(progress.saved, goal.currency),
                        CurrencyFormatter.formatCurrency(goal.targetAmount, goal.currency),
                    ),
                    style = PennyWiseText.amountSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GoalStatusPill(progress)
                    Text(
                        text = goalTimeLabel(progress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The Goals section at the top of the Budgets screen. Owns the Pro gate for
 * "New goal" (#914): free users get [com.pennywiseai.tracker.billing.FreeTierLimits.MAX_ACTIVE_GOALS].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsSection(
    onOpenGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
    modifier: Modifier = Modifier,
    createOnOpen: Boolean = false,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val goals by viewModel.activeGoals.collectAsStateWithLifecycle()
    val archived by viewModel.archivedGoals.collectAsStateWithLifecycle()
    var showUpgrade by rememberSaveable { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val active = goals ?: return
    val create = { viewModel.requestCreate(onAllowed = onCreateGoal, onBlocked = { showUpgrade = true }) }
    // The goals widget's "+": start "New goal" once, through the same Pro gate.
    LaunchedEffect(createOnOpen) {
        if (createOnOpen) viewModel.createOnOpenOnce(onAllowed = onCreateGoal, onBlocked = { showUpgrade = true })
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent)) {
        SectionHeaderV2(
            title = stringResource(R.string.goals_section_title),
            topSpacing = Spacing.none,
            action = if (active.isNotEmpty()) {
                {
                    TextButton(onClick = create, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Dimensions.Icon.small))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.goals_new))
                    }
                }
            } else null,
        )
        if (active.isEmpty()) {
            GoalsEmptyCard(onCreate = create)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                active.forEach { GoalCard(it, onClick = { onOpenGoal(it.goal.id) }) }
            }
        }
        if (archived.isNotEmpty()) {
            GroupedList {
                GroupedRow(position = ListItemPosition.Single, onClick = { showArchived = true }) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    RowLabels(title = stringResource(R.string.goals_archived_row, archived.size))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Icon.inline),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showArchived && archived.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showArchived = false },
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            // Scrolls, so a long archive stays reachable.
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(stringResource(R.string.goals_archived_title), style = MaterialTheme.typography.titleMedium)
                GroupedList {
                    archived.forEachIndexed { i, goal ->
                        GroupedRow(
                            position = ListItemPosition.from(i, archived.size),
                            onClick = { showArchived = false; onOpenGoal(goal.goal.id) },
                        ) {
                            RowLabels(
                                title = goal.goal.name,
                                subtitle = stringResource(
                                    R.string.goals_amount_of,
                                    CurrencyFormatter.formatCurrency(goal.saved, goal.goal.currency),
                                    CurrencyFormatter.formatCurrency(goal.goal.targetAmount, goal.goal.currency),
                                ),
                            )
                            TextButton(onClick = { viewModel.unarchive(goal.goal.id) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.goals_unarchive))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUpgrade) UpgradeSheet(onDismiss = { showUpgrade = false })
}

@Composable
private fun GoalsEmptyCard(onCreate: () -> Unit) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth(), contentPadding = Dimensions.Padding.card) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                Modifier
                    .size(Dimensions.Icon.avatarLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.goals_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.goals_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(Spacing.md))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.goals_empty_action))
        }
    }
}
