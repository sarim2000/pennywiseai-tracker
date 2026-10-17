package com.pennywiseai.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.components.toColorOr
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Glance state key: the goal a 2×2 widget shows, set by [GoalWidgetConfigActivity]. */
internal val GOAL_WIDGET_GOAL_ID = longPreferencesKey("goal_id")

private suspend fun loadGoals(context: Context): List<GoalWidgetItem> = try {
    withTimeoutOrNull(5000L) { GoalWidgetDataStore.getData(context).first() }.orEmpty()
} catch (e: Exception) {
    android.util.Log.e("GoalWidget", "Failed to load widget data", e)
    emptyList()
}

/**
 * The snapshot as state: a Glance session that is still alive when the worker
 * writes a new one recomposes from here; provideGlance doesn't run again for it.
 */
@Composable
private fun observeGoals(context: Context, initial: List<GoalWidgetItem>): State<List<GoalWidgetItem>> =
    remember { GoalWidgetDataStore.getData(context) }.collectAsState(initial)

@Composable
private fun WidgetTheme(content: @Composable () -> Unit) = GlanceTheme(
    colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else PennyWiseWidgetTheme.colors,
    content = content,
)

/**
 * Opens the app on a goal, the Budgets screen or "New goal" (see
 * [MainActivity.EXTRA_OPEN_GOAL_ID]). The data URI only keeps each row's
 * PendingIntent distinct; extras alone don't.
 */
private fun openApp(context: Context, key: String, extras: Intent.() -> Unit): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "pennywise-widget://$key".toUri()
        extras()
    }
)

private fun openGoal(context: Context, goalId: Long) =
    openApp(context, "goal/$goalId") { putExtra(MainActivity.EXTRA_OPEN_GOAL_ID, goalId) }

private fun openBudgets(context: Context, createGoal: Boolean = false) =
    openApp(context, if (createGoal) "budgets/new-goal" else "budgets") {
        putExtra(MainActivity.EXTRA_OPEN_BUDGETS, true)
        putExtra(MainActivity.EXTRA_CREATE_GOAL, createGoal)
    }

/**
 * The goal's ring, like the in-app [com.pennywiseai.tracker.presentation.goals.GoalRing].
 * Glance can't draw arcs, so both layers are bitmaps; the track is drawn white and
 * tinted by the launcher, so it follows day/night even when this process hasn't.
 */
@Composable
private fun GoalRingImage(item: GoalWidgetItem, size: Dp, stroke: Dp) {
    val context = LocalContext.current
    val color = item.color.toColorOr(GlanceTheme.colors.primary.getColor(context)).toArgb()
    Box(modifier = GlanceModifier.size(size)) {
        Image(
            provider = ImageProvider(renderArc(context, 1f, android.graphics.Color.WHITE, size, stroke)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
            modifier = GlanceModifier.size(size),
        )
        Image(
            provider = ImageProvider(renderArc(context, item.fraction, color, size, stroke)),
            contentDescription = context.getString(R.string.goals_ring_description, item.percent),
            modifier = GlanceModifier.size(size),
        )
    }
}

/** An arc from 12 o'clock covering [fraction] of a ring, on a transparent bitmap. */
private fun renderArc(context: Context, fraction: Float, color: Int, size: Dp, stroke: Dp): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).roundToInt().coerceAtLeast(1)
    val strokePx = stroke.value * density
    val bitmap = createBitmap(px, px)
    if (fraction <= 0f) return bitmap
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        this.color = color
    }
    val inset = strokePx / 2f
    Canvas(bitmap).drawArc(RectF(inset, inset, px - inset, px - inset), -90f, 360f * fraction.coerceAtMost(1f), false, paint)
    return bitmap
}

@Composable
private fun TwoLineMessage(title: String, hint: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = Typography.titleSmall.fontSize, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
        Spacer(GlanceModifier.height(Spacing.xxs))
        Text(
            text = hint,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = Typography.bodySmall.fontSize, textAlign = TextAlign.Center),
        )
    }
}

