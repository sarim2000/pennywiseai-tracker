package com.pennywiseai.tracker.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pennywiseai.tracker.domain.spending.Bill
import com.pennywiseai.tracker.domain.spending.BillSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate

/**
 * One payment the Next bills widget lists, in its own currency. The amount is
 * formatted (or hidden) and the "Due in 3 days" worked out when it's drawn, so
 * the eye and the app's language apply live.
 */
@Serializable
data class NextBillItem(
    val name: String = "",
    /** Plain decimal in [currency], e.g. "12.99". */
    val amount: String = "0",
    val currency: String = "",
    /** [LocalDate.toEpochDay] of the due date. */
    val dueEpochDay: Long = 0,
) {
    val amountValue: BigDecimal get() = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
}

/**
 * The widget's snapshot. [asOfEpochDay] is the day it was built: it changes the
 * snapshot each day even when the bills don't, so a live widget redraws its
 * "Tomorrow" as "Today". The default (0) is a snapshot not built yet.
 */
@Serializable
data class NextBillsSnapshot(
    val asOfEpochDay: Long = 0,
    val items: List<NextBillItem> = emptyList(),
) {
    /** False until the worker first writes one: no items then doesn't mean nothing is due. */
    val isBuilt: Boolean get() = asOfEpochDay != 0L
}

/** How many bills the widget lists; three fit from 160dp tall, a common 4×2. */
const val NEXT_BILLS_MAX = 3

/**
 * The next [max] payments, soonest first, each once: a daily or weekly schedule
 * shows its next occurrence, not the same payment three times. Nothing is added up.
 */
fun nextBills(bills: List<Bill>, max: Int = NEXT_BILLS_MAX): List<NextBillItem> =
    bills.sortedBy { it.dueDate }
        .distinctBy { bill ->
            when (val source = bill.source) {
                is BillSource.Card -> source
                else -> listOf(source, bill.name.trim().lowercase(), bill.amount.currency, bill.amount.amount.stripTrailingZeros())
            }
        }
        .take(max)
        .map {
            NextBillItem(
                name = it.name,
                amount = it.amount.amount.toPlainString(),
                currency = it.amount.currency,
                dueEpochDay = it.dueDate.toEpochDay(),
            )
        }

private val Context.nextBillsWidgetDataStore by preferencesDataStore(name = "widget_next_bills")

object NextBillsWidgetDataStore {
    private val DATA = stringPreferencesKey("next_bills_json")
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: NextBillsSnapshot): String = json.encodeToString(NextBillsSnapshot.serializer(), snapshot)

    /** A missing or unreadable snapshot reads as not built yet. */
    fun decode(raw: String?): NextBillsSnapshot =
        raw?.let { runCatching { json.decodeFromString(NextBillsSnapshot.serializer(), it) }.getOrNull() } ?: NextBillsSnapshot()

    fun getData(context: Context): Flow<NextBillsSnapshot> =
        context.nextBillsWidgetDataStore.data.map { decode(it[DATA]) }

    suspend fun update(context: Context, snapshot: NextBillsSnapshot) {
        context.nextBillsWidgetDataStore.edit { it[DATA] = encode(snapshot) }
    }
}
