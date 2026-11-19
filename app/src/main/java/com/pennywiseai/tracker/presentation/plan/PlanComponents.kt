package com.pennywiseai.tracker.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import com.pennywiseai.tracker.ui.components.WavyMeter
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.readableOn

/** A determinate data meter: the app's still wavy bar (see [WavyMeter]). */
@Composable
internal fun PlanMeter(
    fraction: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    WavyMeter(fraction = fraction, color = color, trackColor = trackColor, modifier = modifier, dense = dense)
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

/** [PlanShapeBadge] holding [icon] in its own [color], darkened/lightened just enough to read on the wash. */
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
            // A goal's colour is user-picked and can be pale; keep its hue but make it read.
            tint = color.readableOn(listOf(color.tintOver())),
            modifier = Modifier.size(Dimensions.Icon.medium),
        )
    }
}
