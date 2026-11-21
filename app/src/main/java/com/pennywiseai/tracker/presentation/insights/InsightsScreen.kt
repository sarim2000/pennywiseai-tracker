package com.pennywiseai.tracker.presentation.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightAction
import com.pennywiseai.tracker.domain.insights.InsightRules
import com.pennywiseai.tracker.domain.insights.InsightSeverity
import com.pennywiseai.tracker.domain.review.ReviewRange
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.presentation.analytics.PeriodBar
import com.pennywiseai.tracker.presentation.analytics.PeriodBarChart
import com.pennywiseai.tracker.ui.animations.pwCollapse
import com.pennywiseai.tracker.ui.animations.pwExpand
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.CustomTitleTopAppBar
import com.pennywiseai.tracker.ui.components.PennyWiseEmptyState
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.cards.GroupedColumn
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.IconTile
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.RowLabels
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.effects.overScrollVertical
import com.pennywiseai.tracker.ui.effects.rememberOverscrollFlingBehavior
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money
import com.pennywiseai.tracker.ui.theme.success
import com.pennywiseai.tracker.ui.theme.warning
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.math.BigDecimal

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
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val hazeState = remember { HazeState() }
    val loaded = state

    PennyWiseScaffold(
        modifier = Modifier.nestedScroll(scrollBehaviorLarge.nestedScrollConnection),
        containerColor = Color.Transparent,
        customTopBar = {
            CustomTitleTopAppBar(
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                title = stringResource(R.string.insights_title),
                // The period the whole page is about, kept in view once the summary scrolls away.
                subtitle = loaded?.takeUnless { it.isEmpty }?.let {
                    "${stringResource(R.string.insights_this_cycle)} · ${periodLabel(it.range)}"
                },
                hasBackButton = true,
                navigationContent = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.insights_back))
                    }
                },
                hazeState = hazeState,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            loaded.isEmpty -> Box(Modifier.fillMaxSize().padding(padding)) {
                PennyWiseEmptyState(
                    icon = Icons.Default.Insights,
                    headline = stringResource(
                        if (loaded.hasHistory) R.string.insights_quiet_headline else R.string.insights_empty_headline
                    ),
                    description = stringResource(
                        if (loaded.hasHistory) R.string.insights_quiet_body else R.string.insights_empty_body
                    ),
                )
            }
            else -> InsightsContent(
                state = loaded,
                padding = padding,
                hazeState = hazeState,
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

@Composable
private fun InsightsContent(
    state: InsightsUiState,
    padding: PaddingValues,
    hazeState: HazeState,
    onSelectCurrency: (String) -> Unit,
    onAction: (InsightAction) -> Unit,
    onDismiss: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showDismissed by rememberSaveable { mutableStateOf(false) }
    var showAllHeadsUp by rememberSaveable { mutableStateOf(false) }
    val sections = InsightSeverity.entries.mapNotNull { severity ->
        state.insights.filter { it.severity == severity }.takeIf { it.isNotEmpty() }?.let { severity to it }
    }
    val toggle = { id: String -> expanded = if (expanded == id) null else id }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.background)
            .overScrollVertical(),
        contentPadding = PaddingValues(
            start = Dimensions.Padding.content,
            end = Dimensions.Padding.content,
            top = padding.calculateTopPadding() + Spacing.sm,
            bottom = padding.calculateBottomPadding() + Spacing.Layout.scrollBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        flingBehavior = rememberOverscrollFlingBehavior { listState },
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
        // What needs attention leads: it's what the page is for, and the cycle's figures
        // below are context for it, not the other way round.
        if (sections.isEmpty()) {
            item(key = "clear") { AllClearRow(Modifier.animateItem()) }
        }
        sections.forEach { (severity, insights) ->
            // Heads-ups can pile up (several spikes, large spends, bills): the first few, in
            // the rules' order of importance, and the rest a tap away.
            val hidden = if (severity == InsightSeverity.WARNING) insights.size - HEADS_UP_SHOWN else 0
            val shown = if (hidden > 0 && !showAllHeadsUp) insights.take(HEADS_UP_SHOWN) else insights
            item(key = "section_${severity.name}") {
                InsightSection(
                    title = severityLabel(severity),
                    insights = shown,
                    expanded = expanded,
                    onToggle = toggle,
                    onAction = onAction,
                    secondaryLabel = stringResource(R.string.insights_dismiss),
                    onSecondary = onDismiss,
                    modifier = Modifier.animateItem(),
                ) {
                    if (hidden > 0) {
                        TextButton(onClick = { showAllHeadsUp = !showAllHeadsUp }, shapes = ButtonDefaults.shapes()) {
                            Text(
                                if (showAllHeadsUp) stringResource(R.string.insights_show_fewer)
                                else pluralStringResource(R.plurals.insights_show_more, hidden, hidden)
                            )
                        }
                    }
                }
            }
        }

        if (state.dismissed.isNotEmpty()) {
            item(key = "dismissed") {
                Column(Modifier.animateItem()) {
                    TextButton(onClick = { showDismissed = !showDismissed }, shapes = ButtonDefaults.shapes()) {
                        Text(
                            if (showDismissed) stringResource(R.string.insights_hide_dismissed)
                            else stringResource(R.string.insights_show_dismissed, state.dismissed.size)
                        )
                    }
                    AnimatedVisibility(visible = showDismissed, enter = pwExpand(), exit = pwCollapse()) {
                        InsightGroup(
                            insights = state.dismissed,
                            expanded = expanded,
                            onToggle = toggle,
                            onAction = onAction,
                            secondaryLabel = stringResource(R.string.insights_restore),
                            onSecondary = onRestore,
                            dismissed = true,
                        )
                    }
                }
            }
        }

        item(key = "summary") {
            Column(Modifier.animateItem()) {
                SectionHeaderV2(title = stringResource(R.string.insights_this_cycle))
                Spacer(Modifier.height(Spacing.Layout.headerToContent))
                PeriodSummaryCard(state)
            }
        }

        item(key = "coverage") { CoverageNote(Modifier.animateItem()) }
    }
}

