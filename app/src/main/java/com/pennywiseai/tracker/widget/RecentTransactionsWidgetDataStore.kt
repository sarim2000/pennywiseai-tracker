package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

private val Context.recentTransactionsWidgetDataStore by preferencesDataStore(name = "widget_recent_transactions")

object RecentTransactionsWidgetDataStore {

    private const val MAX_ITEMS = 10

    private val TOTAL_SPENT = stringPreferencesKey("total_spent")
    private val CURRENCY = stringPreferencesKey("currency")
    private val COUNT = intPreferencesKey("count")

    private fun titleKey(index: Int) = stringPreferencesKey("tx_title_$index")
    private fun subtitleKey(index: Int) = stringPreferencesKey("tx_subtitle_$index")
    private fun amountKey(index: Int) = stringPreferencesKey("tx_amount_$index")
    private fun currencyKey(index: Int) = stringPreferencesKey("tx_currency_$index")
    private fun typeKey(index: Int) = stringPreferencesKey("tx_type_$index")

    fun getData(context: Context): Flow<RecentTransactionsWidgetData> {
        return context.recentTransactionsWidgetDataStore.data.map { prefs ->
            val count = (prefs[COUNT] ?: 0).coerceIn(0, MAX_ITEMS)
            val items = (0 until count).mapNotNull { index ->
                val title = prefs[titleKey(index)] ?: return@mapNotNull null
                val subtitle = prefs[subtitleKey(index)] ?: ""
                val amount = prefs[amountKey(index)]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val currency = prefs[currencyKey(index)] ?: prefs[CURRENCY] ?: "INR"
                val type = prefs[typeKey(index)]
                    ?.let { com.pennywiseai.tracker.data.database.entity.TransactionType.valueOf(it) }
                    ?: com.pennywiseai.tracker.data.database.entity.TransactionType.EXPENSE
                RecentTransactionItem(
                    title = title,
                    subtitle = subtitle,
                    amount = amount,
                    currency = currency,
                    transactionType = type
                )
            }

            RecentTransactionsWidgetData(
                totalSpent = prefs[TOTAL_SPENT]?.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                currency = prefs[CURRENCY] ?: prefs[currencyKey(0)] ?: "INR",
                transactions = items
            )
        }
    }

    suspend fun update(context: Context, data: RecentTransactionsWidgetData) {
        context.recentTransactionsWidgetDataStore.edit { prefs ->
            prefs[TOTAL_SPENT] = data.totalSpent.toPlainString()
            prefs[CURRENCY] = data.currency

            // Clear previous entries
            for (index in 0 until MAX_ITEMS) {
                prefs.remove(titleKey(index))
                prefs.remove(subtitleKey(index))
                prefs.remove(amountKey(index))
                prefs.remove(currencyKey(index))
                prefs.remove(typeKey(index))
            }

            val limitedItems = data.transactions.take(MAX_ITEMS)
            prefs[COUNT] = limitedItems.size

            limitedItems.forEachIndexed { index, item ->
                prefs[titleKey(index)] = item.title
                prefs[subtitleKey(index)] = item.subtitle
                prefs[amountKey(index)] = item.amount.toPlainString()
                prefs[currencyKey(index)] = item.currency
                prefs[typeKey(index)] = item.transactionType.name
            }
        }
    }

    suspend fun clear(context: Context) {
        context.recentTransactionsWidgetDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
