package com.pennywiseai.tracker.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.domain.goals.GoalTone
import com.pennywiseai.tracker.ui.theme.*

/**
 * Semantic colors are day/night pairs the launcher resolves itself, so a theme
 * switch repaints them without a new composition.
 */
object PennyWiseWidgetTheme {
    val colors = ColorProviders(
        light = LightColorScheme,
        dark = DarkColorScheme
    )

    /** Spending against a limit: the theme's own color until 90%, then amber, and red once over. */
    @Composable
    fun budgetStatusColor(percentageUsed: Float): ColorProvider = when {
        percentageUsed > 100f -> ColorProvider(day = expense_light, night = expense_dark)
        percentageUsed >= 90f -> ColorProvider(day = warning_light, night = warning_dark)
        else -> GlanceTheme.colors.primary
    }

    fun savingsColor(isPositive: Boolean): ColorProvider =
        if (isPositive) ColorProvider(day = success_light, night = success_dark)
        else ColorProvider(day = expense_light, night = expense_dark)

    /** A row's amount: income stands out; spending, most rows, stays plain rather than a wall of red. */
    @Composable
    fun transactionAmountColor(type: TransactionType): ColorProvider = when (type) {
        TransactionType.INCOME -> ColorProvider(day = income_light, night = income_dark)
        TransactionType.TRANSFER -> GlanceTheme.colors.onSurfaceVariant
        else -> GlanceTheme.colors.onSurface
    }

    /** A goal's status line colour (#914), matching the in-app pill roles. */
    @Composable
    fun goalToneColor(tone: GoalTone): ColorProvider = when (tone) {
        GoalTone.SUCCESS -> ColorProvider(day = success_light, night = success_dark)
        GoalTone.WARNING -> ColorProvider(day = warning_light, night = warning_dark)
        GoalTone.ERROR -> ColorProvider(day = expense_light, night = expense_dark)
        GoalTone.NEUTRAL -> GlanceTheme.colors.onSurfaceVariant
    }
}
