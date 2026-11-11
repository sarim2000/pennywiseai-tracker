package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.repository.GoalProgress
import com.pennywiseai.tracker.domain.goals.percent
import com.pennywiseai.tracker.domain.goals.ratioOf
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

/**
 * Circular progress in the goal's colour over a neutral track. [content] sits in
 * the middle and gets the whole percent read off the animated sweep, so the
 * number and the arc move together.
 *
 * [addedFrom] is what was saved just before the user's own Add money, in the
 * goal's currency. The portion it adds draws as its own segment (split off by a
 * gap, the older part dimmed) and then settles into the ring, after which
 * [onAddedSettled] fires. Leave it null for loads, refreshes and withdrawals:
 * those just move the ring.
 */
@Composable
fun GoalRing(
    progress: GoalProgress,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    addedFrom: BigDecimal? = null,
    onAddedSettled: () -> Unit = {},
    content: @Composable (percent: Int) -> Unit,
) {
    // Effects specs throughout: a progress arc never bounces past its value.
    val target = progress.ratioOf(progress.saved)
    val animated by animateFloatAsState(target, MaterialTheme.motionScheme.slowEffectsSpec(), label = "goalRing")
    val percent = ringPercent(animated, target, progress.percent())

    // 1 while the added segment stands apart, easing to 0 as it joins the ring.
    val emphasis = remember { Animatable(0f) }
    val fromRatio = addedFrom?.let(progress::ratioOf)
    val settleSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    val currentTarget by rememberUpdatedState(target)
    val currentSaved by rememberUpdatedState(progress.saved)
    val currentOnSettled by rememberUpdatedState(onAddedSettled)
    LaunchedEffect(addedFrom) {
        val from = addedFrom ?: return@LaunchedEffect
        emphasis.snapTo(1f)
        // The save and the new total can arrive in either order: wait for the
        // total to pass the old one and the sweep to reach it, then settle once.
        // Compares money, not the 4-dp ratio: a small Add can leave the ratio unchanged.
        snapshotFlow { currentSaved > from && animated == currentTarget }.first { it }
        emphasis.animateTo(0f, settleSpec)
        currentOnSettled()
    }

    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = progress.goal.color.toColorOr(MaterialTheme.colorScheme.primary)
    val description = stringResource(R.string.goals_ring_description, progress.percent())
    Box(
        modifier = modifier.size(size).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - width, this.size.height - width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            fun arc(c: Color, from: Float, sweep: Float) =
                drawArc(c, -90f + from, sweep, false, topLeft, arcSize, style = Stroke(width, cap = StrokeCap.Round))
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(width))
            val end = animated.coerceAtMost(1f)
            val start = fromRatio?.coerceAtMost(1f)
            val e = emphasis.value
            if (start == null || e == 0f || end <= start) {
                if (end > 0f) arc(color, 0f, 360f * end)
            } else {
                if (start > 0f) arc(color.copy(alpha = lerp(1f, Dimensions.Alpha.medium, e)), 0f, 360f * start)
                // Round caps reach half the stroke past each end, so the gap clears both before it shows.
                val gap = if (start > 0f) e * Math.toDegrees(((width + Spacing.xs.toPx()) / (arcSize.width / 2)).toDouble()).toFloat() else 0f
                val sweep = 360f * (end - start) - gap
                if (sweep > 0f) arc(color, 360f * start + gap, sweep)
            }
        }
        content(percent)
    }
}

/**
 * The ring's centre label while its sweep animates toward [target] (a [ratioOf]):
 * read off the same [animated] ratio, and exactly [percent] once it lands (or, heading
 * past 100%, once the ring is full, so it doesn't tick on), so the float never shows 28%
 * for 29%.
 */
fun ringPercent(animated: Float, target: Float, percent: Int): Int =
    if (animated == target || (animated >= 1f && target >= 1f)) percent else kotlin.math.floor(animated * 100f + 0.001f).toInt()
