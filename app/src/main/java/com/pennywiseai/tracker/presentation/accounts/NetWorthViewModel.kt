package com.pennywiseai.tracker.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.domain.spending.NetWorthAccount
import com.pennywiseai.tracker.domain.spending.NetWorthLoader
import com.pennywiseai.tracker.domain.spending.NetWorthPoint
import com.pennywiseai.tracker.domain.spending.NetWorthSnapshot
import com.pennywiseai.tracker.domain.spending.NetWorthTotal
import com.pennywiseai.tracker.domain.spending.PeopleOwed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** How far back the trend reaches. */
enum class NetWorthRange(val months: Long) { MONTH(1), QUARTER(3), HALF_YEAR(6), YEAR(12) }

/** One currency's page: amounts in different currencies never share a figure. */
data class NetWorthUiState(
    /** Currencies with an account, the one with most accounts first. */
    val currencies: List<String>,
    val currency: String?,
    val total: NetWorthTotal?,
    /** The selected currency's accounts. */
    val accounts: List<NetWorthAccount>,
    val peopleOwed: PeopleOwed?,
    val range: NetWorthRange,
    /** Null while the trend loads; empty when there's too little history for a line. */
    val trend: List<NetWorthPoint>?,
) {
    val isEmpty: Boolean get() = currencies.isEmpty()

    /**
     * A cash account here was never given a balance, so its past is unknown and the
     * currency has no trend until the user updates it.
     */
    val trendWaitsOnCash: Boolean
        get() = accounts.any {
            !it.account.isLiability && it.account.anchorAt == null &&
                it.account.sourceType == AccountBalanceRepository.SOURCE_MANUAL
        }
}

/** Most points the trend draws; the chart averages anything longer. */
private const val MAX_TREND_POINTS = 50

/**
 * Every few days of a long trend, counted back from the last so the line still ends
 * on today's figure. The chart would otherwise average days together, and its line
 * wouldn't end at the net worth above it.
 */
internal fun List<NetWorthPoint>.sampled(max: Int = MAX_TREND_POINTS): List<NetWorthPoint> {
    if (size <= max) return this
    val every = (size + max - 1) / max
    return indices.reversed().step(every).map { this[it] }.reversed()
}

internal fun netWorthUiState(
    snapshot: NetWorthSnapshot,
    trends: Map<String, List<NetWorthPoint>>?,
    chosen: String?,
    range: NetWorthRange,
): NetWorthUiState {
    val currencies = snapshot.totals.map { it.currency }
    val currency = chosen?.takeIf { it in currencies } ?: currencies.firstOrNull()
    return NetWorthUiState(
        currencies = currencies,
        currency = currency,
        total = snapshot.totals.firstOrNull { it.currency == currency },
        accounts = snapshot.accounts.filter { it.account.currency == currency },
        peopleOwed = snapshot.peopleOwed.firstOrNull {
            it.currency == currency && (it.owedToYou.signum() != 0 || it.youOwe.signum() != 0)
        },
        range = range,
        // A single day isn't a line.
        trend = trends?.let { it[currency].orEmpty().takeIf { points -> points.size > 1 }.orEmpty().sampled() },
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NetWorthViewModel @Inject constructor(
    loader: NetWorthLoader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val range = savedStateHandle.getStateFlow(KEY_RANGE, NetWorthRange.QUARTER.name)
        .map { name -> NetWorthRange.entries.firstOrNull { it.name == name } ?: NetWorthRange.QUARTER }

    private val trends = range.flatMapLatest { selected ->
        val today = LocalDate.now()
        loader.observeTrend(today.minusMonths(selected.months), today)
            .map<Map<String, List<NetWorthPoint>>, Map<String, List<NetWorthPoint>>?> { it }
            .onStart { emit(null) }
    }

    /** Null while loading. */
    val state: StateFlow<NetWorthUiState?> = combine(
        loader.observe(),
        trends,
        savedStateHandle.getStateFlow<String?>(KEY_CURRENCY, null),
        range,
    ) { snapshot, trend, chosen, selectedRange ->
        netWorthUiState(snapshot, trend, chosen, selectedRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectCurrency(currency: String) {
        savedStateHandle[KEY_CURRENCY] = currency
    }

    fun selectRange(range: NetWorthRange) {
        savedStateHandle[KEY_RANGE] = range.name
    }

    private companion object {
        const val KEY_CURRENCY = "netWorthCurrency"
        const val KEY_RANGE = "netWorthRange"
    }
}
