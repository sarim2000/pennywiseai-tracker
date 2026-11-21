package com.pennywiseai.tracker.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.*

/**
 * "Read messages again": scan every message again, or rebuild SMS
 * transactions from scratch. Each option closes the dialog, then acts.
 */
@Composable
internal fun FullResyncDialog(
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
    onRebuild: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(stringResource(R.string.home_resync_title))
        },
        text = {
            // Two ways to re-read: the gentle one first, since a full rebuild
            // resets edits to SMS transactions.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ResyncOption(
                    title = stringResource(R.string.home_resync_scan_again),
                    description = stringResource(R.string.home_resync_scan_again_desc),
                    onClick = {
                        onDismiss()
                        onScanAgain()
                    }
                )
                ResyncOption(
                    title = stringResource(R.string.home_resync_rebuild),
                    description = stringResource(R.string.home_resync_rebuild_desc),
                    onClick = {
                        onDismiss()
                        onRebuild()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDismiss() },
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.home_cancel))
            }
        }
    )
}

/** One choice in the "Read messages again" dialog: a tappable title + what it does. */
@Composable
private fun ResyncOption(title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        // The dialog itself is surfaceContainerHigh, so a step up keeps each
        // option reading as a tappable card.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
