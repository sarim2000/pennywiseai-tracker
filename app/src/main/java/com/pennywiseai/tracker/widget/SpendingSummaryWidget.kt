package com.pennywiseai.tracker.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal

/**
 * One glance, one tap: what today and this month cost, and a + for the cash
 * spend no SMS will ever report. Reads the snapshot the recent-transactions
 * worker writes (same spend definition, same currency), so the two widgets
 * never disagree.
 */
class SpendingSummaryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(NARROW, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = withTimeoutOrNull(5000L) {
            RecentTransactionsWidgetDataStore.getData(context).first()
        } ?: RecentTransactionsWidgetData()

        provideContent {
            val data by remember { RecentTransactionsWidgetDataStore.getData(context) }.collectAsState(initial)
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else PennyWiseWidgetTheme.colors
            ) {
                Content(data)
            }
        }
    }

    @Composable
    private fun Content(data: RecentTransactionsWidgetData) {
        val context = LocalContext.current
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(start = Spacing.md, end = Spacing.sm)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Figure(
                label = context.getString(R.string.widget_summary_today),
                amount = data.todaySpent,
                currency = data.currency,
                modifier = GlanceModifier.defaultWeight()
            )
            if (LocalSize.current.width >= WIDE.width) {
                Figure(
                    label = context.getString(R.string.widget_summary_month),
                    amount = data.totalSpent,
                    currency = data.currency,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            Spacer(GlanceModifier.width(Spacing.xs))
            // A 48dp touch target around the smaller visible button.
            Box(
                modifier = GlanceModifier
                    .size(Dimensions.Component.minTouchTarget)
                    .clickable(actionRunCallback<OpenAddTransactionAction>()),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(ADD_BUTTON)
                        .cornerRadius(ADD_BUTTON / 2)
                        .background(GlanceTheme.colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = context.getString(R.string.widget_add_content_description),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                        modifier = GlanceModifier.size(Dimensions.Icon.inline)
                    )
                }
            }
        }
    }

    @Composable
    private fun Figure(label: String, amount: BigDecimal, currency: String, modifier: GlanceModifier) {
        Column(modifier = modifier) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
            )
            Text(
                text = CurrencyFormatter.formatCurrency(amount, currency),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            )
        }
    }

    private companion object {
        val NARROW = DpSize(110.dp, 48.dp)
        val WIDE = DpSize(250.dp, 48.dp)
        val ADD_BUTTON = 40.dp
    }
}

class SpendingSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingSummaryWidget()

    // Every launcher update tick (and the first add) recomputes the snapshot,
    // so "Today" rolls over at midnight even without the recent-transactions
    // widget's periodic job.
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context)
    }
}
