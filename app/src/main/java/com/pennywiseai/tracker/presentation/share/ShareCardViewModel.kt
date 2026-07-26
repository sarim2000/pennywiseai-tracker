package com.pennywiseai.tracker.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.data.share.ShareCardConfig
import com.pennywiseai.tracker.data.share.SharePeriod
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
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

private val MONTH_LABEL: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

@HiltViewModel
class ShareCardViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(ShareCardConfig())
    val config: StateFlow<ShareCardConfig> = _config.asStateFlow()

    private val _data = MutableStateFlow(ShareCardData())
    val data: StateFlow<ShareCardData> = _data.asStateFlow()

    /**
     * Period the caller asked for, if any. Held separately because it can arrive before
     * the saved config finishes loading: the sheet's LaunchedEffect fires on first
     * composition, while [init] is still suspended reading DataStore. Without this the
     * load completes second and silently clobbers the override — the monthly prompt then
     * opens on the current (nearly empty) month instead of the finished one.
     */
    private var pendingPeriodOverride: SharePeriod? = null

    /**
     * The in-flight refresh, cancelled before a new one starts.
     *
     * Each refresh reads several suspending sources, so two of them racing can finish out
     * of order and leave [_data] describing a period the user has already moved off —
     * flick between the period chips quickly and the card settles on the wrong one.
     */
    private var refreshJob: Job? = null

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { refresh() }
    }

    init {
        viewModelScope.launch {
            val saved = userPreferencesRepository.shareCardConfig.first()
            _config.value = pendingPeriodOverride?.let { saved.copy(period = it) } ?: saved
            scheduleRefresh()
        }
        // The sheet's ViewModel outlives a single viewing, so filtering by profile at
        // read time isn't enough on its own: switch profile, reopen the sheet, and
        // nothing would have re-read the database — the card would still show the
        // previous profile's totals. Following the selection keeps the two in step
        // whether it changes between viewings or while the sheet is open.
        viewModelScope.launch {
            userPreferencesRepository.selectedProfileId
                .distinctUntilChanged()
                .collect { scheduleRefresh() }
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
        viewModelScope.launch { userPreferencesRepository.setShareCardConfig(candidate) }
        // Only the period changes what we have to read back out of the database;
        // toggling a section just shows or hides something already loaded.
        if (periodChanged) scheduleRefresh()
    }

    /**
     * Points the card at [period] for this viewing only, without writing it to
     * preferences — the monthly prompt opens on last month, but that shouldn't silently
     * rewrite a choice the user made in Customise.
     */
    fun applyPeriodOverride(period: SharePeriod) {
        pendingPeriodOverride = period
        if (_config.value.period == period) return
        _config.value = _config.value.copy(period = period)
        scheduleRefresh()
    }

    private suspend fun refresh() {
        val period = _config.value.period
        val now = LocalDate.now()

        val (start, end) = when (period) {
            SharePeriod.THIS_MONTH ->
                YearMonth.from(now).atDay(1).atStartOfDay() to now.atTime(23, 59, 59)
            SharePeriod.LAST_MONTH -> {
                val month = YearMonth.from(now).minusMonths(1)
                month.atDay(1).atStartOfDay() to month.atEndOfMonth().atTime(23, 59, 59)
            }
            SharePeriod.ALL_TIME ->
                LocalDateTime.of(2000, 1, 1, 0, 0) to LocalDateTime.now().plusYears(10)
        }

        // Scope to the profile the user is currently viewing. Home does this for every
        // figure it shows (filterTransactionsByProfile); a recap that ignored it would
        // count a Business profile's card using the owner's personal transactions too —
        // wrong, and it would put those counts into an image built for sharing.
        val selectedProfileId = userPreferencesRepository.selectedProfileId.first()
        val profileAccountKeys =
            buildProfileAccountKeys(accountBalanceRepository.getAllLatestBalances().first())

        val transactions = filterTransactionsByProfile(
            transactionRepository.getTransactionsBetweenDates(start, end).first(),
            selectedProfileId,
            profileAccountKeys,
        )

        // Ranked in Kotlin from the same filtered list rather than by a GROUP BY: the
        // query can't see the profile filter, and deriving both figures from one list
        // means the count and the categories can never describe different populations.
        val categories = transactions
            .groupingBy { it.category }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .map { it.key }

        // Subscriptions are deliberately not filtered: SubscriptionEntity carries no
        // profileId and Home doesn't scope them either, so filtering here would invent
        // semantics the rest of the app doesn't have.
        val subscriptions = subscriptionRepository.getActiveSubscriptions().first()

        _data.value = ShareCardData(
            transactionCount = transactions.size,
            topCategories = categories,
            subscriptionCount = subscriptions.size,
            periodLabel = when (period) {
                SharePeriod.THIS_MONTH ->
                    now.format(MONTH_LABEL).uppercase()
                SharePeriod.LAST_MONTH ->
                    now.minusMonths(1).format(MONTH_LABEL).uppercase()
                SharePeriod.ALL_TIME -> "ALL TIME"
            },
        )
    }
}
