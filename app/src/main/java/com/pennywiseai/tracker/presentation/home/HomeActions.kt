package com.pennywiseai.tracker.presentation.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.components.ProfileFilterDropdown
import com.pennywiseai.tracker.ui.components.profileFilterIcon
import com.pennywiseai.tracker.ui.components.spotlightTarget
import com.pennywiseai.tracker.ui.theme.*

/**
 * Home's top-bar actions: the Pro chip (free users only), the profile filter,
 * Add (while the floating nav bar is up) and More options.
 */
@Composable
internal fun HomeTopBarActions(
    isProEntitled: Boolean,
    blurEffects: Boolean,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    showProfileFilterMenu: Boolean,
    showAddAction: Boolean,
    onUpgradeClick: () -> Unit,
    onOpenProfileFilter: () -> Unit,
    onProfileSelected: (Long?) -> Unit,
    onDismissProfileFilter: () -> Unit,
    onNavigateToAddScreen: () -> Unit,
    onMoreClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Subtle Pro discovery chip — yellow sparkle that ties
        // back to the Settings → PennyWise Pro entry. Hidden
        // for already-entitled users so it's never pushy.
        // Tap → opens the same UpgradeSheet as Settings.
        if (!isProEntitled) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.iconButton)
                    .clip(CircleShape)
                    .background(
                        color = com.pennywiseai.tracker.ui.theme.yellow_light,
                        shape = CircleShape,
                    )
                    .clickable(
                        onClick = { onUpgradeClick() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.home_upgrade_pro),
                    tint = com.pennywiseai.tracker.ui.theme.yellow_dark,
                    modifier = Modifier.size(Dimensions.Icon.inline),
                )
            }
        }

        // Business/Personal filter dropdown
        Box {
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.iconButton)
                    .clip(CircleShape)
                    .background(
                        color = if (blurEffects) containerColor.copy(0.5f) else containerColor,
                        shape = CircleShape
                    )
                    .clickable(
                        onClick = { onOpenProfileFilter() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = profileFilterIcon(profiles, selectedProfileId),
                    contentDescription = stringResource(R.string.home_profile_filter),
                    tint = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.size(Dimensions.Icon.inline)
                )
            }
            ProfileFilterDropdown(
                expanded = showProfileFilterMenu,
                profiles = profiles,
                selectedProfileId = selectedProfileId,
                onProfileSelected = { onProfileSelected(it) },
                onDismiss = { onDismissProfileFilter() }
            )
        }
        if (showAddAction) {
            Box(
                modifier = Modifier
                    .size(Dimensions.Component.iconButton)
                    .clip(CircleShape)
                    .background(
                        color = if (blurEffects) containerColor.copy(0.5f) else containerColor,
                        shape = CircleShape
                    )
                    .clickable(
                        onClick = onNavigateToAddScreen,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_fab_add),
                    tint = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.size(Dimensions.Icon.medium)
                )
            }
        }
        // More options button
        Box(
            modifier = Modifier
                .size(Dimensions.Component.iconButton)
                .clip(CircleShape)
                .background(
                    color = if (blurEffects) containerColor.copy(0.5f) else containerColor,
                    shape = CircleShape
                )
                .clickable(
                    onClick = { onMoreClick() },
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.home_more_options),
                tint = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}

/**
 * The FAB stack shown when the floating nav bar is off: Add on top, Sync
 * below it (tap to scan, long-press for "Read messages again").
 */
@Composable
internal fun BoxScope.HomeFabStack(
    isScanning: Boolean,
    showSyncHint: Boolean,
    onNavigateToAddScreen: () -> Unit,
    onSync: () -> Unit,
    onRequestFullResync: () -> Unit,
    onFabPositioned: (Rect) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(
                end = Dimensions.Padding.content,
                bottom = Dimensions.Component.fabBottomInset
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.smd),
        horizontalAlignment = Alignment.End
    ) {
        // Add FAB (top, small)
        SmallFloatingActionButton(
            onClick = onNavigateToAddScreen,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.home_fab_add)
            )
        }

        // Sync FAB (bottom, primary)
        // Single tap: incremental scan, Long press: full resync
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Surface(
                modifier = Modifier
                    .spotlightTarget(onFabPositioned)
                    .size(Dimensions.Component.fab)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onSync() },
                            onLongPress = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                onRequestFullResync()
                            }
                        )
                    },
                shape = FloatingActionButtonDefaults.shape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = Dimensions.Elevation.fab,
                tonalElevation = Dimensions.Elevation.fab,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // M3 Expressive: the shape-morphing loading indicator
                    // takes the icon's place while a scan runs.
                    if (isScanning) {
                        LoadingIndicator(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(R.string.home_fab_sync)
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.home_fab_sync),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            // Hint for long-press functionality - only show for new users (no transactions yet)
            if (showSyncHint) {
                Text(
                    text = stringResource(R.string.home_fab_sync_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
