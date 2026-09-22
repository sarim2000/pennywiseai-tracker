package com.pennywiseai.tracker.presentation.paywall

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import com.pennywiseai.tracker.core.Constants
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.yellow_dark
import com.pennywiseai.tracker.ui.theme.yellow_light
import kotlinx.coroutines.launch

/**
 * PennyWise Pro sheet.
 *
 * Nothing is sold here. Pro is not purchasable inside the app at all, so the
 * sheet lists what Pro unlocks, takes a license key, and offers Restore for
 * anyone who bought through Play while that was possible. It quotes no
 * price, launches no checkout, and points nowhere to buy one — which is what
 * keeps a consumption-only app on the right side of Play's Payments policy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    onDismiss: () -> Unit,
    viewModel: UpgradeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // One-shot dismiss event from the ViewModel. Modeled as a Channel/Flow
    // rather than persistent state so a stale ViewModel (Hilt scopes it to
    // the Activity, surviving sheet open/close cycles) can't re-trigger
    // dismiss on the next open. Collected once per composition.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                UpgradeEvent.Dismiss -> {
                    sheetState.hide()
                    onDismiss()
                }
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
            onRestore = viewModel::onRestore,
            onCelebrationComplete = viewModel::markCelebrationComplete,
            onShowLicenseDialog = viewModel::onShowLicenseDialog,
            onRemoveLicense = viewModel::onRemoveLicense,
            onHelp = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(Constants.Links.DISCORD_URL),
                        ),
                    )
                }
            },
        )
    }

    if (state.showLicenseDialog) {
        LicenseKeyDialog(
            isActivating = state.isActivating,
            error = state.licenseError,
            canMove = state.licenseCanMove,
            onActivate = viewModel::onActivateLicense,
            onMoveHere = viewModel::onMoveLicenseHere,
            onDismiss = viewModel::onDismissLicenseDialog,
        )
    }
}

@Composable
private fun UpgradeSheetContent(
    state: UpgradeUiState,
    onRestore: () -> Unit,
    onCelebrationComplete: () -> Unit,
    onShowLicenseDialog: () -> Unit,
    onRemoveLicense: () -> Unit,
    onHelp: () -> Unit,
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
            MemberCard(licenseProductName = state.licenseProductName.takeIf { state.isLicensed })
            Spacer(Modifier.height(Spacing.md))
            ManageRow(
                isLicensed = state.isLicensed,
                onRestore = onRestore,
                onRemoveLicense = onRemoveLicense,
            )
        } else {
            UpgradeBody(
                state = state,
                onRestore = onRestore,
                onLicenseKey = onShowLicenseDialog,
                onHelp = onHelp,
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
                    "Everything unlocked with a license key"
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
    onRestore: () -> Unit,
    onLicenseKey: () -> Unit,
    onHelp: () -> Unit,
) {
    IncludesBlock()
    Spacer(Modifier.height(Spacing.lg))

    SupportNote()
    Spacer(Modifier.height(Spacing.lg))

    // The only call to action left. Pro is not sold through this app, so the
    // sheet shows what Pro does and takes a key — it never quotes a price,
    // launches a checkout, or points anywhere to buy one.
    Button(
        onClick = onLicenseKey,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
            .height(Dimensions.Component.buttonHeight),
        shape = RoundedCornerShape(Dimensions.CornerRadius.large),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = "Enter license key",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(Modifier.height(Spacing.lg))

    TrustRow(
        isRestoring = state.isPurchasing,
        onRestore = onRestore,
        onHelp = onHelp,
    )

    // Restore is the only thing here that can fail, and it fails silently
    // otherwise: the error used to ride along with the purchase CTA.
    state.errorMessage?.let { message ->
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimensions.Padding.content),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Support note — the emotional beat at the buy moment. Reminds the buyer
// there's a real person behind the app and what their money actually funds.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SupportNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content)
            .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
            .background(yellow_light)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = yellow_dark,
            modifier = Modifier.size(Dimensions.Icon.medium),
        )
        Text(
            text = "Built by a solo dev — Pro funds what's next. Thank you.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF3A2B00),
        )
    }
}

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
                modifier = Modifier.padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimensions.Icon.small),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrustRow(
    isRestoring: Boolean,
    onRestore: () -> Unit,
    onHelp: () -> Unit,
) {
    // FlowRow, not Row: at large accessibility font sizes these three items
    // don't fit one line on a narrow screen, and Restore must stay reachable.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "On-device data",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onRestore,
            enabled = !isRestoring,
            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.none),
        ) {
            Text(
                text = if (isRestoring) "Restoring…" else "Restore",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        TextButton(
            onClick = onHelp,
            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.none),
        ) {
            Text(
                text = "Get help",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
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
                        modifier = Modifier.padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimensions.Icon.small),
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
private fun MemberCard(licenseProductName: String?) {
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
            if (licenseProductName != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Via license key · $licenseProductName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            IncludesBlock()
        }
    }
}

@Composable
private fun ManageRow(
    isLicensed: Boolean,
    onRestore: () -> Unit,
    onRemoveLicense: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isLicensed) {
            // Frees the one allowed activation so the key can be used on
            // another phone. Play subscribers get the Play manage link instead.
            TextButton(onClick = onRemoveLicense) {
                Text(
                    text = "Remove license key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else TextButton(
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
