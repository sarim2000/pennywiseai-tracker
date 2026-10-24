package com.pennywiseai.tracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Home-screen widget with this cycle's spending pie (#665) — the analytics
 * category donut at a glance. The chart itself is rendered to a bitmap
 * (Glance can't draw arcs); slice colors come pre-resolved from the worker so
 * the donut matches the in-app pie.
 */
class CategoryPieWidget : GlanceAppWidget() {

    // The donut is sized to the space it gets.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = try {
            withTimeoutOrNull(5000L) {
                CategoryPieWidgetDataStore.getData(context).first()
            } ?: CategoryPieWidgetData()
        } catch (e: Exception) {
            android.util.Log.e("CategoryPieWidget", "Failed to load widget data", e)
            CategoryPieWidgetData()
        }

        val privacy = WidgetPrivacy.load(context)
        provideContent {
            // A session still alive when the worker writes recomposes from
            // here; provideGlance doesn't run again for it.
            val data by remember { CategoryPieWidgetDataStore.getData(context) }.collectAsState(initial)
            WidgetRoot(privacy) {
                PieWidgetContent(data)
            }
        }
    }

    @Composable
    private fun PieWidgetContent(data: CategoryPieWidgetData) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(WidgetDefaults.padding)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            val month = runCatching { LocalDate.parse(data.cycleStart) }.getOrNull()
                ?.format(DateTimeFormatter.ofPattern("MMM", context.appLocale))
            WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                item { WidgetTitle(context.getString(R.string.widget_pie_title), GlanceModifier.defaultWeight()) }
                if (month != null) item { WidgetLabel(month) }
            }
            Spacer(modifier = GlanceModifier.height(HEADER_GAP))

            if (data.slices.isEmpty()) {
                WidgetEmptyState(
                    title = context.getString(R.string.widget_pie_empty),
                    hint = context.getString(R.string.widget_pie_empty_hint)
                )
            } else {
                Chart(data)
            }
        }
    }

    @Composable
    private fun Chart(data: CategoryPieWidgetData) {
        val context = LocalContext.current
        val size = LocalSize.current
        val height = size.height - WidgetDefaults.padding * 2 - HEADER - HEADER_GAP
        val donut = minOf(height, size.width * DONUT_WIDTH_SHARE).coerceAtLeast(Spacing.xl)
        val legendRows = (height.value / LEGEND_ROW.value).toInt().coerceAtLeast(1)
        val total = data.total.toBigDecimalOrNull()

        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                item {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            provider = ImageProvider(renderDonut(context, data.slices, donut) { if (isLowContrast(it)) null else it }),
                            contentDescription = context.getString(R.string.widget_pie_content_description),
                            modifier = GlanceModifier.size(donut)
                        )
                        // Slices too dark (or light) for one of the themes are drawn white and
                        // tinted by the launcher, which knows whether it's day or night.
                        if (data.slices.any { isLowContrast(it.colorArgb.toInt()) }) {
                            Image(
                                provider = ImageProvider(
                                    renderDonut(context, data.slices, donut) { if (isLowContrast(it)) android.graphics.Color.WHITE else null }
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                                modifier = GlanceModifier.size(donut)
                            )
                        }
                        if (total != null && donut >= DONUT_WITH_TOTAL) {
                            Text(
                                text = totalText(total, data.currency),
                                maxLines = 1,
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = Typography.labelLarge.fontSize,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                item { Spacer(modifier = GlanceModifier.width(Spacing.md)) }
                item {
                    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = startAlignment()) {
                        data.slices.take(legendRows).forEach { LegendRow(it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun LegendRow(slice: CategoryPieSlice) {
        val context = LocalContext.current
        val argb = slice.colorArgb.toInt()
        WidgetRow(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Box(
                    modifier = GlanceModifier
                        .size(Dimensions.Component.legendDot)
                        .cornerRadius(Dimensions.Component.legendDot / 2)
                        .background(if (isLowContrast(argb)) GlanceTheme.colors.onSurface else ColorProvider(Color(argb)))
                ) {}
            }
            item { Spacer(modifier = GlanceModifier.width(Spacing.sm)) }
            item {
                Text(
                    text = slice.name,
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = Typography.bodySmall.fontSize,
                        textAlign = startTextAlign()
                    )
                )
            }
            item { Spacer(modifier = GlanceModifier.width(Spacing.sm)) }
            item {
                Text(
                    text = context.latinString(R.string.widget_pie_percent, slice.percent.roundToInt()),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = Typography.bodySmall.fontSize,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }

    /**
     * Draws the slices [color] gives a color for (others leave their gap) on a
     * transparent bitmap, so the widget's own background shows through; the
     * hole hosts the total, drawn by Glance so it stays real text.
     */
    private fun renderDonut(context: Context, slices: List<CategoryPieSlice>, size: Dp, color: (Int) -> Int?): Bitmap {
        val sizePx = (size.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val strokePx = sizePx * DONUT_STROKE_SHARE
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.BUTT
        }
        val inset = strokePx / 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

        val totalPercent = slices.sumOf { it.percent.toDouble() }.toFloat().takeIf { it > 0f } ?: 100f
        var startAngle = -90f
        // A hairline gap between slices, skipped when a slice is too thin to
        // survive it.
        val gapDegrees = 2f
        slices.forEach { slice ->
            val sweep = (slice.percent / totalPercent) * 360f
            if (sweep <= 0f) return@forEach
            color(slice.colorArgb.toInt())?.let {
                val gap = if (sweep > gapDegrees * 2) gapDegrees else 0f
                paint.color = it
                canvas.drawArc(rect, startAngle + gap / 2f, sweep - gap, false, paint)
            }
            startAngle += sweep
        }
        return bitmap
    }

    private companion object {
        /** WidgetTitle's line, for sizing what's under it. */
        val HEADER = Spacing.lg
        val HEADER_GAP = Spacing.sm
        val LEGEND_ROW = Spacing.md + Spacing.xs
        /** Below this the hole is too small for "JD 1,241"; the legend still has the split. */
        val DONUT_WITH_TOTAL = Dimensions.Icon.extraLarge
        const val DONUT_WIDTH_SHARE = 0.4f
        const val DONUT_STROKE_SHARE = 0.14f
    }
}

/**
 * A category color that one of the themes' backgrounds would swallow: near
 * black vanishes at night, near white by day. Such slices follow the text color.
 */
internal fun isLowContrast(argb: Int): Boolean = Color(argb).luminance().let { it < 0.04f || it > 0.85f }
