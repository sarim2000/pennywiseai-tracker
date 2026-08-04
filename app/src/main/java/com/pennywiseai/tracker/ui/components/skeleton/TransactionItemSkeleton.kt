package com.pennywiseai.tracker.ui.components.skeleton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.components.shimmer
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Loading placeholder for a [TransactionItem][com.pennywiseai.tracker.ui.components.cards.TransactionItem].
 *
 * Geometry is deliberately derived from the same tokens the real row uses —
 * `Padding.cardCompact`, `Icon.list`, `minTouchTarget` — so rows don't shift
 * position or change height at the moment the data arrives. It previously used
 * `Spacing.md` padding and a 42dp avatar against the row's 40dp, which made the
 * whole list jump by a few dp when loading finished.
 */
@Composable
fun TransactionItemSkeleton(
    modifier: Modifier = Modifier
) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimensions.Padding.cardCompact)
            .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimensions.Icon.list)
                .clip(CircleShape)
                .background(placeholderColor)
                .shimmer()
        )

        Spacer(modifier = Modifier.width(Spacing.smd))

        // Title line + metadata line, matching the real row's two-line block.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SkeletonLine(width = TITLE_WIDTH, height = TITLE_HEIGHT, color = placeholderColor)
            SkeletonLine(width = SUBTITLE_WIDTH, height = META_HEIGHT, color = placeholderColor)
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        SkeletonLine(width = AMOUNT_WIDTH, height = TITLE_HEIGHT, color = placeholderColor)
    }
}

@Composable
private fun SkeletonLine(width: Dp, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(Dimensions.CornerRadius.small))
            .background(color)
            .shimmer()
    )
}

// Bar heights approximate the cap height of the text they stand in for, so the
// placeholder has the same visual weight as the loaded row.
private val TITLE_HEIGHT = 14.dp
private val META_HEIGHT = 10.dp
private val TITLE_WIDTH = 120.dp
private val SUBTITLE_WIDTH = 80.dp
private val AMOUNT_WIDTH = 60.dp
