package com.pennywiseai.tracker.presentation.share

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.share.SHARE_CARD_URL
import com.pennywiseai.tracker.data.share.ShareCardExporter
import com.pennywiseai.tracker.domain.review.ReviewKind
import com.pennywiseai.tracker.domain.review.isCalendarMonth
import com.pennywiseai.tracker.domain.review.latestReviewOffset
import com.pennywiseai.tracker.presentation.review.PeriodSwitcher
import com.pennywiseai.tracker.presentation.review.periodLabel
import com.pennywiseai.tracker.ui.components.ConnectedChoiceRow
import com.pennywiseai.tracker.ui.components.PennyWiseScaffold
import com.pennywiseai.tracker.ui.components.PreferenceSwitch
import com.pennywiseai.tracker.ui.components.cards.GroupedList
import com.pennywiseai.tracker.ui.components.cards.ListItemPosition
import com.pennywiseai.tracker.ui.components.cards.SectionHeaderV2
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.AmountPrivacy
import com.pennywiseai.tracker.utils.AppDates
import com.pennywiseai.tracker.utils.DateRangeUtils
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Pick a card, pick exactly what it shows, see it, then share it.
 *
 * The preview stays pinned above the controls so every switch visibly changes the image
 * it is about: nothing reaches the share sheet that the user hasn't just looked at. A
 * silent generate-and-fling from someone's financial data would be the wrong shape for
 * this app. Amounts start off; the user turns on each figure they want to send.
 */
@Composable
fun ShareStudioScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShareStudioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val card = shareCardState(selection, data)
    val graphicsLayer = rememberGraphicsLayer()
    // With amounts hidden app-wide, previews stay covered until the user asks to see them.
    // Hiding amounts again covers them again.
    var revealed by rememberSaveable(AmountPrivacy.hidden) { mutableStateOf(false) }

    PennyWiseScaffold(
        title = stringResource(R.string.share_studio_title),
        navigationIcon = {
            IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.share_studio_back))
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    // Only an image the user has seen goes out: with amounts hidden
                    // app-wide, that means after Show preview.
                    enabled = card is ShareCardState.Ready && (revealed || !AmountPrivacy.hidden),
                    onClick = {
                        scope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            val uri = ShareCardExporter.writeCard(context, bitmap)
                            context.startActivity(
                                ShareCardExporter.shareIntent(uri, shareText(context, selection.kind))
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.smd),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(stringResource(R.string.share_studio_share), modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Half the screen at most, so the controls under it stay reachable; never
            // wider than the screen's gutters allow.
            val scale = min(
                minOf(maxHeight * PREVIEW_HEIGHT_SHARE, ShareCardHeight) / ShareCardHeight,
                (maxWidth - Dimensions.Padding.content * 2) / ShareCardWidth,
            )
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    CardPreview(card, selection.theme, scale, revealed, { revealed = true }, previewModifier = Modifier.drawWithContent {
                        // The image shared is recorded here, at the card's full size, from
                        // the very pixels being previewed.
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    })
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimensions.Padding.content)
                        .padding(bottom = Spacing.Layout.scrollBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Layout.headerToContent),
                ) {
                    Controls(selection, data, viewModel)
                    Text(
                        text = stringResource(R.string.share_studio_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md),
                    )
                }
            }
        }
    }
}

private const val PREVIEW_HEIGHT_SHARE = 0.5f

/**
 * The message that travels with the image.
 *
 * Deliberately written in the sharer's voice, not the app's — this lands in a
 * WhatsApp thread between friends, and marketing copy in that context gets ignored.
 * It names the card, never a figure: the user chose which figures go out, on the
 * image, and a number repeated here would ignore that choice. The URL closes the loop
 * back to the landing pages.
 */
