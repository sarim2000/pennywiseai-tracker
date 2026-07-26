package com.pennywiseai.tracker.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.SharePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Numbers behind the share card. Everything here is a count or a name — no amounts, by
 * design, which is why nothing in this file touches CurrencyFormatter or sumByCurrency.
 */
data class ShareCardData(
    val transactionCount: Int = 0,
    val topCategories: List<String> = emptyList(),
    val subscriptionCount: Int = 0,
    val periodLabel: String = "",
)

@HiltViewModel
class ShareCardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(ShareCardConfig())
    val config: StateFlow<ShareCardConfig> = _config.asStateFlow()

    private val _data = MutableStateFlow(ShareCardData())
    val data: StateFlow<ShareCardData> = _data.asStateFlow()

    init {
        viewModelScope.launch {
            _config.value = userPreferencesRepository.shareCardConfig.first()
            refresh()
        }
    }

    /**
     * Applies [update], unless it would leave the card with nothing on it.
     *
     * Silently keeping the last switch on is friendlier than disabling Share and letting
     * the user stare at an empty card wondering what's wrong.
     */
    fun updateConfig(update: (ShareCardConfig) -> ShareCardConfig) {
        val candidate = update(_config.value)
        if (!candidate.hasAnySection) return

        val periodChanged = candidate.period != _config.value.period
        _config.value = candidate
        viewModelScope.launch {
            userPreferencesRepository.setShareCardConfig(candidate)
            // Only the period changes what we have to read back out of the database;
            // toggling a section just shows or hides something already loaded.
            if (periodChanged) refresh()
        }
    }

    private suspend fun refresh() {
        val period = _config.value.period
        val now = LocalDate.now()

        val (start, end) = when (period) {
            SharePeriod.THIS_MONTH ->
                YearMonth.from(now).atDay(1).atStartOfDay() to now.atTime(23, 59, 59)
            SharePeriod.ALL_TIME ->
                LocalDateTime.of(2000, 1, 1, 0, 0) to LocalDateTime.now().plusYears(10)
        }

        val transactions = transactionRepository.getTransactionsBetweenDates(start, end).first()
        val categories = when (period) {
            SharePeriod.THIS_MONTH ->
                transactionRepository.getTopCategoriesByUsageBetween(start, end, limit = 3)
            SharePeriod.ALL_TIME ->
                transactionRepository.getTopCategoriesByUsage(limit = 3)
        }
        val subscriptions = subscriptionRepository.getActiveSubscriptions().first()

        _data.value = ShareCardData(
            transactionCount = transactions.size,
            topCategories = categories,
            subscriptionCount = subscriptions.size,
            periodLabel = when (period) {
                SharePeriod.THIS_MONTH ->
                    now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)).uppercase()
                SharePeriod.ALL_TIME -> "ALL TIME"
            },
        )
    }
}