/** A severity's heading, its rows as one grouped block, and whatever goes under them. */
@Composable
private fun InsightSection(
    title: String,
    insights: List<Insight>,
    expanded: String?,
    onToggle: (String) -> Unit,
    onAction: (InsightAction) -> Unit,
    secondaryLabel: String,
    onSecondary: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Column(modifier) {
        SectionHeaderV2(title = title)
        Spacer(Modifier.height(Spacing.Layout.headerToContent))
        InsightGroup(insights, expanded, onToggle, onAction, secondaryLabel, onSecondary)
        footer()
    }
}

/**
 * The rows share a surface and only the block's outer corners are round, so a
 * section reads as one list rather than a stack of separate cards.
 */
@Composable
private fun InsightGroup(
    insights: List<Insight>,
    expanded: String?,
    onToggle: (String) -> Unit,
    onAction: (InsightAction) -> Unit,
    secondaryLabel: String,
    onSecondary: (String) -> Unit,
    dismissed: Boolean = false,
) {
    GroupedList {
        insights.forEachIndexed { index, insight ->
            key(insight.id) {
                InsightRow(
                    insight = insight,
                    position = ListItemPosition.from(index, insights.size),
                    expanded = expanded == insight.id,
                    onToggle = { onToggle(insight.id) },
                    onAction = onAction,
                    secondaryLabel = secondaryLabel,
                    onSecondary = { onSecondary(insight.id) },
                    dismissed = dismissed,
                )
            }
        }
    }
}

