package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.widgetDataStore by preferencesDataStore(name = "widget_budget_data")

object BudgetWidgetDataStore {

    private val TOTAL_SPENT = stringPreferencesKey("total_spent")
    private val TOTAL_LIMIT = stringPreferencesKey("total_limit")
    private val REMAINING = stringPreferencesKey("remaining")
    private val PERCENTAGE_USED = floatPreferencesKey("percentage_used")
    private val DAILY_ALLOWANCE = stringPreferencesKey("daily_allowance")
    private val TOTAL_INCOME = stringPreferencesKey("total_income")
    private val NET_SAVINGS = stringPreferencesKey("net_savings")
    private val SAVINGS_RATE = floatPreferencesKey("savings_rate")
    private val SAVINGS_DELTA = stringPreferencesKey("savings_delta")
    private val CURRENCY = stringPreferencesKey("currency")

    fun getData(context: Context): Flow<BudgetWidgetData> {
        return context.widgetDataStore.data.map { prefs ->
            BudgetWidgetData(
                totalSpent = prefs[TOTAL_SPENT]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                totalLimit = prefs[TOTAL_LIMIT]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                remaining = prefs[REMAINING]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                percentageUsed = prefs[PERCENTAGE_USED] ?: 0f,
                dailyAllowance = prefs[DAILY_ALLOWANCE]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                totalIncome = prefs[TOTAL_INCOME]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                netSavings = prefs[NET_SAVINGS]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                savingsRate = prefs[SAVINGS_RATE] ?: 0f,
                savingsDelta = prefs[SAVINGS_DELTA]?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull(),
                currency = prefs[CURRENCY] ?: "INR"
            )
        }
    }

    suspend fun update(context: Context, data: BudgetWidgetData) {
        context.widgetDataStore.edit { prefs ->
            prefs[TOTAL_SPENT] = data.totalSpent.toPlainString()
            prefs[TOTAL_LIMIT] = data.totalLimit.toPlainString()
            prefs[REMAINING] = data.remaining.toPlainString()
            prefs[PERCENTAGE_USED] = data.percentageUsed
            prefs[DAILY_ALLOWANCE] = data.dailyAllowance.toPlainString()
            prefs[TOTAL_INCOME] = data.totalIncome.toPlainString()
            prefs[NET_SAVINGS] = data.netSavings.toPlainString()
            prefs[SAVINGS_RATE] = data.savingsRate
            prefs[SAVINGS_DELTA] = data.savingsDelta?.toPlainString() ?: ""
            prefs[CURRENCY] = data.currency
        }
    }
}
