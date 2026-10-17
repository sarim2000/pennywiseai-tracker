package com.pennywiseai.tracker.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.goals.GoalTone
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

    fun budgetStatusColor(percentageUsed: Float): ColorProvider = when {
        percentageUsed > 90f -> ColorProvider(day = budget_danger_light, night = budget_danger_dark)
        percentageUsed > 70f -> ColorProvider(day = budget_warning_light, night = budget_warning_dark)
        else -> ColorProvider(day = budget_safe_light, night = budget_safe_dark)
    }

    fun savingsColor(isPositive: Boolean): ColorProvider =
        if (isPositive) ColorProvider(day = budget_safe_light, night = budget_safe_dark)
        else ColorProvider(day = budget_danger_light, night = budget_danger_dark)

    fun transactionAmountColor(type: TransactionType): ColorProvider = when (type) {
        TransactionType.INCOME -> ColorProvider(day = income_light, night = income_dark)
        TransactionType.TRANSFER -> ColorProvider(day = transfer_light, night = transfer_dark)
        else -> ColorProvider(day = expense_light, night = expense_dark)
    }

    /** A goal's status line colour (#914), matching the in-app pill roles. */
    @Composable
    fun goalToneColor(tone: GoalTone): ColorProvider = when (tone) {
        GoalTone.SUCCESS -> ColorProvider(day = budget_safe_light, night = budget_safe_dark)
        GoalTone.WARNING -> ColorProvider(day = budget_warning_light, night = budget_warning_dark)
        GoalTone.ERROR -> ColorProvider(day = budget_danger_light, night = budget_danger_dark)
        GoalTone.NEUTRAL -> GlanceTheme.colors.onSurfaceVariant
    }
}
