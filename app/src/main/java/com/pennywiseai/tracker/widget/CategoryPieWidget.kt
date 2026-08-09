package com.pennywiseai.tracker.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home-screen widget with this cycle's spending pie (#665) — the analytics
 * category donut at a glance. The chart itself is rendered to a bitmap
 * (Glance can't draw arcs); slice colors come pre-resolved from the worker so
 * the donut matches the in-app pie exactly, in both themes.
 */
class CategoryPieWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            withTimeoutOrNull(5000L) {
                CategoryPieWidgetDataStore.getData(context).first()
            } ?: CategoryPieWidgetData()
        } catch (e: Exception) {
            android.util.Log.e("CategoryPieWidget", "Failed to load widget data", e)
            CategoryPieWidgetData()
        }

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    GlanceTheme.colors
                else
                    PennyWiseWidgetTheme.colors
            ) {
                PieWidgetContent(data)
            }
        }
    }

    @Composable
    private fun PieWidgetContent(data: CategoryPieWidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(14.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "Spending",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = data.monthLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (data.slices.isEmpty()) {
                EmptyPie()
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            provider = ImageProvider(renderDonut(data.slices)),
                            contentDescription = "Spending by category",
                            modifier = GlanceModifier.size(110.dp)
                        )
                        Text(
                            text = data.totalFormatted,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(14.dp))

                    Column(
                        modifier = GlanceModifier.fillMaxHeight().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        data.slices.forEach { slice ->
                            LegendRow(slice)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LegendRow(slice: CategoryPieSlice) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .cornerRadius(4.dp)
                    .background(ColorProvider(Color(slice.colorArgb.toInt())))
            ) {}
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = slice.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "${slice.percent.toInt()}%",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun EmptyPie() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No spending yet this month",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }

    /**
     * Draws the donut off-screen. The bitmap has a transparent background so
     * the widget's own (theme-aware) background shows through; the ring hole
     * hosts the total, drawn by Glance so it stays a real, scalable text.
     */
    private fun renderDonut(slices: List<CategoryPieSlice>): Bitmap {
        val sizePx = 330
        val strokePx = 60f
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.BUTT
        }
        val inset = strokePx / 2f + 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

        val totalPercent = slices.sumOf { it.percent.toDouble() }.toFloat().takeIf { it > 0f } ?: 100f
        var startAngle = -90f
        // A hairline gap between slices, skipped when a slice is too thin to
        // survive it.
        val gapDegrees = 2f
        slices.forEach { slice ->
            val sweep = (slice.percent / totalPercent) * 360f
            if (sweep <= 0f) return@forEach
            val gap = if (sweep > gapDegrees * 2) gapDegrees else 0f
            paint.color = slice.colorArgb.toInt()
            canvas.drawArc(rect, startAngle + gap / 2f, sweep - gap, false, paint)
            startAngle += sweep
        }
        return bitmap
    }
}
