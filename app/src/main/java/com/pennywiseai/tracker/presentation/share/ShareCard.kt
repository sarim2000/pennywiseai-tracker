package com.pennywiseai.tracker.presentation.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.share.SHARE_CARD_URL
import com.pennywiseai.tracker.domain.achievements.AchievementId
import com.pennywiseai.tracker.ui.components.AchievementBadge
import com.pennywiseai.tracker.ui.components.badgeArt
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexFamily
import com.pennywiseai.tracker.ui.theme.GoogleSansFlexHeadingFamily
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The image users send to WhatsApp, and its link-preview twin.
 *
 * Designed at ~260px chat-thumbnail scale and scaled up, not the other way round. An early
 * version was calibrated against the in-app preview — the one context where the card is
 * never actually consumed — and downscaled to a chat bubble almost nothing on it was
 * legible, the URL included. So: one huge figure, a chart with chunky marks, at most three
 * stats, and the URL at a size that survives the same reduction as the hero.
 *
 * Two constraints carried over deliberately:
 *
 * 1. **Fixed palettes, not Material You** (see [ShareTheme]). The card is a fixed artboard,
 *    which is also why its sizes are its own and not the app's spacing tokens: it is
 *    drawn at one size and exported as an image, never laid out to a screen.
 * 2. **Only [ShareCardContent].** Nothing here formats a number or reads a setting, so the
 *    card can never show something the studio didn't offer the user to switch off.
 */

/** 4:5 — the aspect WhatsApp and Instagram both render without cropping. */
val ShareCardWidth = 360.dp
val ShareCardHeight = 450.dp

/**
 * The link preview (Open Graph) artboard. Rendered at density 2 it is exactly the
 * 1200×630 px that chat apps and social sites expect.
 */
val ShareCardLandscapeWidth = 600.dp
val ShareCardLandscapeHeight = 315.dp

/**
 * Draws a fixed artboard at [scale] and reports the scaled size to the layout, so a
 * preview takes only the room it shows. What the artboard draws is unchanged, so an
 * export recorded inside it stays full size.
 */
fun Modifier.scaleTo(scale: Float): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    val width = (placeable.width * scale).roundToInt()
    val height = (placeable.height * scale).roundToInt()
    layout(width, height) {
        placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
            scaleX = scale
            scaleY = scale
        }
    }
}

@Immutable
data class SharePalette(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val glowSecondary: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val good: Color,
    val bad: Color,
    val track: Color,
)

fun ShareTheme.palette(): SharePalette = when (this) {
    ShareTheme.MIDNIGHT -> SharePalette(
        top = Color(0xFF0B0E13), bottom = Color(0xFF141B26),
        glow = Color(0x40FFB74D), glowSecondary = Color(0x00000000),
        text = Color(0xFFEFF3F8), muted = Color(0xFF8D99AA),
        accent = Color(0xFFFFB74D), good = Color(0xFF7FE0A7), bad = Color(0xFFFF8F87),
        track = Color(0xFF222B37),
    )
    ShareTheme.AURORA -> SharePalette(
        top = Color(0xFF081A2B), bottom = Color(0xFF1C1140),
        glow = Color(0x5530D5C8), glowSecondary = Color(0x55A78BFA),
        text = Color(0xFFF2F5FF), muted = Color(0xFFA3AECB),
        accent = Color(0xFF5EEAD4), good = Color(0xFFA7F3D0), bad = Color(0xFFFDA4AF),
        track = Color(0xFF26304F),
    )
    ShareTheme.PAPER -> SharePalette(
        top = Color(0xFFF8F3EA), bottom = Color(0xFFEDE3D2),
        glow = Color(0x33E8590C), glowSecondary = Color(0x00000000),
        text = Color(0xFF1E1B17), muted = Color(0xFF6B6358),
        accent = Color(0xFFC2410C), good = Color(0xFF2E7A4E), bad = Color(0xFFB42330),
        track = Color(0xFFE0D5C2),
    )
}

private fun SharePalette.colorFor(sentiment: Sentiment): Color = when (sentiment) {
    Sentiment.GOOD -> good
    Sentiment.BAD -> bad
    Sentiment.NEUTRAL -> accent
}

// Headings take the rounded cut, figures the crisp one with tabular digits; both fall
// back per glyph (Arabic included) to the system font.
private val Figures = TextStyle(fontFamily = GoogleSansFlexFamily, fontFeatureSettings = "tnum")
private val Heading = TextStyle(fontFamily = GoogleSansFlexHeadingFamily)
private val Body = TextStyle(fontFamily = GoogleSansFlexFamily)

