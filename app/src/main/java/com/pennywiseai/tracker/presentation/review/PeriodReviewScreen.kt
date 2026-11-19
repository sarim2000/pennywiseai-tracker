package com.pennywiseai.tracker.presentation.review

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.export.pdfShareIntent
import com.pennywiseai.tracker.domain.review.CategoryLine
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.PeriodReview
import com.pennywiseai.tracker.domain.review.REVIEW_CATEGORY_LINES
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.ReviewRange
import com.pennywiseai.tracker.domain.review.latestReviewOffset
import com.pennywiseai.tracker.domain.review.percentChange
import com.pennywiseai.tracker.presentation.paywall.UpgradeSheet
import com.pennywiseai.tracker.ui.components.CategoryIcon
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.PeriodSwitcher
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.GroupedRow
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.PennyWiseCardV2
import com.pennywiseai.tracker.ui.components.comparisonTarget
import com.pennywiseai.tracker.ui.components.periodLabel
import com.pennywiseai.tracker.ui.icons.categoryLabel
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.Money
import com.pennywiseai.tracker.utils.changeText
import java.time.LocalDate

/**
 * What changed in a finished week or cycle, and where a year went. Neutral on purpose:
 * no scores, no ranks, no red for spending more. Automated commentary on someone's
 * spending has hurt people (the Year in Monzo complaint), and the app can't know why
 * a number moved.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PeriodReviewScreen(
    onNavigateBack: () -> Unit,
    onOpenCategory: (category: String, currency: String, start: LocalDate, end: LocalDate) -> Unit,
    onShare: (ReviewKind, offset: Int, today: LocalDate) -> Unit = { _, _, _ -> },
    viewModel: PeriodReviewViewModel = hiltViewModel(),
) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val loaded = review
    val exportingPdf by viewModel.exportingPdf.collectAsStateWithLifecycle()
    val isProEntitled by viewModel.isProEntitled.collectAsStateWithLifecycle()
    var showUpgradeSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // For its result alone: it says when the share sheet has closed.
    val sharePdf = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.pdfShareClosed()
    }

    LaunchedEffect(viewModel) {
        viewModel.pdfReady.collect { uri ->
            when {
                uri == null -> Toast.makeText(context, R.string.review_export_pdf_failed, Toast.LENGTH_SHORT).show()
                // Amounts were hidden while the file was written: it prints them, so it stays here.
                AmountPrivacy.hidden -> {
                    viewModel.pdfShareClosed()
                    Toast.makeText(context, R.string.review_export_pdf_amounts_hidden, Toast.LENGTH_LONG).show()
                }
                else -> sharePdf.launch(pdfShareIntent(uri))
            }
        }
    }

    PennyWiseScaffold(
        title = stringResource(R.string.review_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
            }
        },
        actions = {
            // A report is for a month or a year; a week has the share card.
            if (loaded != null && loaded.currencies.isNotEmpty() && loaded.range.kind != ReviewKind.WEEK) {
                val title = periodLabel(loaded.range)
                val label = stringResource(R.string.review_export_pdf)
                NamedAction(label) {
                    IconButton(
                        onClick = {
                            when {
                                // Data going out is Pro, as CSV export is.
                                !isProEntitled -> showUpgradeSheet = true
                                // A report prints every amount, so it isn't made while the app hides them.
                                AmountPrivacy.hidden ->
                                    Toast.makeText(context, R.string.review_export_pdf_amounts_hidden, Toast.LENGTH_LONG).show()
                                else -> viewModel.exportPdf(title)
                            }
                        },
                        enabled = !exportingPdf,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = label)
                    }
                }
            }
            if (loaded != null && loaded.firstRecorded != null) {
                val label = stringResource(R.string.review_share)
                NamedAction(label) {
                    IconButton(
                        // The studio opens on the period shown here, built from the same review.
                        onClick = { onShare(selection.kind, selection.offset, selection.today) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = label)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimensions.Padding.content),
        ) {
            ConnectedChoiceRow(
                options = ReviewKind.entries,
                selected = selection.kind,
                onSelect = viewModel::selectKind,
                label = {
                    stringResource(
                        when (it) {
                            ReviewKind.WEEK -> R.string.review_kind_week
                            ReviewKind.MONTH -> R.string.review_kind_month
                            ReviewKind.YEAR -> R.string.review_kind_year
                        }
                    )
                },
            )
            PeriodSwitcher(
                label = loaded?.let { periodLabel(it.range) }.orEmpty(),
                canGoOlder = loaded?.hasOlder == true,
                canGoNewer = selection.offset < latestReviewOffset(selection.kind),
                onOlder = viewModel::older,
                onNewer = viewModel::newer,
            )
            if (loaded == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                // The first figures to arrive fade in once, as one group, replacing the
                // loading indicator: no travel (amounts settle on effects specs) and no
                // stagger; later periods, or a return here, just appear.
                LaunchedEffect(Unit) { hasAnimated = true }
                val shown = remember { MutableTransitionState(hasAnimated) }.apply { targetState = true }
                AnimatedVisibility(visibleState = shown, enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())) {
                    ReviewContent(loaded, onOpenCategory)
                }
            }
        }
    }
    if (showUpgradeSheet) {
        UpgradeSheet(onDismiss = { showUpgradeSheet = false })
    }
}

/** Two icon-only actions sit side by side here, so a long-press says which is which. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NamedAction(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

@Composable
private fun ReviewContent(
    loaded: PeriodReview,
    onOpenCategory: (category: String, currency: String, start: LocalDate, end: LocalDate) -> Unit,
) {
    if (loaded.currencies.isEmpty()) {
        Text(
            text = stringResource(R.string.review_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimensions.Padding.empty),
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = Spacing.Layout.scrollBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(loaded.currencies, key = { it.currency }) { currency ->
                CurrencyReviewCard(
                    review = currency,
                    range = loaded.range,
                    onOpenCategory = { name ->
                        onOpenCategory(name, currency.currency, loaded.range.start, loaded.range.end)
                    },
                )
            }
            item { CoverageNote(loaded) }
        }
    }
}

@Composable
private fun CurrencyReviewCard(
    review: CurrencyReview,
    range: ReviewRange,
    onOpenCategory: (String) -> Unit,
) {
    PennyWiseCardV2(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.review_spent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The recap's headline: the same hero figure as Home's month spent, one
        // line, shrinking for a long amount rather than wrapping mid-number.
        Text(
            text = CurrencyFormatter.formatCurrency(review.spent, review.currency),
            style = PennyWiseText.heroAmount,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = PennyWiseText.autoSizeFloor,
                maxFontSize = PennyWiseText.heroAmount.fontSize,
            ),
        )
        comparisonText(review, range)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (review.income.signum() > 0) {
                Figure(
                    label = stringResource(R.string.review_income),
                    value = CurrencyFormatter.formatCurrency(review.income, review.currency),
                    modifier = Modifier.weight(1f),
                )
            }
            Figure(
                label = stringResource(R.string.review_transactions),
                value = review.transactionCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        if (review.months.isNotEmpty() && !AmountPrivacy.hidden) {
            MonthBars(review)
        }

        if (review.categories.isNotEmpty()) {
            val showsChange = review.showsCategoryChange(range.kind)
            Text(
                text = stringResource(if (showsChange) R.string.review_categories_changed else R.string.review_categories_top),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm).semantics { heading() },
            )
            val lines = review.categories.take(REVIEW_CATEGORY_LINES)
            GroupedList {
                lines.forEachIndexed { index, line ->
                    CategoryRow(line, ListItemPosition.from(index, lines.size), review.currency, showsChange) {
                        onOpenCategory(line.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = PennyWiseText.amountRow, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryRow(
    line: CategoryLine,
    position: ListItemPosition,
    currency: String,
    showsChange: Boolean,
    onClick: () -> Unit
) {
    GroupedRow(
        position = position,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
    ) {
        CategoryIcon(
            category = line.name,
            size = Dimensions.Icon.medium,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = categoryLabel(line.name), style = PennyWiseText.rowTitle)
            Text(text = CurrencyFormatter.formatCurrency(line.amount, currency), style = PennyWiseText.amountRow)
            val delta = line.amount - (line.previous ?: line.amount)
            if (showsChange && !AmountPrivacy.hidden && delta.signum() != 0) {
                Text(
                    text = Money(delta.abs(), currency).format(if (delta.signum() > 0) "+" else "−"),
                    style = PennyWiseText.amountSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A plain bar per month: the shape of the year, with each month's figure for screen readers. */