/** This cycle's spending as the hero, its other figures as tiles, and the last cycles for scale. */
@Composable
private fun PeriodSummaryCard(state: InsightsUiState) {
    val currency = state.currency ?: return
    val review = state.review
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
        ) {
            Text(
                text = stringResource(R.string.insights_spent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The same headline as the Insights tab's total, shrinking for a long amount.
            val heroStyle = MaterialTheme.typography.displayMediumEmphasized.money()
            Text(
                text = CurrencyFormatter.formatCurrency(review?.spent ?: BigDecimal.ZERO, currency),
                style = heroStyle,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = PennyWiseText.autoSizeFloor,
                    maxFontSize = heroStyle.fontSize,
                ),
            )
            val figures = buildList {
                val income = review?.income?.takeIf { it.signum() > 0 }
                if (income != null && review != null) {
                    val saved = income - review.spent
                    add(stringResource(R.string.insights_income) to CurrencyFormatter.formatCurrency(income, currency))
                    add(stringResource(R.string.insights_saved) to CurrencyFormatter.formatCurrency(saved, currency))
                    add(stringResource(R.string.insights_savings_rate) to percent(InsightRules.savingsPercent(saved, income)))
                }
                state.subscriptionsMonthly?.let { add(stringResource(R.string.insights_subscriptions_monthly) to it.format()) }
                state.runway?.let {
                    add(stringResource(R.string.insights_runway) to runwayLength(it.months, it.lowConfidence))
                }
            }
            // Two to a row, so a figure has room and the grid stays even.
            figures.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { (label, value) -> FigureTile(label, value, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // Each bar carries its figure, so the bars hide with the amounts.
            if (state.cycles.count { it.hasActivity } > 1 && !AmountPrivacy.hidden) {
                PeriodBarChart(
                    bars = state.cycles.map { cycle ->
                        PeriodBar(
                            key = cycle.start,
                            label = cycle.start.format(AppDates.pattern("MMM")),
                            spokenLabel = DateRangeUtils.formatDateRange(cycle.start, cycle.end),
                            amount = cycle.spent,
                            hasData = cycle.hasActivity,
                        )
                    },
                    currency = currency,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun FigureTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.smd),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Shrinks rather than cut a figure short: a long balance, or "under a month, from the balances known".
            Text(
                text = value,
                style = PennyWiseText.amountRow,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = PennyWiseText.autoSizeFloor,
                    maxFontSize = PennyWiseText.amountRow.fontSize,
                ),
            )
        }
    }
}

/** Nothing to act on: said as a positive row, not a stray line of grey text. */
@Composable
private fun AllClearRow(modifier: Modifier = Modifier) {
    GroupedRow(
        position = ListItemPosition.Single,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
    ) {
        IconTile(
            icon = Icons.Default.CheckCircle,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.success,
            size = Dimensions.Icon.list,
            glyphSize = Dimensions.Icon.inline,
        )
        RowLabels(title = stringResource(R.string.insights_all_clear), titleMaxLines = 3)
    }
}

@Composable
private fun CoverageNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimensions.Icon.small),
        )
        Text(
            text = stringResource(R.string.insights_coverage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
 * How much an insight matters, as a row's leading tile. Spoken first, so TalkBack moving
 * row by row hears it before the headline. [muted] greys it for a dismissed insight.
 */
@Composable
internal fun SeverityTile(severity: InsightSeverity, muted: Boolean = false) {
    val (icon, color) = severityStyle(severity)
    IconTile(
        icon = icon,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else color,
        size = Dimensions.Icon.list,
        glyphSize = Dimensions.Icon.inline,
        contentDescription = severityLabel(severity),
    )
}

/**
 * The headline alone until tapped; then why, the figures behind it, and the one
 * thing to do about it. [secondaryLabel] dismisses or brings back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightRow(
    insight: Insight,
    position: ListItemPosition,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAction: (InsightAction) -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    dismissed: Boolean = false,
) {
    val text = insightText(insight)
    val stateLabel = stringResource(if (expanded) R.string.insights_collapse else R.string.insights_expand)
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "insight_chevron",
    )
    GroupedColumn(
        position = position,
        onClick = onToggle,
        modifier = modifier.semantics { stateDescription = stateLabel },
        verticalArrangement = Arrangement.spacedBy(Spacing.none),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.smd)) {
            SeverityTile(insight.severity, muted = dismissed)
            Column(Modifier.weight(1f)) {
                Text(
                    text = text.title,
                    style = PennyWiseText.rowTitle,
                    color = if (dismissed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.Icon.inline).rotate(chevron),
            )
        }
        AnimatedVisibility(visible = expanded, enter = pwExpand(), exit = pwCollapse()) {
            // Under the title, not the icon, so the details read as the headline's own.
            Column(
                modifier = Modifier.padding(start = Dimensions.Icon.list + Spacing.smd, top = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.smd),
            ) {
                text.body?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (text.metrics.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        text.metrics.forEach { (label, value) -> MetricChip(label, value) }
                    }
                }
                text.note?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    itemVerticalAlignment = Alignment.CenterVertically,
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
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.sm)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = PennyWiseText.amountSmall,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, PennyWiseText.amountSmall.fontSize),
            )
        }
    }
}

/** A month's name on a calendar cycle; explicit dates on any other. */
@Composable
private fun periodLabel(range: ReviewRange): String =
    if (isCalendarMonth(range.start, range.end)) range.start.format(AppDates.pattern("MMMM"))
    else DateRangeUtils.formatDateRange(range.start, range.end)

/** Heads-ups shown before "Show more". */
private const val HEADS_UP_SHOWN = 3