/** Background: a two-stop gradient lit by one or two soft glows, fixed per theme. */
private fun Modifier.cardBackground(p: SharePalette): Modifier = this
    .background(Brush.verticalGradient(listOf(p.top, p.bottom)))
    .drawBehind {
        drawCircle(
            brush = Brush.radialGradient(listOf(p.glow, Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.9f),
            radius = size.width * 0.9f,
            center = Offset(size.width, 0f),
        )
        if (p.glowSecondary.alpha > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(p.glowSecondary, Color.Transparent),
                    center = Offset(0f, size.height), radius = size.width * 0.8f,
                ),
                radius = size.width * 0.8f,
                center = Offset(0f, size.height),
            )
        }
    }

@Composable
fun ShareCard(content: ShareCardContent, theme: ShareTheme, modifier: Modifier = Modifier) {
    val p = theme.palette()
    if (content.kind == ShareKind.ACHIEVEMENTS) return AchievementsCard(content, p, modifier)
    if (content.kind == ShareKind.PROGRESS) return ProgressCard(content, p, modifier)
    val ringHero = content.chart?.type == ShareChartType.RING && content.hero != null
    Column(
        modifier = modifier
            .size(ShareCardWidth, ShareCardHeight)
            .cardBackground(p)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        BrandRow(p, content.periodLabel)
        Spacer(Modifier.height(20.dp))
        Text(
            text = content.title,
            style = Heading.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = p.text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        content.subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = Body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = p.muted),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (ringHero) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingHero(content.hero!!, content.chart!!, p, diameter = 188.dp, valueSize = 46.sp)
            }
        } else {
            // With a chart, every spare dp goes to it; without one, the hero sits mid-card.
            if (content.chart != null) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
            content.hero?.let {
                val maxSize = when {
                    content.chart == null -> 96.sp
                    content.stats.isEmpty() -> 76.sp
                    else -> 64.sp
                }
                HeroBlock(it, p, maxSize)
            }
            // The chart takes what the rest leaves, up to its own height, so a card with
            // every detail on still fits instead of pushing the stats into it.
            val chart = content.chart
            if (chart != null) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { ChartBlock(chart, p, Modifier.fillMaxWidth().heightIn(max = chartHeight(chart))) }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        if (content.stats.isNotEmpty()) {
            StatsRow(content.stats.take(3), p)
            Spacer(Modifier.height(16.dp))
        }
        Footer(p)
    }
}

/**
 * The same content laid out for a link preview: words on the start side, the chart on
 * the end side. Rendered at 600×315dp, density 2, for the uploaded 1200×630 image.
 */
