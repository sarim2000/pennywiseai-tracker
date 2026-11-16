package com.pennywiseai.tracker.presentation.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightAction
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.insights.PeriodScore
import com.pennywiseai.tracker.domain.review.CycleTotal
import com.pennywiseai.tracker.domain.review.ReviewRange
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What needs attention this cycle, why, and where to fix it, for one currency at a
 * time. Everything is worked out on the phone from what PennyWise already holds.
 */
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    onAction: (InsightAction) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismissedMessage = stringResource(R.string.insights_dismissed)
    val undoLabel = stringResource(R.string.insights_undo)
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        onPauseOrDispose { }
    }

    PennyWiseScaffold(
        title = stringResource(R.string.insights_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.insights_back))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val loaded = state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loaded == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                loaded.isEmpty -> PennyWiseEmptyState(
                    icon = Icons.Default.Insights,
                    headline = stringResource(
                        if (loaded.hasHistory) R.string.insights_quiet_headline else R.string.insights_empty_headline
                    ),
                    description = stringResource(
                        if (loaded.hasHistory) R.string.insights_quiet_body else R.string.insights_empty_body
                    ),
                )
                else -> InsightsContent(
                    state = loaded,
                    onSelectCurrency = viewModel::selectCurrency,
                    onAction = onAction,
                    onDismiss = { id ->
                        viewModel.dismiss(id)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                dismissedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(id)
                        }
                    },
                    onRestore = { id ->
                        // Brought back by hand: an Undo still on screen would offer nothing.
                        snackbarHostState.currentSnackbarData?.dismiss()
                        viewModel.restore(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun InsightsContent(
    state: InsightsUiState,
    onSelectCurrency: (String) -> Unit,
    onAction: (InsightAction) -> Unit,
    onDismiss: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showDismissed by rememberSaveable { mutableStateOf(false) }
    var showScore by rememberSaveable { mutableStateOf(false) }
    val sections = InsightSeverity.entries.mapNotNull { severity ->
        state.insights.filter { it.severity == severity }.takeIf { it.isNotEmpty() }?.let { severity to it }
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            bottom = Spacing.Layout.scrollBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.currencies.size > 1 && state.currency != null) {
            item(key = "currencies") {
                ConnectedChoiceRow(
                    options = state.currencies,
                    selected = state.currency,
                    onSelect = onSelectCurrency,
                    label = { it },
                    fillMaxWidth = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
        item(key = "summary") { PeriodSummaryCard(state) }
        state.score?.let { score ->
            item(key = "score") { ScoreCard(score, onOpen = { showScore = true }) }
        }

        if (sections.isEmpty()) {
            item(key = "clear") {
                Text(
                    text = stringResource(R.string.insights_all_clear),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                )
            }
        }
        sections.forEach { (severity, insights) ->
            item(key = "header_${severity.name}") { SeverityHeader(severity) }
            items(insights, key = { it.id }) { insight ->
                InsightCard(
                    insight = insight,
                    expanded = expanded == insight.id,
                    onToggle = { expanded = if (expanded == insight.id) null else insight.id },
                    onAction = onAction,
                    secondaryLabel = stringResource(R.string.insights_dismiss),
                    onSecondary = { onDismiss(insight.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (state.dismissed.isNotEmpty()) {
            item(key = "dismissed_toggle") {
                TextButton(
                    onClick = { showDismissed = !showDismissed },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (showDismissed) stringResource(R.string.insights_hide_dismissed)
                        else stringResource(R.string.insights_show_dismissed, state.dismissed.size)
                    )
                }
            }
            if (showDismissed) {
                items(state.dismissed, key = { "dismissed_${it.id}" }) { insight ->
                    InsightCard(
                        insight = insight,
                        expanded = expanded == insight.id,
                        onToggle = { expanded = if (expanded == insight.id) null else insight.id },
                        onAction = onAction,
                        secondaryLabel = stringResource(R.string.insights_restore),
                        onSecondary = { onRestore(insight.id) },
                        dismissed = true,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        item(key = "coverage") {
            Text(
                text = stringResource(R.string.insights_coverage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.score?.takeIf { showScore }?.let { score ->
        ScoreBreakdownSheet(score, onDismiss = { showScore = false })
    }
}

/** The 0–100 tracking health: one number, its parts a tap away. */
@Composable
private fun ScoreCard(score: PeriodScore, onOpen: () -> Unit) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Text(
            text = stringResource(R.string.insights_score_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text = score.score.toString(), style = PennyWiseText.amountLarge)
            Text(
                text = stringResource(R.string.insights_score_out_of),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        LinearProgressIndicator(
            progress = { score.score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm)
                .height(Dimensions.Component.progressBarHeight),
        )
        Text(
            text = stringResource(R.string.insights_score_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreBreakdownSheet(score: PeriodScore, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.dialog)
                .padding(bottom = Dimensions.Padding.dialog),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.insights_score_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            score.savings?.let {
                ScorePartRow(
                    stringResource(R.string.insights_score_savings),
                    stringResource(R.string.insights_score_savings_detail, percent(it.percent)),
                    // Its points are the savings rate scaled, so they hide with it.
                    if (AmountPrivacy.hidden) AmountPrivacy.MASK else stringResource(R.string.insights_score_points, it.points),
                )
            }
            score.budgets?.let {
                ScorePartRow(
                    stringResource(R.string.insights_score_budgets),
                    pluralStringResource(R.plurals.insights_score_budgets_detail, it.total, it.onTrack, it.total),
                    stringResource(R.string.insights_score_points, it.points),
                )
            }
            score.categorised.let {
                ScorePartRow(
                    stringResource(R.string.insights_score_categorised),
                    pluralStringResource(R.plurals.insights_score_categorised_detail, it.total, it.categorised, it.total),
                    stringResource(R.string.insights_score_points, it.points),
                )
            }
            Text(
                text = stringResource(R.string.insights_score_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScorePartRow(title: String, detail: String, points: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = PennyWiseText.rowTitle)
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = points, style = PennyWiseText.amountRow)
    }
}

/** This cycle's income, spending and what's left of it, with the last cycles for scale. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSummaryCard(state: InsightsUiState) {
    val currency = state.currency ?: return
    val review = state.review
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${stringResource(R.string.insights_this_cycle)} · ${periodLabel(state.range)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = CurrencyFormatter.formatCurrency(review?.spent ?: BigDecimal.ZERO, currency),
            style = PennyWiseText.amountLarge,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = PennyWiseText.amountLarge.fontSize,
            ),
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.insights_spent),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val income = review?.income?.takeIf { it.signum() > 0 }
        val subscriptions = state.subscriptionsMonthly
        if (income != null || subscriptions != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (income != null && review != null) {
                    val saved = income - review.spent
                    Figure(stringResource(R.string.insights_income), CurrencyFormatter.formatCurrency(income, currency))
                    Figure(stringResource(R.string.insights_saved), CurrencyFormatter.formatCurrency(saved, currency))
                    Figure(
                        stringResource(R.string.insights_savings_rate),
                        percent(saved.multiply(BigDecimal(100)).divide(income, 0, RoundingMode.HALF_UP).toInt()),
                    )
                }
                subscriptions?.let {
                    Figure(stringResource(R.string.insights_subscriptions_monthly), it.format())
                }
            }
        }
        if (state.cycles.count { it.hasActivity } > 1) {
            CycleBars(state.cycles, currency)
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = PennyWiseText.amountRow, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A bar per cycle, this one in the accent; each bar reads its figure to a screen reader. */
@Composable
private fun CycleBars(cycles: List<CycleTotal>, currency: String) {
    val highest = cycles.maxOf { it.spent }
    Text(
        text = stringResource(R.string.insights_last_cycles),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.md).semantics { heading() },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        cycles.forEachIndexed { index, cycle ->
            val current = index == cycles.lastIndex
            val fraction = if (highest.signum() > 0) cycle.spent.toFloat() / highest.toFloat() else 0f
            val spoken = "${DateRangeUtils.formatDateRange(cycle.start, cycle.end)}: " +
                CurrencyFormatter.formatCurrency(cycle.spent, currency)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(Dimensions.Component.reviewBarHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(MIN_BAR_FRACTION))
                            .background(
                                if (current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                MaterialTheme.shapes.extraSmall,
                            ),
                    )
                }
                Text(
                    text = cycle.start.format(AppDates.pattern("MMM")),
                    style = PennyWiseText.chartLabel,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SeverityHeader(severity: InsightSeverity) {
    val (icon, color) = severityStyle(severity)
    SectionHeaderV2(
        title = severityLabel(severity),
        leading = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Dimensions.Icon.inline)) },
    )
}

@Composable
internal fun severityLabel(severity: InsightSeverity): String = stringResource(
    when (severity) {
        InsightSeverity.ALERT -> R.string.insights_section_alert
        InsightSeverity.WARNING -> R.string.insights_section_warning
        InsightSeverity.GOOD -> R.string.insights_section_good
    }
)

@Composable
internal fun severityStyle(severity: InsightSeverity): Pair<ImageVector, Color> = when (severity) {
    InsightSeverity.ALERT -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
    InsightSeverity.WARNING -> Icons.Default.WarningAmber to MaterialTheme.colorScheme.warning
    InsightSeverity.GOOD -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.success
}

/**
 * The headline alone until tapped; then why, the figures behind it, and the one
 * thing to do about it. [secondaryLabel] dismisses or brings back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsightCard(
    insight: Insight,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (InsightAction) -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    dismissed: Boolean = false,
) {
    val text = insightText(insight)
    val (icon, severityColor) = severityStyle(insight.severity)
    // A dismissed card is muted and says so, so it can't pass for an active one.
    val color = if (dismissed) MaterialTheme.colorScheme.onSurfaceVariant else severityColor
    val stateLabel = stringResource(if (expanded) R.string.insights_collapse else R.string.insights_expand)
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth().semantics { stateDescription = stateLabel },
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.smd)) {
            // Read first, so TalkBack moving card by card hears how much each one matters.
            Icon(
                icon,
                contentDescription = severityLabel(insight.severity),
                tint = color,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = text.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (dismissed) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                if (dismissed) {
                    Text(
                        text = stringResource(R.string.insights_dismissed_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline),
            )
        }
        if (expanded) {
            Text(
                text = text.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            if (text.metrics.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.smd),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    text.metrics.forEach { (label, value) -> MetricChip(label, value) }
                }
            }
            text.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.smd),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSecondary, shapes = ButtonDefaults.shapes()) { Text(secondaryLabel) }
                insight.action?.let { action ->
                    FilledTonalButton(onClick = { onAction(action) }, shapes = ButtonDefaults.shapes()) {
                        Text(actionLabel(action))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.sm)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = PennyWiseText.amountSmall, maxLines = 1)
        }
    }
}

/** A month's name on a calendar cycle; explicit dates on any other. */
@Composable
private fun periodLabel(range: ReviewRange): String =
    if (isCalendarMonth(range.start, range.end)) range.start.format(AppDates.pattern("MMMM"))
    else DateRangeUtils.formatDateRange(range.start, range.end)

/** A cycle with almost nothing still shows a sliver, so it doesn't read as missing data. */
private const val MIN_BAR_FRACTION = 0.02f
