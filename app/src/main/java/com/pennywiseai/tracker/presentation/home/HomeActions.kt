package com.pennywiseai.tracker.presentation.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.components.ProfileFilterDropdown
import com.pennywiseai.tracker.ui.components.profileFilterIcon
import com.pennywiseai.tracker.ui.components.spotlightTarget
import com.pennywiseai.tracker.ui.theme.*

/**
 * Home's collapsed top-bar actions: the profile filter (only with more than one profile
 * to choose between) and More options. Voice lives in the Add sheet, Pro in Settings.
 */
@Composable
internal fun HomeTopBarActions(
    blurEffects: Boolean,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    showProfileFilterMenu: Boolean,
    onOpenProfileFilter: () -> Unit,
    onProfileSelected: (Long?) -> Unit,
    onDismissProfileFilter: () -> Unit,
    onMoreClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = if (blurEffects) containerColor.copy(alpha = Dimensions.Alpha.medium) else containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = Dimensions.Padding.content),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (profiles.size > 1) {
            Box {
                FilledIconButton(onClick = onOpenProfileFilter, colors = colors, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        imageVector = profileFilterIcon(profiles, selectedProfileId),
                        contentDescription = stringResource(R.string.home_profile_filter),
                        modifier = Modifier.size(Dimensions.Icon.inline)
                    )
                }
                ProfileFilterDropdown(
                    expanded = showProfileFilterMenu,
                    profiles = profiles,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = onProfileSelected,
                    onDismiss = onDismissProfileFilter
                )
            }
        }
        FilledIconButton(onClick = onMoreClick, colors = colors, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.home_more_options),
                modifier = Modifier.size(Dimensions.Icon.medium)
            )
        }
    }
}

/**
 * Height of [HomeFabStack]'s buttons: the small Add FAB, the gap, then Sync. The
 * small FAB draws 40dp but lays out in a 48dp slot (its minimum touch target).
 */
internal val HomeFabStackHeight = Dimensions.Component.minTouchTarget + Spacing.smd + Dimensions.Component.fab

/** How far [HomeFabStack] stands above the bottom edge: clear of the system bar and the nav bar. */
internal fun homeFabStackBottomPadding(systemBarBottom: Dp): Dp = systemBarBottom + Dimensions.Component.fabBottomInset

/**
 * How much the Sync hint under [HomeFabStack]'s Sync button lifts the stack: the gap
 * above it plus one labelSmall line, which grows with the font scale.
 */
@Composable
internal fun homeSyncHintHeight(): Dp =
    Spacing.xs + with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }

/**
 * Bottom padding for Home's list, so its last row can scroll clear of what floats
 * over it. [systemBarBottom] is the Scaffold's bottom inset (the system nav bar);
 * [syncHintHeight] is how much the Sync hint lifts the FAB stack while it shows.
 */
internal fun homeListBottomPadding(systemBarBottom: Dp, floatingNav: Boolean, syncHintHeight: Dp = 0.dp): Dp =
    if (floatingNav) {
        // The floating pill's row sits above the nav bar; Add docks above Ask's squircle in it.
        systemBarBottom + Dimensions.Component.floatingNavBottomGap + Dimensions.Component.floatingNavHeight +
            Spacing.sm + Dimensions.Component.dockedActionSmallSize + Spacing.md
    } else {
        // The nav bar, then the Add + Sync stack standing above it.
        homeFabStackBottomPadding(systemBarBottom) + syncHintHeight + HomeFabStackHeight + Spacing.md
    }

/**
 * The FAB stack shown when the floating nav bar is off: Add on top, Sync
 * below it (tap to scan, long-press for "Read messages again"). It stands
 * [Dimensions.Component.fabBottomInset] above [systemBarBottom], clear of the nav bar.
 */
@Composable
internal fun BoxScope.HomeFabStack(
    systemBarBottom: Dp,
    isScanning: Boolean,
    showSyncHint: Boolean,
    onNavigateToAddScreen: () -> Unit,
    onSync: () -> Unit,
    onRequestFullResync: () -> Unit,
    onFabPositioned: ((Rect) -> Unit)?
) {
    val view = LocalView.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(
                end = Dimensions.Padding.content,
                bottom = homeFabStackBottomPadding(systemBarBottom)
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
                    .then(onFabPositioned?.let { Modifier.spotlightTarget(it) } ?: Modifier)
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
