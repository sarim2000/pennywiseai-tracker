package com.pennywiseai.tracker.presentation.paywall

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.billing.ProProduct
import com.pennywiseai.tracker.billing.ProSku
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import kotlinx.coroutines.launch

/**
 * PennyWise Pro upgrade sheet.
 *
 * Backed by a two-SKU billing model: a single managed `pro_lifetime` product
 * (carries an optional Play Console Discount offer for time-limited founder
 * pricing) and a single `pro_subscription` product (carries two base plans,
 * monthly + annual). The renderer is agnostic to launch-marketing state — the
 * "is there a discount?" question is answered by [ProProduct.isDiscounted],
 * which the gateway populates from Play's offer details. Retiring the
 * founder window is a Play Console toggle, no code change.
 *
 * Single-decision layout: one segmented control flips Monthly / Annual /
 * Lifetime, and one big price block dominates the middle of the sheet. The
 * strikethrough original price is shown above the active price when (and
 * only when) Play is returning a discount offer for the selected plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    onDismiss: () -> Unit,
    viewModel: UpgradeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.didBecomePro) {
        if (state.didBecomePro) {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets.navigationBars },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        UpgradeSheetContent(
            state = state,
            onSelectKey = viewModel::onSelectPlan,
            onPurchase = { activity?.let(viewModel::onPurchase) },
            onRestore = viewModel::onRestore,
            onCelebrationComplete = viewModel::markCelebrationComplete,
        )
    }
}

@Composable
private fun UpgradeSheetContent(
    state: UpgradeUiState,
    onSelectKey: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onCelebrationComplete: () -> Unit,
) {
    // Celebration takes the whole sheet — even members shouldn't see the
    // status card when a fresh purchase has just landed.
    if (state.showCelebration) {
        CelebrationContent(onContinue = onCelebrationComplete)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.lg),
    ) {
        BrandHeader(isMember = state.isAlreadyEntitled)
        Spacer(Modifier.height(Spacing.lg))
        if (state.isAlreadyEntitled) {
            MemberCard()
            Spacer(Modifier.height(Spacing.md))
            ManageRow(onRestore = onRestore)
        } else {
            UpgradeBody(
                state = state,
                onSelectKey = onSelectKey,
                onPurchase = onPurchase,
                onRestore = onRestore,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Brand header — yellow icon tile + title row, matches Settings entry
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun BrandHeader(isMember: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(yellow_light, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = yellow_dark,
                modifier = Modifier.size(Dimensions.Icon.medium),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PennyWise Pro",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isMember) {
                    "All Pro capabilities active"
                } else {
                    "One tap to unlock everything"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Upgrade variant
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun UpgradeBody(
    state: UpgradeUiState,
    onSelectKey: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    val merged = remember(state.products) { mergedPlans(state.products) }

    var activeTier by remember { mutableStateOf(PlanTier.Lifetime) }

    val activeProduct by remember(merged, activeTier) {
        derivedStateOf { merged.productForTier(activeTier) }
    }

    LaunchedEffect(activeProduct?.key) {
        activeProduct?.key?.let(onSelectKey)
    }

    val monthlyMicros = remember(merged) {
        merged.find { it.type == ProProduct.ProductType.SUBSCRIPTION_MONTHLY }?.priceMicros
    }

    PlanSegment(
        selected = activeTier,
        onSelect = { activeTier = it },
    )
    Spacer(Modifier.height(Spacing.xl))

    PriceStage(
        product = activeProduct,
        monthlyMicros = monthlyMicros,
    )
    Spacer(Modifier.height(Spacing.lg))

    CtaButton(
        state = state,
        selectedPlan = activeProduct,
        onPurchase = onPurchase,
    )
    Spacer(Modifier.height(Spacing.lg))

    IncludesBlock()
    Spacer(Modifier.height(Spacing.lg))

    TrustRow(
        liveCatalogEmpty = state.products.isEmpty() && !state.isLoading,
        onRestore = onRestore,
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Plan segment — 3 tabs in a pill, single source of truth for cadence.
// ─────────────────────────────────────────────────────────────────────────

private enum class PlanTier(val displayName: String) {
    Monthly("Monthly"),
    Annual("Annual"),
    Lifetime("Lifetime"),
}

@Composable
private fun PlanSegment(
    selected: PlanTier,
    onSelect: (PlanTier) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PlanTier.entries.forEach { tier ->
                    SegmentTab(
                        tier = tier,
                        isSelected = tier == selected,
                        onClick = { onSelect(tier) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentTab(
    tier: PlanTier,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "tab-bg",
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "tab-fg",
    )
    Surface(
        onClick = onClick,
        color = container,
        shape = RoundedCornerShape(50),
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = tier.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Price stage — eyebrow, optional strike-through original, big price, deal line.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PriceStage(
    product: ProProduct?,
    monthlyMicros: Long?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = product?.eyebrowText(),
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label = "eyebrow",
            ) { eyebrow ->
                if (eyebrow != null) {
                    EyebrowChip(
                        text = eyebrow,
                        isAccent = product?.isDiscounted == true,
                    )
                } else {
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))

        Box(modifier = Modifier.heightIn(min = 20.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = product?.originalPriceFormatted,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "comparison",
            ) { original ->
                if (original != null) {
                    Text(
                        text = original,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough,
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        AnimatedContent(
            targetState = product?.priceFormatted ?: "—",
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "price",
        ) { price ->
            Text(
                text = price,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xs))

        AnimatedContent(
            targetState = product?.dealOrCadence(monthlyMicros) ?: ("" to false),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "deal-cadence",
        ) { (line, isDeal) ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isDeal) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isDeal) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Small filled-tonal eyebrow chip. Accent variant uses the gold identity
 * for active-discount moments; standard uses surfaceContainerLow + primary
 * for neutral classifiers.
 */
