package com.pennywiseai.tracker.presentation.share

import com.pennywiseai.tracker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.data.share.ShareWindow
import com.pennywiseai.tracker.data.share.ShareCardExporter
import com.pennywiseai.tracker.ui.components.ShareCard
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Preview-then-share.
 *
 * The card is always shown before it can be sent, even though it exposes nothing by
 * design — the user has to be able to see that for themselves, and a silent
 * generate-and-fling from someone's financial data would be the wrong shape for this
 * app. Customisation is collapsed by default so the repeat path stays two taps.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareCardSheet(
    onDismiss: () -> Unit,
    window: ShareWindow? = null,
    viewModel: ShareCardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))

    val config by viewModel.config.collectAsStateWithLifecycle()
    val loadedData by viewModel.data.collectAsStateWithLifecycle()
    // Null while (re)loading — preview a blank card and keep Share disabled rather than
    // let a previous profile's or period's numbers go out under the current label.
    val data = loadedData ?: ShareCardData()

    LaunchedEffect(window) {
        viewModel.setWindow(window)
    }

    var showCustomise by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ShareCard(
                config = config,
                data = data,
                modifier = Modifier.drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
            )

            Text(
                text = stringResource(R.string.share_sheet_no_amounts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Button(
                enabled = loadedData != null,
                onClick = {
                    scope.launch {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        val uri = ShareCardExporter.writeCard(context, bitmap)
                        val hero = config.effectiveHero(data.subscriptionCount, data.merchantCount)
                        context.startActivity(
                            ShareCardExporter.shareIntent(
                                uri, ShareCardExporter.shareText(context, hero, data.countFor(hero))
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text(stringResource(R.string.share_sheet_share), modifier = Modifier.padding(start = Spacing.sm))
            }

            TextButton(onClick = { showCustomise = !showCustomise }, shapes = ButtonDefaults.shapes()) {
                Text(if (showCustomise) stringResource(R.string.share_sheet_done) else stringResource(R.string.share_sheet_customise))
            }

            AnimatedVisibility(visible = showCustomise) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // A single choice rather than three switches: the card shows one
                    // figure, so offering three independent toggles would imply a stacked
                    // layout that no longer exists.
                    Text(
                        text = stringResource(R.string.share_sheet_show),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                    // Selection reflects the hero actually drawn, not the stored one, so
                    // the chip can never disagree with the card above it.
                    val effectiveHero = config.effectiveHero(data.subscriptionCount, data.merchantCount)
                    val hasSubscriptions = data.subscriptionCount > 0
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = effectiveHero == ShareHero.TRANSACTIONS,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            onClick = {
                                viewModel.updateConfig { it.copy(hero = ShareHero.TRANSACTIONS) }
                            },
                            label = { Text(stringResource(R.string.share_sheet_transactions)) },
                        )
                        FilterChip(
                            selected = effectiveHero == ShareHero.MERCHANTS,
                            shapes = FilterChipDefaults.shapes(),
                            colors = FilterChipDefaults.filterChipColors(),
                            enabled = data.merchantCount > 0,
                            onClick = {
                                viewModel.updateConfig { it.copy(hero = ShareHero.MERCHANTS) }
                            },
                            label = { Text(stringResource(R.string.share_sheet_merchants)) },
                        )
                        // Not offered for a review's period: it's today's count, not that period's.
                        if (window == null) {
                            FilterChip(
                                selected = effectiveHero == ShareHero.SUBSCRIPTIONS,
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                // Disabled rather than silently ignored: tapping a chip and
                                // seeing nothing happen reads as a bug, not as a choice the
                                // data can't support.
                                enabled = hasSubscriptions,
                                onClick = {
                                    viewModel.updateConfig { it.copy(hero = ShareHero.SUBSCRIPTIONS) }
                                },
                                label = { Text(stringResource(R.string.share_sheet_subscriptions)) },
                            )
                        }
                    }
                    if (!hasSubscriptions && window == null) {
                        Text(
                            text = stringResource(R.string.share_sheet_no_subscriptions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }

                    // A review chose the dates already.
                    if (window == null) {
                        Text(
                            text = stringResource(R.string.share_sheet_period),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilterChip(
                                selected = config.period == SharePeriod.THIS_MONTH,
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                onClick = {
                                    viewModel.updateConfig { it.copy(period = SharePeriod.THIS_MONTH) }
                                },
                                label = { Text(stringResource(R.string.share_sheet_this_month)) },
                            )
                            FilterChip(
                                selected = config.period == SharePeriod.LAST_MONTH,
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                onClick = {
                                    viewModel.updateConfig { it.copy(period = SharePeriod.LAST_MONTH) }
                                },
                                label = { Text(stringResource(R.string.share_sheet_last_month)) },
                            )
                            FilterChip(
                                selected = config.period == SharePeriod.ALL_TIME,
                                shapes = FilterChipDefaults.shapes(),
                                colors = FilterChipDefaults.filterChipColors(),
                                onClick = {
                                    viewModel.updateConfig { it.copy(period = SharePeriod.ALL_TIME) }
                                },
                                label = { Text(stringResource(R.string.share_sheet_all_time)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

