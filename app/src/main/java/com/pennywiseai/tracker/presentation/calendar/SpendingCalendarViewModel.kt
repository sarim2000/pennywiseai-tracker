package com.pennywiseai.tracker.presentation.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.domain.review.localFirstDayOfWeek
import com.pennywiseai.tracker.domain.spending.CalendarDay
import com.pennywiseai.tracker.domain.spending.SpendingCalendar
import com.pennywiseai.tracker.domain.spending.SpendingCalendarLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One month on screen. Days are shaded by spend in [currency] alone: amounts in two currencies never compare. */
data class SpendingCalendarUiState(
    val month: YearMonth,
    val today: LocalDate,
    val firstDayOfWeek: DayOfWeek,
    val weeks: List<List<LocalDate?>>,
    val days: Map<LocalDate, CalendarDay>,
    /** Currencies spent in this month, the most used first. */
    val currencies: List<String>,
    val currency: String?,
) {
    private val maxSpend: BigDecimal =
        currency?.let { c -> days.values.mapNotNull { it.spent[c] }.maxOrNull() } ?: BigDecimal.ZERO

    /** 0 (nothing spent in [currency]) to [SpendingCalendar.LEVELS]. */
    fun level(date: LocalDate): Int = SpendingCalendar.level(currency?.let { days[date]?.spent?.get(it) }, maxSpend)

    val isThisMonth: Boolean get() = month == YearMonth.from(today)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SpendingCalendarViewModel @Inject constructor(
    loader: SpendingCalendarLoader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Moved on by [refreshDate] on resume, so what's due follows the calendar. */
    private val today = MutableStateFlow(LocalDate.now())

    private val month = savedStateHandle.getStateFlow(KEY_MONTH, YearMonth.now().toString())

    /** Null while loading. */
    val state: StateFlow<SpendingCalendarUiState?> = combine(today, month, ::Pair)
        .flatMapLatest { (day, monthText) ->
            val shown = YearMonth.parse(monthText)
            loader.observe(shown, day).map { Triple(shown, day, it) }
        }
        .combine(savedStateHandle.getStateFlow<String?>(KEY_CURRENCY, null)) { (shown, day, days), chosen ->
            val currencies = SpendingCalendar.currencies(days)
            val firstDayOfWeek = localFirstDayOfWeek()
            SpendingCalendarUiState(
                month = shown,
                today = day,
                firstDayOfWeek = firstDayOfWeek,
                weeks = SpendingCalendar.weeks(shown, firstDayOfWeek),
                days = days,
                currencies = currencies,
                currency = chosen?.takeIf { it in currencies } ?: currencies.firstOrNull(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshDate() {
        today.value = LocalDate.now()
    }

    fun showMonth(offset: Long) {
        savedStateHandle[KEY_MONTH] = YearMonth.parse(month.value).plusMonths(offset).toString()
    }

    fun showThisMonth() {
        savedStateHandle[KEY_MONTH] = YearMonth.from(today.value).toString()
    }

    fun selectCurrency(currency: String) {
        savedStateHandle[KEY_CURRENCY] = currency
    }

    private companion object {
        const val KEY_MONTH = "calendarMonth"
        const val KEY_CURRENCY = "calendarCurrency"
    }
}
