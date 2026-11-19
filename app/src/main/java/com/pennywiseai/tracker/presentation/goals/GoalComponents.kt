package com.pennywiseai.tracker.presentation.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.domain.goals.GoalPill
import com.pennywiseai.tracker.domain.goals.GoalTone
import com.pennywiseai.tracker.domain.goals.fraction
import com.pennywiseai.tracker.domain.goals.pill
import com.pennywiseai.tracker.domain.goals.tone
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.presentation.plan.PlanIconBadge
import com.pennywiseai.tracker.presentation.plan.PlanMeter
import com.pennywiseai.tracker.ui.components.DockNavAction
import com.pennywiseai.tracker.ui.components.GoalRing
import com.pennywiseai.tracker.ui.components.LocalNavDock
import com.pennywiseai.tracker.ui.components.NavDockAction
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.components.mascot.Mascot
import com.pennywiseai.tracker.ui.components.mascot.MascotShape
import com.pennywiseai.tracker.ui.components.statusLine
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import java.time.format.DateTimeFormatter

internal val goalDateFormat: DateTimeFormatter get() = AppDates.dateFormatter()

@Composable
internal fun GoalProgress.color(): Color = goal.color.toColorOr(MaterialTheme.colorScheme.primary)

/** The one pill a goal shows: per month, Behind, Reached, Missed by or Archived. */
@Composable
fun GoalStatusPill(progress: GoalProgress, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val currency = progress.goal.currency
    val pill = progress.pill()
    val (text, content) = when (pill) {
        GoalPill.Reached -> stringResource(R.string.goals_reached) to scheme.success
        is GoalPill.MissedBy -> stringResource(R.string.goals_missed_by, CurrencyFormatter.formatCurrency(pill.amount, currency)) to scheme.error
        is GoalPill.Behind -> stringResource(R.string.goals_behind, CurrencyFormatter.formatCurrency(pill.amount, currency)) to scheme.warning
        is GoalPill.PerMonth -> stringResource(R.string.goals_per_month, CurrencyFormatter.formatCurrency(pill.amount, currency)) to scheme.onSecondaryContainer
        GoalPill.Archived -> stringResource(R.string.goals_archived) to scheme.onSurfaceVariant
    }
    val container = when (pill) {
        is GoalPill.PerMonth -> scheme.secondaryContainer
        GoalPill.Archived -> scheme.surfaceContainerHighest
        else -> content.copy(alpha = Dimensions.Alpha.divider).compositeOver(scheme.surface)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
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
                    titleMaxLines = Int.MAX_VALUE,
                    subtitleMaxLines = Int.MAX_VALUE,
                )
            }
        }
    }
}

/** A savings goal with one amount summary and one status. */
@Composable
fun GoalCard(
    progress: GoalProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    position: ListItemPosition = ListItemPosition.Single,
) {
    val goal = progress.goal
    val color = progress.color()
    GroupedRow(position = position, onClick = onClick, modifier = modifier) {
        PlanIconBadge(icon = Icons.Default.Savings, color = color, index = index)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.plan_goals_saved_of,
                    CurrencyFormatter.formatCurrency(progress.saved, goal.currency),
                    CurrencyFormatter.formatCurrency(goal.targetAmount, goal.currency),
                ),
                style = PennyWiseText.rowSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GoalStatusPill(progress)
            if (!AmountPrivacy.hidden) {
                PlanMeter(
                    fraction = progress.fraction(),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    dense = true,
                )
            }
        }
    }
}

/**
 * The Goals section at the top of the Budgets screen. Owns the Pro gate for
 * "New goal" (#914): free users get [com.pennywiseai.tracker.core.entitlement.FreeTierLimits.MAX_ACTIVE_GOALS].
 * With no goals, its empty card shows the jar [mascot], or a plain icon where another
 * mascot already has the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsSection(
    onOpenGoal: (Long) -> Unit,
    onCreateGoal: () -> Unit,
    modifier: Modifier = Modifier,
    createOnOpen: Boolean = false,
    mascot: Boolean = true,
    /** Drawn as the Plan tab's Goals section: no heading (the switch names it), "New goal" docked by the nav bar. */
    inPlanTab: Boolean = false,
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

    // In the Plan tab "New goal" docks beside the nav bar like the other sections' add.
    val docked = inPlanTab && LocalNavDock.current != null
    if (docked && active.isNotEmpty()) {
        DockNavAction(
            NavDockAction(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.goals_new),
                onClick = create,
            )
        )
    }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (inPlanTab) Spacing.smd else Spacing.Layout.headerToContent),
    ) {
        if (!inPlanTab || !docked) {
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
        }
        if (active.isEmpty()) {
            GoalsEmptyCard(onCreate = create, mascot = mascot)
        } else {
            GroupedList {
                active.forEachIndexed { i, goal ->
                    key(goal.goal.id) {
                        GoalCard(
                            goal,
                            onClick = { onOpenGoal(goal.goal.id) },
                            index = i,
                            position = ListItemPosition.from(i, active.size),
                        )
                    }
                }
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
private fun GoalsEmptyCard(onCreate: () -> Unit, mascot: Boolean) {
    PennyWiseCardV2(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = Dimensions.Padding.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (mascot) {
                // An empty jar: nothing is saved toward a goal yet.
                Mascot(size = Dimensions.Icon.extraLarge, shape = MascotShape.Jar, interactive = true)
            } else {
                Box(
                    Modifier
                        .size(Dimensions.Icon.avatarLarge)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
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
