package com.pennywiseai.tracker.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Row
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
import com.pennywiseai.tracker.ui.theme.budget_danger_dark
import com.pennywiseai.tracker.ui.theme.budget_danger_light
import com.pennywiseai.tracker.ui.theme.budget_safe_dark
import com.pennywiseai.tracker.ui.theme.budget_safe_light
import com.pennywiseai.tracker.ui.theme.budget_warning_dark
import com.pennywiseai.tracker.ui.theme.budget_warning_light
import com.pennywiseai.tracker.utils.CurrencyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal

class BudgetWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 100.dp),
            DpSize(280.dp, 160.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = try {
            withTimeoutOrNull(5000L) {
                BudgetWidgetDataStore.getData(context).first()
            } ?: BudgetWidgetData()
        } catch (e: Exception) {
            android.util.Log.e("BudgetWidget", "Failed to load widget data", e)
            BudgetWidgetData()
        }

        provideContent {
            GlanceTheme {
                BudgetWidgetContent(data)
            }
        }
    }

    @Composable
    private fun BudgetWidgetContent(data: BudgetWidgetData) {
        val hasBudget = data.totalLimit > BigDecimal.ZERO

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!hasBudget) {
                NoBudgetContent()
            } else {
                BudgetOverviewContent(data)
            }
        }
    }

    @Composable
    private fun NoBudgetContent() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Monthly Budget",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = "Tap to set up",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }

    @Composable
    private fun BudgetOverviewContent(data: BudgetWidgetData) {
        val size = LocalSize.current
        val isSmall = size.width < 280.dp
        val isDark = (LocalContext.current.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val statusColor = when {
            data.percentageUsed > 90f -> ColorProvider(if (isDark) budget_danger_dark else budget_danger_light)
            data.percentageUsed > 70f -> ColorProvider(if (isDark) budget_warning_dark else budget_warning_light)
            else -> ColorProvider(if (isDark) budget_safe_dark else budget_safe_light)
        }

        // Title row with percentage
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Monthly Budget",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${data.percentageUsed.toInt()}% used",
                style = TextStyle(
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        // Spent amount (large)
        Text(
            text = CurrencyFormatter.formatCurrency(data.totalSpent, data.currency),
            style = TextStyle(
                color = statusColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Progress bar
        val progressWidth = (data.percentageUsed / 100f * 300f).coerceIn(4f, 300f)

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(8.dp)
                .cornerRadius(4.dp)
                .background(GlanceTheme.colors.surfaceVariant),
            contentAlignment = Alignment.CenterStart
        ) {
            if (data.percentageUsed > 0f) {
                Spacer(
                    modifier = GlanceModifier
                        .width(progressWidth.dp)
                        .height(8.dp)
                        .cornerRadius(4.dp)
                        .background(statusColor)
                )
            }
        }

        // Show details and savings only in large layout
        if (!isSmall) {
            Spacer(modifier = GlanceModifier.height(8.dp))

            // "of ₹55,000" + remaining on same row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "of ${CurrencyFormatter.formatCurrency(data.totalLimit, data.currency)}",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                val remainingText = if (data.remaining >= BigDecimal.ZERO) {
                    "${CurrencyFormatter.formatCurrency(data.remaining, data.currency)} left"
                } else {
                    "${CurrencyFormatter.formatCurrency(data.remaining.abs(), data.currency)} over"
                }
                Text(
                    text = remainingText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Daily allowance + savings
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.dailyAllowance > BigDecimal.ZERO) {
                    Text(
                        text = "${CurrencyFormatter.formatCurrency(data.dailyAllowance, data.currency)}/day",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    )
                }

                if (data.totalIncome > BigDecimal.ZERO) {
                    Spacer(modifier = GlanceModifier.defaultWeight())

                    val savingsColor = if (data.netSavings >= BigDecimal.ZERO) {
                        ColorProvider(if (isDark) budget_safe_dark else budget_safe_light)
                    } else {
                        ColorProvider(if (isDark) budget_danger_dark else budget_danger_light)
                    }

                    val savingsText = buildString {
                        append(if (data.netSavings >= BigDecimal.ZERO) "Saved " else "Over ")
                        append(CurrencyFormatter.formatCurrency(data.netSavings.abs(), data.currency))
                        data.savingsDelta?.let { delta ->
                            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                                append(if (delta >= BigDecimal.ZERO) " \u2191" else " \u2193")
                            }
                        }
                    }

                    Text(
                        text = savingsText,
                        style = TextStyle(
                            color = savingsColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