@Composable
fun ShareCardLandscape(content: ShareCardContent, theme: ShareTheme, modifier: Modifier = Modifier) {
    val p = theme.palette()
    if (content.kind == ShareKind.ACHIEVEMENTS) return AchievementsLandscape(content, p, modifier)
    if (content.kind == ShareKind.PROGRESS) return ProgressLandscape(content, p, modifier)
    val chart = content.chart
    Row(
        modifier = modifier
            .size(ShareCardLandscapeWidth, ShareCardLandscapeHeight)
            .cardBackground(p)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(if (chart == null) 1f else 0.56f).fillMaxHeight()) {
            BrandRow(p, periodLabel = if (chart == null) content.periodLabel else null)
            Spacer(Modifier.height(14.dp))
            if (chart != null) content.periodLabel?.let { PeriodText(it, p) }
            Text(
                text = content.title,
                style = Heading.copy(fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = p.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content.subtitle?.let {
                Text(
                    text = it,
                    style = Body.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = p.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (chart?.type != ShareChartType.RING) content.hero?.let { HeroBlock(it, p, maxSize = 64.sp) }
            Spacer(Modifier.weight(1f))
            if (content.stats.isNotEmpty()) {
                StatsRow(content.stats.take(3), p, compact = true)
                Spacer(Modifier.height(12.dp))
            }
            Text(text = SHARE_CARD_URL, style = Heading.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text))
        }
        if (chart != null) {
            Box(Modifier.weight(0.44f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (chart.type == ShareChartType.RING && content.hero != null) {
                    RingHero(content.hero, chart, p, diameter = 210.dp, valueSize = 48.sp)
                } else {
                    ChartBlock(chart, p, Modifier.fillMaxWidth().height(chartHeight(chart, landscape = true)))
                }
            }
        }
    }
}

@Composable
private fun BrandRow(p: SharePalette, periodLabel: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // A raster copy of the launcher icon, NOT R.mipmap.ic_launcher: that resolves to
        // an <adaptive-icon> XML on API 26+, and painterResource only accepts
        // VectorDrawables or rasters — it throws at composition time, which a green build
        // will not tell you.
        Image(
            painter = painterResource(id = R.drawable.share_card_logo),
            contentDescription = null,
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "PENNYWISE",
            style = Heading.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = p.text),
        )
        Spacer(Modifier.weight(1f))
        periodLabel?.let { PeriodText(it, p) }
    }
}

@Composable
private fun PeriodText(label: String, p: SharePalette) {
    Text(
        text = label,
        style = Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = p.accent),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeroBlock(hero: ShareCardHero, p: SharePalette, maxSize: TextUnit) {
    Column {
        Text(
            text = hero.value,
            style = Heading.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
                color = p.colorFor(hero.sentiment),
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            // Deliberately enormous: at thumbnail scale this is the one element certain
            // to read. A long amount shrinks to fit rather than wrapping mid-number.
            autoSize = TextAutoSize.StepBased(minFontSize = 34.sp, maxFontSize = maxSize),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hero.label,
                style = Body.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = p.text.copy(alpha = 0.82f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            hero.delta?.let {
                Spacer(Modifier.width(10.dp))
                DeltaPill(it, p.colorFor(hero.sentiment))
            }
        }
    }
}

@Composable
private fun DeltaPill(text: String, color: Color) {
    Text(
        text = text,
        style = Figures.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun RingHero(hero: ShareCardHero, chart: ShareChart, p: SharePalette, diameter: Dp, valueSize: TextUnit) {
    val fraction = chart.points.firstOrNull()?.value?.toFloat()?.coerceIn(0f, 1f) ?: 0f
    val color = p.colorFor(hero.sentiment)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.085f
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(p.track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (fraction > 0f) {
                    drawArc(
                        color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = diameter * 0.16f),
            ) {
                Text(
                    text = hero.value,
                    style = Heading.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp, color = p.text, fontFeatureSettings = "tnum"),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = valueSize),
                )
                Text(
                    text = hero.label,
                    style = Body.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = p.muted, textAlign = TextAlign.Center),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        hero.delta?.let {
            Spacer(Modifier.height(10.dp))
            DeltaPill(it, color)
        }
    }
}

@Composable
private fun StatsRow(stats: List<ShareCardStat>, p: SharePalette, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { stat ->
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind {
                        // A short accent rule above each figure, on the reading-start side.
                        val w = 18.dp.toPx()
                        val x = if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f
                        drawRoundRect(p.track, Offset(x, 0f), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                    }
                    .padding(top = 9.dp),
            ) {
                Text(
                    text = stat.label,
                    style = Body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = p.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Shrinks rather than cuts: "₹12,34,5…" in three columns would be a wrong figure.
                Text(
                    text = stat.value,
                    style = Figures.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (stat.sentiment == Sentiment.NEUTRAL) p.text else p.colorFor(stat.sentiment),
                    ),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = if (compact) 15.sp else 17.sp),
                )
            }
        }
    }
}

@Composable
private fun Footer(p: SharePalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.share_card_tagline),
            style = Body.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = p.muted),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        // The only element that brings anyone back, so it gets a size and contrast that
        // survive the same reduction as the hero instead of muted grey.
        Text(text = SHARE_CARD_URL, style = Heading.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.text))
    }
}

private fun chartHeight(chart: ShareChart, landscape: Boolean = false): Dp = when (chart.type) {
    ShareChartType.LINE -> if (landscape) 200.dp else 112.dp
    ShareChartType.RING -> 120.dp
    ShareChartType.BARS, ShareChartType.DIVERGING ->
        (chart.points.take(MAX_BAR_ROWS).size * (if (landscape) 44 else 36)).dp
}

private const val MAX_BAR_ROWS = 4

@Composable
private fun ChartBlock(chart: ShareChart, p: SharePalette, modifier: Modifier) {
    when (chart.type) {
        ShareChartType.BARS -> BarsChart(chart, p, modifier)
        ShareChartType.DIVERGING -> DivergingChart(chart, p, modifier)
        ShareChartType.LINE -> LineChart(chart, p, modifier)
        // Without a hero to sit in it, a ring still says how full: draw it on its own.
        ShareChartType.RING -> Box(modifier, contentAlignment = Alignment.Center) {
            RingHero(ShareCardHero(label = "", value = chart.points.firstOrNull()?.display.orEmpty()), chart, p, 110.dp, 26.sp)
        }
    }
}

