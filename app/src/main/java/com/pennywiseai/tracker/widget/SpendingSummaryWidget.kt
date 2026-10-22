package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal

/**
 * The main widget: "am I OK?" and a + for the cash spend no SMS will ever
 * report. With a budget it leads with what's left of it; without one, with
 * today. Today and the month come from the recent-transactions snapshot
 * (same spend definition as that widget), the budget from the budget one.
 */
class SpendingSummaryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(NARROW, NARROW.withBar(), NARROW.withMeta(), WIDE, WIDE.withBar(), WIDE.withMeta())
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = withTimeoutOrNull(5000L) {
            RecentTransactionsWidgetDataStore.getData(context).first()
        } ?: RecentTransactionsWidgetData()
        val initialBudget = withTimeoutOrNull(5000L) {
            BudgetWidgetDataStore.getData(context).first()
        } ?: BudgetWidgetData()

        val privacy = WidgetPrivacy.load(context)
        provideContent {
            val data by remember { RecentTransactionsWidgetDataStore.getData(context) }.collectAsState(initial)
            val budget by remember { BudgetWidgetDataStore.getData(context) }.collectAsState(initialBudget)
            WidgetRoot(privacy) {
                Content(data, budgetIn(budget, data.currency))
            }
        }
    }

    @Composable
    private fun Content(data: RecentTransactionsWidgetData, budget: BudgetWidgetData?) {
        val context = LocalContext.current
        val size = LocalSize.current
        val wide = size.width >= WIDE.width
        WidgetRow(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(horizontal = WidgetDefaults.padding, vertical = Spacing.xs)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                if (budget != null) {
                    BudgetFigure(budget, size.height, GlanceModifier.defaultWeight())
                } else {
                    Figure(
                        context.getString(R.string.widget_summary_today),
                        totalText(data.todaySpent, data.currency),
                        Typography.headlineSmall.fontSize,
                        GlanceModifier.defaultWeight()
                    )
                }
            }
            if (wide) {
                item { Spacer(GlanceModifier.width(Spacing.md)) }
                item {
                    Figure(
                        context.getString(if (budget != null) R.string.widget_summary_today else R.string.widget_summary_month),
                        totalText(if (budget != null) data.todaySpent else data.totalSpent, data.currency),
                        Typography.titleMedium.fontSize
                    )
                }
            }
            item { Spacer(GlanceModifier.width(Spacing.sm)) }
            item { AddButton(actionRunCallback<OpenAddTransactionAction>(), context.getString(R.string.widget_add_content_description)) }
        }
    }

    @Composable
    private fun BudgetFigure(budget: BudgetWidgetData, height: Dp, modifier: GlanceModifier) {
        val context = LocalContext.current
        val over = budget.remaining < BigDecimal.ZERO
        Column(modifier = modifier, horizontalAlignment = startAlignment()) {
            Figure(
                label = context.getString(if (over) R.string.widget_summary_over else R.string.widget_summary_left),
                value = totalText(budget.remaining.abs(), budget.currency),
                fontSize = Typography.headlineSmall.fontSize,
                color = if (over) PennyWiseWidgetTheme.savingsColor(false) else null
            )
            if (height >= NARROW.withBar().height) {
                Spacer(GlanceModifier.height(Spacing.xs))
                WidgetProgressBar(budget.percentageUsed / 100f, PennyWiseWidgetTheme.budgetStatusColor(budget.percentageUsed))
            }
            if (height >= NARROW.withMeta().height && !amountsHidden()) {
                Spacer(GlanceModifier.height(Spacing.xxs))
                WidgetLabel(budgetMeta(budget))
            }
        }
    }

    @Composable
    private fun Figure(
        label: String,
        value: String,
        fontSize: TextUnit,
        modifier: GlanceModifier = GlanceModifier,
        color: ColorProvider? = null
    ) {
        Column(modifier = modifier, horizontalAlignment = startAlignment()) {
            WidgetLabel(label)
            Text(
                text = value,
                maxLines = 1,
                style = TextStyle(
                    color = color ?: GlanceTheme.colors.onSurface,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = startTextAlign()
                )
            )
        }
    }

    private companion object {
        val NARROW = DpSize(110.dp, 48.dp)
        val WIDE = DpSize(250.dp, 48.dp)

        // A one-row widget is 60–110dp tall depending on the launcher. The bar
        // needs ~10dp under label + figure, the "of … · …/day" line ~18dp more.
        fun DpSize.withBar() = copy(height = 72.dp)
        fun DpSize.withMeta() = copy(height = 88.dp)
    }
}

/**
 * The budget snapshot when it can sit beside the recent-transactions one:
 * a limit is set and both are in the same currency (never mix two).
 */
internal fun budgetIn(budget: BudgetWidgetData, currency: String): BudgetWidgetData? =
    budget.takeIf { it.totalLimit > BigDecimal.ZERO && it.currency == currency }

class SpendingSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendingSummaryWidget()

    // Every launcher update tick (and the first add) recomputes both
    // snapshots, so "Today" rolls over at midnight even without the other
    // widgets' periodic jobs.
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context)
        BudgetWidgetUpdateWorker.enqueueOneShot(context)
    }
}
