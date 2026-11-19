package com.pennywiseai.tracker.presentation.home

import android.text.format.DateUtils
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.delay

/**
 * Home's SMS status under the greeting: when messages were last read, what the last
 * scan found, or that one is running. Reading happens on its own, so this replaces a
 * Sync button: tap reads now, long-press offers a full re-read.
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
    // Ticks each minute so "5 min ago" doesn't go stale while Home stays open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(DateUtils.MINUTE_IN_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    val text = when {
        isScanning -> stringResource(R.string.home_status_reading)
        newCount != null && newCount > 0 -> pluralStringResource(R.plurals.home_status_new, newCount, newCount)
        lastScanAt == null -> stringResource(R.string.home_status_never)
        now - lastScanAt < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.home_status_just_now)
        else -> stringResource(
            R.string.home_status_updated,
            DateUtils.getRelativeTimeSpanString(lastScanAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
        )
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
                lastScanAt == null ->
                    Icon(Icons.Default.Sync, null, tint = accent, modifier = Modifier.size(Dimensions.Icon.small))
                else ->
                    Icon(Icons.Default.CheckCircle, null, tint = accent, modifier = Modifier.size(Dimensions.Icon.small))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val SPIN_MILLIS = 1_000
