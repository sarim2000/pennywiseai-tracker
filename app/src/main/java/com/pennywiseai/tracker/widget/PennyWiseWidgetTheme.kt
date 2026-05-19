package com.pennywiseai.tracker.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.glance.LocalContext
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.ui.theme.*

object PennyWiseWidgetTheme {
    val colors = ColorProviders(
        light = LightColorScheme,
        dark = DarkColorScheme
    )

    @Composable
    fun budgetStatusColor(percentageUsed: Float): ColorProvider {
        val isDark = isWidgetDarkMode()
        return when {
            percentageUsed > 90f -> ColorProvider(if (isDark) budget_danger_dark else budget_danger_light)
            percentageUsed > 70f -> ColorProvider(if (isDark) budget_warning_dark else budget_warning_light)
            else -> ColorProvider(if (isDark) budget_safe_dark else budget_safe_light)
        }
    }

    @Composable
    fun savingsColor(isPositive: Boolean): ColorProvider {
        val isDark = isWidgetDarkMode()
        return if (isPositive) {
            ColorProvider(if (isDark) budget_safe_dark else budget_safe_light)
        } else {
            ColorProvider(if (isDark) budget_danger_dark else budget_danger_light)
        }
    }

    @Composable
    fun transactionAmountColor(type: TransactionType): ColorProvider {
        val isDark = isWidgetDarkMode()
        return when (type) {
            TransactionType.INCOME ->
                ColorProvider(if (isDark) income_dark else income_light)
            TransactionType.TRANSFER ->
                ColorProvider(if (isDark) transfer_dark else transfer_light)
            else ->
                ColorProvider(if (isDark) expense_dark else expense_light)
        }
    }

    @Composable
    private fun isWidgetDarkMode(): Boolean {
        return (LocalContext.current.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