// Bar rows are one line of text tall, set explicitly: the app's body style would make
// each 24dp.
private val ChartRowHeight = 17.sp
private val ChartLabel = Body.copy(fontSize = 13.sp, lineHeight = ChartRowHeight, fontWeight = FontWeight.SemiBold)

/**
 * Chart rows stacked [gap] apart, as many as fit whole. A card with every detail on has
 * little height left for its chart, and Arabic lines run taller than Latin ones; a row
 * squeezed into what's left would be cut through its text, so it is left off instead.
 */
@Composable
private fun WholeRows(modifier: Modifier, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val row = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val placeables = mutableListOf<Placeable>()
        var height = 0
        for (measurable in measurables) {
            val placeable = measurable.measure(row)
            val next = if (placeables.isEmpty()) placeable.height else height + gapPx + placeable.height
            if (placeables.isNotEmpty() && next > constraints.maxHeight) break
            placeables += placeable
            height = next
        }
        val layoutHeight = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, layoutHeight) {
            var y = (layoutHeight - height) / 2
            placeables.forEach {
                it.place(0, y)
                y += it.height + gapPx
            }
        }
    }
}

/** One row per point: name and figure over a thick bar sized against the largest. */
@Composable
private fun BarsChart(chart: ShareChart, p: SharePalette, modifier: Modifier) {
    val points = chart.points.take(MAX_BAR_ROWS)
    val max = points.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    val valueStyle = Figures.copy(fontSize = 13.sp, lineHeight = ChartRowHeight, fontWeight = FontWeight.Bold, color = p.text, textAlign = TextAlign.End)
    val valueWidth = valueColumnWidth(points, valueStyle)
    WholeRows(modifier) {
        points.forEachIndexed { i, point ->
            val highlighted = i == (chart.highlightIndex ?: 0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = point.label,
                    style = ChartLabel.copy(color = p.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(104.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(p.track),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((point.value / max).toFloat().coerceIn(0.03f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(if (highlighted) p.accent else p.accent.copy(alpha = 0.55f)),
                    )
                }
                point.display?.let { ChartValue(it, valueStyle, Modifier.padding(start = 10.dp).width(valueWidth)) }
            }
        }
    }
}

/**
 * Change either side of a centre line: growth toward the end edge in the accent, a drop
 * toward the start edge in the "good" colour. Rows mirror on their own in RTL.
 */
@Composable
private fun DivergingChart(chart: ShareChart, p: SharePalette, modifier: Modifier) {
    val points = chart.points.take(MAX_BAR_ROWS)
    val max = points.maxOfOrNull { abs(it.value) }?.takeIf { it > 0 } ?: 1.0
    val valueStyle = Figures.copy(fontSize = 13.sp, lineHeight = ChartRowHeight, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    val valueWidth = valueColumnWidth(points, valueStyle)
    WholeRows(modifier) {
        points.forEach { point ->
            val fraction = (abs(point.value) / max).toFloat().coerceIn(0.04f, 1f)
            val positive = point.value >= 0
            val color = if (positive) p.accent else p.good
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = point.label,
                    style = ChartLabel.copy(color = p.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(98.dp),
                )
                Row(Modifier.weight(1f).height(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                        if (!positive) Bar(fraction, color)
                    }
                    Box(Modifier.width(2.dp).height(20.dp).background(p.muted.copy(alpha = 0.5f)))
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        if (positive) Bar(fraction, color)
                    }
                }
                point.display?.let {
                    ChartValue(it, valueStyle.copy(color = color), Modifier.padding(start = 8.dp).width(valueWidth))
                }
            }
        }
    }
}

/**
 * One width for every row's figure, so the bars all end on the same line: as wide as the
 * longest figure, up to [MaxValueWidth]. A figure is never cut ("+₹1,23,4"): past that
 * width [ChartValue] shrinks it instead.
 */
@Composable
private fun valueColumnWidth(points: List<ShareChartPoint>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val widest = points.maxOfOrNull { point ->
        point.display?.let { measurer.measure(it, style, maxLines = 1).size.width } ?: 0
    } ?: 0
    // A dp over, so rounding never makes an exact fit step the font down.
    return with(LocalDensity.current) { widest.toDp() + 1.dp }.coerceAtMost(MaxValueWidth)
}

private val MaxValueWidth = 104.dp

@Composable
private fun ChartValue(text: String, style: TextStyle, modifier: Modifier) {
    Text(
        text = text,
        style = style,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

@Composable
private fun BoxScope.Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

/**
 * Oldest to newest along the reading direction (mirrored in RTL), with the area under
 * the line washed in the accent. Below-zero values get a dashed zero line, so a period
 * that spent beyond its income reads as under it rather than as a low point.
 */
@Composable
private fun LineChart(chart: ShareChart, p: SharePalette, modifier: Modifier) {
    val points = chart.points
    if (points.size < 2) return
    val measurer = rememberTextMeasurer()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val highlight = chart.highlightIndex ?: points.lastIndex
    val valueStyle = Figures.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.text)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val top = 22.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            // Scaled to the data, so a move from 50% to 43% reads as a move; a dip below
            // zero keeps zero in view.
            val lowest = points.minOf { it.value }
            val highest = points.maxOf { it.value }
            val low = if (lowest < 0) lowest else lowest - max(highest - lowest, 0.1) * 0.8
            val high = highest
            val span = (high - low).takeIf { it > 0 } ?: 1.0
            val cell = size.width / points.size
            fun x(i: Int) = if (rtl) size.width - cell * (i + 0.5f) else cell * (i + 0.5f)
            fun y(v: Double) = (bottom - (v - low) / span * (bottom - top)).toFloat()
            val line = Path().apply {
                points.forEachIndexed { i, point -> if (i == 0) moveTo(x(i), y(point.value)) else lineTo(x(i), y(point.value)) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(points.lastIndex), bottom)
                lineTo(x(0), bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.32f), Color.Transparent), startY = top, endY = bottom))
            if (low < 0) {
                drawLine(
                    p.muted.copy(alpha = 0.6f), Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
            }
            drawPath(line, p.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, point ->
                val center = Offset(x(i), y(point.value))
                if (i == highlight) {
                    drawCircle(p.accent.copy(alpha = 0.25f), 10.dp.toPx(), center)
                    drawCircle(p.accent, 6.dp.toPx(), center)
                } else {
                    drawCircle(p.bottom, 4.5.dp.toPx(), center)
                    drawCircle(p.accent, 4.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                }
            }
            points[highlight].display?.let { text ->
                val layout = measurer.measure(text, valueStyle)
                val center = Offset(x(highlight), y(points[highlight].value))
                val left = (center.x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(left, max(0f, center.y - layout.size.height - 12.dp.toPx())))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEachIndexed { i, point ->
                Text(
                    text = point.label,
                    style = Body.copy(
                        fontSize = 11.sp,
                        fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Medium,
                        color = if (i == highlight) p.text else p.muted,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// --- Achievements. One is a medal under a glow in its own hue; several are a set of
// --- badges under how many there are. Badges keep their hues on every theme: each one
// --- carries its own background (see AchievementBadge).

@Composable
private fun AchievementsCard(content: ShareCardContent, p: SharePalette, modifier: Modifier) {
    val single = content.achievements.singleOrNull()
    Column(
        modifier = modifier
            .size(ShareCardWidth, ShareCardHeight)
            .cardBackground(p)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = if (single != null) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        BrandRow(p, content.periodLabel)
        if (single != null) {
            Spacer(Modifier.weight(1f))
            GlowingBadge(single, 156.dp)
            Spacer(Modifier.height(22.dp))
            AchievementWords(content, single, p, nameSize = 34.sp, center = true)
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(20.dp))
            Text(
                text = content.title,
                style = Heading.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = p.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            content.hero?.let { CountHero(it, p, 64.sp) }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                BadgeGrid(content.achievements, p, portraitGrid(content.achievements.size))
            }
        }
        Footer(p)
    }
}

@Composable
private fun AchievementsLandscape(content: ShareCardContent, p: SharePalette, modifier: Modifier) {
    val single = content.achievements.singleOrNull()
    Row(
        modifier = modifier
            .size(ShareCardLandscapeWidth, ShareCardLandscapeHeight)
            .cardBackground(p)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(0.56f).fillMaxHeight()) {
            BrandRow(p, periodLabel = content.periodLabel)
            Spacer(Modifier.weight(1f))
            if (single != null) {
                AchievementWords(content, single, p, nameSize = 32.sp, center = false)
            } else {
                Text(
                    text = content.title,
                    style = Heading.copy(fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = p.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                content.hero?.let { CountHero(it, p, 72.sp) }
            }
            Spacer(Modifier.weight(1f))
            Text(text = SHARE_CARD_URL, style = Heading.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text))
        }
        Box(Modifier.weight(0.44f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (single != null) GlowingBadge(single, 176.dp)
            else BadgeGrid(content.achievements, p, landscapeGrid(content.achievements.size))
        }
    }
}

/** The kicker, the achievement's name, what it took and its tier. */
@Composable
private fun AchievementWords(content: ShareCardContent, a: ShareAchievement, p: SharePalette, nameSize: TextUnit, center: Boolean) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = content.title,
            style = Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = p.accent, textAlign = align),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = a.title,
            style = Heading.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, color = p.text, textAlign = align),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = nameSize),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = a.description,
            style = Body.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = p.text.copy(alpha = 0.82f), textAlign = align),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        content.subtitle?.let {
            Spacer(Modifier.height(12.dp))
            DeltaPill(it, p.accent)
        }
    }
}

/** A medal lit from behind in its own hue. */
@Composable
private fun GlowingBadge(a: ShareAchievement, size: Dp) {
    val id = AchievementId.fromKey(a.id)
    val hue = badgeArt(id).hue
    AchievementBadge(
        id = id,
        tier = a.tier,
        size = size,
        modifier = Modifier.drawBehind {
            val radius = this.size.width * 0.9f
            drawCircle(Brush.radialGradient(listOf(hue.copy(alpha = 0.45f), Color.Transparent), center, radius), radius, center)
        },
    )
}

/** How a set of badges is laid out: [columns] per row, each [badge] across. */
private data class GridShape(val columns: Int, val badge: Dp)

// Rows as even as the count allows (five over four, not seven over two), and the badges
// smaller as they fill: a portrait card has room for two rows under the count.
private fun portraitGrid(n: Int): GridShape {
    val columns = if (n <= 3) n else (n + 1) / 2
    return GridShape(columns, if (columns <= 2) 80.dp else if (columns == 3) 70.dp else if (columns == 4) 58.dp else 48.dp)
}

private fun landscapeGrid(n: Int): GridShape {
    val columns = if (n <= 3) n else if (n == 4) 2 else 3
    return GridShape(columns, if (columns <= 2) 72.dp else if (n <= 6) 58.dp else 46.dp)
}

/** Badges in rows of [GridShape.columns], a short last row centred under the others. */
@Composable
private fun BadgeGrid(items: List<ShareAchievement>, p: SharePalette, shape: GridShape) {
    BoxWithConstraints {
        val cell = maxWidth / shape.columns
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(shape.columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    row.forEach { a ->
                        Column(Modifier.width(cell).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AchievementBadge(AchievementId.fromKey(a.id), a.tier, shape.badge)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = a.title,
                                style = Body.copy(
                                    fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = p.text, textAlign = TextAlign.Center,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The count beside its words: as big a figure as the recap's, in less height, so the badges get the room. */
@Composable
private fun CountHero(hero: ShareCardHero, p: SharePalette, size: TextUnit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = hero.value,
            style = Heading.copy(
                fontSize = size, lineHeight = size, fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp, color = p.accent, fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = hero.label,
            style = Body.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = p.text.copy(alpha = 0.82f)),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Progress. Badges in one strip, then a row per goal, savings and budget: one
// --- figure each over a thin bar, so five sections still read at thumbnail size.

@Composable
private fun ProgressCard(content: ShareCardContent, p: SharePalette, modifier: Modifier) {
    Column(
        modifier = modifier
            .size(ShareCardWidth, ShareCardHeight)
            .cardBackground(p)
            .padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        BrandRow(p, content.periodLabel)
        Spacer(Modifier.height(20.dp))
        Text(
            text = content.title,
            style = Heading.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = p.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ProgressBody(content, p, Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp))
        Footer(p)
    }
}

@Composable
private fun ProgressLandscape(content: ShareCardContent, p: SharePalette, modifier: Modifier) {
    Row(
        modifier = modifier
            .size(ShareCardLandscapeWidth, ShareCardLandscapeHeight)
            .cardBackground(p)
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(0.4f).fillMaxHeight()) {
            BrandRow(p, periodLabel = null)
            Spacer(Modifier.weight(1f))
            content.periodLabel?.let { PeriodText(it, p) }
            Text(
                text = content.title,
                style = Heading.copy(fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = p.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(text = SHARE_CARD_URL, style = Heading.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.text))
        }
        ProgressBody(content, p, Modifier.weight(0.6f).fillMaxHeight())
    }
}

/**
 * The badges and rows, centred in what's left. Badges alone get the full grid; next to
 * rows they share one strip, with "+N" for any that don't fit, never silently fewer.
 */
@Composable
private fun ProgressBody(content: ShareCardContent, p: SharePalette, modifier: Modifier) {
    val badges = content.achievements
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        if (content.rows.isEmpty()) {
            if (badges.isNotEmpty()) BadgeGrid(badges, p, portraitGrid(badges.size))
        } else {
            // Bigger badges while there's room; both sizes fill the same width (5×56, 6×44).
            val roomy = content.rows.size <= 2
            if (badges.isNotEmpty()) BadgeStrip(badges, p, max = if (roomy) 5 else 6, size = if (roomy) 56.dp else 44.dp)
            content.rows.forEach { ProgressRow(it, p) }
        }
    }
}

@Composable
private fun BadgeStrip(badges: List<ShareAchievement>, p: SharePalette, max: Int, size: Dp) {
    val shown = if (badges.size <= max) badges else badges.take(max - 1)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { AchievementBadge(AchievementId.fromKey(it.id), it.tier, size) }
        if (shown.size < badges.size) {
            Text(
                text = "+${badges.size - shown.size}",
                style = Figures.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = p.muted),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun ProgressRow(row: ShareCardRow, p: SharePalette) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.label,
                style = Body.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = p.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            // Shrinks rather than cuts, like every other figure on the card.
            Text(
                text = row.value,
                style = Figures.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (row.sentiment == Sentiment.NEUTRAL) p.text else p.colorFor(row.sentiment),
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 17.sp),
                // Measured first, so the label takes what's left and the figure ends the row.
                modifier = Modifier.widthIn(max = 168.dp),
            )
        }
        row.note?.let {
            Text(
                text = it,
                style = Body.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = p.muted),
                maxLines = 1,
            )
        }
        row.fraction?.let { fraction ->
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(p.track),
            ) {
                if (fraction > 0) Bar(fraction.toFloat().coerceIn(0.03f, 1f), p.colorFor(row.sentiment))
            }
        }
    }
}

// --- Previews. The thumbnail one is the size that matters: if it fails there it fails in
// --- a chat, however good it looks in the app.

internal val previewSpending = ShareCardContent(
    kind = ShareKind.SPENDING,
    title = "My spending",
    subtitle = "Biggest changes: Travel, Food & Dining",
    periodLabel = "OCTOBER 2026",
    hero = ShareCardHero(label = "more than September", value = "+12%", delta = "+₹4,120"),
    stats = listOf(ShareCardStat("Spent", "₹38,420"), ShareCardStat("September", "₹34,300")),
    chart = ShareChart(
        ShareChartType.DIVERGING,
        listOf(
            ShareChartPoint("Travel", 3200.0, "+₹3,200"),
            ShareChartPoint("Food & Dining", 1400.0, "+₹1,400"),
            ShareChartPoint("Shopping", -900.0, "−₹900"),
        ),
    ),
)

internal val previewSavings = ShareCardContent(
    kind = ShareKind.SAVINGS,
    title = "My savings rate",
    subtitle = "Up from September",
    periodLabel = "OCTOBER 2026",
    hero = ShareCardHero(label = "of income saved", value = "24%", delta = "+6 pts", sentiment = Sentiment.GOOD),
    stats = emptyList(),
    chart = ShareChart(
        ShareChartType.LINE,
        listOf("May" to 12.0, "Jun" to 9.0, "Jul" to 15.0, "Aug" to -4.0, "Sep" to 18.0, "Oct" to 24.0)
            .map { (m, v) -> ShareChartPoint(m, v, "${v.toInt()}%") },
        highlightIndex = 5,
    ),
)

internal val previewGoal = ShareCardContent(
    kind = ShareKind.GOAL,
    title = "Emergency fund",
    periodLabel = "BY MAR 2027",
    hero = ShareCardHero(label = "saved", value = "68%"),
    stats = listOf(ShareCardStat("Pace", "On track", Sentiment.GOOD)),
    chart = ShareChart(ShareChartType.RING, listOf(ShareChartPoint("", 0.68, "68%"))),
)

internal val previewRecap = ShareCardContent(
    kind = ShareKind.RECAP,
    title = "My month, tracked",
    periodLabel = "OCTOBER 2026",
    hero = ShareCardHero(label = "transactions tracked", value = "312"),
    stats = listOf(ShareCardStat("Change", "+12%")),
    chart = ShareChart(
        ShareChartType.BARS,
        listOf(
            ShareChartPoint("Food & Dining", 40.0, "40%"),
            ShareChartPoint("Shopping", 22.0, "22%"),
            ShareChartPoint("Transport", 14.0, "14%"),
        ),
        highlightIndex = 0,
    ),
)

@Preview(name = "Spending", widthDp = 400, heightDp = 490)
@Composable
private fun ShareCardSpendingPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewSpending, ShareTheme.MIDNIGHT) }
}

