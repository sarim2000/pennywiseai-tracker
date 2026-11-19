package com.pennywiseai.tracker.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.money

/*
 * The Plan tab's shared visual language: one hero card per section, segmented
 * rows that lead with a glyph in an Expressive shape, a status pill and a flat
 * meter. Budgets, Bills and Goals all draw from here so the three read as one tab.
 */

/** The big tonal card that opens a Plan section (the month's budget, the bills total). */
@Composable
internal fun PlanHeroCard(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.smd),
            content = content,
        )
    }
}

/** The one big figure on a hero card. Shrinks to fit rather than wrapping mid-number. */
@Composable
internal fun PlanHeroAmount(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold).money()
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(PennyWiseText.autoSizeFloor, style.fontSize),
        modifier = modifier,
    )
}

/**
 * A determinate data meter: flat, like Home's budget card. The wave is kept for
 * work in progress (scans, imports), so a budget never reads as "loading".
 */
@Composable
internal fun PlanMeter(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f) }
            .height(Dimensions.Component.progressBarHeight)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Small tinted pill naming a status: "On track", "Getting close", "Reached". */
@Composable
internal fun PlanStatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(color.tintOver(MaterialTheme.colorScheme.surfaceContainerLow), CircleShape)
            .padding(horizontal = Spacing.smd, vertical = Spacing.xxs),
    )
}

/** [this] colour washed over [surface]: the fill behind a glyph or pill drawn in it. */
@Composable
internal fun Color.tintOver(surface: Color = MaterialTheme.colorScheme.surfaceContainerLow): Color =
    copy(alpha = Dimensions.Alpha.categoryTint).compositeOver(surface)

/** The Expressive shapes Plan rows cycle through, so a list doesn't read as a column of circles. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun planBadgeShape(index: Int): Shape = when (index % 4) {
    0 -> MaterialShapes.Cookie9Sided.toShape()
    1 -> MaterialShapes.Clover4Leaf.toShape()
    2 -> MaterialShapes.Sunny.toShape()
    else -> MaterialShapes.SoftBurst.toShape()
}

/** A tint of [color] clipped to one of [planBadgeShape]'s shapes, with [glyph] centred on it. */
@Composable
internal fun PlanShapeBadge(
    color: Color,
    index: Int,
    modifier: Modifier = Modifier,
    glyph: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(Dimensions.Icon.avatarLarge)
            .clip(planBadgeShape(index))
            .background(color.tintOver()),
        contentAlignment = Alignment.Center,
        content = glyph,
    )
}

/** [PlanShapeBadge] holding [icon] in its own [color]. */
@Composable
internal fun PlanIconBadge(
    icon: ImageVector,
    color: Color,
    index: Int,
    modifier: Modifier = Modifier,
) {
    PlanShapeBadge(color = color, index = index, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Dimensions.Icon.medium),
        )
    }
}
