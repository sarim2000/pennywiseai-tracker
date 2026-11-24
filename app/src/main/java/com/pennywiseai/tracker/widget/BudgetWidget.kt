package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import com.pennywiseai.tracker.ui.components.mascot.MascotMood
import com.pennywiseai.tracker.ui.components.mascot.drawStaticOwl
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** The month's budget: what's left (or how far over), the bar, and the limit. */
class BudgetWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, COMPACT_WIDE, REGULAR, REGULAR_WIDE, LARGE, TALL, HUGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = try {
            withTimeoutOrNull(5000L) {
                BudgetWidgetDataStore.getData(context).first()
            } ?: BudgetWidgetData()
        } catch (e: Exception) {
            android.util.Log.e("BudgetWidget", "Failed to load widget data", e)
            BudgetWidgetData()
        }

        val privacy = WidgetPrivacy.load(context)
        provideContent {
            // A session still alive when the worker writes recomposes from
            // here; provideGlance doesn't run again for it.
            val data by remember { BudgetWidgetDataStore.getData(context) }.collectAsState(initial)
            WidgetRoot(privacy) {
                Content(data)
            }
        }
    }

    @Composable
    private fun Content(data: BudgetWidgetData) {
        val context = LocalContext.current
        val size = LocalSize.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(WidgetDefaults.padding)
                .clickable(openBudgets(context)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = startAlignment()
        ) {
            if (!data.hasLimit) {
                // The whole widget opens Budgets too; the button only once there's room for it.
                val roomy = size.height >= REGULAR.height
                WidgetEmptyState(
                    title = context.getString(R.string.widget_budget_empty_title),
                    hint = context.getString(R.string.widget_budget_empty_hint),
                    button = if (roomy) context.getString(R.string.widget_budget_create) else null,
                    onButton = if (roomy) openBudgets(context) else null
                )
            } else if (size.height >= REGULAR.height) {
                Hero(
                    data,
                    owl = size.width >= COMPACT_WIDE.width,
                    // The label, figure and limit line are sp: a larger font needs a taller widget for the big figure.
                    tall = size.height >= TALL.height * textGrowth(),
                    large = size.width >= LARGE.width,
                    huge = size.width >= HUGE.width && size.height >= HUGE.height * textGrowth()
                )
            } else {
                // At 180dp the owl would squeeze the figure: it only joins once there's room beside it.
                if (size.width < COMPACT_WIDE.width) {
                    Overview(data)
                } else {
                    WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        item { BudgetOwl(data, Dimensions.Icon.list) }
                        item { Spacer(GlanceModifier.width(Spacing.md)) }
                        item {
                            Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = startAlignment()) {
                                Overview(data)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Two rows and up: the title pinned to the top, the bar and the limit to
     * the foot, and what's left between them as the one big figure, the owl
     * beside it. The weighted gaps hand the widget's spare height to the
     * figure instead of leaving it above and below the whole block.
     */
    @Composable
    private fun Hero(data: BudgetWidgetData, owl: Boolean, tall: Boolean, large: Boolean, huge: Boolean) {
        val context = LocalContext.current
        val statusColor = PennyWiseWidgetTheme.budgetStatusColor(data.percentageUsed)
        // On the shown figure: 0.3 over reads "Left · JD 0", not "Over · JD 0".
        val over = wholeUnits(data.remaining) < BigDecimal.ZERO
        val owlSize = when {
            huge -> Dimensions.Icon.extraLarge
            tall -> Dimensions.Icon.hero
            else -> Dimensions.Icon.list
        }
        Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = startAlignment()) {
            WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                item { WidgetTitle(context.getString(R.string.widget_budget_title), GlanceModifier.defaultWeight()) }
                item { Spacer(GlanceModifier.width(Spacing.sm)) }
                item { PercentUsed(data, statusColor) }
            }
            Spacer(GlanceModifier.defaultWeight())
            WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                item {
                    Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = startAlignment()) {
                        WidgetLabel(context.getString(if (over) R.string.widget_budget_over_label else R.string.widget_budget_left_label))
                        val figure = totalText(data.remaining.abs(), data.currency)
                        Text(
                            text = figure,
                            maxLines = 1,
                            style = TextStyle(
                                color = if (over) PennyWiseWidgetTheme.savingsColor(false) else GlanceTheme.colors.onSurface,
                                fontSize = fitFontSize(
                                    figure,
                                    LocalSize.current.width - WidgetDefaults.padding * 2 - if (owl) Spacing.md + owlSize else 0.dp,
                                    when {
                                        huge -> Typography.displayMedium.fontSize
                                        tall -> Typography.displaySmall.fontSize
                                        else -> Typography.headlineSmall.fontSize
                                    }
                                ),
                                fontWeight = FontWeight.Bold,
                                textAlign = startTextAlign()
                            )
                        )
                    }
                }
                if (owl) {
                    item { Spacer(GlanceModifier.width(Spacing.md)) }
                    item { BudgetOwl(data, owlSize) }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            WidgetProgressBar(data.percentageUsed / 100f, statusColor, height = WidgetDefaults.barHeightLarge)
            if (!amountsHidden()) {
                Spacer(GlanceModifier.height(Spacing.sm))
                WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    item { WidgetLabel(budgetMeta(data), GlanceModifier.defaultWeight()) }
                    if (large && data.totalIncome > BigDecimal.ZERO) {
                        item { Spacer(GlanceModifier.width(Spacing.sm)) }
                        item { Savings(data) }
                    }
                }
            }
        }
    }

    @Composable
    private fun PercentUsed(data: BudgetWidgetData, color: ColorProvider) {
        Text(
            text = LocalContext.current.latinString(R.string.widget_budget_percent_used, data.percentageUsed.toInt()),
            maxLines = 1,
            style = TextStyle(color = color, fontSize = Typography.labelLarge.fontSize, fontWeight = FontWeight.Bold)
        )
    }

    /** One row high: no room for a title, so the label says "in budget" and carries the percent. */
    @Composable
    private fun Overview(data: BudgetWidgetData) {
        val context = LocalContext.current
        val statusColor = PennyWiseWidgetTheme.budgetStatusColor(data.percentageUsed)
        // On the shown figure: 0.3 over reads "Left · JD 0", not "Over · JD 0".
        val over = wholeUnits(data.remaining) < BigDecimal.ZERO
        WidgetRow(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            item {
                WidgetLabel(
                    context.getString(if (over) R.string.widget_summary_over else R.string.widget_summary_left),
                    GlanceModifier.defaultWeight()
                )
            }
            item { Spacer(GlanceModifier.width(Spacing.sm)) }
            item { PercentUsed(data, statusColor) }
        }
        Text(
            text = totalText(data.remaining.abs(), data.currency),
            maxLines = 1,
            style = TextStyle(
                color = if (over) PennyWiseWidgetTheme.savingsColor(false) else GlanceTheme.colors.onSurface,
                fontSize = Typography.headlineSmall.fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = startTextAlign()
            )
        )
        Spacer(GlanceModifier.height(Spacing.sm))
        WidgetProgressBar(data.percentageUsed / 100f, statusColor)
    }

    @Composable
    private fun Savings(data: BudgetWidgetData) {
        val context = LocalContext.current
        val saved = data.netSavings >= BigDecimal.ZERO
        val arrow = when (data.savingsDelta?.signum()) {
            1 -> " ↑"
            -1 -> " ↓"
            else -> ""
        }
        val text = context.getString(
            if (saved) R.string.widget_budget_saved else R.string.widget_budget_overspent,
            totalText(data.netSavings.abs(), data.currency)
        ) + arrow
        Text(
            text = text,
            maxLines = 1,
            style = TextStyle(
                color = PennyWiseWidgetTheme.savingsColor(saved),
                fontSize = Typography.bodySmall.fontSize,
                fontWeight = FontWeight.Medium
            )
        )
    }

    /**
     * The owl's read of the budget (see [budgetMood]), Calm while amounts are hidden so the
     * launcher doesn't give the state away. TalkBack hears the mood, not the drawing.
     */
    @Composable
    private fun BudgetOwl(data: BudgetWidgetData, size: Dp) {
        val context = LocalContext.current
        val mood = if (amountsHidden()) MascotMood.Calm else budgetMood(data)
        val description = when (mood) {
            MascotMood.Happy -> R.string.widget_budget_mood_on_track
            MascotMood.Thinking -> R.string.widget_budget_mood_fast
            // Worried and Calm say nothing: Worried is always beside the visible "Over" label.
            else -> null
        }
        Image(
            // Drawn white and tinted by the launcher, so it follows day/night even when this process hasn't.
            provider = ImageProvider(renderOwl(context, mood, size)),
            contentDescription = description?.let(context::getString),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(size)
        )
    }

    private companion object {
        val COMPACT = DpSize(180.dp, 80.dp)
        val COMPACT_WIDE = DpSize(260.dp, 80.dp)
        // Title, label, figure, bar and the limit line need ~110dp inside the padding.
        val REGULAR = DpSize(180.dp, 140.dp)
        val REGULAR_WIDE = DpSize(260.dp, 140.dp)
        val LARGE = DpSize(280.dp, 160.dp)
        // A standard 4x2 on most phones: room for a display-size figure and the big owl.
        val TALL = DpSize(280.dp, 180.dp)
        // One UI lays a 4x2 out at ~500x267dp and scales it down to fit: a
        // display figure that wide still leaves room for "CHF 12,345" beside the big owl.
        val HUGE = DpSize(440.dp, 240.dp)
    }
}

/** "of JD 360 · JD 18/day": the limit and, while there's any left, the per-day pace. */
@Composable
internal fun budgetMeta(data: BudgetWidgetData): String {
    val context = LocalContext.current
    val limit = context.getString(R.string.widget_budget_of_limit, totalText(data.totalLimit, data.currency))
    if (data.dailyAllowance <= BigDecimal.ZERO) return limit
    return limit + " · " + context.getString(R.string.widget_budget_per_day, totalText(data.dailyAllowance, data.currency))
}

/**
 * The owl's read of the budget: over the limit (as the figure says it) is Worried, a
 * running limit more than a day's budget past an even spend Thinking, otherwise Happy.
 * Calm with no budget or no running limit.
 */
internal fun budgetMood(data: BudgetWidgetData): MascotMood = when {
    !data.hasLimit -> MascotMood.Calm
    wholeUnits(data.remaining) < BigDecimal.ZERO -> MascotMood.Worried
    data.overPace == true -> MascotMood.Thinking
    data.overPace == false -> MascotMood.Happy
    else -> MascotMood.Calm
}

/** Drawn owls by mood, pixel size and direction: a widget recomposes often, the owl rarely changes. */
private val owlBitmaps = ConcurrentHashMap<Triple<MascotMood, Int, Boolean>, Bitmap>()

/** The in-app owl in [mood] on a transparent bitmap, in white. Never recycled: it's shared. */
private fun renderOwl(context: Context, mood: MascotMood, size: Dp): Bitmap {
    val px = (size.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val rtl = context.isRtlLayout
    return owlBitmaps.getOrPut(Triple(mood, px, rtl)) {
        val bitmap = createBitmap(px, px)
        val pxSize = Size(px.toFloat(), px.toFloat())
        CanvasDrawScope().draw(Density(context), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()), pxSize) {
            drawStaticOwl(mood, Color.White, rtl)
        }
        bitmap
    }
}

/** The app's Budgets screen. The data URI keeps this PendingIntent apart from the goal widgets'. */
private fun openBudgets(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "pennywise-widget://budget-widget".toUri()
        putExtra(MainActivity.EXTRA_OPEN_BUDGETS, true)
    }
)