@Preview(name = "Savings", widthDp = 400, heightDp = 490)
@Composable
private fun ShareCardSavingsPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewSavings, ShareTheme.AURORA) }
}

@Preview(name = "Goal", widthDp = 400, heightDp = 490)
@Composable
private fun ShareCardGoalPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewGoal, ShareTheme.PAPER) }
}

@Preview(name = "Recap, Arabic", widthDp = 400, heightDp = 490, locale = "ar")
@Composable
private fun ShareCardRecapRtlPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewRecap, ShareTheme.MIDNIGHT) }
}

/** Chat-thumbnail scale — the size this card is genuinely read at. */
@Preview(name = "Thumbnail (chat size)", widthDp = 130, heightDp = 165)
@Composable
private fun ShareCardThumbnailPreview() {
    Box(Modifier.background(Color(0xFF303030))) {
        ShareCard(previewSpending, ShareTheme.MIDNIGHT, Modifier.scaleTo(130.dp / ShareCardWidth))
    }
}

/** The 1200×630 link-preview image, at the size it is rendered for upload. */
@Preview(name = "Link preview (savings)", widthDp = 600, heightDp = 315)
@Composable
private fun ShareCardLandscapeSavingsPreview() {
    ShareCardLandscape(previewSavings, ShareTheme.AURORA)
}

