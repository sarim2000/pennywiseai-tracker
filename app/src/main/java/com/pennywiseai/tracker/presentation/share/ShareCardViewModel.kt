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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
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

    /**
     * Period the caller asked for, if any. Held separately because it can arrive before
     * the saved config finishes loading: the sheet's LaunchedEffect fires on first
     * composition, while [init] is still suspended reading DataStore. Without this the
     * load completes second and silently clobbers the override — the monthly prompt then
     * opens on the current (nearly empty) month instead of the finished one.
     */
    private var pendingPeriodOverride: SharePeriod? = null

    /**
     * The period as persisted, mirrored here so an override can be undone without an
     * async DataStore read (which could race the write [updateConfig] just started).
     * Null until the saved config has loaded.
     */
    private var savedPeriod: SharePeriod? = null

    /**
     * Gates [data]: queries wait for the saved config so the card never loads the
     * default period's data only to flip to the saved one a frame later.
     */
    private val configLoaded = MutableStateFlow(false)

    /**
     * Edits made before the saved config finished loading, kept so the load can replay
     * them on top of the snapshot it read. Simply letting either side win loses data:
     * the snapshot winning reverts the user's tap, and the edit winning persists a
     * candidate derived from the *default* config — a pre-load period tap would
     * silently reset a saved non-default hero.
     */
    private val preLoadEdits = mutableListOf<(ShareCardConfig) -> ShareCardConfig>()

    init {
        viewModelScope.launch {
            val saved = userPreferencesRepository.shareCardConfig.first()
            // An explicit period change before the load already recorded the newest
            // saved period; only fall back to the snapshot when it didn't.
            if (savedPeriod == null) savedPeriod = saved.period
            var merged = pendingPeriodOverride?.let { saved.copy(period = it) } ?: saved
            preLoadEdits.forEach { merged = it(merged) }
            _config.value = merged
            if (preLoadEdits.isNotEmpty()) {
                preLoadEdits.clear()
                // The pre-load updateConfig persisted its default-derived candidate;
                // re-persist the merge to put the fields it never touched back.
                userPreferencesRepository.setShareCardConfig(merged.copy(period = savedPeriod!!))
            }
            configLoaded.value = true
        }
    }

    /**
     * Everything on the card, or null while it is still being computed — the sheet keeps
     * Share disabled on null so a half-refreshed card can never be exported.
     *
     * Built as one reactive pipeline rather than one-shot reads because this ViewModel
     * outlives a single viewing of the sheet: any snapshot taken "on open" goes stale the
     * moment the underlying data changes while the sheet is closed. Deriving from the
     * live flows means a profile switch, an account being reassigned between profiles,
     * or new transactions all reach the card without anyone having to remember to
     * trigger a refresh. flatMapLatest drops the old period/profile's in-flight query
     * when the selection changes, and the onStart null publishes "loading" for the gap
     * so the previous selection's numbers are never shown — or shared — under the new
     * selection's label.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<ShareCardData?> =
        combine(
            configLoaded,
            _config,
            userPreferencesRepository.selectedProfileId,
        ) { loaded, config, profileId -> if (loaded) config.period to profileId else null }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { (period, profileId) -> cardData(period, profileId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun cardData(period: SharePeriod, profileId: Long?): Flow<ShareCardData?> {
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

        return combine(
            transactionRepository.getTransactionsBetweenDates(start, end),
            accountBalanceRepository.getAllLatestBalances(),
            subscriptionRepository.getActiveSubscriptions(),
        ) { allTransactions, balances, subscriptions ->
            // Scope to the profile the user is currently viewing. Home does this for
            // every figure it shows (filterTransactionsByProfile); a recap that ignored
            // it would count a Business profile's card using the owner's personal
            // transactions too — wrong, and it would put those counts into an image
            // built for sharing.
            val transactions = filterTransactionsByProfile(
                allTransactions,
                profileId,
                buildProfileAccountKeys(balances),
            )

            // Ranked in Kotlin from the same filtered list rather than by a GROUP BY:
            // the query can't see the profile filter, and deriving both figures from one
            // list means the count and the categories can never describe different
            // populations.
            val categories = transactions
                .groupingBy { it.category }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
                )
                .take(3)
                .map { it.key }

            ShareCardData(
                transactionCount = transactions.size,
                topCategories = categories,
                // Subscriptions are deliberately not filtered: SubscriptionEntity
                // carries no profileId and Home doesn't scope them either, so filtering
                // here would invent semantics the rest of the app doesn't have.
                subscriptionCount = subscriptions.size,
                periodLabel = when (period) {
                    SharePeriod.THIS_MONTH ->
                        now.format(MONTH_LABEL).uppercase()
                    SharePeriod.LAST_MONTH ->
                        now.minusMonths(1).format(MONTH_LABEL).uppercase()
                    SharePeriod.ALL_TIME -> "ALL TIME"
                },
            )
        }.onStart<ShareCardData?> { emit(null) }
    }

    /**
     * Applies [update]. No emptiness guard is needed any more: with a single hero there is
     * no configuration that produces a blank card.
     */
    fun updateConfig(update: (ShareCardConfig) -> ShareCardConfig) {
        if (!configLoaded.value) preLoadEdits += update
        val candidate = update(_config.value)
        if (candidate.period != _config.value.period) {
            // An explicit choice in Customise supersedes whatever the caller opened on.
            pendingPeriodOverride = null
            savedPeriod = candidate.period
        }
        _config.value = candidate
        // Persist the user's choices, but never an active override's period: editing
        // the hero while the banner's override is showing shouldn't silently rewrite
        // the period the user saved in Customise.
        val persisted = savedPeriod
            ?.takeIf { pendingPeriodOverride != null }
            ?.let { candidate.copy(period = it) }
            ?: candidate
        viewModelScope.launch { userPreferencesRepository.setShareCardConfig(persisted) }
    }

    /**
     * Points the card at [period] for this viewing only, without writing it to
     * preferences — the monthly prompt opens on last month, but that shouldn't silently
     * rewrite a choice the user made in Customise. Null means "no override": because
     * this ViewModel survives the sheet closing, a later plain opening (the overflow
     * menu) must actively put the saved period back or it would inherit the previous
     * viewing's override.
     */
    fun setPeriodOverride(period: SharePeriod?) {
        pendingPeriodOverride = period
        // Before the saved config loads there is nothing to restore (and nothing shown);
        // init applies pendingPeriodOverride on top of the load, covering both cases.
        val target = period ?: savedPeriod ?: return
        if (_config.value.period != target) {
            _config.value = _config.value.copy(period = target)
        }
    }
}
