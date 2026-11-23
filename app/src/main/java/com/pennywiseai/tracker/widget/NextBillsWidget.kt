package com.pennywiseai.tracker.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import java.util.Locale

/**
 * The next three payments due: subscriptions, recurring payments and card bills,
 * soonest first, each in its own currency and never added up. Tapping it opens Bills.
 */
class NextBillsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, ONE_ROW, TWO_ROWS, THREE_ROWS))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = try {
            withTimeoutOrNull(5000L) { NextBillsWidgetDataStore.getData(context).first() } ?: NextBillsSnapshot()
        } catch (e: Exception) {
            android.util.Log.e("NextBillsWidget", "Failed to load widget data", e)
            NextBillsSnapshot()
        }

        val privacy = WidgetPrivacy.load(context)
        provideContent {
            // A session still alive when the worker writes recomposes from
            // here; provideGlance doesn't run again for it.
            val data by remember { NextBillsWidgetDataStore.getData(context) }.collectAsState(initial)
            WidgetRoot(privacy) { Content(data) }
        }
    }

    @Composable
    private fun Content(data: NextBillsSnapshot) {
        val context = LocalContext.current
        val height = LocalSize.current.height
        // The 48dp header and rows carry their own air, so the frame is only tight vertically.
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = WidgetDefaults.padding, vertical = Spacing.xs)
                .clickable(openBills(context)),
        ) {
            // Below one row under the header, the bill is all there's room for.
            if (height >= ONE_ROW.height) WidgetHeader(title = context.getString(R.string.widget_next_bills_title))
            if (data.items.isEmpty()) {
                WidgetEmptyState(
                    title = context.getString(R.string.widget_next_bills_empty),
                    hint = context.getString(R.string.widget_next_bills_empty_hint),
                )
            } else {
                val rows = when {
                    height >= THREE_ROWS.height -> 3
                    height >= TWO_ROWS.height -> 2
                    else -> 1
                }
                val today = LocalDate.now()
                data.items.take(rows).forEach { BillRow(it, today) }
            }
        }
    }

    @Composable
    private fun BillRow(item: NextBillItem, today: LocalDate) {
        val days = item.dueEpochDay - today.toEpochDay()
        WidgetRow(
            modifier = GlanceModifier.fillMaxWidth().height(Dimensions.Component.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = startAlignment()) {
                    Text(
                        text = item.name,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = Typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Medium,
                            textAlign = startTextAlign(),
                        ),
                    )
                    WidgetLabel(dueLabel(days), color = dueColor(days))
                }
            }
            item { Spacer(GlanceModifier.width(Spacing.sm)) }
            item {
                Text(
                    // The eye hides bills, as it does on the Bills screen.
                    text = if (amountsHidden()) HIDDEN_AMOUNT else exactText(item.amountValue, item.currency),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = Typography.titleSmall.fontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = endTextAlign(),
                    ),
                )
            }
        }
    }

    /** "Overdue", "Due today", "Due tomorrow" or "Due in 5 days", with Latin digits as the amounts have. */
    @Composable
    private fun dueLabel(days: Long): String {
        val context = LocalContext.current
        return when {
            days < 0 -> context.getString(R.string.home_due_overdue)
            days == 0L -> context.getString(R.string.subscriptions_due_today)
            days == 1L -> context.getString(R.string.subscriptions_due_tomorrow)
            else -> {
                val n = days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                String.format(Locale.US, context.resources.getQuantityString(R.plurals.subscriptions_due_in_days, n), n)
            }
        }
    }

    /** As Home's "Coming up": overdue in red, today and tomorrow in amber. */
    @Composable
    private fun dueColor(days: Long): ColorProvider = when {
        days < 0 -> PennyWiseWidgetTheme.savingsColor(false)
        days <= 1 -> PennyWiseWidgetTheme.dueSoonColor
        else -> GlanceTheme.colors.onSurfaceVariant
    }

    private companion object {
        // Each step is one more 48dp row; vertical padding is Spacing.xs each side.
        val COMPACT = DpSize(180.dp, 56.dp)
        val ONE_ROW = DpSize(180.dp, 104.dp)
        val TWO_ROWS = DpSize(180.dp, 152.dp)
        val THREE_ROWS = DpSize(180.dp, 200.dp)
    }
}

/** The Plan tab on Bills. The data URI keeps this PendingIntent apart from the other widgets'. */
private fun openBills(context: Context): Action = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        data = "pennywise-widget://next-bills".toUri()
        putExtra(MainActivity.EXTRA_OPEN_BILLS, true)
    }
)

class NextBillsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextBillsWidget()

    /** On each update period and when first added: a new day moves "Tomorrow" to "Today". */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        NextBillsWidgetUpdateWorker.enqueueOneShot(context)
    }
}