internal val previewAchievement = ShareCardContent(
    kind = ShareKind.ACHIEVEMENTS,
    title = "Achievement unlocked",
    subtitle = "Tier 3 of 4",
    periodLabel = "14 OCT 2026",
    achievements = listOf(ShareAchievement("transactions_tracked", "Bookkeeper", "1,000 transactions tracked", 3)),
)

internal val previewAchievements = ShareCardContent(
    kind = ShareKind.ACHIEVEMENTS,
    title = "My achievements",
    hero = ShareCardHero(label = "achievements earned", value = "6"),
    achievements = listOf(
        ShareAchievement("savings_rate", "Saver", "Kept 30% of a month's income", 2),
        ShareAchievement("tracking_streak", "Steady habit", "Tracked 6 months in a row", 2),
        ShareAchievement("goals_reached", "Goal getter", "Reached 1 goal", 1),
        ShareAchievement("budget_kept", "Within limits", "3 months in a row under every spending limit", 2),
        ShareAchievement("spending_down", "Trimmed", "Spent 10% less than the month before", 1),
        ShareAchievement("transactions_tracked", "Bookkeeper", "500 transactions tracked", 2),
    ),
)

@Preview(name = "Achievement", widthDp = 400, heightDp = 490)
@Composable
private fun ShareCardAchievementPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewAchievement, ShareTheme.MIDNIGHT) }
}

