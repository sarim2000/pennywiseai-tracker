package com.pennywiseai.tracker.presentation.paywall

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pennywiseai.tracker.billing.ProProduct
import com.pennywiseai.tracker.billing.ProSku
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The Pro upgrade sheet. Three plan cards (lifetime / annual / monthly),
 * a short feature list, single CTA at the bottom. Material 3 with
 * restrained accent — no rainbow gradients, no emoji-laden hype. The
 * premium feel comes from spacing, type hierarchy, and a single soft
 * gradient over the hero.
 *
 * State is hoisted into [UpgradeViewModel]; this composable is a pure
 * renderer + click forwarder. The caller controls visibility via [onDismiss].
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

    // Auto-dismiss when entitlement lands (purchase / restore success).
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
    ) {
        UpgradeSheetContent(
            state = state,
            onSelectPlan = viewModel::onSelectPlan,
            onPurchase = { activity?.let(viewModel::onPurchase) },
            onRestore = viewModel::onRestore,
            onClose = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
        )
    }
}

@Composable
private fun UpgradeSheetContent(
    state: UpgradeUiState,
    onSelectPlan: (String) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.md),
    ) {
        CloseHeader(onClose = onClose)
        HeroSection()
        Spacer(Modifier.height(Spacing.lg))
        FeatureList()
        Spacer(Modifier.height(Spacing.xl))
        PlanList(
            products = state.products,
            selectedSku = state.selectedSku,
            isLoading = state.isLoading,
            onSelect = onSelectPlan,
        )
        Spacer(Modifier.height(Spacing.lg))
        PurchaseCta(
            state = state,
            onPurchase = onPurchase,
        )
        Spacer(Modifier.height(Spacing.md))
        FooterRow(onRestore = onRestore)
    }
}

@Composable
private fun CloseHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Spacing.xs, top = Spacing.xs),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Hero: subtle vertical gradient (primaryContainer → transparent),
 * single icon chip, two-line type stack. No graphics, no stock art —
 * the typography carries the moment.
 */
@Composable
private fun HeroSection() {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            Color.Transparent,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Spacing.lg,
                bottom = Spacing.md,
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "PennyWise Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Power features for serious tracking",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FeatureList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FeatureRow(Icons.AutoMirrored.Outlined.Rule, "Unlimited custom rules")
        FeatureRow(Icons.Outlined.PictureAsPdf, "Unlimited PDF statement imports")
        FeatureRow(Icons.Outlined.FileDownload, "Unlimited CSV export")
        FeatureRow(Icons.AutoMirrored.Outlined.CallMerge, "Merge duplicate accounts")
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.Icon.medium),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlanList(
    products: List<ProProduct>,
    selectedSku: String?,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
) {
    val ordered = remember(products) { products.orderedForPaywall() }
    val monthlyMicros = ordered.find { it.sku == ProSku.MONTHLY }?.priceMicros

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (isLoading && ordered.isEmpty()) {
            PlanCardSkeleton()
            PlanCardSkeleton()
            PlanCardSkeleton()
        } else {
            ordered.forEach { product ->
                PlanCard(
                    product = product,
                    isSelected = product.sku == selectedSku,
                    monthlyMicros = monthlyMicros,
                    onClick = { onSelect(product.sku) },
                )
            }
        }
    }
}

/**
 * Single plan card. Selected = filled background + 2dp primary outline.
 * Unselected = surfaceContainer (one level of elevation). Big price on the
 * right, cadence below; left side carries the plan name + optional badge.
 */
@Composable
private fun PlanCard(
    product: ProProduct,
    isSelected: Boolean,
    monthlyMicros: Long?,
    onClick: () -> Unit,
) {
    val container = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Dimensions.Padding.card,
                vertical = Spacing.md,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.planTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    product.badgeLabel()?.let { label ->
                        Spacer(Modifier.width(Spacing.sm))
                        PlanBadge(
                            label = label,
                            tone = if (product.type == ProProduct.ProductType.LIFETIME_FOUNDER) {
                                BadgeTone.Founder
                            } else {
                                BadgeTone.Saving
                            },
                        )
                    }
                }
                product.cadenceLabel()?.let { cadence ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = cadence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                product.savingsLabel(monthlyMicros = monthlyMicros)?.let { savings ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = savings,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.priceFormatted,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PlanCardSkeleton() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
    ) {}
}

private enum class BadgeTone { Founder, Saving }

@Composable
private fun PlanBadge(label: String, tone: BadgeTone) {
    val (bg, fg) = when (tone) {
        BadgeTone.Founder ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        BadgeTone.Saving ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

@Composable
private fun PurchaseCta(
    state: UpgradeUiState,
    onPurchase: () -> Unit,
) {
    val selected = state.products.firstOrNull { it.sku == state.selectedSku }
    val canPurchase = !state.isLoading && !state.isPurchasing && selected != null
    Button(
        onClick = onPurchase,
        enabled = canPurchase,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
            .height(Dimensions.Component.buttonHeight),
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
                text = selected?.ctaLabel() ?: "Unlock Pro",
                style = MaterialTheme.typography.titleSmall,
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
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun FooterRow(onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cancel anytime · ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onRestore,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                text = "Restore purchases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// region: ProProduct extensions — display-only labels

private fun List<ProProduct>.orderedForPaywall(): List<ProProduct> {
    val priority = mapOf(
        ProSku.LIFETIME_FOUNDER to 0,
        ProSku.LIFETIME to 1,
        ProSku.ANNUAL to 2,
        ProSku.MONTHLY to 3,
    )
    return sortedBy { priority[it.sku] ?: Int.MAX_VALUE }
}

private fun ProProduct.planTitle(): String = when (type) {
    ProProduct.ProductType.LIFETIME_FOUNDER,
    ProProduct.ProductType.LIFETIME -> "Lifetime"
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> "Annual"
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> "Monthly"
}

private fun ProProduct.cadenceLabel(): String? = when (type) {
    ProProduct.ProductType.LIFETIME_FOUNDER -> "One-time · keep forever"
    ProProduct.ProductType.LIFETIME -> "One-time · keep forever"
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> "Billed every 12 months"
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> "Billed monthly"
}

private fun ProProduct.badgeLabel(): String? = when (type) {
    ProProduct.ProductType.LIFETIME_FOUNDER -> "Founder price"
    ProProduct.ProductType.LIFETIME -> null
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> null // savings shown as separate row
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> null
}

/**
 * "Save 32%" — derived from annual vs monthly × 12. Returns null when the
 * comparison isn't meaningful (this product is monthly, or we don't have
 * monthly to compare against).
 */
private fun ProProduct.savingsLabel(monthlyMicros: Long?): String? {
    if (type != ProProduct.ProductType.SUBSCRIPTION_ANNUAL) return null
    if (monthlyMicros == null || monthlyMicros == 0L) return null
    val twelveMonths = monthlyMicros * 12
    if (twelveMonths <= priceMicros) return null
    val savedPercent = ((twelveMonths - priceMicros).toDouble() / twelveMonths * 100).toInt()
    if (savedPercent <= 0) return null
    return "Save $savedPercent% vs monthly"
}

private fun ProProduct.ctaLabel(): String = when (type) {
    ProProduct.ProductType.LIFETIME_FOUNDER -> "Get Lifetime · $priceFormatted"
    ProProduct.ProductType.LIFETIME -> "Get Lifetime · $priceFormatted"
    ProProduct.ProductType.SUBSCRIPTION_ANNUAL -> "Subscribe annually · $priceFormatted"
    ProProduct.ProductType.SUBSCRIPTION_MONTHLY -> "Start monthly · $priceFormatted"
}

// endregion
