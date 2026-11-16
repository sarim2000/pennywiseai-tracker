package com.pennywiseai.tracker.presentation.achievements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.domain.achievements.AchievementId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Badge art, drawn rather than shipped as images so it stays sharp at every size it
 * appears: a 40dp row in the app, a 170dp medal on a share card.
 *
 * Each achievement keeps one hue wherever it is drawn, on any card theme or app theme:
 * the badge carries its own background, so it needs no contrast from what's behind it.
 * The hues sit around the colour wheel so a set of them reads as a set, not a smear.
 */

/** An achievement's art: its glyph and its hue. */
data class BadgeArt(val icon: ImageVector, val hue: Color)

fun badgeArt(id: AchievementId?): BadgeArt = when (id) {
    AchievementId.TRACKED -> BadgeArt(Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFF5B9BFF))
    AchievementId.STREAK -> BadgeArt(Icons.Filled.LocalFireDepartment, Color(0xFFFF7A45))
    AchievementId.SUBSCRIPTIONS -> BadgeArt(Icons.Filled.Autorenew, Color(0xFFD77BF5))
    AchievementId.GOAL_HALFWAY -> BadgeArt(Icons.Filled.Flag, Color(0xFFFF6F91))
    AchievementId.GOALS_REACHED -> BadgeArt(Icons.Filled.EmojiEvents, Color(0xFFF7B731))
    AchievementId.BUDGET_KEPT -> BadgeArt(Icons.Filled.Shield, Color(0xFF26C6DA))
    AchievementId.SAVINGS_RATE -> BadgeArt(Icons.Filled.Savings, Color(0xFF34C77B))
    AchievementId.SAVINGS_UP -> BadgeArt(Icons.Filled.KeyboardDoubleArrowUp, Color(0xFF9B7BFF))
    AchievementId.SPENDING_DOWN -> BadgeArt(Icons.Filled.ContentCut, Color(0xFF9CCC3A))
    null -> BadgeArt(Icons.Filled.Star, Color(0xFFF7B731))
}

/**
 * An earned badge: a scalloped seal in the achievement's hue around a medallion holding
 * its glyph, with a pip per tier along the rim. Locked ([tier] 0), it is a plain ring in
 * [lockedColor] whose arc fills to [progress].
 */
@Composable
fun AchievementBadge(
    id: AchievementId?,
    tier: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    maxTier: Int = id?.thresholds?.size ?: 1,
    progress: Float = 0f,
    lockedColor: Color = Color(0xFF2A303A),
    lockedContent: Color = Color(0xFF8D99AA),
) {
    val art = badgeArt(id)
    val painter = rememberVectorPainter(art.icon)
    val locked = tier <= 0
    val seal = remember { Path() }
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = center
        if (locked) {
            val stroke = r * 0.13f
            val ring = r - stroke / 2
            drawCircle(lockedColor, ring, c, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    art.hue, -90f, 360f * progress.coerceIn(0f, 1f), false,
                    Offset(c.x - ring, c.y - ring), Size(ring * 2, ring * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            drawCircle(lockedColor.copy(alpha = 0.6f), r * 0.7f, c)
            drawGlyph(painter, r * 0.82f, lockedContent)
            return@Canvas
        }

        val light = lerp(art.hue, Color.White, 0.38f)
        val deep = lerp(art.hue, Color.Black, 0.42f)
        // The seal: a rim with soft scallops, lit from the top-start corner.
        seal.reset()
        val lobes = 14
        val steps = 168
        for (i in 0..steps) {
            val a = 2 * PI * i / steps
            val radius = r * (0.95f + 0.05f * cos(lobes * a).toFloat())
            val x = c.x + radius * cos(a).toFloat()
            val y = c.y + radius * sin(a).toFloat()
            if (i == 0) seal.moveTo(x, y) else seal.lineTo(x, y)
        }
        seal.close()
        drawPath(seal, Brush.linearGradient(listOf(light, art.hue, deep), Offset(0f, 0f), Offset(this.size.width, this.size.height)))

        // The medallion, darker than the rim so the glyph sits in it.
        val inner = r * 0.72f
        drawCircle(Brush.radialGradient(listOf(art.hue, deep), c, inner * 1.25f), inner, c)
        drawCircle(light.copy(alpha = 0.7f), inner, c, style = Stroke(r * 0.035f))
        // A sheen across the top half.
        drawCircle(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), c.y - inner, c.y),
            inner, c,
        )
        drawGlyph(painter, r * 0.78f, Color.White)

        // One pip per tier on the bottom of the rim, hollow for tiers still to earn.
        if (maxTier > 1) {
            val pip = r * 0.055f
            val track = r * 0.86f
            val spread = 15.0
            for (i in 0 until maxTier) {
                // First tier on the reading-start side: angles grow clockwise, from the right.
                val step = if (layoutDirection == LayoutDirection.Rtl) spread else -spread
                val deg = 90.0 + (i - (maxTier - 1) / 2.0) * step
                val rad = deg * PI / 180
                val p = Offset(c.x + track * cos(rad).toFloat(), c.y + track * sin(rad).toFloat())
                if (i < tier) drawCircle(Color.White, pip, p)
                else drawCircle(Color.White.copy(alpha = 0.7f), pip * 0.8f, p, style = Stroke(r * 0.02f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(
    painter: androidx.compose.ui.graphics.vector.VectorPainter,
    extent: Float,
    color: Color,
) {
    translate(center.x - extent / 2, center.y - extent / 2) {
        with(painter) { draw(Size(extent, extent), colorFilter = ColorFilter.tint(color)) }
    }
}

@Preview(name = "Badges")
@Composable
private fun AchievementBadgePreview() {
    androidx.compose.foundation.layout.FlowRow {
        AchievementId.entries.forEachIndexed { i, id ->
            AchievementBadge(id, tier = (i % id.thresholds.size) + 1, size = 72.dp)
        }
        AchievementBadge(AchievementId.TRACKED, tier = 0, size = 72.dp, progress = 0.6f)
    }
}