@Composable
private fun MonthBars(review: CurrencyReview) {
    val highest = review.months.maxOf { it.spent }
    Text(
        text = stringResource(R.string.review_by_month),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.lg).semantics { heading() },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        review.months.forEach { point ->
            val fraction = if (highest.signum() > 0) point.spent.toFloat() / highest.toFloat() else 0f
            val spoken = "${point.month.format(AppDates.pattern("MMMM"))}: " +
                CurrencyFormatter.formatCurrency(point.spent, review.currency)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimensions.Component.reviewBarHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(MIN_BAR_FRACTION))
                            .background(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.shapes.small,
                            ),
                    )
                }
                Text(
                    text = point.month.format(AppDates.pattern("MMMMM")),
                    style = PennyWiseText.chartLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Says what the figures rest on: only what was recorded, across the dates it actually spans. */
@Composable
private fun CoverageNote(review: PeriodReview) {
    val first = review.firstRecorded
    val last = review.lastRecorded
    val text = if (review.range.kind == ReviewKind.YEAR && first != null && last != null) {
        stringResource(
            R.string.review_coverage_span,
            first.format(AppDates.dayMonthFormatter()),
            last.format(AppDates.dayMonthFormatter()),
        )
    } else {
        stringResource(R.string.review_coverage)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun comparisonText(review: CurrencyReview, range: ReviewRange): String? {
    // A percent says how the amounts relate, so it hides with them, as on Home.
    if (AmountPrivacy.hidden) return null
    val change = percentChange(review.spent, review.previousSpent) ?: return null
    return changeText(LocalContext.current, change, comparisonTarget(range))
}

/** A month with almost nothing still shows a sliver, so it doesn't read as missing data. */
private const val MIN_BAR_FRACTION = 0.02f
