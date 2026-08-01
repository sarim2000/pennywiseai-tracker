package com.pennywiseai.tracker.presentation.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.data.share.ShareHero
import com.pennywiseai.tracker.data.share.SharePeriod
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(
    onDismiss: () -> Unit,
    initialPeriod: SharePeriod? = null,
    viewModel: ShareCardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val config by viewModel.config.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()

    // The monthly prompt opens on the finished month regardless of the saved period,
    // but doesn't overwrite the user's saved choice.
    LaunchedEffect(initialPeriod) {
        initialPeriod?.let(viewModel::applyPeriodOverride)
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
                text = "No amounts are included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = {
                    scope.launch {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        val uri = ShareCardExporter.writeCard(context, bitmap)
                        context.startActivity(
                            ShareCardExporter.shareIntent(
                                uri, config, data.transactionCount, data.subscriptionCount
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Share", modifier = Modifier.padding(start = Spacing.sm))
            }

            TextButton(onClick = { showCustomise = !showCustomise }) {
                Text(if (showCustomise) "Done" else "Customise")
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
                        text = "Show",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = config.hero == ShareHero.TRANSACTIONS,
                            onClick = {
                                viewModel.updateConfig { it.copy(hero = ShareHero.TRANSACTIONS) }
                            },
                            label = { Text("Transactions") },
                        )
                        FilterChip(
                            selected = config.hero == ShareHero.SUBSCRIPTIONS,
                            onClick = {
                                viewModel.updateConfig { it.copy(hero = ShareHero.SUBSCRIPTIONS) }
                            },
                            label = { Text("Subscriptions") },
                        )
                    }

                    Text(
                        text = "Period",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(
                            selected = config.period == SharePeriod.THIS_MONTH,
                            onClick = {
                                viewModel.updateConfig { it.copy(period = SharePeriod.THIS_MONTH) }
                            },
                            label = { Text("This month") },
                        )
                        FilterChip(
                            selected = config.period == SharePeriod.LAST_MONTH,
                            onClick = {
                                viewModel.updateConfig { it.copy(period = SharePeriod.LAST_MONTH) }
                            },
                            label = { Text("Last month") },
                        )
                        FilterChip(
                            selected = config.period == SharePeriod.ALL_TIME,
                            onClick = {
                                viewModel.updateConfig { it.copy(period = SharePeriod.ALL_TIME) }
                            },
                            label = { Text("All time") },
                        )
                    }
                }
            }
        }
    }
}

