package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.add.QuickAddActivity
import com.pennywiseai.tracker.receiver.QuickCategoryPickerActivity
import com.pennywiseai.tracker.receiver.SmsBroadcastReceiver
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.Typography
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Did the app catch it, and where did it go? The latest transactions, each
 * with its category, and a Sort pill on the ones still in "Others". Totals
 * live in the summary widget.
 */
class RecentTransactionsWidget : GlanceAppWidget() {

    // Exact: the row count comes from the real height, so no row is cut in half.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = try {
            withTimeoutOrNull(5000L) {
                RecentTransactionsWidgetDataStore.getData(context).first()
            } ?: RecentTransactionsWidgetData()
        } catch (e: Exception) {
            android.util.Log.e("TransactionsWidget", "Failed to load widget data", e)
            RecentTransactionsWidgetData()
        }

        val privacy = WidgetPrivacy.load(context)
        provideContent {
            // A session still alive when the worker writes recomposes from
            // here; provideGlance doesn't run again for it.
            val data by remember { RecentTransactionsWidgetDataStore.getData(context) }.collectAsState(initial)
            WidgetRoot(privacy) {
                Content(data)
            }
        }
    }

    @Composable
    private fun Content(data: RecentTransactionsWidgetData) {
        val context = LocalContext.current
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(WidgetDefaults.padding)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            WidgetHeader(
                title = context.getString(R.string.widget_recent_header),
                add = actionRunCallback<OpenAddTransactionAction>(),
                addDescription = context.getString(R.string.widget_add_content_description)
            )
            if (data.transactions.isEmpty()) {
                WidgetEmptyState(
                    title = context.getString(R.string.widget_recent_empty),
                    hint = context.getString(R.string.widget_recent_empty_add_cash)
                )
            } else {
                val room = LocalSize.current.height - WidgetDefaults.padding * 2 - Dimensions.Component.minTouchTarget
                // ponytail: a widget shorter than one row still shows one, cut, rather than nothing.
                val rows = (room.value / Dimensions.Component.minTouchTarget.value).toInt().coerceAtLeast(1)
                data.transactions.take(rows).forEach { TransactionRow(context, it) }
            }
        }
    }

    @Composable
    private fun TransactionRow(context: Context, item: RecentTransactionItem) {
        WidgetRow(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(Dimensions.Component.minTouchTarget)
                .clickable(openTransaction(context, item.transactionId)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { Avatar(item) }
            item { Spacer(GlanceModifier.width(Spacing.smd)) }
            item {
                Column(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = startAlignment()) {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = Typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    WidgetLabel(item.subtitle)
                }
            }
            val sortId = item.transactionId
            if (item.needsCategory && sortId != null) {
                item { Spacer(GlanceModifier.width(Spacing.sm)) }
                item { SortPill(sortAction(context, sortId), context.getString(R.string.widget_recent_sort)) }
            }
            item { Spacer(GlanceModifier.width(Spacing.sm)) }
            item {
                val amount = exactText(item.amount, item.currency)
                Text(
                    text = if (item.transactionType == TransactionType.INCOME) withPlus(amount) else amount,
                    maxLines = 1,
                    style = TextStyle(
                        color = PennyWiseWidgetTheme.transactionAmountColor(item.transactionType),
                        fontSize = Typography.titleSmall.fontSize,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }

    /**
     * The category at a glance, as the app's list draws it: a solid circle in
     * its color with a white glyph, which stays legible for a near-black
     * category on the dark theme where a tint of it vanished.
     */
    @Composable
    private fun Avatar(item: RecentTransactionItem) {
        val color = item.colorArgb.takeIf { it != 0L }?.let { Color(it) }
        Box(
            modifier = GlanceModifier
                .size(Dimensions.Icon.large)
                .cornerRadius(Dimensions.Icon.large / 2)
                .background(color?.let { ColorProvider(it) } ?: GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.badge,
                maxLines = 1,
                style = TextStyle(
                    color = if (color != null) ColorProvider(Color.White) else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = Typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    /** A small tonal pill inside a full-height touch target. */
    @Composable
    private fun SortPill(action: Action, label: String) {
        Box(
            modifier = GlanceModifier.height(Dimensions.Component.minTouchTarget).clickable(action),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                maxLines = 1,
                modifier = GlanceModifier
                    .cornerRadius(Dimensions.CornerRadius.card)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .padding(horizontal = Spacing.smd, vertical = Spacing.xs),
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontSize = Typography.labelMedium.fontSize,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    private fun openTransaction(context: Context, transactionId: Long?): Action =
        if (transactionId == null) {
            actionStartActivity<MainActivity>()
        } else {
            actionStartActivity(
                Intent(context, MainActivity::class.java).apply {
                    action = SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION
                    putExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, transactionId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }

    /**
     * The alert's category picker; it refreshes the widgets itself. It takes
     * the transaction's own alert id (as the evening recap's Sort does), so an
     * alert still in the shade becomes the "Saved as" note with its Undo
     * instead of asking again for a transaction that's already sorted.
     */
    private fun sortAction(context: Context, transactionId: Long): Action =
        actionStartActivity(
            Intent(context, QuickCategoryPickerActivity::class.java).apply {
                // One PendingIntent per row; extras alone don't tell them apart.
                data = Uri.parse("pennywise://widget/sort/$transactionId")
                putExtra(QuickCategoryPickerActivity.EXTRA_TRANSACTION_ID, transactionId)
                putExtra(QuickCategoryPickerActivity.EXTRA_NOTIFICATION_ID, transactionId.toInt())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )

    private companion object {
        /** "+" inside the LTR isolate, so Arabic keeps it on the amount's left. */
        fun withPlus(amount: String): String =
            if (amount.startsWith('\u2066')) "\u2066+${amount.drop(1)}" else "+$amount"
    }
}

/** Every widget's +: the quick add sheet, which hands over to the full form on "More details". */
class OpenAddTransactionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        context.startActivity(Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