@Preview(name = "Achievements", widthDp = 400, heightDp = 490)
@Composable
private fun ShareCardAchievementsPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewAchievements, ShareTheme.PAPER) }
}

@Preview(name = "Link preview (achievements)", widthDp = 600, heightDp = 315)
@Composable
private fun ShareCardLandscapeAchievementsPreview() {
    ShareCardLandscape(previewAchievements, ShareTheme.AURORA)
}

@Preview(name = "Link preview (goal)", widthDp = 600, heightDp = 315)
@Composable
private fun ShareCardLandscapeGoalPreview() {
    ShareCardLandscape(previewGoal, ShareTheme.MIDNIGHT)
}

internal val previewProgress = ShareCardContent(
    kind = ShareKind.PROGRESS,
    title = "My progress",
    periodLabel = "OCTOBER 2026",
    achievements = previewAchievements.achievements + ShareAchievement("goal_halfway", "Halfway there", "", 1),
    rows = listOf(
        ShareCardRow("New laptop", "64%", 0.64),
        ShareCardRow("Emergency fund", "100%", 1.0, Sentiment.GOOD),
        ShareCardRow("Trip", "12%", 0.12),
        ShareCardRow("Saved of income", "23%", 0.23, Sentiment.GOOD),
        ShareCardRow("Budget used", "81%", 0.81, Sentiment.GOOD),
    ),
)

@Preview(name = "Progress", widthDp = 400, heightDp = 490)
@Composable
private fun ShareCardProgressPreview() {
    Box(Modifier.background(Color(0xFF303030)).padding(20.dp)) { ShareCard(previewProgress, ShareTheme.MIDNIGHT) }
}

@Preview(name = "Link preview (progress)", widthDp = 600, heightDp = 315)
@Composable
private fun ShareCardLandscapeProgressPreview() {
    ShareCardLandscape(previewProgress, ShareTheme.PAPER)
}