@Composable
private fun EyebrowChip(text: String, isAccent: Boolean) {
    val container = if (isAccent) yellow_light else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (isAccent) yellow_dark else MaterialTheme.colorScheme.primary
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 6.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// CTA — primary fill, 56dp tall. Label tracks active plan.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun CtaButton(
    state: UpgradeUiState,
    selectedPlan: ProProduct?,
    onPurchase: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onPurchase,
            enabled = !state.isPurchasing && selectedPlan != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md),
        ) {
            if (state.isPurchasing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.Component.progressIndicatorSize),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    text = selectedPlan?.ctaLabel() ?: "Select a plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Includes — checklist of unlocked features. Reassures post-decision.
// ─────────────────────────────────────────────────────────────────────────

private val PRO_FEATURES = listOf(
    "Unlimited custom rules",
    "Unlimited PDF statement imports",
    "Unlimited CSV export",
    "Merge duplicate accounts",
)

@Composable
private fun IncludesBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
    ) {
        Text(
            text = "Includes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        Spacer(Modifier.height(Spacing.xs))
        PRO_FEATURES.forEach { feature ->
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = feature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Trust row + fallback disclosure
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TrustRow(
    liveCatalogEmpty: Boolean,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Cancel anytime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Dot()
            Text(
                text = "On-device data",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Dot()
            TextButton(
                onClick = onRestore,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "Restore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (liveCatalogEmpty) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Prices shown are indicative · Play Store confirms at checkout",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Dot() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Celebration — post-purchase moment. Animated sparkle, congratulations,
// includes list, Continue. Auto-dismisses after a short hold.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun CelebrationContent(onContinue: () -> Unit) {
    // Auto-dismiss timer — gives the user time to read but doesn't trap
    // them. Tapping Continue short-circuits.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4_000L)
        onContinue()
    }

    // Run the entrance animation once on mount.
    var animateIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animateIn = true }

    val iconScale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "celebration-icon-scale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 200),
        label = "celebration-content-alpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimensions.Padding.content,
                vertical = Spacing.xl,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Animated yellow tile carrying the sparkle — same identity as the
        // Settings entry and the brand header, so the celebration feels
        // like the natural climax of the journey, not a stranger.
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
                .background(yellow_light, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = yellow_dark,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(Spacing.lg))

        Column(
            modifier = Modifier.graphicsLayer { alpha = contentAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowChip(text = "WELCOME", isAccent = true)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "You're a Pro member",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Thank you for backing PennyWise — every feature on the list is now yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))

            // Quiet recap of what they unlocked.
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                PRO_FEATURES.forEach { feature ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Member variant — already entitled. Status block + manage row.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun MemberCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Dimensions.Padding.card,
                vertical = Spacing.lg,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EyebrowChip(text = "ACTIVE", isAccent = true)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "All Pro features unlocked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            IncludesBlock()
        }
    }
}

@Composable
private fun ManageRow(onRestore: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(
            onClick = {
                runCatching {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            "https://play.google.com/store/account/subscriptions" +
                                "?package=${context.packageName}",
                        ),
                    )
                    context.startActivity(intent)
                }
            },
        ) {
            Text(
                text = "Manage subscription",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        TextButton(onClick = onRestore) {
            Text(
                text = "Restore",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Fallback catalog + display extensions
// ─────────────────────────────────────────────────────────────────────────

/**
 * Static fallback used when the live Play catalog hasn't landed (e.g. dev
 * emulator with no Play Console setup, or a brief network blip on cold
 * start). Models the SAME shape Play would return:
 *   - Lifetime with both an active ₹999 founder offer AND the regular
 *     ₹1,999 baseline → the renderer shows the strikethrough exactly as
 *     it would with a real Play Console Discount offer active.
 *   - Subscription expanded into 2 entries (monthly + annual), each with
 *     its own basePlanId.
 *
 * `offerToken` is null in fallback — if the user taps Purchase before
 * the live catalog arrives, the gateway raises a meaningful "plans
 * loading" error, not a dead CTA.
 */
private val FALLBACK_CATALOG = listOf(
    ProProduct(
        sku = ProSku.LIFETIME,
        type = ProProduct.ProductType.LIFETIME,
        priceFormatted = "₹999",
        priceMicros = 999_000_000L,
        originalPriceFormatted = "₹1,999",
        originalPriceMicros = 1_999_000_000L,
        currencyCode = "INR",
    ),
    ProProduct(
        sku = ProSku.SUBSCRIPTION,
        basePlanId = ProSku.BASE_PLAN_MONTHLY,
        type = ProProduct.ProductType.SUBSCRIPTION_MONTHLY,
        priceFormatted = "₹49",
        priceMicros = 49_000_000L,
        currencyCode = "INR",
    ),
    ProProduct(
        sku = ProSku.SUBSCRIPTION,
        basePlanId = ProSku.BASE_PLAN_ANNUAL,
        type = ProProduct.ProductType.SUBSCRIPTION_ANNUAL,
        priceFormatted = "₹399",
        priceMicros = 399_000_000L,
        currencyCode = "INR",
    ),
)

/**
 * Live entries win on `key` collision (e.g. `pro_subscription#monthly`),
 * fallback entries fill any gaps. Result is never empty in practice.
 */
private fun mergedPlans(live: List<ProProduct>): List<ProProduct> {
    val liveKeys = live.map { it.key }.toSet()
    return live + FALLBACK_CATALOG.filter { it.key !in liveKeys }
}

private fun List<ProProduct>.productForTier(tier: PlanTier): ProProduct? = when (tier) {
    PlanTier.Monthly -> find { it.type == ProProduct.ProductType.SUBSCRIPTION_MONTHLY }
    PlanTier.Annual -> find { it.type == ProProduct.ProductType.SUBSCRIPTION_ANNUAL }
    PlanTier.Lifetime -> find { it.type == ProProduct.ProductType.LIFETIME }
}

private fun ProProduct.eyebrowText(): String = when (type) {
    ProProduct.ProductType.LIFETIME -> if (isDiscounted) "FOUNDER OFFER" else "LIFETIME ACCESS"
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> if (isDiscounted) "LIMITED OFFER" else "BILLED ANNUALLY"
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> if (isDiscounted) "LIMITED OFFER" else "BILLED MONTHLY"
}

/**
 * Returns (text, isDeal). `isDeal == true` renders in primary + SemiBold,
 * the "you're saving money" highlight. For lifetime we lead with the rupee
 * amount saved (Indian shoppers respond to ₹ saved more than to %).
 */
private fun ProProduct.dealOrCadence(monthlyMicros: Long?): Pair<String, Boolean> = when (type) {
    ProProduct.ProductType.LIFETIME -> when {
        isDiscounted -> {
            val savedMicros = (originalPriceMicros ?: 0L) - priceMicros
            val savedRupees = (savedMicros / 1_000_000.0).toInt()
            val pct = ((savedMicros.toDouble() / (originalPriceMicros ?: priceMicros).toDouble()) * 100).toInt()
            "Save ₹$savedRupees · $pct% off · Pay once, keep forever" to true
        }
        else -> "Pay once. Keep forever." to false
    }
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> {
        val pct = annualSavingsPct(monthlyMicros)
        val perMo = (priceMicros / 12L / 1_000_000.0).toInt()
        if (pct != null) {
            "Save $pct% · works out to ₹$perMo/month" to true
        } else {
            "Works out to ₹$perMo/month" to false
        }
    }
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> "Cancel anytime" to false
}

private fun ProProduct.ctaLabel(): String = when (type) {
    ProProduct.ProductType.LIFETIME -> "Get Lifetime · $priceFormatted"
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> "Subscribe · $priceFormatted/year"
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> "Start · $priceFormatted/month"
}

private fun ProProduct.annualSavingsPct(monthlyMicros: Long?): Int? {
    if (type != ProProduct.ProductType.SUBSCRIPTION_ANNUAL) return null
    if (monthlyMicros == null || monthlyMicros == 0L) return null
    val twelveMonths = monthlyMicros * 12
    if (twelveMonths <= priceMicros) return null
    val saved = ((twelveMonths - priceMicros).toDouble() / twelveMonths * 100).toInt()
    return saved.takeIf { it > 0 }
}
