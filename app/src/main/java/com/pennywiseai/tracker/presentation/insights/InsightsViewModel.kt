package com.pennywiseai.tracker.presentation.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.InsightPreferences
import com.pennywiseai.tracker.domain.insights.Insight
import com.pennywiseai.tracker.domain.insights.InsightsLoader
import com.pennywiseai.tracker.domain.insights.InsightsSnapshot
import com.pennywiseai.tracker.domain.insights.PeriodScore
import com.pennywiseai.tracker.domain.review.CurrencyReview
import com.pennywiseai.tracker.domain.review.CycleTotal
import com.pennywiseai.tracker.domain.review.ReviewRange
import com.pennywiseai.tracker.utils.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One currency's page: amounts in different currencies never share a screen. */
data class InsightsUiState(
    val range: ReviewRange,
    /** Currencies with something to show, the busiest first. */
    val currencies: List<String>,
    val currency: String?,
    val review: CurrencyReview?,
    /** The selected currency's last cycles, oldest first, ending with this one. */
    val cycles: List<CycleTotal>,
    val insights: List<Insight>,
    val dismissed: List<Insight>,
    val score: PeriodScore? = null,
    /** What active subscriptions in this currency cost a month; null without any. */
    val subscriptionsMonthly: Money? = null,
    /** Something was recorded before this cycle: an empty page is a quiet start, not a new user. */
    val hasHistory: Boolean = false,
) {
    /** Nothing recorded this cycle and nothing to say. */
    val isEmpty: Boolean get() = currencies.isEmpty()
}

/** [chosen] when it still has something to show, else the busiest currency. */
internal fun insightsUiState(snapshot: InsightsSnapshot, chosen: String?): InsightsUiState {
    val reviewed = snapshot.review.currencies.filter { it.hasActivity }
    val currencies = (reviewed.map { it.currency } + snapshot.insights.map { it.currency }).distinct()
    val currency = chosen?.takeIf { it in currencies } ?: currencies.firstOrNull()
    val inCurrency = snapshot.insights.filter { it.currency == currency }
    return InsightsUiState(
        range = snapshot.review.range,
        currencies = currencies,
        currency = currency,
        review = reviewed.firstOrNull { it.currency == currency },
        cycles = currency?.let { snapshot.review.detail?.cycles?.get(it) }.orEmpty(),
        insights = inCurrency.filter { it.id !in snapshot.dismissed },
        dismissed = inCurrency.filter { it.id in snapshot.dismissed },
        score = currency?.let { snapshot.scores[it] },
        subscriptionsMonthly = currency?.let { snapshot.subscriptionsMonthly[it] },
        hasHistory = snapshot.review.hasOlder,
    )
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    loader: InsightsLoader,
    private val insightPreferences: InsightPreferences,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Home keeps this ViewModel for days: moved on by [refreshDate] on resume, so the
     * cycle, the bills window and budget pace follow the calendar, as Home's figures do.
     */
    private val today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val snapshot = today.flatMapLatest { loader.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null while loading. */
    val state: StateFlow<InsightsUiState?> =
        combine(snapshot, savedStateHandle.getStateFlow<String?>(KEY_CURRENCY, null)) { loaded, chosen ->
            loaded?.let { insightsUiState(it, chosen) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Home's card: the two that matter most, in any currency (each carries its own). */
    val top: StateFlow<List<Insight>> = snapshot
        .map { it?.active?.take(HOME_COUNT).orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called on resume; a new day reloads, the same day does nothing. */
    fun refreshDate() {
        today.value = LocalDate.now()
    }

    fun selectCurrency(currency: String) {
        savedStateHandle[KEY_CURRENCY] = currency
    }

    fun dismiss(id: String) {
        viewModelScope.launch { insightPreferences.dismissInsight(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { insightPreferences.restoreInsight(id) }
    }

    private companion object {
        const val KEY_CURRENCY = "insightsCurrency"
        const val HOME_COUNT = 2
    }
}
