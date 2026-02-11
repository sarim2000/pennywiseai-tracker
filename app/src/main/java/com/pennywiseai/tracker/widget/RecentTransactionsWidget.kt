package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
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
import androidx.glance.text.TextAlign
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.MainActivity
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first

class RecentTransactionsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(240.dp, 160.dp),
            DpSize(320.dp, 320.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = RecentTransactionsWidgetDataStore.getData(context).first()

        provideContent {
            GlanceTheme {
                RecentTransactionsContent(data)
            }
        }
    }

    @Composable
    private fun RecentTransactionsContent(data: RecentTransactionsWidgetData) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Header()

            Column(
                modifier = GlanceModifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Summary(totalSpent = data.totalSpent, currency = data.currency)

                Spacer(modifier = GlanceModifier.height(10.dp))

                if (data.transactions.isEmpty()) {
                    EmptyState()
                } else {
                    TransactionList(data.transactions)
                }
            }
        }
    }

    @Composable
    private fun Header() {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.primary)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Recent Transactions",
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(14.dp)
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionRunCallback<OpenAddTransactionAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_add),
                        contentDescription = "Add Transaction",
                        modifier = GlanceModifier.size(16.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun Summary(totalSpent: java.math.BigDecimal, currency: String) {
        Column(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Text(
                text = "Total spend this month",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = CurrencyFormatter.formatCurrency(totalSpent, currency),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    @Composable
    private fun TransactionList(items: List<RecentTransactionItem>) {
        LazyColumn(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            items(items) { item ->
                TransactionRow(item)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun TransactionRow(item: RecentTransactionItem) {
        Box(
            modifier = GlanceModifier.fillMaxWidth()
        ) {
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(end = 72.dp)
            ) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }

            val amountColor = when (item.transactionType) {
                TransactionType.INCOME -> ColorProvider(Color(0xFF2E7D32))
                TransactionType.TRANSFER -> GlanceTheme.colors.onSurfaceVariant
                else -> ColorProvider(Color(0xFFD32F2F))
            }

            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = CurrencyFormatter.formatCurrency(item.amount, item.currency),
                    style = TextStyle(
                        color = amountColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                )
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No transactions yet",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "Tap + to add manually",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
            )
        }
    }
}

class OpenAddTransactionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ADD_TRANSACTION, true)
        }
        context.startActivity(intent)
    }
}