internal fun shareText(context: Context, kind: ShareKind): String = context.getString(
    when (kind) {
        ShareKind.RECAP -> R.string.share_text_recap
        ShareKind.SPENDING -> R.string.share_text_spending
        ShareKind.SAVINGS -> R.string.share_text_savings
        ShareKind.GOAL -> R.string.share_text_goal
        ShareKind.BUDGET -> R.string.share_text_budget
    },
    SHARE_CARD_URL,
)

@Composable
private fun CardPreview(
    card: ShareCardState,
    theme: ShareTheme,
    scale: Float,
    revealed: Boolean,
    onReveal: () -> Unit,
    previewModifier: Modifier,
) {
    val description = stringResource(R.string.share_studio_preview_description)
    Box(
        Modifier
            .size(ShareCardWidth * scale, ShareCardHeight * scale)
            .clip(MaterialTheme.shapes.large)
            // Outside the exported pixels: only separates a dark card from a dark screen.
            .border(Dimensions.Component.hairline, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (card) {
            is ShareCardState.Ready -> {
                ShareCard(card.content, theme, Modifier.scaleTo(scale).then(previewModifier))
                // With amounts hidden app-wide, the figures stay covered on screen until
                // asked for. The cover is drawn over the card, outside the recorded
                // layer, so the shared image is still exactly what was chosen.
                if (AmountPrivacy.hidden && !revealed) {
                    Placeholder {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.lg)) {
                            Text(
                                text = stringResource(R.string.share_studio_hidden),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = onReveal, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.share_studio_hidden_reveal))
                            }
                        }
                    }
                }
            }
            ShareCardState.Loading -> Placeholder { LoadingIndicator() }
            is ShareCardState.Unavailable -> Placeholder {
                Text(
                    text = card.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun Placeholder(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Controls(selection: ShareStudioSelection, data: ShareStudioData, viewModel: ShareStudioViewModel) {
    SectionHeaderV2(title = stringResource(R.string.share_studio_section_card), topSpacing = Spacing.none)
    ChipRow(
        options = ShareKind.entries,
        selected = selection.kind,
        label = { kindLabel(it) },
        onSelect = viewModel::selectKind,
    )

    if (selection.kind == ShareKind.GOAL) {
        val goals = (data as? ShareStudioData.Goals)?.goals.orEmpty()
        if (goals.size > 1) {
            SectionHeaderV2(title = stringResource(R.string.share_studio_section_goal))
            val selectedId = goals.firstOrNull { it.goal.id == selection.goalId }?.goal?.id ?: goals.first().goal.id
            ChipRow(
                options = goals.map { it.goal.id },
                selected = selectedId,
                label = { id -> goals.first { it.goal.id == id }.goal.name },
                onSelect = viewModel::selectGoal,
            )
        }
    } else {
        SectionHeaderV2(title = stringResource(R.string.share_studio_section_period))
        if (selection.kind != ShareKind.BUDGET) {
            ConnectedChoiceRow(
                options = ReviewKind.entries,
                selected = selection.period.kind,
                onSelect = viewModel::selectPeriodKind,
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
        }
        val review = (data as? ShareStudioData.Reviews)?.reviews?.firstOrNull()
        val budgetWindow = (data as? ShareStudioData.Budget)?.summary?.pageWindow
        PeriodSwitcher(
            label = when {
                review != null -> periodLabel(review.range)
                budgetWindow != null ->
                    if (isCalendarMonth(budgetWindow.start, budgetWindow.end)) budgetWindow.start.format(AppDates.pattern("MMMM yyyy"))
                    else DateRangeUtils.formatDateRange(budgetWindow.start, budgetWindow.end)
                else -> ""
            },
            canGoOlder = if (selection.kind == ShareKind.BUDGET) selection.period.offset > -BUDGET_MONTHS_BACK
            else review?.hasOlder == true,
            canGoNewer = selection.period.offset < latestReviewOffset(selection.period.kind),
            onOlder = viewModel::older,
            onNewer = viewModel::newer,
        )
        val currencies = currenciesOf(data)
        if (currencies.size > 1) {
            SectionHeaderV2(title = stringResource(R.string.share_studio_section_currency))
            ChipRow(
                options = currencies,
                selected = selection.currency?.takeIf { it in currencies } ?: currencies.first(),
                label = { it },
                onSelect = viewModel::selectCurrency,
            )
        }
    }

    SectionHeaderV2(title = stringResource(R.string.share_studio_section_details))
    DetailSwitches(selection, viewModel)

    SectionHeaderV2(title = stringResource(R.string.share_studio_section_style))
    ChipRow(
        options = ShareTheme.entries,
        selected = selection.theme,
        label = { themeLabel(it) },
        onSelect = viewModel::selectTheme,
        leading = { ThemeSwatch(it) },
    )
}

@Composable
private fun DetailSwitches(selection: ShareStudioSelection, viewModel: ShareStudioViewModel) {
    val kind = selection.kind
    val shown = effectiveDetails(kind, selection.details)
    val details = kind.details
    GroupedList {
        details.forEachIndexed { i, detail ->
            val isFigure = detail == ShareDetail.AMOUNTS || detail == ShareDetail.PERCENTAGES
            // The last figure on a card that needs one can't go: say why instead of
            // letting the switch flip and the card quietly put it back.
            val locked = kind.needsFigure && isFigure && detail in shown &&
                (shown - detail).none { it == ShareDetail.AMOUNTS || it == ShareDetail.PERCENTAGES }
            PreferenceSwitch(
                title = detailLabel(detail, kind),
                subtitle = when {
                    locked -> stringResource(R.string.share_detail_need_figure)
                    detail == ShareDetail.AMOUNTS -> stringResource(R.string.share_detail_amounts_hint)
                    detail == ShareDetail.CATEGORIES -> stringResource(R.string.share_detail_categories_hint)
                    else -> ""
                },
                checked = detail in shown,
                onCheckedChange = { on -> if (!locked) viewModel.setDetail(detail, on) },
                position = ListItemPosition.from(i, details.size),
            )
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
) {
    // Wraps rather than scrolls: a card type hidden past the edge is one nobody finds.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), maxLines = 1) },
                leadingIcon = leading?.let { { it(option) } },
                shapes = FilterChipDefaults.shapes(),
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun ThemeSwatch(theme: ShareTheme) {
    val p = theme.palette()
    Box(
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(p.top, p.bottom, p.accent))),
    )
}

private val SwatchSize: Dp = Dimensions.Icon.small

@Composable
private fun kindLabel(kind: ShareKind): String = stringResource(
    when (kind) {
        ShareKind.RECAP -> R.string.share_kind_recap
        ShareKind.SPENDING -> R.string.share_kind_spending
        ShareKind.SAVINGS -> R.string.share_kind_savings
        ShareKind.GOAL -> R.string.share_kind_goal
        ShareKind.BUDGET -> R.string.share_kind_budget
    }
)

@Composable
private fun themeLabel(theme: ShareTheme): String = stringResource(
    when (theme) {
        ShareTheme.MIDNIGHT -> R.string.share_theme_midnight
        ShareTheme.AURORA -> R.string.share_theme_aurora
        ShareTheme.PAPER -> R.string.share_theme_paper
    }
)

@Composable
private fun detailLabel(detail: ShareDetail, kind: ShareKind): String = stringResource(
    when (detail) {
        ShareDetail.AMOUNTS -> R.string.share_detail_amounts
        ShareDetail.PERCENTAGES -> R.string.share_detail_percentages
        ShareDetail.CATEGORIES -> R.string.share_detail_categories
        ShareDetail.CHART -> R.string.share_detail_chart
        ShareDetail.GOAL_NAME -> R.string.share_detail_goal_name
        ShareDetail.PACE -> R.string.share_detail_pace
        ShareDetail.PERIOD -> if (kind == ShareKind.GOAL) R.string.share_detail_due_date else R.string.share_detail_period
    }
)
