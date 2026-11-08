package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R

/**
 * Bulk-edit actions (#369) as an M3 Expressive floating toolbar at the bottom
 * of the Transactions list while rows are selected. Mark as transfer appears
 * only when exactly two rows are selected.
 */
@Composable
fun BulkActionsToolbar(
    visible: Boolean,
    canMarkAsTransfer: Boolean,
    onChangeCategory: () -> Unit,
    onAddToGroup: () -> Unit,
    onDelete: () -> Unit,
    onMarkAsTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            contentAlignment = Alignment.Center
        ) {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
            ) {
                IconButton(onClick = onChangeCategory, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.Category, contentDescription = stringResource(R.string.txn_list_change_category))
                }
                IconButton(onClick = onAddToGroup, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.txn_list_add_to_group)
                    )
                }
                IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.txn_list_delete_selected),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                // Linking needs exactly two rows — the manual counterpart to the
                // auto-detected chip (#614).
                if (canMarkAsTransfer) {
                    IconButton(onClick = onMarkAsTransfer, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.txn_list_mark_as_transfer))
                    }
                }
            }
        }
    }
}
