package com.pennywiseai.tracker.presentation.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Home's SMS status under the greeting, shown only when it has something to say: a read
 * is running, the last one found new messages, or nothing has been read yet. Reading
 * happens on its own, so a quiet "Updated 5 min ago" isn't shown; the menu's "Read
 * messages again" stays for a manual re-read. Tap reads now, long-press offers a full re-read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeSyncStatus(
    isScanning: Boolean,
    newCount: Int?,
    lastScanAt: Long?,
    onSync: () -> Unit,
    onFullResync: () -> Unit,
    modifier: Modifier = Modifier,
    /** Reports the chip's window bounds, for the spotlight tutorial. */
    onPositioned: ((Rect) -> Unit)? = null,
) {
    val text = when {
        isScanning -> stringResource(R.string.home_status_reading)
        newCount != null && newCount > 0 -> pluralStringResource(R.plurals.home_status_new, newCount, newCount)
        lastScanAt == null -> stringResource(R.string.home_status_never)
        // Up to date: nothing worth a pill.
        else -> return
    }
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .then(
                if (onPositioned != null) Modifier.onGloballyPositioned { onPositioned(it.boundsInWindow()) }
                else Modifier
            )
            .clip(CircleShape)
            .combinedClickable(
                enabled = !isScanning,
                onClickLabel = stringResource(R.string.home_status_read_now),
                onClick = onSync,
                onLongClickLabel = stringResource(R.string.home_resync_title),
                onLongClick = onFullResync,
            )
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = Dimensions.Component.statusChipHeight)
                .padding(horizontal = Spacing.smd, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            when {
                isScanning -> {
                    val spin by rememberInfiniteTransition(label = "syncSpin").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                        label = "syncSpinAngle"
                    )
                    Icon(Icons.Default.Sync, null, tint = accent, modifier = Modifier.size(Dimensions.Icon.small).rotate(spin))
                }
                newCount != null && newCount > 0 ->
                    Icon(Icons.Default.NewReleases, null, tint = accent, modifier = Modifier.size(Dimensions.Icon.small))
                else ->
                    Icon(Icons.Default.Sync, null, tint = accent, modifier = Modifier.size(Dimensions.Icon.small))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val SPIN_MILLIS = 1_000