/** 2×2: one chosen goal — ring with %, name, and "₹9,600 left · 5 mo". */
class GoalWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadGoals(context)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            val items by observeGoals(context, initial)
            val chosenId = currentState<Preferences>()[GOAL_WIDGET_GOAL_ID]
            WidgetTheme { Content(singleGoalState(items, chosenId), appWidgetId) }
        }
    }

    @Composable
    private fun Content(state: SingleGoalState, appWidgetId: Int) {
        val context = LocalContext.current
        val tap = when (state) {
            is SingleGoalState.Shown -> openGoal(context, state.item.id)
            SingleGoalState.Removed -> actionStartActivity(
                Intent(context, GoalWidgetConfigActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    data = "pennywise-widget://configure/$appWidgetId".toUri()
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
            )
            SingleGoalState.NoGoals -> openBudgets(context, createGoal = true)
        }
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(Spacing.smd)
                .clickable(tap),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is SingleGoalState.Shown -> Goal(state.item)
                SingleGoalState.Removed -> TwoLineMessage(
                    context.getString(R.string.widget_goal_removed), context.getString(R.string.widget_goal_removed_hint)
                )
                SingleGoalState.NoGoals -> TwoLineMessage(
                    context.getString(R.string.widget_goals_empty), context.getString(R.string.widget_goals_empty_hint)
                )
            }
        }
    }

    @Composable
    private fun Goal(item: GoalWidgetItem) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                GoalRingImage(item, Dimensions.Component.goalRing, Dimensions.Component.goalRingStroke)
                Text(
                    text = context.getString(R.string.budgets_percent, item.percent),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = Typography.labelMedium.fontSize, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.height(Spacing.sm))
            Text(
                text = item.name,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = Typography.titleSmall.fontSize, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
            Text(
                text = item.shortLine,
                maxLines = 1,
                style = TextStyle(color = PennyWiseWidgetTheme.goalToneColor(item.tone), fontSize = Typography.bodySmall.fontSize, textAlign = TextAlign.Center),
            )
        }
    }
}

/** 4×2: up to three goals, soonest due first (Reached last), with a "+" for "New goal". */
class GoalsListWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadGoals(context)
        provideContent {
            val items by observeGoals(context, initial)
            WidgetTheme { Content(items) }
        }
    }

    @Composable
    private fun Content(items: List<GoalWidgetItem>) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .clickable(openBudgets(context)),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.getString(R.string.goals_section_title),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = Typography.titleSmall.fontSize, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.defaultWeight(),
                )
                // A 48dp touch target around the smaller visible button.
                Box(
                    modifier = GlanceModifier
                        .size(Dimensions.Component.minTouchTarget)
                        .clickable(openBudgets(context, createGoal = true)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(ADD_BUTTON)
                            .cornerRadius(ADD_BUTTON / 2)
                            .background(GlanceTheme.colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_widget_add),
                            contentDescription = context.getString(R.string.goals_new),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                            modifier = GlanceModifier.size(Dimensions.Icon.inline),
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                TwoLineMessage(context.getString(R.string.widget_goals_empty), context.getString(R.string.widget_goals_empty_hint))
            } else {
                // Only as many 48dp rows as fit under the 48dp header, so a short
                // widget never cuts one in half (a standard 4×2 fits all three).
                val room = LocalSize.current.height - Dimensions.Component.minTouchTarget - Spacing.xs * 2
                val rows = (room.value / Dimensions.Component.minTouchTarget.value).toInt().coerceIn(1, GOALS_WIDGET_MAX)
                items.take(rows).forEach { GoalRow(it) }
            }
        }
    }

    @Composable
    private fun GoalRow(item: GoalWidgetItem) {
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(Dimensions.Component.minTouchTarget)
                .clickable(openGoal(context, item.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoalRingImage(item, ROW_RING, ROW_RING_STROKE)
            Spacer(GlanceModifier.width(Spacing.smd))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = item.name,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = Typography.bodyMedium.fontSize, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = item.line,
                    maxLines = 1,
                    style = TextStyle(color = PennyWiseWidgetTheme.goalToneColor(item.tone), fontSize = Typography.bodySmall.fontSize),
                )
            }
            Spacer(GlanceModifier.width(Spacing.sm))
            Text(
                text = context.getString(R.string.budgets_percent, item.percent),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = Typography.labelLarge.fontSize, fontWeight = FontWeight.Bold),
            )
        }
    }

    private companion object {
        val ADD_BUTTON = Dimensions.Icon.large
        val ROW_RING = Dimensions.Icon.large
        val ROW_RING_STROKE = Dimensions.Component.goalRingSmallStroke
    }
}

/** Both goal widgets refresh their shared snapshot on each update period and when first added. */
private fun refreshGoalWidgets(context: Context) = GoalWidgetUpdateWorker.enqueueOneShot(context)

class GoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshGoalWidgets(context)
    }
}

class GoalsListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalsListWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshGoalWidgets(context)
    }
}
